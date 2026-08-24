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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.GameLibrary
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.model.GameProfile

/** Library: search, sort and browse everything installed. */
@Composable
fun LibraryScreen(
    library: LibraryRepository,
    games: List<LibraryEntry>,
    profiles: Map<String, GameProfile>,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableIntStateOf(GameLibrary.SORT_TITLE) }

    val visible = remember(games, profiles, query, sortMode) {
        library.sorted(library.search(query), sortMode)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Library", color = MobiColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Import",
                color = MobiColors.Accent,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onImport),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search title or vendor") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip("Title", GameLibrary.SORT_TITLE, sortMode) { sortMode = it }
            SortChip("Recent", GameLibrary.SORT_RECENT, sortMode) { sortMode = it }
            SortChip("Vendor", GameLibrary.SORT_VENDOR, sortMode) { sortMode = it }
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.VideogameAsset,
                title = if (games.isEmpty()) "No games yet" else "Nothing matches",
                body = if (games.isEmpty()) {
                    "Import a .jar file, or a .jar and .jad pair, to get started."
                } else {
                    "Try a different search term."
                },
                action = if (games.isEmpty()) onImport else null,
                actionLabel = "Import game",
            )
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.suiteId() }) { entry ->
                GameRow(library, entry, profiles[entry.suiteId()], onOpen)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SortChip(label: String, mode: Int, selected: Int, onSelect: (Int) -> Unit) {
    val active = mode == selected
    Text(
        text = label,
        color = if (active) MobiColors.Accent else MobiColors.TextDim,
        fontSize = 12.sp,
        modifier = Modifier
            .clickable { onSelect(mode) }
            .padding(horizontal = 2.dp),
    )
}
