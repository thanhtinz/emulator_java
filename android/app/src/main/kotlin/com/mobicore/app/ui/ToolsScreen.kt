package com.mobicore.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.LibraryEntry

/**
 * Developer tools: inspect a suite's descriptor, classes and resources without
 * running it.
 */
@Composable
fun ToolsScreen(library: LibraryRepository, games: List<LibraryEntry>) {
    var selected by remember(games) { mutableStateOf(games.firstOrNull()?.suiteId()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Công cụ", color = MobiColors.Text, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        }

        if (games.isEmpty()) {
            item {
                Text("Hãy cài một trò chơi để xem bên trong.", color = MobiColors.TextDim, fontSize = 14.sp)
            }
            return@LazyColumn
        }

        item {
            SectionCard(title = "BỘ CÀI") {
                Column {
                    games.forEach { entry ->
                        Text(
                            text = entry.title(),
                            color = if (entry.suiteId() == selected) MobiColors.Accent else MobiColors.Text,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = entry.suiteId() }
                                .padding(vertical = 5.dp),
                        )
                    }
                }
            }
        }

        val suiteId = selected
        if (suiteId != null) {
            val suite = remember(suiteId) { library.load(suiteId) }

            item {
                SectionCard(title = "MANIFEST / JAD") {
                    Column {
                        suite.info().attributes().keys().take(10).forEach { key ->
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

            item {
                SectionCard(title = "LỚP JAVA", trailing = "${suite.archive().classNames().size}") {
                    Column {
                        suite.archive().classNames().take(12).forEach { name ->
                            Text(name, color = MobiColors.TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                SectionCard(title = "TÀI NGUYÊN") {
                    Column {
                        suite.archive().names()
                            .filterNot { it.endsWith(".class") }
                            .take(12)
                            .forEach { name ->
                                FieldRow(name, formatBytes(
                                    (suite.archive().read(name)?.size ?: 0).toLong()))
                            }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
