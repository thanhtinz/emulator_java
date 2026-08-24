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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MobiColors.Text)
                }
                Text("Saves", color = MobiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionCard(title = "RECORD STORES", trailing = "${stores.size}") {
                Column {
                    if (stores.isEmpty()) {
                        Text("Nothing saved yet.", color = MobiColors.TextDim, fontSize = 13.sp)
                    } else {
                        stores.forEach { name ->
                            val store = records.openStore(name, false)
                            FieldRow(
                                label = name,
                                value = "${store?.size() ?: 0} records · ${store?.byteSize() ?: 0} B",
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "BACKUPS", trailing = "${backups.size}") {
                Column {
                    if (backups.isEmpty()) {
                        Text("No snapshots yet.", color = MobiColors.TextDim, fontSize = 13.sp)
                    } else {
                        backups.forEach { name -> FieldRow(name, "snapshot") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Back up now", Modifier.weight(1f)) {
                            status = "Saved to ${library.backup(suiteId).substringAfterLast('/')}"
                            revision++
                        }
                        SecondaryButton("Restore latest", Modifier.weight(1f)) {
                            val latest = library.backupsFor(suiteId).lastOrNull()
                            status = if (latest == null) {
                                "There is no backup to restore"
                            } else {
                                val path = com.mobicore.core.storage.StorageLayout.join(
                                    library.storageLayout().backupDir(suiteId), latest,
                                )
                                library.restore(com.mobicore.core.storage.LocalVfs().read(path))
                                "Restored $latest"
                            }
                            revision++
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "RESET") {
                Column {
                    Text(
                        text = "Clear all saved data",
                        color = MobiColors.Bad,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            val path = library.resetGameData(suiteId)
                            status = "Cleared. Backup at ${path.substringAfterLast('/')}"
                            revision++
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A snapshot is taken automatically before anything is cleared.",
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
