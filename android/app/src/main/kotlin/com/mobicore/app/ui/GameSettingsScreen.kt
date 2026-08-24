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
        EmptyState(Icons.AutoMirrored.Filled.ArrowBack, "No profile",
            "This game is not installed.", onBack, "Back")
        return
    }

    var deviceId by remember { mutableStateOf(profile.device().id()) }
    var scaleMode by remember { mutableIntStateOf(profile.scaleMode()) }
    var frameLimit by remember { mutableIntStateOf(profile.frameLimit()) }
    var volume by remember { mutableIntStateOf(profile.volume()) }
    var showFps by remember { mutableStateOf(profile.showFps()) }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MobiColors.Text)
                }
                Text("Game settings", color = MobiColors.Text, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionCard(title = "DEVICE PROFILE", trailing = profile.device().keypadName()) {
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
            SectionCard(title = "DISPLAY") {
                Column {
                    OptionRow("Scaling", listOf("Fit", "Integer", "Stretch", "Original"), scaleMode) {
                        scaleMode = it
                        persist { profile -> profile.setScaleMode(it) }
                    }
                    FieldRow("Frame limit", if (frameLimit == 0) "Unlimited" else "$frameLimit fps")
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
                        Text("Show FPS", color = MobiColors.TextDim, fontSize = 13.sp)
                        Switch(checked = showFps, onCheckedChange = {
                            showFps = it
                            persist { profile -> profile.setShowFps(it) }
                        })
                    }
                }
            }
        }

        item {
            SectionCard(title = "AUDIO") {
                Column {
                    FieldRow("Volume", "$volume%")
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
            SectionCard(title = "INPUT", trailing = preset) {
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
                    listOf("up", "down", "left", "right", "fire", "softLeft", "softRight")
                        .forEach { button ->
                            val code = profile.input().keyCodeFor(button)
                            FieldRow(button, "${MidpContext.keyName(code)} ($code)")
                        }
                }
            }
        }

        item {
            SectionCard(title = "NETWORK") {
                OptionRow("Access", listOf("Blocked", "Ask", "Allowed"), networkMode) {
                    networkMode = it
                    persist { profile -> profile.setNetworkMode(it) }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MobiColors.TextDim, fontSize = 13.sp)
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
