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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.model.GameProfile

/**
 * Home: recently played, favourites and a shortcut into the library.
 *
 * Recently played comes first because reopening the game you were mid-way
 * through is the single most common thing to do with an emulator.
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
