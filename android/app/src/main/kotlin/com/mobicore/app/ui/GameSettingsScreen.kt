package com.mobicore.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.midp.MidpContext
import com.mobicore.core.model.GameProfile
import com.mobicore.core.model.GamepadProfile
import com.mobicore.core.model.HandsetIdentity
import com.mobicore.core.model.InputProfile

/** Per-game configuration: device, display, audio, input and network. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSettingsScreen(
    library: LibraryRepository,
    suiteId: String,
    onBack: () -> Unit,
    onArrangeKeys: () -> Unit = {},
) {
    val profile = remember(suiteId) { library.profile(suiteId) }
    if (profile == null) {
        EmptyState(Icons.AutoMirrored.Filled.ArrowBack, "Không có hồ sơ",
            "Trò chơi này chưa được cài.", onBack, "Quay lại")
        return
    }

    var scaleMode by remember { mutableIntStateOf(profile.scaleMode()) }
    var frameLimit by remember { mutableIntStateOf(profile.frameLimit()) }
    var volume by remember { mutableIntStateOf(profile.volume()) }
    var showFps by remember { mutableStateOf(profile.showFps()) }
    var smoothing by remember { mutableStateOf(profile.smoothing()) }
    var vibration by remember { mutableStateOf(profile.vibration()) }
    var networkMode by remember { mutableIntStateOf(profile.networkMode()) }
    var preset by remember { mutableStateOf(profile.input().presetName()) }
    var keyOpacity by remember { mutableIntStateOf(profile.keypadOpacity()) }
    var keyShape by remember { mutableIntStateOf(profile.keypadShape()) }
    var keyFade by remember { mutableIntStateOf(profile.keypadFadeDelay()) }
    var gamepadOn by remember { mutableStateOf(profile.gamepad().isEnabled) }
    var tiltOn by remember { mutableStateOf(profile.tilt().isEnabled) }
    var tiltSensitivity by remember { mutableIntStateOf(profile.tilt().sensitivity()) }
    var tiltAxes by remember { mutableIntStateOf(profile.tilt().axes()) }
    var tiltInverted by remember { mutableStateOf(profile.tilt().isInverted) }
    val handset = profile.identity()
    var handsetId by remember { mutableStateOf(handset.handsetId()) }
    // Bumped when a control is remapped: the pad profile is a plain object,
    // and Compose has no way of knowing a string inside it changed.
    var padRevision by remember { mutableIntStateOf(0) }

    // Everything below the automatic card is hidden until asked for: a
    // player who just wants to play should not have to scroll past a page of
    // switches to find out there is nothing they need to do.
    var showAdvanced by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(profile.setupNotes().toList()) }
    var auto by remember { mutableStateOf(profile.isAuto) }

    fun persist(mutate: (GameProfile) -> Unit) {
        mutate(profile)
        library.saveProfile(profile)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = MobiColors.Text)
                }
                Text("Cài đặt trò chơi", color = MobiColors.Text, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionCard(
                title = "ĐÃ TỰ CẤU HÌNH",
                trailing = if (auto) "tự động" else "đã chỉnh tay",
            ) {
                Column {
                    notes.forEach { note ->
                        Row(
                            Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MobiColors.Good,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(note, color = MobiColors.Text, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Không cần chỉnh gì để chơi.", color = MobiColors.TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            label = "Dò lại",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Refresh,
                        ) {
                            val fresh = library.autoSetup(suiteId)
                            if (fresh != null) {
                                notes = fresh.setupNotes().toList()
                                auto = true
                                preset = fresh.input().presetName()
                                scaleMode = fresh.scaleMode()
                                frameLimit = fresh.frameLimit()
                                networkMode = fresh.networkMode()
                            }
                        }
                        SecondaryButton(
                            label = if (showAdvanced) "Ẩn nâng cao" else "Nâng cao",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Tune,
                        ) { showAdvanced = !showAdvanced }
                    }
                }
            }
        }

        if (!showAdvanced) {
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        item {
            Text(
                "Chỉ chỉnh khi game chạy sai.",
                color = MobiColors.TextDim,
                fontSize = 12.sp,
            )
        }

        item {
            // One screen for every game, so this states it rather than
            // offering a choice nobody has a reason to make.
            SectionCard(title = "MÀN HÌNH", trailing = profile.device().keypadName()) {
                Column {
                    FieldRow("Kích thước", profile.device().resolution())
                    FieldRow("Kiểu bàn phím", profile.device().keypadName())
                }
            }
        }

        item {
            // Held sideways the keypad sits over the game itself, so how
            // solid it is decides how much of the game is left to look at.
            SectionCard(title = "BÀN PHÍM ẢO", trailing = "$keyOpacity%") {
                Column {
                    FieldRow("Độ rõ", "$keyOpacity%")
                    Slider(
                        value = keyOpacity.toFloat(),
                        onValueChange = { keyOpacity = it.toInt() },
                        onValueChangeFinished = { persist { it.setKeypadOpacity(keyOpacity) } },
                        valueRange = 20f..100f,
                    )
                    OptionRow("Hình phím", listOf("Bo góc", "Vuông", "Tròn"), keyShape) {
                        keyShape = it
                        persist { profile -> profile.setKeypadShape(it) }
                    }
                    SecondaryButton(
                        label = "Sắp xếp bàn phím",
                        modifier = Modifier.padding(top = 6.dp),
                        onClick = onArrangeKeys,
                    )
                    // It fades rather than disappears: a keypad that vanishes
                    // leaves the thumb hunting a blank screen.
                    OptionRow(
                        "Tự mờ khi không dùng",
                        listOf("Luôn rõ", "5 giây", "10 giây", "30 giây"),
                        listOf(0, 5, 10, 30).indexOf(keyFade).coerceAtLeast(0),
                    ) {
                        keyFade = listOf(0, 5, 10, 30)[it]
                        persist { profile -> profile.setKeypadFadeDelay(keyFade) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "HIỂN THỊ") {
                Column {
                    OptionRow("Phóng ảnh", listOf("Vừa khung", "Bội số nguyên", "Kéo đầy", "Nguyên cỡ"), scaleMode) {
                        scaleMode = it
                        persist { profile -> profile.setScaleMode(it) }
                    }
                    FieldRow("Giới hạn khung hình",
                        if (frameLimit == 0) "Không giới hạn" else "$frameLimit hình/giây")
                    Slider(
                        value = frameLimit.toFloat(),
                        onValueChange = { frameLimit = it.toInt() },
                        onValueChangeFinished = { persist { it.setFrameLimit(frameLimit) } },
                        valueRange = 0f..60f,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Làm mượt", color = MobiColors.TextDim, fontSize = 14.sp)
                            Text(
                                "Khử răng cưa cạnh chéo và làm mượt khi phóng to",
                                color = MobiColors.TextDim,
                                fontSize = 11.sp,
                            )
                        }
                        Switch(checked = smoothing, onCheckedChange = {
                            smoothing = it
                            persist { profile -> profile.setSmoothing(it) }
                        })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Hiện số khung hình", color = MobiColors.TextDim, fontSize = 14.sp)
                        Switch(checked = showFps, onCheckedChange = {
                            showFps = it
                            persist { profile -> profile.setShowFps(it) }
                        })
                    }
                }
            }
        }

        item {
            SectionCard(title = "ÂM THANH") {
                Column {
                    FieldRow("Âm lượng", "$volume%")
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { volume = it.toInt() },
                        onValueChangeFinished = { persist { it.setVolume(volume) } },
                        valueRange = 0f..100f,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The buzz was part of the game, so it is on; off is a
                        // real choice, because a game that vibrates on every
                        // hit cannot be played quietly next to someone.
                        Text("Rung", color = MobiColors.TextDim, fontSize = 14.sp)
                        Switch(checked = vibration, onCheckedChange = {
                            vibration = it
                            persist { profile -> profile.setVibration(it) }
                        })
                    }
                }
            }
        }

        item {
            // A controller gives back the one thing glass cannot: an edge to
            // feel for, so the player looks at the game instead of at their
            // thumbs.
            SectionCard(
                title = "TAY CẦM",
                trailing = if (gamepadOn) "Đang bật" else "Đang tắt",
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Dùng tay cầm", color = MobiColors.TextDim, fontSize = 14.sp)
                            Text(
                                "Tay cầm Bluetooth, tay cầm kẹp máy, hoặc bàn phím ngoài",
                                color = MobiColors.TextDim,
                                fontSize = 11.sp,
                            )
                        }
                        Switch(checked = gamepadOn, onCheckedChange = {
                            gamepadOn = it
                            library.setGamepadEnabled(suiteId, it)
                            padRevision++
                        })
                    }
                    if (gamepadOn) {
                        key(padRevision) {
                            GamepadProfile.PADS.forEach { pad ->
                                PadRow(library, suiteId, profile, pad) { padRevision++ }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(label = "Đặt lại tay cầm") {
                            library.resetGamepad(suiteId)
                            padRevision++
                        }
                    }
                }
            }
        }

        item {
            // No J2ME handset could do this, so it is not emulation but a way
            // to play: it suits a racing game steered left and right, and
            // suits nothing else — hence off until it is asked for.
            SectionCard(
                title = "NGHIÊNG MÁY",
                trailing = if (tiltOn) "Đang bật" else "Đang tắt",
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Nghiêng máy để lái", color = MobiColors.TextDim,
                                fontSize = 14.sp)
                            Text(
                                "Hợp với game đua; game khác thì máy sẽ tự đi khi xe xóc",
                                color = MobiColors.TextDim,
                                fontSize = 11.sp,
                            )
                        }
                        Switch(checked = tiltOn, onCheckedChange = {
                            tiltOn = it
                            persist { profile -> profile.tilt().setEnabled(it) }
                        })
                    }
                    if (tiltOn) {
                        FieldRow("Độ nhạy", "$tiltSensitivity%")
                        Slider(
                            value = tiltSensitivity.toFloat(),
                            onValueChange = { tiltSensitivity = it.toInt() },
                            onValueChangeFinished = {
                                persist { it.tilt().setSensitivity(tiltSensitivity) }
                            },
                            valueRange = 50f..200f,
                        )
                        OptionRow(
                            "Hướng",
                            listOf("Bốn hướng", "Chỉ trái phải", "Chỉ lên xuống"),
                            tiltAxes,
                        ) {
                            tiltAxes = it
                            persist { profile -> profile.tilt().setAxes(it) }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Đảo chiều", color = MobiColors.TextDim, fontSize = 14.sp)
                            Switch(checked = tiltInverted, onCheckedChange = {
                                tiltInverted = it
                                persist { profile -> profile.tilt().setInverted(it) }
                            })
                        }
                    }
                }
            }
        }

        item {
            // Game J2ME hỏi nó đang chạy trên máy nào rồi mới chọn bộ ảnh,
            // nhánh vẽ và mã phím. Đây là chỗ trả lời câu hỏi ấy, và là thứ
            // đầu tiên nên thử khi một game chạy sai mà không rõ vì sao.
            SectionCard(title = "MÁY GIẢ LẬP", trailing = handset.handset().name()) {
                Column {
                    Text(
                        "Game đọc microedition.platform rồi mới chọn cách chạy. " +
                            "Nghe thấy một cái tên lạ, nó rơi vào nhánh dành cho máy lạ.",
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    HandsetIdentity.CATALOG.forEach { candidate ->
                        val chosen = candidate.id() == handsetId
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    handsetId = candidate.id()
                                    persist { it.identity().setHandset(candidate.id()) }
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                candidate.name(),
                                color = if (chosen) MobiColors.Accent else MobiColors.Text,
                                fontSize = 14.sp,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(candidate.note(), color = MobiColors.TextDim, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Đúng chuỗi game đọc được: khi một game chạy sai vì
                    // tưởng mình ở trên máy khác, đây là thứ cần nhìn.
                    handset.all().forEach { (name, value) ->
                        FieldRow(name, value)
                    }
                    // Game đang chạy đã đọc xong câu này từ lúc mở màn.
                    Text(
                        "Đổi máy chỉ ăn từ lần mở game sau.",
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        item {
            SectionCard(title = "GÁN PHÍM", trailing = preset) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PresetChip("Nokia", preset) {
                            preset = "Nokia"
                            persist { it.setInput(InputProfile.nokia()) }
                        }
                        PresetChip("Sony Ericsson", preset) {
                            preset = "Sony Ericsson"
                            persist { it.setInput(InputProfile.sonyEricsson()) }
                        }
                        PresetChip("Samsung", preset) {
                            preset = "Samsung"
                            persist { it.setInput(InputProfile.samsung()) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BUTTON_LABELS.forEach { (button, label) ->
                        KeyRow(library, suiteId, profile, button, label)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Turbo where it belongs: beside the keys it acts on.
                    // Only fire and the numbers get it — a d-pad that repeats
                    // is a d-pad that stutters.
                    val turbo = profile.input().turboFor("fire")
                    OptionRow(
                        label = "Liên thanh phím Chọn",
                        options = listOf("Tắt", "Chậm", "Nhanh"),
                        selected = when {
                            turbo == 0 -> 0
                            turbo >= 100 -> 1
                            else -> 2
                        },
                    ) { index ->
                        library.setTurbo(
                            suiteId,
                            "fire",
                            when (index) {
                                1 -> 120
                                2 -> 50
                                else -> 0
                            },
                        )
                    }
                }
            }
        }

        item {
            // Somebody with eighty games has one answer to "how big, how
            // loud, how many frames". A preset is that answer with a name on
            // it: worked out here, applied to the rest.
            PresetCard(library, suiteId)
        }

        item {
            SectionCard(title = "MẠNG") {
                OptionRow("Truy cập mạng", listOf("Chặn", "Hỏi trước", "Cho phép"), networkMode) {
                    networkMode = it
                    persist { profile -> profile.setNetworkMode(it) }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * One virtual button and the key it sends, with the key changeable.
 *
 * The presets are a guess. A game written for one handset reads the code that
 * handset sent — plenty read '2' and '8' for up and down — and when the guess
 * is wrong the game simply does not respond, which reads as a broken emulator
 * rather than a wrong key. The list offers only codes a MIDlet of the era
 * might read: a free-text number box would let someone map a button to a code
 * no handset ever sent.
 */
