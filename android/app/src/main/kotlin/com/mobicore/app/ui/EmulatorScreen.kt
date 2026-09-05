package com.mobicore.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mobicore.app.data.LibraryRepository
import com.mobicore.app.emu.EmulatorEngine
import com.mobicore.app.emu.GamepadRouter
import com.mobicore.app.emu.TiltSensor
import com.mobicore.core.model.DeviceProfile
import com.mobicore.core.model.GameProfile

/**
 * Màn hình chơi: khung hình của trò chơi và bàn phím ảo.
 *
 * The system bars are hidden while a game runs. The emulated screen is small to
 * begin with, and giving up a strip of it to a status bar wastes the space the
 * player actually looks at.
 */
@Composable
fun EmulatorScreen(
    library: LibraryRepository,
    filesDir: String,
    suiteId: String,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val engine = remember(suiteId) {
        EmulatorEngine(filesDir, com.mobicore.app.emu.PhoneVibration(context))
    }
    val profiles by library.profiles.collectAsState()
    val profile = profiles[suiteId]
    LaunchedEffect(suiteId) {
        val loaded = library.load(suiteId)
        val active = library.profile(suiteId) ?: return@LaunchedEffect
        library.markPlayed(suiteId)
        engine.start(loaded, active)
        // Straight back to where the player left off. The state is restored
        // after the game has started, because starting is what loads its
        // classes and builds the machine the state goes into.
        library.readSaveState(suiteId)?.let { engine.restoreState(it) }
    }

    // Which way the phone is held is the game's decision, not the player's to
    // make every time: auto-setup turns a game written for a wide screen, and
    // the button in the bar is there for the ones that drew sideways on a
    // portrait handset.
    val landscape = profile?.orientation() == DeviceProfile.ORIENTATION_LANDSCAPE
    DisposableEffect(landscape) {
        val activity = context as? Activity
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // A controller belongs to whatever is on screen, and while a game is on
    // screen that is the game. Cleared on the way out so the d-pad goes back
    // to walking the library.
    DisposableEffect(engine) {
        GamepadRouter.engine = engine
        onDispose { GamepadRouter.engine = null }
    }

    // The sensor runs only while a game that asked for it is on screen: one
    // left running is a battery drained for a screen nobody is looking at.
    DisposableEffect(engine, profile?.tilt()?.isEnabled) {
        val wanted = profile?.tilt()?.isEnabled == true
        val sensor = if (wanted) {
            TiltSensor(context) { x, y -> engine.tilted(x, y) }.also { it.start() }
        } else {
            null
        }
        onDispose {
            sensor?.stop()
            engine.releaseTilt()
        }
    }

    // Immersive while playing, restored on the way out.
    DisposableEffect(suiteId) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            // Leaving a game saves it. On a phone, leaving is not always the
            // player's decision — a call, a notification, a flat battery —
            // and a J2ME game closed mid-level otherwise loses the level.
            engine.captureState()?.let { state ->
                library.writeSaveState(suiteId, state, engine.screenshot())
            }
            engine.stop()
        }
    }

    Column(Modifier.fillMaxSize().background(MobiColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "‹  Thư viện",
                color = MobiColors.Accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable { engine.stop(); onExit() },
            )
            // Giữa thanh này để trống có chủ ý: tên game thì MIDlet tự vẽ ở
            // trong màn hình của nó, còn cỡ màn hình và số hình mỗi giây là
            // con số của người viết máy ảo — một con số nhảy liên tục ngay
            // trên đầu màn game chỉ kéo mắt đi khỏi thứ đang chơi.
            Spacer(Modifier.weight(1f))
            Text(
                text = if (engine.paused) "Tiếp tục" else "Tạm ngưng",
                color = MobiColors.Accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable {
                    if (engine.paused) engine.resume() else engine.pause()
                },
            )
            GameMenu(engine, library, suiteId, profile, landscape, onExit)
        }

        // Reading the revision ties the softkey labels to the running screen: a
        // command that swaps screens swaps the labels with it.
        @Suppress("UNUSED_EXPRESSION")
        engine.commandRevision
        val wantsText = engine.isTextInputActive()
        // Read off the frame counter so the keypad's fade is recomputed as
        // the game runs; the session is what knows how long it has been since
        // the keypad was last touched.
        engine.frameCounter
        val keyShape = profile?.keypadShape() ?: GameProfile.KEY_SHAPE_ROUNDED
        val keyOpacity = engine.keypadOpacity()
        // Where the player dragged the keys. Read from the profile the
        // arranging screen edits, so the keypad played with is the keypad
        // that was arranged.
        val placement = remember(profile) { KeyPlacement(profile?.keypadArrangement()) }

        if (landscape && !wantsText && profile?.keypadHidden() != true) {
            // Held sideways, the game keeps the middle and each hand gets a
            // column. A keypad stacked under a wide screen would leave the
            // game a strip along the top.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ControlColumn(
                    directional = true,
                    softKeyLabel = engine.leftSoftKeyLabel(),
                    onPress = { engine.pressButton(it) },
                    onRelease = { engine.releaseButton(it) },
                    modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                    layout = profile?.keypadLayout() ?: GameProfile.KEYPAD_FULL,
                    shape = keyShape,
                    opacity = keyOpacity,
                    placement = placement,
                )
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    GameSurface(engine, library, suiteId)
                }
                ControlColumn(
                    directional = false,
                    softKeyLabel = engine.rightSoftKeyLabel(),
                    onPress = { engine.pressButton(it) },
                    onRelease = { engine.releaseButton(it) },
                    modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                    layout = profile?.keypadLayout() ?: GameProfile.KEYPAD_FULL,
                    shape = keyShape,
                    opacity = keyOpacity,
                    placement = placement,
                )
            }
            CrashDialog(engine, library, suiteId, profile, onExit)
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            GameSurface(engine, library, suiteId)
        }

        CrashDialog(engine, library, suiteId, profile, onExit)

        // While the game wants text, the phone's own keyboard takes this half
        // of the screen: multi-tap on a numeric pad was the only way a handset
        // could enter a name, and asking for that with a real keyboard in the
        // user's hand would be a museum exhibit.
        if (wantsText) {
            GameTextField(engine, Modifier.fillMaxWidth().padding(horizontal = 14.dp))
        } else if (profile?.keypadHidden() != true) {
            Keypad(
                onPress = { engine.pressButton(it) },
                onRelease = { engine.releaseButton(it) },
                leftSoftKey = engine.leftSoftKeyLabel(),
                rightSoftKey = engine.rightSoftKeyLabel(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                layout = profile?.keypadLayout() ?: GameProfile.KEYPAD_FULL,
                shape = keyShape,
                opacity = keyOpacity,
                placement = placement,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * The menu behind the toolbar, and the reason it exists.
 *
 * J2ME Loader keeps exactly this set behind its overflow — a screenshot,
 * which keys the keypad shows, which way the screen is locked, the way out —
 * because these are the things a player wants **while** a game is running and
 * cannot reach from a settings page they would have to quit to get to.
 */
@Composable
private fun GameMenu(
    engine: EmulatorEngine,
    library: LibraryRepository,
    suiteId: String,
    profile: com.mobicore.core.model.GameProfile?,
    landscape: Boolean,
    onExit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    Box {
        Text(
            text = "Menu",
            color = MobiColors.Accent,
            fontSize = 15.sp,
            modifier = Modifier.clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Chụp màn hình") },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                onClick = {
                    val png = engine.screenshot()
                    note = if (png != null) {
                        library.writeScreenshot(suiteId, png)
                        "Đã lưu ảnh chụp"
                    } else {
                        "Chưa có khung hình nào để chụp"
                    }
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text("Bàn phím") },
                trailingIcon = {
                    Text(
                        profile?.keypadLayoutName() ?: "Đầy đủ",
                        color = MobiColors.TextDim,
                        fontSize = 13.sp,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = { library.cycleKeypadLayout(suiteId) },
            )
            // Putting the keypad away is its own item, not a fourth keypad on
            // the cycle: coming back has to bring back the one that was there.
            DropdownMenuItem(
                text = { Text(if (profile?.keypadHidden() == true) "Hiện bàn phím" else "Ẩn bàn phím") },
                leadingIcon = { Icon(Icons.Filled.Keyboard, contentDescription = null) },
                onClick = { library.toggleKeypad(suiteId) },
            )
            DropdownMenuItem(
                text = { Text("Màn hình") },
                trailingIcon = {
                    Text(
                        if (landscape) "Ngang" else "Dọc",
                        color = MobiColors.TextDim,
                        fontSize = 13.sp,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.ScreenRotation, contentDescription = null) },
                onClick = { library.toggleOrientation(suiteId) },
            )
            // Four slots of the player's own, plus the automatic one the
            // emulator writes when the game is left. Saving before something
            // hard and coming back to it is what one slot per game cannot
            // do — quitting would overwrite it.
            (1..4).forEach { slot ->
                val used = library.saveSlots(suiteId).firstOrNull { it.slot == slot }?.used == true
                DropdownMenuItem(
                    text = { Text(if (used) "Lưu vào ô $slot (ghi đè)" else "Lưu vào ô $slot") },
                    leadingIcon = if (slot == 1) {
                        { Icon(Icons.Filled.Save, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        engine.captureState()?.let { state ->
                            library.writeSaveState(suiteId, slot, state, engine.screenshot())
                        }
                        note = "Đã lưu vào ô $slot"
                        open = false
                    },
                )
            }
            (1..4).forEach { slot ->
                val used = library.saveSlots(suiteId).firstOrNull { it.slot == slot }?.used == true
                if (used) {
                    DropdownMenuItem(
                        text = { Text("Nạp ô $slot") },
                        onClick = {
                            library.readSaveState(suiteId, slot)?.let { engine.restoreState(it) }
                            note = "Đã nạp ô $slot"
                            open = false
                        },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Thoát") },
                leadingIcon = { Icon(Icons.Filled.ExitToApp, contentDescription = null) },
                onClick = {
                    open = false
                    onExit()
                },
            )
        }
    }
    // Leaving the game saves it anyway, so nothing here needs a confirmation;
    // what it needs is to say it happened.
    note?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2000)
            note = null
        }
        Text(message, color = MobiColors.TextDim, fontSize = 12.sp)
    }
}

/**
 * The field the game is asking for, backed by the system keyboard.
 *
 * Whole strings rather than key events: an on-screen keyboard does its own
 * editing — caret moves, autocorrect, a paste — and what the game should see
 * is the result. The emulator applies the field's own limits to it.
 */
@Composable
private fun GameTextField(engine: EmulatorEngine, modifier: Modifier = Modifier) {
    var value by remember(engine.textInput()) { mutableStateOf(engine.textInput()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                engine.setTextInput(it)
            },
            singleLine = true,
            label = { Text("Nhập cho trò chơi") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { engine.pressButton("softLeft") }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                label = engine.leftSoftKeyLabel() ?: "Xong",
                modifier = Modifier.weight(1f),
            ) { engine.pressButton("softLeft") }
            SecondaryButton(
                label = engine.rightSoftKeyLabel() ?: "Quay lại",
                modifier = Modifier.weight(1f),
            ) { engine.pressButton("softRight") }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun GameSurface(engine: EmulatorEngine, library: LibraryRepository, suiteId: String) {
    val profiles by library.profiles.collectAsState()
    val profile = profiles[suiteId]

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(suiteId) {
                detectTapGestures(
                    onPress = { offset ->
                        val point = toGameCoordinates(engine, profile, offset, size.width, size.height)
                        if (point != null) {
                            engine.pointerDown(point.first, point.second)
                            tryAwaitRelease()
                            engine.pointerUp(point.first, point.second)
                        }
                    },
                )
            }
            .pointerInput(suiteId) {
                detectDragGestures { change, _ ->
                    val point = toGameCoordinates(engine, profile, change.position, size.width, size.height)
                    if (point != null) {
                        engine.pointerMove(point.first, point.second)
                    }
                }
            },
    ) {
        // Reading the counter inside the draw scope is what ties a repaint to
        // a finished emulator frame.
        @Suppress("UNUSED_EXPRESSION")
        engine.frameCounter

        val bitmap = engine.bitmap ?: return@Canvas
        val viewport = profile?.viewport(size.width.toInt(), size.height.toInt())
            ?: intArrayOf(0, 0, bitmap.width, bitmap.height)

        drawIntoCanvas { canvas ->
            // Smoothing is on by default. A handset packed this many pixels
            // into about two inches; drawing them as hard blocks on a modern
            // display looks more pixelated than the hardware ever did.
            val smooth = profile?.smoothing() ?: true
            val paint = android.graphics.Paint().apply {
                isFilterBitmap = smooth
                isAntiAlias = false
                isDither = false
            }
            val destination = android.graphics.Rect(
                viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3],
            )
            synchronized(engine.frameLock) {
                canvas.nativeCanvas.drawBitmap(bitmap, null, destination, paint)
            }
        }
    }
}

/** Maps a touch on the scaled surface back to emulated screen coordinates. */
private fun toGameCoordinates(
    engine: EmulatorEngine,
    profile: com.mobicore.core.model.GameProfile?,
    offset: Offset,
    width: Int,
    height: Int,
): Pair<Int, Int>? {
    val screenWidth = engine.screenWidth()
    val screenHeight = engine.screenHeight()
    if (screenWidth == 0 || screenHeight == 0) return null
    val viewport = profile?.viewport(width, height) ?: intArrayOf(0, 0, width, height)
    if (viewport[2] <= 0 || viewport[3] <= 0) return null
    val x = ((offset.x - viewport[0]) * screenWidth / viewport[2]).toInt()
    val y = ((offset.y - viewport[1]) * screenHeight / viewport[3]).toInt()
    if (x < 0 || y < 0 || x >= screenWidth || y >= screenHeight) return null
    return x to y
}

/**
 * Lời giải thích khi game chết.
 *
 * Chỗ này trước đây là một dòng chữ đỏ cỡ 12sp ghi tên lớp ngoại lệ, nằm lọt
 * thỏm giữa bàn phím ảo — thứ mà không người chơi nào đọc và cũng chẳng ai
 * hiểu. Game đã dừng hẳn rồi thì màn hình phải nói ra: hỏng cái gì, vì sao, và
 * làm gì tiếp. Hai nút là hai việc thật sự làm được lúc ấy.
 */
@Composable
private fun CrashDialog(
    engine: EmulatorEngine,
    library: LibraryRepository,
    suiteId: String,
    profile: GameProfile?,
    onExit: () -> Unit,
) {
    val crash = engine.crash ?: return
    var showDetail by remember(crash) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { },
        containerColor = MobiColors.Surface,
        title = {
            Text(crash.title(), color = MobiColors.Text, fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(crash.reason(), color = MobiColors.Text, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text(crash.advice(), color = MobiColors.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (showDetail) "Ẩn chi tiết kỹ thuật" else "Chi tiết kỹ thuật",
                    color = MobiColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { showDetail = !showDetail },
                )
                if (showDetail) {
                    Spacer(Modifier.height(6.dp))
                    // Nguyên văn tiếng Anh: đây là phần dành cho người sửa
                    // game, và dịch nó ra chỉ làm mất chỗ để tra cứu.
                    Text(
                        text = crash.technical() + "\n" + engine.crashStack.trim(),
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = "Chơi lại",
                color = MobiColors.Accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable {
                    engine.dismissCrash()
                    val active = profile ?: library.profile(suiteId)
                    val loaded = library.load(suiteId)
                    if (active != null) {
                        engine.start(loaded, active)
                    }
                }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        },
        dismissButton = {
            Text(
                text = "Đóng",
                color = MobiColors.TextDim,
                fontSize = 15.sp,
                modifier = Modifier.clickable {
                    engine.dismissCrash()
                    engine.stop()
                    onExit()
                }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        },
    )
}
