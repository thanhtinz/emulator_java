package com.mobicore.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.LibraryEntry

/**
 * Công cụ: nhìn vào bên trong một bộ cài, và tự thay tệp trong đó.
 *
 * Chia thành thẻ chứ không xếp thành một trang dài: bốn phần này trả lời bốn
 * câu hỏi khác nhau, và người đang tìm một tấm ảnh để thay không việc gì phải
 * cuộn qua danh sách lớp Java.
 */
@Composable
fun ToolsScreen(library: LibraryRepository, games: List<LibraryEntry>) {
    var selected by remember(games) { mutableStateOf(games.firstOrNull()?.suiteId()) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Vật phẩm", "Tệp game", "Bộ cài", "Lớp Java")

    if (games.isEmpty()) {
        Text(
            "Hãy cài một trò chơi để xem bên trong.",
            color = MobiColors.TextDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (games.size > 1) {
            GamePicker(games, selected) { selected = it }
        }
        TabRow(tabs, tab) { tab = it }

        val suiteId = selected
        if (suiteId != null) {
            when (tab) {
                0 -> TreasureTab(library, suiteId)
                1 -> ResourcesTab(library, suiteId)
                2 -> SuiteTab(library, suiteId)
                else -> ClassesTab(library, suiteId)
            }
        }
    }
}

/**
 * Tìm số vàng, số ngọc trong phần lưu — rồi đặt số mới.
 *
 * Phần lưu của game là một dãy byte không nhãn: không chỗ nào ghi "đây là số
 * vàng". Nhưng người chơi thì biết mình đang có bao nhiêu, nên cách làm là đi
 * ngược: gõ con số đang thấy, chơi cho nó đổi, gõ lại — chỗ nào đổi theo đúng
 * như vậy mới là chỗ thật.
 */
@Composable
private fun TreasureTab(library: LibraryRepository, suiteId: String) {
    var query by remember(suiteId) { mutableStateOf("") }
    var amount by remember(suiteId) { mutableStateOf("") }
    var chosen by remember(suiteId) { mutableStateOf<String?>(null) }
    var revision by remember(suiteId) { mutableIntStateOf(0) }
    var note by remember(suiteId) { mutableStateOf("") }
    val items = remember(suiteId, query, revision) { library.items(suiteId, query) }

    // Vòng tìm một vật phẩm mới: gõ số đang thấy, chơi cho nó đổi, gõ lại.
    var typed by remember(suiteId) { mutableStateOf("") }
    var round by remember(suiteId) { mutableIntStateOf(0) }
    var hits by remember(suiteId) { mutableIntStateOf(0) }
    var newName by remember(suiteId) { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Bảng vật phẩm: tìm theo tên, gõ số lượng, gửi.
        item {
            SectionCard(title = "VẬT PHẨM ĐÃ TÌM ĐƯỢC", trailing = "${items.size} thứ") {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Tìm vật phẩm…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (items.isEmpty()) {
                        Text(
                            "Chưa có vật phẩm nào. Dùng phần dưới để tìm số vàng, số " +
                                "thuốc… rồi đặt tên cho nó.",
                            color = MobiColors.TextDim,
                            fontSize = 11.sp,
                        )
                    }
                    items.forEach { item ->
                        val picked = item.id() == chosen
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { chosen = item.id() }
                                .padding(vertical = 6.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    item.name(),
                                    color = if (picked) MobiColors.Accent else MobiColors.Text,
                                    fontSize = 15.sp,
                                    fontWeight = if (picked) FontWeight.SemiBold
                                                 else FontWeight.Normal,
                                )
                                Text(
                                    "${item.amount()}",
                                    color = MobiColors.Accent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "${item.places().size} chỗ trong phần lưu  ·  " +
                                    "nhiều nhất ${item.ceiling()}",
                                color = MobiColors.TextDim,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        if (chosen != null) {
            item {
                val name = items.firstOrNull { it.id() == chosen }?.name() ?: ""
                SectionCard(title = "GỬI VÀO GAME", trailing = name) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { text ->
                                    amount = text.filter { it.isDigit() }.take(10)
                                },
                                singleLine = true,
                                label = { Text("Số lượng") },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Gửi",
                                color = MobiColors.Accent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        val value = amount.toLongOrNull()
                                        val id = chosen
                                        if (value != null && id != null) {
                                            val written = library.sendItem(suiteId, id, value)
                                            revision++
                                            note = if (written > 0) {
                                                "Đã gửi $value. Mở lại game để thấy."
                                            } else {
                                                "Số này không vừa chỗ game để dành cho nó."
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        if (note.isNotEmpty()) {
                            Text(note, color = MobiColors.TextDim, fontSize = 11.sp)
                        }
                        Text(
                            "Phần lưu được sao lưu trước khi sửa.",
                            color = MobiColors.TextDim,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // Tìm một vật phẩm mới: phần lưu không có nhãn, nên cách duy nhất là
        // đi ngược từ con số người chơi đang nhìn thấy.
        item {
            SectionCard(
                title = if (round == 0) "TÌM VẬT PHẨM MỚI" else "SỐ MỚI SAU KHI CHƠI TIẾP",
                trailing = if (round == 0) null else "$hits chỗ",
            ) {
                Column {
                    Text(
                        if (round == 0) {
                            "Mở game, nhìn số đang có (vàng, thuốc, ngọc…) rồi gõ vào đây."
                        } else {
                            "Chơi cho con số đổi đi rồi gõ số mới. Mỗi lần như vậy lọc bớt " +
                                "những chỗ chỉ tình cờ trùng số."
                        },
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { text -> typed = text.filter { it.isDigit() }.take(9) },
                        singleLine = true,
                        label = { Text("Con số đang thấy") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            if (round == 0) "Tìm" else "Lọc tiếp",
                            color = MobiColors.Accent,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                val value = typed.toLongOrNull() ?: return@clickable
                                hits = if (round == 0) {
                                    library.scanSave(suiteId, value).size
                                } else {
                                    library.narrowSave(suiteId, value).size
                                }
                                round++
                                typed = ""
                                note = if (hits == 1) "Còn đúng một chỗ — đặt tên rồi cất đi."
                                       else "Còn $hits chỗ. Chơi tiếp cho số đổi rồi lọc nữa."
                            },
                        )
                        if (round > 0) {
                            Text(
                                "Tìm lại từ đầu",
                                color = MobiColors.TextDim,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable {
                                    library.clearSaveScan()
                                    round = 0
                                    hits = 0
                                    typed = ""
                                    note = ""
                                },
                            )
                        }
                    }
                    if (round > 0) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            label = { Text("Đặt tên: Vàng, Thuốc hồi máu…") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cất vào bảng",
                            color = MobiColors.Accent,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                if (library.keepItem(suiteId, newName)) {
                                    revision++
                                    round = 0
                                    hits = 0
                                    newName = ""
                                    note = "Đã cất. Từ giờ chỉ cần gõ số lượng rồi gửi."
                                }
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Kho tài nguyên: mọi thứ nằm trong tệp game, và nút để thay.
 *
 * Loại của từng tệp đọc từ chính mấy byte đầu chứ không đoán theo tên, vì
 * game đời ấy đặt tên rất tuỳ hứng — một tấm PNG nằm trong data/12.dat là
 * chuyện thường.
 */
@Composable
private fun ResourcesTab(library: LibraryRepository, suiteId: String) {
    val context = LocalContext.current
    // Đọc lại sau mỗi lần thay, để dấu "đã thay" hiện ra ngay.
    var revision by remember(suiteId) { mutableIntStateOf(0) }
    val entries = remember(suiteId, revision) { library.resources(suiteId) }
    var picking by remember { mutableStateOf<String?>(null) }

    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val path = picking
        picking = null
        if (uri != null && path != null) {
            readBytes(context, uri)?.let { bytes ->
                library.replaceResource(suiteId, path, bytes)
                revision++
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "${entries.size} tệp trong game. Thay tệp nào thì tệp đó được phủ lên " +
                    "khi chơi; bản gốc trong bộ cài không bị đụng tới.",
                color = MobiColors.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(entries.size) { index ->
            val entry = entries[index]
            SectionCard(
                title = entry.kindName().uppercase(),
                trailing = if (entry.isReplaced) "đã thay" else entry.format(),
            ) {
                Column {
                    Text(entry.path(), color = MobiColors.Text, fontSize = 14.sp)
                    Text(
                        buildString {
                            append(formatBytes(entry.bytes().toLong()))
                            if (entry.width() > 0) {
                                append("  ·  ${entry.width()}×${entry.height()}")
                            }
                            if (entry.isReplaced) {
                                append("  ·  bản của ${entry.replacedBy()}")
                            }
                        },
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Thay tệp",
                            color = MobiColors.Accent,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                picking = entry.path()
                                chooser.launch(arrayOf("*/*"))
                            },
                        )
                        if (entry.isReplaced) {
                            Text(
                                "Trả về bản gốc",
                                color = MobiColors.Bad,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    library.restoreResource(suiteId, entry.path())
                                    revision++
                                },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SuiteTab(library: LibraryRepository, suiteId: String) {
    val suite = remember(suiteId) { library.load(suiteId) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            SectionCard(title = "MANIFEST / JAD") {
                Column {
                    suite.info().attributes().keys().forEach { key ->
                        FieldRow(key, suite.info().attributes().get(key) ?: "")
                    }
                }
            }
        }
        item {
            SectionCard(title = "CÁC MIDLET", trailing = "${suite.info().midlets().size}") {
                Column {
                    suite.info().midlets().forEach { midlet ->
                        FieldRow(midlet.name(), midlet.className())
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ClassesTab(library: LibraryRepository, suiteId: String) {
    val suite = remember(suiteId) { library.load(suiteId) }
    val names = remember(suiteId) { suite.archive().classNames() }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                "${names.size} lớp",
                color = MobiColors.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(names.size) { index ->
            Text(names[index], color = MobiColors.TextDim, fontSize = 12.sp)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Hàng thẻ ở đầu trang. */
@Composable
private fun TabRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .weight(1f)
                    .clickable { onPick(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (index == selected) MobiColors.Accent else MobiColors.TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun GamePicker(games: List<LibraryEntry>, selected: String?, onPick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 16.dp)) {
        items(games.size) { index ->
            val entry = games[index]
            Text(
                text = entry.title(),
                color = if (entry.suiteId() == selected) MobiColors.Accent else MobiColors.Text,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry.suiteId()) }
                    .padding(vertical = 5.dp),
            )
        }
    }
}

/** Tệp người chơi chọn, đọc hết vào bộ nhớ — tài nguyên game vốn nhỏ. */
private fun readBytes(context: Context, uri: android.net.Uri): ByteArray? = try {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
} catch (failed: java.io.IOException) {
    null
}