@Composable
private fun KeyRow(
    library: LibraryRepository,
    suiteId: String,
    profile: GameProfile,
    button: String,
    label: String,
) {
    var open by remember { mutableStateOf(false) }
    val code = profile.input().keyCodeFor(button)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = MobiColors.TextDim, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Text(
                text = "${MidpContext.keyName(code)}  ($code)",
                color = MobiColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            InputProfile.keyChoices().forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${MidpContext.keyName(choice)}  ($choice)",
                            color = if (choice == code) MobiColors.Accent else MobiColors.Text,
                        )
                    },
                    onClick = {
                        library.setKeyMapping(suiteId, button, choice)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Saving these settings under a name, and putting a saved one back.
 *
 * The name is typed rather than picked, because the useful ones are the
 * player's own words — "điện thoại của tôi", "màn hình nhỏ" — and no list the
 * app writes would contain them.
 */
@Composable
private fun PresetCard(library: LibraryRepository, suiteId: String) {
    val presets by library.presets.collectAsState()
    var name by remember { mutableStateOf("") }

    SectionCard(title = "BỘ CẤU HÌNH", trailing = "${presets.size} bộ") {
        Column {
            if (presets.isEmpty()) {
                Text(
                    "Chưa có bộ nào. Lưu cấu hình của game này rồi áp cho các game khác.",
                    color = MobiColors.TextDim,
                    fontSize = 13.sp,
                )
            } else {
                presets.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(preset, color = MobiColors.Text, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Text(
                            "Áp dụng",
                            color = MobiColors.Accent,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { library.applyPreset(preset, suiteId) },
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "Xoá",
                            color = MobiColors.Bad,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { library.deletePreset(preset) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Tên bộ cấu hình") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Lưu",
                    color = if (name.isBlank()) MobiColors.TextDim else MobiColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = name.isNotBlank()) {
                        library.savePreset(name.trim(), suiteId)
                        name = ""
                    },
                )
            }
        }
    }
}

/** Virtual buttons in the order the keypad shows them, with Vietnamese names. */
private val BUTTON_LABELS = listOf(
    "up" to "Lên",
    "down" to "Xuống",
    "left" to "Trái",
    "right" to "Phải",
    "fire" to "Chọn",
    "softLeft" to "Phím mềm L",
    "softRight" to "Phím mềm R",
)

@Composable
/** A labelled row of mutually exclusive choices, used by both settings screens. */
fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MobiColors.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEachIndexed { index, option ->
                Text(
                    text = option,
                    color = if (index == selected) MobiColors.Accent else MobiColors.TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clickable { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun PresetChip(name: String, selected: String, onSelect: () -> Unit) {
    Text(
        text = name,
        color = if (name == selected) MobiColors.Accent else MobiColors.TextDim,
        fontSize = 12.sp,
        modifier = Modifier.clickable(onClick = onSelect),
    )
}

/**
 * One control on a real pad, and what it presses.
 *
 * The same shape as a key row, because it is the same question asked about a
 * different device: this control, that button. "Không dùng" is on the list on
 * purpose — a pad has buttons a J2ME game has no use for, and leaving one
 * doing nothing is better than leaving it doing something surprising.
 */
@Composable
private fun PadRow(
    library: LibraryRepository,
    suiteId: String,
    profile: GameProfile,
    pad: String,
    onChanged: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val button = profile.gamepad().mapping(pad)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(GamepadProfile.padName(pad), color = MobiColors.TextDim, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Text(
                text = padButtonLabel(button),
                color = if (button.isEmpty()) MobiColors.TextDim else MobiColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf("") + InputProfile.BUTTONS.toList()).forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            padButtonLabel(choice),
                            color = if (choice == button) MobiColors.Accent else MobiColors.Text,
                        )
                    },
                    onClick = {
                        library.setPadMapping(suiteId, pad, choice)
                        onChanged()
                        open = false
                    },
                )
            }
        }
    }
}

/** What the settings screen calls one of the emulator's own buttons. */
private fun padButtonLabel(button: String): String = when {
    button.isEmpty() -> "Không dùng"
    button == "up" -> "Lên"
    button == "down" -> "Xuống"
    button == "left" -> "Trái"
    button == "right" -> "Phải"
    button == "fire" -> "Bắn"
    button == "softLeft" -> "Phím mềm trái"
    button == "softRight" -> "Phím mềm phải"
    button == "star" -> "Phím *"
    button == "hash" -> "Phím #"
    button == "clear" -> "Xoá"
    button.startsWith("num") -> "Phím ${button.removePrefix("num")}"
    else -> button
}
