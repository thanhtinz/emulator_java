package com.mobicore.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MobiCore's palette.
 *
 * Dark is the default because it is what the app is for: a bright chrome
 * around a small 240x320 screen washes out the pixel art inside it.
 */
object MobiColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF171C24)
    val SurfaceAlt = Color(0xFF1F2630)
    val Border = Color(0xFF2C3543)
    val Text = Color(0xFFE6EDF3)
    val TextDim = Color(0xFF8B98A8)
    val Accent = Color(0xFF4CC2FF)
    val AccentDim = Color(0xFF1B4E68)
    val Good = Color(0xFF56D364)
    val Warn = Color(0xFFE3B341)
    val Bad = Color(0xFFF85149)
}

private val DarkScheme = darkColorScheme(
    primary = MobiColors.Accent,
    onPrimary = Color(0xFF04222F),
    primaryContainer = MobiColors.AccentDim,
    onPrimaryContainer = MobiColors.Accent,
    background = MobiColors.Background,
    onBackground = MobiColors.Text,
    surface = MobiColors.Surface,
    onSurface = MobiColors.Text,
    surfaceVariant = MobiColors.SurfaceAlt,
    onSurfaceVariant = MobiColors.TextDim,
    outline = MobiColors.Border,
    error = MobiColors.Bad,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B6E99),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    outline = Color(0xFFD0D7DE),
)

@Composable
fun MobiCoreTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
