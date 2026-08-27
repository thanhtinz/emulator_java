package com.mobicore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.GameLibrary
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.model.GameProfile

/**
 * Home: a toolbar and the games.
 *
 * Shaped after the emulators people already use — one flat, sorted list of
 * what is installed, with find, sort and everything else on the toolbar, and
 * the one floating button that adds a game.
 *
 * The sections, cards and bottom tabs this screen used to carry were the app
 * talking about itself. A library screen's job is to get out of the way of the
 * game someone opened it to reach, and every row it spends on headings is a
 * row it does not spend on a game.
 */
@Composable
fun HomeScreen(
    library: LibraryRepository,
    games: List<LibraryEntry>,
    profiles: Map<String, GameProfile>,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val sortMode by library.librarySort.collectAsState()
    val shown = remember(games, profiles, query, sortMode) {
        library.sorted(if (query.isBlank()) games else library.search(query), sortMode)
    }

    Column(Modifier.fillMaxSize()) {
        ToolBar(
            library = library,
            query = query,
            searching = searching,
            onQuery = { query = it },
            onSearching = {
                searching = it
                if (!it) query = ""
            },
            onTools = onTools,
            onSettings = onSettings,
        )

        if (games.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.VideogameAsset,
                title = "Chưa có trò chơi nào",
                body = "Chọn một tệp .jar, hoặc cặp .jar và .jad, để bắt đầu.",
                action = onImport,
                actionLabel = "Nhập trò chơi",
            )
            return@Column
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown) { index, entry ->
                    GameRow(library, entry, profiles[entry.suiteId()], onOpen)
                    if (index < shown.size - 1) {
                        HorizontalDivider(
                            Modifier.padding(start = 72.dp),
                            color = MobiColors.Border,
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            "Không tìm thấy. Thử một từ khoá khác.",
                            color = MobiColors.TextDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                // Room for the floating button to sit clear of the last row.
                item { Spacer(Modifier.height(88.dp)) }
            }

            // Importing is the first thing a new install must do and the
            // reason for most later visits, so it gets the one floating
            // button on the screen.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MobiColors.Accent)
                    .clickable(onClick = onImport),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Nhập trò chơi",
                    tint = MobiColors.Background,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * The toolbar: the app's name, and on the right the three things that act on
 * the list — find one, order them, everything else.
 *
 * Searching takes the toolbar over rather than living in a box below it. A
 * field that is always on screen costs a row of games on every visit, and
 * most visits are not searches.
 */
@Composable
private fun ToolBar(
    library: LibraryRepository,
    query: String,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onSearching: (Boolean) -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MobiColors.Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Tìm trò chơi") },
                keyboardOptions = KeyboardOptions.Default,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSearching(false) }) {
                Icon(Icons.Filled.Close, contentDescription = "Đóng tìm kiếm",
                    tint = MobiColors.TextDim)
            }
            return@Row
        }

        Text(
            text = "MobiCore",
            color = MobiColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        IconButton(onClick = { onSearching(true) }) {
            Icon(Icons.Filled.Search, contentDescription = "Tìm", tint = MobiColors.TextDim)
        }
        Box {
            IconButton(onClick = { sortOpen = true }) {
                Icon(Icons.Filled.Sort, contentDescription = "Sắp xếp",
                    tint = MobiColors.TextDim)
            }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SortItem(library, "Theo tên", GameLibrary.SORT_TITLE) { sortOpen = false }
                SortItem(library, "Vừa chơi", GameLibrary.SORT_RECENT) { sortOpen = false }
                SortItem(library, "Nhà phát hành", GameLibrary.SORT_VENDOR) { sortOpen = false }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Thêm",
                    tint = MobiColors.TextDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (MobiColors.dark) "Giao diện sáng" else "Giao diện tối") },
                    leadingIcon = {
                        Icon(
                            if (MobiColors.dark) Icons.Filled.BrightnessHigh
                            else Icons.Filled.Brightness4,
                            contentDescription = null,
                        )
                    },
                    onClick = { library.cycleTheme(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("Công cụ") },
                    leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    onClick = { menuOpen = false; onTools() },
                )
                DropdownMenuItem(
                    text = { Text("Cài đặt") },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onSettings() },
                )
            }
        }
    }
    HorizontalDivider(color = MobiColors.Border)
}

@Composable
private fun SortItem(library: LibraryRepository, label: String, mode: Int, onPicked: () -> Unit) {
    val current by library.librarySort.collectAsState()
    DropdownMenuItem(
        text = {
            Text(label, color = if (current == mode) MobiColors.Accent else MobiColors.Text)
        },
        onClick = {
            library.setLibrarySort(mode)
            onPicked()
        },
    )
}

/**
 * One game: its icon, its name, and underneath the vendor and version.
 *
 * A flat row rather than a card. A list of eighty games in eighty cards is
 * eighty rectangles to look past, and the icon already tells one row from the
 * next.
 */
@Composable
fun GameRow(
    library: LibraryRepository,
    entry: LibraryEntry,
    profile: GameProfile?,
    onOpen: (String) -> Unit,
) {
    val artwork = remember(entry.suiteId()) { decodeArtwork(library.artwork(entry.suiteId())) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(entry.suiteId()) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameArtwork(entry.title(), artwork, size = 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title(),
                    color = MobiColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile?.isFavourite == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Yêu thích",
                        tint = MobiColors.Warn,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row {
                Text(
                    text = entry.vendor(),
                    color = MobiColors.TextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.version(),
                    color = MobiColors.TextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
