package com.mobicore.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * MobiCore's palette, in light and dark.
 *
 * Light is the default. A dark chrome looks handsome in a screenshot and is
 * tiring to read in daylight, which is where a phone mostly gets used; whoever
 * prefers dark says so once, in Settings or with the switch on the home
 * screen.
 *
 * The colours stay properties of one object so every screen can keep naming
 * them by role — `MobiColors.Text`, not `palette.text` threaded through
 * everything. [dark] is Compose state, so reading a colour inside a composable
 * subscribes to it and the whole app repaints when it changes.
 */
object MobiColors {

    var dark by mutableStateOf(false)

    val Background: Color get() = if (dark) Color(0xFF0E1116) else Color(0xFFF2F4F7)
    val Surface: Color get() = if (dark) Color(0xFF171C24) else Color(0xFFFFFFFF)
    val SurfaceAlt: Color get() = if (dark) Color(0xFF1F2630) else Color(0xFFE9EDF2)
    val Border: Color get() = if (dark) Color(0xFF2C3543) else Color(0xFFD3DAE3)
    val Text: Color get() = if (dark) Color(0xFFE6EDF3) else Color(0xFF16202B)
    val TextDim: Color get() = if (dark) Color(0xFF8B98A8) else Color(0xFF5C6B7A)

    /** Darker on light: the same blue on white is too pale to read. */
    val Accent: Color get() = if (dark) Color(0xFF4CC2FF) else Color(0xFF0A6FA8)
    val AccentDim: Color get() = if (dark) Color(0xFF1B4E68) else Color(0xFFD7EBF7)
    val Good: Color get() = if (dark) Color(0xFF56D364) else Color(0xFF1A7F37)
    val Warn: Color get() = if (dark) Color(0xFFE3B341) else Color(0xFF9A6700)
    val Bad: Color get() = if (dark) Color(0xFFF85149) else Color(0xFFC0342B)
}

/** Theme choices, matching `AppSettings` in the core. */
object ThemeChoice {
    const val LIGHT = 0
    const val DARK = 1
    const val SYSTEM = 2
}

private val DarkScheme
    get() = darkColorScheme(
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

private val LightScheme
    get() = lightColorScheme(
        primary = MobiColors.Accent,
        onPrimary = Color(0xFFFFFFFF),
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

@Composable
fun MobiCoreTheme(
    themeChoice: Int = ThemeChoice.LIGHT,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeChoice) {
        ThemeChoice.DARK -> true
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        else -> false
    }
    MobiColors.dark = useDark
    // The emulated handset's own title and softkey bars follow the app: a
    // dark strip stapled to the top of a light screen looks like a bug.
    com.mobicore.core.midp.SystemChrome.setDark(useDark)
    MaterialTheme(
        colorScheme = if (useDark) DarkScheme else LightScheme,
        content = content,
    )
}
