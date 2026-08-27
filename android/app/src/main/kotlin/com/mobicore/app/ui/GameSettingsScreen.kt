package com.mobicore.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.midp.MidpContext
import com.mobicore.core.model.DeviceProfile
import com.mobicore.core.model.GameProfile
import com.mobicore.core.model.InputProfile

/** Per-game configuration: device, display, audio, input and network. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSettingsScreen(library: LibraryRepository, suiteId: String, onBack: () -> Unit) {
    val profile = remember(suiteId) { library.profile(suiteId) }
    if (profile == null) {
        EmptyState(Icons.AutoMirrored.Filled.ArrowBack, "Không có hồ sơ",
            "Trò chơi này chưa được cài.", onBack, "Quay lại")
        return
    }

    var deviceId by remember { mutableStateOf(profile.device().id()) }
    var scaleMode by remember { mutableIntStateOf(profile.scaleMode()) }
    var frameLimit by remember { mutableIntStateOf(profile.frameLimit()) }
    var volume by remember { mutableIntStateOf(profile.volume()) }
    var showFps by remember { mutableStateOf(profile.showFps()) }
    var smoothing by remember { mutableStateOf(profile.smoothing()) }
    var networkMode by remember { mutableIntStateOf(profile.networkMode()) }
    var preset by remember { mutableStateOf(profile.input().presetName()) }

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
                                deviceId = fresh.device().id()
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
            SectionCard(title = "MÁY GIẢ LẬP", trailing = profile.device().keypadName()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceProfile.catalog().forEach { candidate ->
                        val selected = candidate.id() == deviceId
                        Text(
                            text = candidate.resolution(),
                            color = if (selected) MobiColors.Accent else MobiColors.TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .clickable {
                                    deviceId = candidate.id()
                                    persist { it.setDevice(candidate) }
                                },
                        )
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
                        val code = profile.input().keyCodeFor(button)
                        FieldRow(label, "${MidpContext.keyName(code)}  ($code)")
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
