package com.mobicore.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.Artwork
import com.mobicore.app.data.LibraryRepository

/** Cover, metadata and the actions available for one installed game. */
@Composable
fun GameDetailScreen(
    library: LibraryRepository,
    suiteId: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onSaves: () -> Unit,
) {
    val profiles by library.profiles.collectAsState()
    val entry = library.games.collectAsState().value.firstOrNull { it.suiteId() == suiteId }
    var confirmUninstall by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf<String?>(null) }
    var coverRevision by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // The photo picker, rather than a storage permission: it hands back the
    // one picture the user chose and asks them for nothing else.
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val png = Artwork.pngFrom(context, uri)
            if (png == null) {
                coverError = "Không đọc được ảnh này."
            } else {
                library.setArtwork(suiteId, png)
                coverRevision++
            }
        }
    }

    if (entry == null) {
        EmptyState(Icons.AutoMirrored.Filled.ArrowBack, "Không tìm thấy trò chơi",
            "Có thể nó đã được gỡ.", onBack, "Quay lại")
        return
    }

    val profile = profiles[suiteId]
    val artwork = remember(suiteId, coverRevision) { decodeArtwork(library.artwork(suiteId)) }
    val stores = remember(suiteId) { library.records(suiteId).listStoreNames() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại",
                        tint = MobiColors.Text)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { library.toggleFavourite(suiteId) }) {
                    Icon(
                        imageVector = if (profile?.isFavourite == true) {
                            Icons.Filled.Star
                        } else {
                            Icons.Filled.StarBorder
                        },
                        contentDescription = "Yêu thích",
                        tint = if (profile?.isFavourite == true) MobiColors.Warn else MobiColors.TextDim,
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameArtwork(entry.title(), artwork, size = 84)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(entry.title(), color = MobiColors.Text, fontSize = 23.sp,
                        fontWeight = FontWeight.Bold)
                    Text(entry.vendor(), color = MobiColors.TextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    // Whether the game runs at all, decided at import: a
                    // J2ME game missing a package does not run badly, it
                    // fails to start with nothing on screen to explain it.
                    CompatibilityChip(profile?.compatibility() ?: 0)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(entry.configuration())
                        Chip(entry.profile())
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Chơi", Modifier.weight(1f), onPlay)
                SecondaryButton("Cài đặt", Modifier.weight(1f), onClick = onSettings)
            }
        }

        if (library.hasSaveState(suiteId)) {
            item {
                SectionCard(title = "ĐANG CHƠI DỞ") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shot = remember(suiteId, coverRevision) {
                            decodeArtwork(library.saveStateThumbnail(suiteId))
                        }
                        if (shot != null) {
                            Image(
                                bitmap = shot,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp, 96.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Chơi tiếp từ chỗ đã dừng", color = MobiColors.Text,
                                fontSize = 15.sp)
                            Text("Tự lưu khi bạn thoát game", color = MobiColors.TextDim,
                                fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Chơi tiếp", Modifier.fillMaxWidth(), onClick = onPlay)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Bỏ và chơi lại từ đầu",
                                color = MobiColors.TextDim,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    library.deleteSaveState(suiteId)
                                    coverRevision++
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "TÊN VÀ ẢNH BÌA") {
                Column {
                    FieldRow("Tên hiển thị", entry.title())
                    if (entry.isRenamed) {
                        FieldRow("Tên gốc", entry.originalTitle())
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            label = if (entry.isRenamed) "Đổi tên" else "Đặt tên",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Edit,
                        ) { renaming = true }
                        SecondaryButton(
                            label = "Chọn ảnh",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.PhotoLibrary,
                        ) {
                            pickCover.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                    if (entry.isRenamed || entry.hasArtwork()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Trả về mặc định",
                            color = MobiColors.Accent,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                library.resetTitle(suiteId)
                                library.resetArtwork(suiteId)
                                coverRevision++
                            },
                        )
                    }
                    if (coverError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(coverError!!, color = MobiColors.Bad, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            SectionCard(title = "THÔNG TIN") {
                Column {
                    FieldRow("Phiên bản", entry.version())
                    FieldRow("Mã bộ cài", entry.suiteId())
                    FieldRow("Dung lượng", formatBytes(entry.jarSize()))
                    FieldRow("Máy giả lập", profile?.device()?.toString() ?: "—")
                    FieldRow("Phóng ảnh", profile?.scaleModeName() ?: "—")
                    FieldRow("Số lần chơi", (profile?.playCount() ?: 0).toString())
                }
            }
        }

        item {
            SectionCard(title = "DỮ LIỆU LƯU", trailing = "${stores.size} kho") {
                Column {
                    if (stores.isEmpty()) {
                        Text("Trò chơi này chưa lưu gì.",
                            color = MobiColors.TextDim, fontSize = 13.sp)
                    } else {
                        stores.forEach { name ->
                            val store = library.records(suiteId).openStore(name, false)
                            FieldRow(name, "${store?.size() ?: 0} bản ghi")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Quản lý dữ liệu lưu và sao lưu",
                        color = MobiColors.Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onSaves),
                    )
                }
            }
        }

        item {
            SectionCard(title = "VÙNG NGUY HIỂM") {
                Column {
                    Text(
                        text = if (confirmUninstall) "Chạm lần nữa để gỡ" else "Gỡ trò chơi",
                        color = MobiColors.Bad,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            if (confirmUninstall) {
                                library.uninstall(suiteId, keepData = false)
                                onBack()
                            } else {
                                confirmUninstall = true
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Dữ liệu lưu luôn được sao lưu trước khi xoá bất cứ thứ gì.",
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (renaming) {
        RenameDialog(
            current = entry.title(),
            onDismiss = { renaming = false },
            onConfirm = { name ->
                library.rename(suiteId, name)
                renaming = false
            },
        )
    }
}

/**
 * Asks for the name a game should be listed under.
 *
 * A blank name is refused here rather than at the store, so the user finds
 * out while the keyboard is still up instead of through a failure afterwards.
 */
/** "Chạy tốt", or exactly what is missing. */
@Composable
private fun CompatibilityChip(level: Int) {
    val (label, colour) = when (level) {
        0 -> "Chạy tốt" to MobiColors.Good
        1 -> "Thiếu vài thứ" to MobiColors.Warn
        else -> "Chưa chạy được" to MobiColors.Bad
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (level >= 2) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = colour,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = colour, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tên trò chơi", color = MobiColors.Text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = text.isBlank(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("Lưu", color = MobiColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ", color = MobiColors.TextDim) }
        },
        containerColor = MobiColors.Surface,
    )
}
