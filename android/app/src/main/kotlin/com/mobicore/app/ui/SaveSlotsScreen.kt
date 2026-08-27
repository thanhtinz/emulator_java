package com.mobicore.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ô lưu trạng thái của một trò chơi.
 *
 * Four slots the player writes by hand and one the emulator writes when the
 * game is left. They are kept apart because quitting must not overwrite the
 * place someone saved deliberately, and each carries the screen as it looked:
 * coming back to four saves, the picture says which is which far faster than
 * a date does.
 */
@Composable
fun SaveSlotsScreen(library: LibraryRepository, suiteId: String, onBack: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val slots = remember(suiteId, revision) { library.saveSlots(suiteId) }

    Column(Modifier.fillMaxSize().background(MobiColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹  Quay lại",
                color = MobiColors.Accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Spacer(Modifier.weight(1f))
            Text("${slots.count { it.used }}/${slots.size} ô đã dùng",
                color = MobiColors.TextDim, fontSize = 13.sp)
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(slots, key = { it.slot }) { slot ->
                val thumbnail = remember(slot.slot, revision) {
                    decodeArtwork(library.saveStateThumbnail(suiteId, slot.slot))
                }
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(72.dp, 96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MobiColors.SurfaceAlt),
                        ) {
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (slot.slot == 0) "Tự động (khi thoát)" else "Ô ${slot.slot}",
                                color = MobiColors.Text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (slot.used) whenSaved(slot.savedAt) else "Trống",
                                color = MobiColors.TextDim,
                                fontSize = 13.sp,
                            )
                        }
                        if (slot.used) {
                            Text(
                                "Xoá",
                                color = MobiColors.Bad,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    library.deleteSaveState(suiteId, slot.slot)
                                    revision++
                                },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The moment a slot was written, in the phone's own format. */
private fun whenSaved(millis: Long): String {
    if (millis <= 0L) return "Trống"
    return SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
}
