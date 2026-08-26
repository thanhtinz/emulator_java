package com.mobicore.app.ui

import android.app.Activity
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    val engine = remember(suiteId) { EmulatorEngine(filesDir) }
    val profiles by library.profiles.collectAsState()
    val profile = profiles[suiteId]
    val context = LocalContext.current
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
            // Deliberately not the game's title: the MIDlet has its own title
            // bar inside the screen, and repeating it invites confusion with
            // the game's own commands.
            Text(
                text = "${engine.screenWidth()}×${engine.screenHeight()}  ·  " +
                    "${engine.measuredFps} hình/giây",
                color = MobiColors.TextDim,
                fontSize = 12.sp,
            )
            Text(
                text = if (engine.paused) "Tiếp tục" else "Tạm ngưng",
                color = MobiColors.Accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable {
                    if (engine.paused) engine.resume() else engine.pause()
                },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            GameSurface(engine, library, suiteId)
        }

        val error = engine.lastError
        if (error != null) {
            Text(
                text = error,
                color = MobiColors.Bad,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // Reading the revision ties the labels to the running screen: a command
        // that swaps screens swaps the softkeys with it.
        @Suppress("UNUSED_EXPRESSION")
        engine.commandRevision
        // While the game wants text, the phone's own keyboard takes this half
        // of the screen: multi-tap on a numeric pad was the only way a handset
        // could enter a name, and asking for that with a real keyboard in the
        // user's hand would be a museum exhibit.
        if (engine.isTextInputActive()) {
            GameTextField(engine, Modifier.fillMaxWidth().padding(horizontal = 14.dp))
        } else {
            Keypad(
                onPress = { engine.pressButton(it) },
                onRelease = { engine.releaseButton(it) },
                leftSoftKey = engine.leftSoftKeyLabel(),
                rightSoftKey = engine.rightSoftKeyLabel(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
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
