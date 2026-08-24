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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Virtual buttons in the order the keypad shows them, with Vietnamese names. */
private val BUTTON_LABELS = listOf(
    "up" to "Lên",
    "down" to "Xuống",
    "left" to "Trái",
    "right" to "Phải",
    "fire" to "Chọn",
    "softLeft" to "Phím mềm 1",
    "softRight" to "Phím mềm 2",
)

@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
