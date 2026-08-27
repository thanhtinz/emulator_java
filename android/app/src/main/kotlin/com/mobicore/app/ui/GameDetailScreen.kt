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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GameDetailScreen(
    library: LibraryRepository,
    suiteId: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onSaves: () -> Unit,
    onScreenshots: () -> Unit,
    onSaveSlots: () -> Unit,
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
    // Bumped when a shelf changes: the store is a plain object and Compose
    // has no way of knowing a list inside it moved.
    var shelfRevision by remember { mutableIntStateOf(0) }
    var newShelf by remember { mutableStateOf("") }

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
            // A shelf is how a person finds a game whose name they do not
            // remember, so putting one on a shelf belongs here, on the game.
            val shelves by library.collections.collectAsState()
            val onShelves = remember(suiteId, shelfRevision) { library.shelvesOf(suiteId) }
            SectionCard(
                title = "BỘ SƯU TẬP",
                trailing = if (onShelves.isEmpty()) null else "${onShelves.size}",
            ) {
                Column {
                    if (shelves.isEmpty()) {
                        Text(
                            "Chưa có bộ sưu tập nào. Tạo một cái để xếp game vào.",
                            color = MobiColors.TextDim,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shelves.forEach { name ->
                            val holds = onShelves.contains(name)
                            Text(
                                text = name,
                                color = if (holds) MobiColors.Background else MobiColors.Text,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (holds) MobiColors.Accent else MobiColors.SurfaceAlt
                                    )
                                    .clickable {
                                        library.toggleCollection(name, suiteId)
                                        shelfRevision++
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newShelf,
                        onValueChange = { newShelf = it },
                        singleLine = true,
                        placeholder = { Text("Tên bộ sưu tập mới") },
                        trailingIcon = {
                            Text(
                                "Thêm",
                                color = if (newShelf.isBlank()) MobiColors.TextDim
                                else MobiColors.Accent,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable(enabled = newShelf.isNotBlank()) {
                                        if (library.createCollection(newShelf)) {
                                            library.toggleCollection(newShelf, suiteId)
                                            newShelf = ""
                                            shelfRevision++
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    // How long it has held someone, which is the thing worth
                    // knowing about a collection of eighty.
                    FieldRow(
                        "Đã chơi",
                        GameProfile.playedName(profile?.playedMs() ?: 0L),
                    )
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
            // Only when there is more than one: a picker over a list of one
            // is a question with a single answer.
            val midlets = remember(suiteId) { library.midlets(suiteId) }
            if (midlets.size > 1) {
                val chosen = remember(suiteId, midlets) { library.chosenMidlet(suiteId) }
                SectionCard(title = "TRONG GÓI NÀY", trailing = "${midlets.size} ứng dụng") {
                    Column {
                        midlets.forEachIndexed { index, midlet ->
                            val active = if (chosen.isEmpty()) index == 0
                                else chosen == midlet.className()
                            Text(
                                text = midlet.name(),
                                color = if (active) MobiColors.Accent else MobiColors.Text,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { library.setMidlet(suiteId, midlet.className()) }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            val slots = remember(suiteId) { library.saveSlots(suiteId) }
            SectionCard(
                title = "Ô LƯU TRẠNG THÁI",
                trailing = "${slots.count { it.used }}/${slots.size}",
            ) {
                Text(
                    text = "Xem và xoá các ô đã lưu",
                    color = MobiColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onSaveSlots),
                )
            }
        }

        item {
            val shots = remember(suiteId) { library.screenshots(suiteId) }
            SectionCard(title = "ẢNH CHỤP", trailing = "${shots.size} ảnh") {
                Text(
                    text = if (shots.isEmpty()) {
                        "Trong lúc chơi, mở Menu rồi chọn \"Chụp màn hình\"."
                    } else {
                        "Xem lại ảnh đã chụp"
                    },
                    color = if (shots.isEmpty()) MobiColors.TextDim else MobiColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onScreenshots),
                )
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
