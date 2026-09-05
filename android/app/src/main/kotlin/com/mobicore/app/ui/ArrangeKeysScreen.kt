package com.mobicore.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

/**
 * Sắp xếp lại bàn phím ảo.
 *
 * The keypad is laid out the way a handset was, because that is what the
 * thumbs of anyone who played these games are trained on. But no two hands are
 * the same and a phone is far bigger than a handset was: the fire key that sat
 * under one player's thumb is a stretch for the next.
 *
 * The keys themselves are the control — drag one and it stays there. There is
 * no list of coordinates to fill in, because nobody knows where a key belongs
 * until their thumb is on it.
 */
@Composable
fun ArrangeKeysScreen(
    library: LibraryRepository,
    suiteId: String,
    onBack: () -> Unit,
) {
    val profile = remember(suiteId) { library.profile(suiteId) }
    if (profile == null) {
        EmptyState(Icons.AutoMirrored.Filled.ArrowBack, "Không có hồ sơ",
            "Trò chơi này chưa được cài.", onBack, "Quay lại")
        return
    }
    val keys = profile.keypadArrangement()
    // Bumped on every drag so the keypad redraws: the arrangement is a plain
    // object, and Compose has no way of knowing a float inside it moved.
    var revision by remember { mutableIntStateOf(0) }
    /// Đang gõ tên cho bộ bàn phím sắp lưu.
    var naming by remember { mutableStateOf(false) }
    var layoutName by remember { mutableStateOf("") }
    var scale by remember { mutableIntStateOf(keys.scale()) }

    fun save() {
        library.saveProfile(profile)
        revision++
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Kéo phím tới chỗ vừa tay",
                color = MobiColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (keys.isCustom()) "Đã sửa — chạm Đặt lại để về như cũ"
                else "Bàn phím đang ở vị trí mặc định",
                color = MobiColors.TextDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cỡ phím", color = MobiColors.TextDim, fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text("$scale%", color = MobiColors.Text, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = scale.toFloat(),
                onValueChange = { scale = it.toInt() },
                onValueChangeFinished = { keys.setScale(scale); save() },
                valueRange = 60f..160f,
            )
            SecondaryButton(label = "Đặt lại") {
                keys.reset()
                scale = keys.scale()
                save()
            }

            // Bộ bàn phím: tay người chơi không đổi từ game này sang game
            // khác, nên sắp một lần rồi dùng lại là đúng cái người ta muốn.
            Spacer(Modifier.height(14.dp))
            Text("BỘ BÀN PHÍM", color = MobiColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            val layouts = remember(revision) { library.keypadLayouts() }
            val current = remember(revision) { library.currentKeypadLayout(suiteId) }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                layouts.forEach { item ->
                    val chosen = item.id() == current
                    Text(
                        text = item.name(),
                        color = if (chosen) MobiColors.Accent else MobiColors.TextDim,
                        fontSize = 13.sp,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable {
                                library.applyKeypadLayout(suiteId, item.id())
                                // Hồ sơ vừa được ghi lại, nên đọc lại cỡ phím
                                // từ đó chứ không giữ con số cũ trên màn hình.
                                scale = library.profile(suiteId)
                                    ?.keypadArrangement()?.scale() ?: scale
                                revision++
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = "+ Lưu",
                    color = MobiColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { naming = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (naming) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = layoutName,
                    onValueChange = { layoutName = it },
                    singleLine = true,
                    label = { Text("Tên bộ: Tay tôi, Cầm dọc…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Lưu bộ này",
                        color = MobiColors.Accent,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            if (layoutName.isNotBlank()) {
                                library.saveKeypadLayout(suiteId, layoutName)
                                layoutName = ""
                                naming = false
                                revision++
                            }
                        },
                    )
                    Text(
                        "Thôi",
                        color = MobiColors.TextDim,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { naming = false },
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            // The real keypad, at the real size, with the real arrangement:
            // arranging keys on a picture of a keypad would be arranging them
            // somewhere other than where they are used.
            key(revision) {
                Keypad(
                    onPress = {},
                    onRelease = {},
                    leftSoftKey = null,
                    rightSoftKey = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    layout = profile.keypadLayout(),
                    shape = profile.keypadShape(),
                    placement = KeyPlacement(
                        arrangement = keys,
                        onMove = { button, dx, dy ->
                            keys.move(button, keys.offsetX(button) + dx,
                                keys.offsetY(button) + dy)
                            save()
                        },
                    ),
                )
            }
        }
    }
}
