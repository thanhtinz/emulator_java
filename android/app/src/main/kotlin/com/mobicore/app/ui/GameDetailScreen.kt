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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    if (entry == null) {
        EmptyState(Icons.AutoMirrored.Filled.ArrowBack, "Game not found",
            "It may have been uninstalled.", onBack, "Back")
        return
    }

    val profile = profiles[suiteId]
    val artwork = remember(suiteId) { decodeArtwork(library.artwork(suiteId)) }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
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
                        contentDescription = "Favourite",
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
                    Text(entry.title(), color = MobiColors.Text, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)
                    Text(entry.vendor(), color = MobiColors.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(entry.configuration())
                        Chip(entry.profile())
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Play", Modifier.weight(1f), onPlay)
                SecondaryButton("Settings", Modifier.weight(1f), onSettings)
            }
        }

        item {
            SectionCard(title = "DETAILS") {
                Column {
                    FieldRow("Version", entry.version())
                    FieldRow("Suite id", entry.suiteId())
                    FieldRow("Size", formatBytes(entry.jarSize()))
                    FieldRow("Device", profile?.device()?.toString() ?: "—")
                    FieldRow("Scaling", profile?.scaleModeName() ?: "—")
                    FieldRow("Times played", (profile?.playCount() ?: 0).toString())
                }
            }
        }

        item {
            SectionCard(title = "SAVES", trailing = "${stores.size} store") {
                Column {
                    if (stores.isEmpty()) {
                        Text("This game has not saved anything yet.",
                            color = MobiColors.TextDim, fontSize = 13.sp)
                    } else {
                        stores.forEach { name ->
                            val store = library.records(suiteId).openStore(name, false)
                            FieldRow(name, "${store?.size() ?: 0} records")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Manage saves and backups",
                        color = MobiColors.Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onSaves),
                    )
                }
            }
        }

        item {
            SectionCard(title = "DANGER ZONE") {
                Column {
                    Text(
                        text = if (confirmUninstall) "Tap again to uninstall" else "Uninstall game",
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
                        "Saves are backed up automatically before anything is removed.",
                        color = MobiColors.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
