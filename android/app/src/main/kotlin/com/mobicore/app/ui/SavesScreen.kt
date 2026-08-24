package com.mobicore.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * Save and backup management: browse record stores, snapshot, restore and
 * reset. Every destructive action here takes a backup first.
 */
@Composable
fun SavesScreen(library: LibraryRepository, suiteId: String, onBack: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    val records = remember(suiteId, revision) { library.records(suiteId) }
    val stores = remember(suiteId, revision) { records.listStoreNames() }
    val backups = remember(suiteId, revision) { library.backupsFor(suiteId) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = MobiColors.Text)
                }
                Text("Dữ liệu lưu", color = MobiColors.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionCard(title = "KHO BẢN GHI", trailing = "${stores.size}") {
                Column {
                    if (stores.isEmpty()) {
                        Text("Chưa lưu gì.", color = MobiColors.TextDim, fontSize = 13.sp)
                    } else {
                        stores.forEach { name ->
                            val store = records.openStore(name, false)
                            FieldRow(
                                label = name,
                                value = "${store?.size() ?: 0} bản ghi · ${store?.byteSize() ?: 0} B",
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "SAO LƯU", trailing = "${backups.size}") {
                Column {
                    if (backups.isEmpty()) {
                        Text("Chưa có bản sao lưu nào.", color = MobiColors.TextDim, fontSize = 13.sp)
                    } else {
                        backups.forEach { name -> FieldRow(name, "bản sao lưu") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Sao lưu ngay", Modifier.weight(1f)) {
                            status = "Đã lưu vào ${library.backup(suiteId).substringAfterLast('/')}"
                            revision++
                        }
                        SecondaryButton("Khôi phục bản mới nhất", Modifier.weight(1f)) {
                            val latest = library.backupsFor(suiteId).lastOrNull()
                            status = if (latest == null) {
                                "Không có bản sao lưu nào để khôi phục"
                            } else {
                                val path = com.mobicore.core.storage.StorageLayout.join(
                                    library.storageLayout().backupDir(suiteId), latest,
                                )
                                library.restore(com.mobicore.core.storage.LocalVfs().read(path))
                                "Đã khôi phục $latest"
                            }
                            revision++
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "ĐẶT LẠI") {
                Column {
                    Text(
                        text = "Xoá toàn bộ dữ liệu lưu",
                        color = MobiColors.Bad,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            val path = library.resetGameData(suiteId)
                            status = "Đã xoá. Bản sao lưu: ${path.substringAfterLast('/')}"
                            revision++
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Một bản sao lưu luôn được tạo trước khi xoá.",
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        status?.let { message ->
            item { Text(message, color = MobiColors.Good, fontSize = 12.sp) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
