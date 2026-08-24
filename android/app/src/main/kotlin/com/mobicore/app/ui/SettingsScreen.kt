package com.mobicore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.storage.StorageLayout

/** Application-wide settings and storage information. */
@Composable
fun SettingsScreen(library: LibraryRepository, games: List<LibraryEntry>) {
    val layout = remember { library.storageLayout() }
    val totalBytes = remember(games) { games.sumOf { it.jarSize() } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", color = MobiColors.Text, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        }

        item {
            SectionCard(title = "EMULATOR") {
                Column {
                    FieldRow("Configuration", "CLDC 1.0 / 1.1")
                    FieldRow("Profile", "MIDP 1.0 / 2.0")
                    FieldRow("Rendering", "Nearest neighbour, integer scale")
                }
            }
        }

        item {
            SectionCard(title = "STORAGE") {
                Column {
                    FieldRow("Installed games", games.size.toString())
                    FieldRow("Suites on disk", formatBytes(totalBytes))
                    FieldRow("Root", layout.root())
                    Spacer(Modifier.height(6.dp))
                    StorageLayout.TOP_LEVEL.forEach { directory ->
                        Text(
                            text = "MobiCore/$directory",
                            color = MobiColors.TextDim,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "SECURITY") {
                Column {
                    FieldRow("Sandbox", "One directory per game")
                    FieldRow("Filesystem access", "Import only, no broad permission")
                    FieldRow("Network", "Off until a game's profile allows it")
                }
            }
        }

        item {
            SectionCard(title = "ABOUT") {
                Column {
                    FieldRow("MobiCore", "1.0")
                    Text(
                        "A J2ME game platform: run, manage and customise Java ME games "
                            + "on a modern device.",
                        color = MobiColors.TextDim,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
