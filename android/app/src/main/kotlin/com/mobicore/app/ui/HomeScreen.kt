package com.mobicore.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.GameLibrary
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.model.GameProfile

/**
 * Home: the search box, recently played, favourites, and everything else.
 *
 * There is no separate library tab. It only ever held a search field over the
 * same games this screen already lists, and a tab that duplicates the screen
 * beside it is a tab that makes people look in two places for one thing.
 *
 * Recently played comes first because reopening the game you were mid-way
 * through is the single most common thing to do with an emulator. Typing in
 * the search box replaces all of it with the matches: someone searching has
 * stopped browsing.
 */
@Composable
fun HomeScreen(
    library: LibraryRepository,
    games: List<LibraryEntry>,
    profiles: Map<String, GameProfile>,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
) {
    val recent = remember(games, profiles) { library.recentlyPlayed() }
    val favourites = remember(games, profiles) { library.favourites() }
    var query by remember { mutableStateOf("") }
    val sortMode by library.librarySort.collectAsState()
    val matches = remember(games, profiles, query, sortMode) {
        library.sorted(library.search(query), sortMode)
    }
    val searching = query.isNotBlank()

    if (games.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.VideogameAsset,
            title = "Chưa có trò chơi nào",
            body = "Chọn một tệp .jar, hoặc cặp .jar và .jad, để bắt đầu.",
            action = onImport,
            actionLabel = "Nhập trò chơi",
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "MobiCore",
                        color = MobiColors.Text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    // One tap, always in the same corner: light and dark is
                    // the setting people change often enough to want on the
                    // way rather than three screens in.
                    IconButton(onClick = { library.cycleTheme() }) {
                        Icon(
                            if (MobiColors.dark) Icons.Filled.BrightnessHigh
                            else Icons.Filled.Brightness4,
                            contentDescription = "Đổi giao diện sáng tối",
                            tint = MobiColors.Accent,
                        )
                    }
                }
            }

            item {
                // Marks are ignored on both sides: "nguoi chay" finds
                // "Người Chạy".
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Tìm theo tên hoặc nhà phát hành") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Xoá tìm kiếm")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (searching) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SortChip("Tên", GameLibrary.SORT_TITLE, sortMode) {
                            library.setLibrarySort(it)
                        }
                        SortChip("Vừa chơi", GameLibrary.SORT_RECENT, sortMode) {
                            library.setLibrarySort(it)
                        }
                        SortChip("Nhà phát hành", GameLibrary.SORT_VENDOR, sortMode) {
                            library.setLibrarySort(it)
                        }
                    }
                }
                item {
                    Text("${matches.size} kết quả", color = MobiColors.TextDim, fontSize = 12.sp)
                }
                items(matches, key = { "hit-" + it.suiteId() }) { entry ->
                    GameRow(library, entry, profiles[entry.suiteId()], onOpen)
                }
                if (matches.isEmpty()) {
                    item {
                        Text("Không tìm thấy. Thử một từ khoá khác.",
                            color = MobiColors.TextDim, fontSize = 14.sp)
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
                return@LazyColumn
            }

            if (recent.isNotEmpty()) {
                item {
                    Column {
                        Text("VỪA CHƠI", color = MobiColors.TextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recent, key = { it.suiteId() }) { entry ->
                                RecentTile(library, entry, onOpen)
                            }
                        }
                    }
                }
            }

            if (favourites.isNotEmpty()) {
                item { Text("YÊU THÍCH", color = MobiColors.TextDim, fontSize = 12.sp) }
                items(favourites, key = { "fav-" + it.suiteId() }) { entry ->
                    GameRow(library, entry, profiles[entry.suiteId()], onOpen)
                }
            }

            item { Text("TẤT CẢ TRÒ CHƠI", color = MobiColors.TextDim, fontSize = 12.sp) }
            items(games, key = { it.suiteId() }) { entry ->
                GameRow(library, entry, profiles[entry.suiteId()], onOpen)
            }

            // Room for the floating button to sit clear of the last row.
            item { Spacer(Modifier.height(88.dp)) }
        }

        // Importing is the first thing a new install must do and the reason for
        // most later visits, so it gets the one floating button on the screen:
        // small, always in the same corner, and never taking a band of the screen
        // away from the games.
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

@Composable
private fun RecentTile(library: LibraryRepository, entry: LibraryEntry, onOpen: (String) -> Unit) {
    val artwork = remember(entry.suiteId()) { decodeArtwork(library.artwork(entry.suiteId())) }
    Column(
        Modifier.width(96.dp).clickable { onOpen(entry.suiteId()) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameArtwork(entry.title(), artwork, size = 88)
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title(),
            color = MobiColors.Text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GameRow(
    library: LibraryRepository,
    entry: LibraryEntry,
    profile: GameProfile?,
    onOpen: (String) -> Unit,
) {
    val artwork = remember(entry.suiteId()) { decodeArtwork(library.artwork(entry.suiteId())) }
    SectionCard(modifier = Modifier.clickable { onOpen(entry.suiteId()) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameArtwork(entry.title(), artwork, size = 48)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title(),
                    color = MobiColors.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.vendor()} · ${entry.version()}",
                    color = MobiColors.TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Chip(profile?.device()?.resolution() ?: entry.profile())
        }
    }
}

/** One sort order, shown as a word rather than a control. */
@Composable
private fun SortChip(label: String, mode: Int, selected: Int, onSelect: (Int) -> Unit) {
    val active = mode == selected
    Text(
        text = label,
        color = if (active) MobiColors.Accent else MobiColors.TextDim,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable { onSelect(mode) }
            .padding(horizontal = 2.dp),
    )
}
