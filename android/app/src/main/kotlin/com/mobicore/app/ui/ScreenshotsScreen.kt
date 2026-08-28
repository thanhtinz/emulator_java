package com.mobicore.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ảnh chụp màn hình của một trò chơi.
 *
 * A screenshot nothing can show again is a dead end, and a J2ME game has no
 * way of showing anyone what happened in it — which is the whole reason the
 * menu can take one. They are kept in the app's own folder, so this is where
 * they are looked at, opened one at a time, and thrown away.
 */
@Composable
fun ScreenshotsScreen(
    library: com.mobicore.app.data.LibraryRepository,
    suiteId: String,
    onBack: () -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val names = remember(suiteId, revision) { library.screenshots(suiteId) }
    var opened by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MobiColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹  Quay lại",
                color = MobiColors.Accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Spacer(Modifier.weight(1f))
            Text(galleryCount(names), color = MobiColors.TextDim, fontSize = 13.sp)
        }

        if (names.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PhotoCamera,
                title = "Chưa có ảnh nào",
                body = "Trong lúc chơi, mở Menu rồi chọn \"Chụp màn hình\" hoặc \"Quay màn hình\".",
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(names, key = { it }) { name ->
                val bitmap = remember(name) {
                    decodeArtwork(library.readScreenshot(suiteId, name))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MobiColors.SurfaceAlt)
                        .clickable { opened = if (opened == name) null else name },
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (opened == name) {
                        // The one action a picture needs, shown on the picture
                        // rather than behind a long press nobody discovers.
                        IconButton(
                            onClick = {
                                library.deleteScreenshot(suiteId, name)
                                opened = null
                                revision++
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Xoá ảnh",
                                tint = MobiColors.Bad)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "3 ảnh" — how many are in here, for the heading. */
private fun galleryCount(names: List<String>): String = "${names.size} ảnh"

