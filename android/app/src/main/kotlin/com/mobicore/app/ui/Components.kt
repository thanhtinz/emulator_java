package com.mobicore.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Rounded panel used for every grouped block in the app. */
@Composable
fun SectionCard(
    title: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MobiColors.Surface)
            .border(1.dp, MobiColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = title,
                    color = MobiColors.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (trailing != null) {
                    Text(text = trailing, color = MobiColors.Accent, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
}

/** Label on the left, value on the right — the app's standard settings row. */
@Composable
fun FieldRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MobiColors.TextDim, fontSize = 14.sp)
        Text(
            text = value,
            color = MobiColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small rounded tag, used for CLDC/MIDP versions and status. */
@Composable
fun Chip(text: String, accent: Color = MobiColors.Accent, background: Color = MobiColors.AccentDim) {
    Text(
        text = text,
        color = accent,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Cover art, falling back to the game's initial. Many suites ship no icon, and
 * an empty square reads as a broken install.
 */
@Composable
fun GameArtwork(title: String, artwork: ImageBitmap?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MobiColors.SurfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Icons are tiny pixel art, so never smooth them.
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                modifier = Modifier.size(size.dp),
            )
        } else {
            Text(
                text = title.take(1).uppercase(),
                color = MobiColors.Accent,
                fontSize = (size / 2.4f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Empty state with a call to action. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: (() -> Unit)? = null,
               actionLabel: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MobiColors.TextDim, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(title, color = MobiColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        Text(body, color = MobiColors.TextDim, fontSize = 14.sp)
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            Text(
                text = actionLabel,
                color = MobiColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MobiColors.AccentDim)
                    .clickable(onClick = action)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

/** Decodes stored artwork bytes for Compose, or null when there is none. */
fun decodeArtwork(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    return runCatching {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
