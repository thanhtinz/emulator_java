package com.mobicore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.core.model.GameProfile

/**
 * Key metrics taken from J2ME Loader's on-screen keypad, which is the one
 * every player of these games already has their thumbs trained on.
 *
 * Its keys are square and sized off the screen rather than off a designer's
 * guess: `keySize = min(width, height) / 6.5` upright, `max(width, height) /
 * 12` when the phone is turned. The two softkeys are the one exception —
 * `PHONE_KEY_SCALE_X = 2.0f`, `PHONE_KEY_SCALE_Y = 0.75f` — so they read as a
 * wide, shallow bar rather than as two more keys in the grid.
 */
private const val KEY_DIVISOR_UPRIGHT = 6.5f
private const val KEY_DIVISOR_TURNED = 12f
private const val SOFT_SCALE_X = 2.0f
private const val SOFT_SCALE_Y = 0.75f
/** A hair of daylight between keys; J2ME Loader snaps its keys together. */
private val GAP = 4.dp

@Composable
private fun uprightKeySize(): Dp {
    val configuration = LocalConfiguration.current
    return (minOf(configuration.screenWidthDp, configuration.screenHeightDp)
        / KEY_DIVISOR_UPRIGHT).dp
}

/**
 * Bàn phím ảo của điện thoại.
 *
 * The directional pad sits on the right: that is the thumb most people use
 * while the other hand holds the phone, and it matches how a handset was held
 * when these games were made.
 *
 * Buttons report press and release separately, because a J2ME game reads held
 * keys through `GameCanvas.getKeyStates` and a press-only button reads as if
 * the player were stuck against a wall.
 */
@Composable
fun Keypad(
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    leftSoftKey: String?,
    rightSoftKey: String?,
    modifier: Modifier = Modifier,
    /** Which keys to show; see `GameProfile.KEYPAD_*`. */
    layout: Int = GameProfile.KEYPAD_FULL,
    /**
     * False while the emulated screen carries the command bar: that bar is
     * drawn with the game's own labels and a tap on it runs the command, so
     * two more keys saying the same two words are two ways to do one thing.
     * A game that goes full screen takes the bar away, and then these are the
     * only way left to reach a command.
     */
    showSoftKeys: Boolean = true,
) {
    val arrows = layout == GameProfile.KEYPAD_FULL || layout == GameProfile.KEYPAD_ARROWS
    val numbers = layout == GameProfile.KEYPAD_FULL || layout == GameProfile.KEYPAD_NUMBERS
    val key = uprightKeySize()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GAP * 3)) {
        // Directly under the screen, so they line up with the labels the
        // system draws along its bottom edge, as they do on a handset.
        // The two softkeys and nothing else. The call, end and clear keys a
        // handset carried are gone from the pad: they were there because the
        // device was a phone, and on screen they crowd the keys games read.
        if (showSoftKeys) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SoftKey(leftSoftKey, "softLeft", onPress, onRelease, key)
                SoftKey(rightSoftKey, "softRight", onPress, onRelease, key)
            }
        }
        // A pad left on its own takes the middle: there is no reason to keep
        // a hole where the other half of the keypad used to be.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                if (arrows && numbers) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (numbers) {
                NumericPad(onPress, onRelease, key)
            }
            if (arrows) {
                DirectionalPad(onPress, onRelease, key)
            }
        }
    }
}

/**
 * One hand's worth of keys, for when the phone is held sideways.
 *
 * The game keeps the middle of a landscape screen, because that is what the
 * player is looking at, and each hand gets a column: a shoulder key on top,
 * its pad in the middle, and the softkey the game labels at the bottom, where
 * the thumb already rests.
 */
@Composable
fun ControlColumn(
    directional: Boolean,
    softKeyLabel: String?,
    showSoftKey: Boolean,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Turned, J2ME Loader sizes its keys off the long edge. Its keypad floats
    // over the game, though, and this one has a column to itself, so the size
    // is also held to what the column can hold.
    val configuration = LocalConfiguration.current
    val turned = (maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
        / KEY_DIVISOR_TURNED).dp
    BoxWithConstraints(modifier.width(turned * 3 + GAP * 2 + 24.dp)) {
        val room = (maxHeight - GAP * 4) / (4 + SOFT_SCALE_Y)
        val key = minOf(turned, room)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (directional) {
                DirectionalPad(onPress, onRelease, key)
            } else {
                NumericPad(onPress, onRelease, key)
            }
            if (showSoftKey) {
                SoftKey(
                    label = softKeyLabel,
                    button = if (directional) "softLeft" else "softRight",
                    onPress = onPress,
                    onRelease = onRelease,
                    key = key,
                )
            }
        }
    }
}

/**
 * A softkey. Blank until the running screen registers a Command, and then
 * showing that command's label — which is the only way a player can reach a
 * MIDlet's menu.
 *
 * The two keys share a width and centre their labels, so neither reads as the
 * more important one; which side a key is on already says which command it
 * runs, because the label bar the system draws inside the screen sits directly
 * above it.
 */
@Composable
private fun SoftKey(
    label: String?,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    key: Dp,
) {
    var held by remember { mutableStateOf(false) }
    val bound = !label.isNullOrEmpty()
    val mark = if (button == "softLeft") "L" else "R"
    Box(
        Modifier
            // Two keys across, three quarters of a key tall: J2ME Loader's
            // own proportions for these two.
            .size(key * SOFT_SCALE_X, key * SOFT_SCALE_Y)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    held -> MobiColors.AccentDim
                    bound -> MobiColors.SurfaceAlt
                    else -> MobiColors.Background
                }
            )
            .border(1.dp, if (held) MobiColors.Accent else MobiColors.Border,
                RoundedCornerShape(12.dp))
            .holdable(button, onPress, onRelease) { held = it }
            .padding(horizontal = 12.dp),
    ) {
        // L and R name the key itself, the way every J2ME emulator labels
        // these two; the text in the middle is the game's command and changes
        // with the screen. With no command on it, the key is simply called
        // what it is — an "L" in the middle rather than an "L" in the corner
        // beside a dash standing in for something that is not there.
        if (bound) {
            Text(
                text = mark,
                color = MobiColors.Accent,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Text(
            text = if (bound) label!! else mark,
            color = if (bound) MobiColors.Text else MobiColors.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * The 3x4 grid, laid out the way a handset does.
 *
 * Digits only. The letters printed under them existed because multi-tap was
 * the only way that keypad could enter a name; this phone has a keyboard, and
 * it comes up by itself when a game asks for text.
 */
@Composable
private fun NumericPad(onPress: (String) -> Unit, onRelease: (String) -> Unit, key: Dp) {
    val rows = listOf(
        listOf("1" to "num1", "2" to "num2", "3" to "num3"),
        listOf("4" to "num4", "5" to "num5", "6" to "num6"),
        listOf("7" to "num7", "8" to "num8", "9" to "num9"),
        listOf("*" to "star", "0" to "num0", "#" to "hash"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { (label, button) ->
                    NumberKey(label, button, onPress, onRelease, key)
                }
            }
        }
    }
}

/** The directional cluster, with fire in the middle. */
/**
 * The directional cluster: eight ways, with fire in the middle.
 *
 * The corners are not keys of their own. MIDP has no diagonal key code and no
 * handset had a diagonal key — a corner of the pad was two directions held at
 * once, which is what these send.
 */
@Composable
private fun DirectionalPad(onPress: (String) -> Unit, onRelease: (String) -> Unit, key: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            ArrowKey(Icons.Filled.NorthWest, "upLeft", "Lên trái", onPress, onRelease, key, true)
            ArrowKey(Icons.Filled.KeyboardArrowUp, "up", "Lên", onPress, onRelease, key)
            ArrowKey(Icons.Filled.NorthEast, "upRight", "Lên phải", onPress, onRelease, key, true)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowKey(Icons.Filled.KeyboardArrowLeft, "left", "Trái", onPress, onRelease, key)
            FireKey(onPress, onRelease, key)
            ArrowKey(Icons.Filled.KeyboardArrowRight, "right", "Phải", onPress, onRelease, key)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            ArrowKey(Icons.Filled.SouthWest, "downLeft", "Xuống trái", onPress, onRelease, key, true)
            ArrowKey(Icons.Filled.KeyboardArrowDown, "down", "Xuống", onPress, onRelease, key)
            ArrowKey(Icons.Filled.SouthEast, "downRight", "Xuống phải", onPress, onRelease, key, true)
        }
    }
}

@Composable
private fun NumberKey(
    label: String,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    key: Dp,
) {
    var held by remember { mutableStateOf(false) }
    Column(
        Modifier
            .size(key)
            .clip(RoundedCornerShape(12.dp))
            .background(if (held) MobiColors.AccentDim else MobiColors.SurfaceAlt)
            .border(1.dp, if (held) MobiColors.Accent else MobiColors.Border, RoundedCornerShape(12.dp))
            .holdable(button, onPress, onRelease) { held = it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = MobiColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ArrowKey(
    icon: ImageVector,
    button: String,
    description: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    key: Dp,
    corner: Boolean = false,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(key)
            .clip(RoundedCornerShape(14.dp))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, RoundedCornerShape(14.dp))
            .holdable(button, onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        // The corners are quieter than the four main directions: there when a
        // game needs them, not competing for the thumb.
        Icon(icon, contentDescription = description, tint = MobiColors.Accent,
            modifier = Modifier.size(if (corner) key * 0.4f else key * 0.6f))
    }
}

@Composable
private fun FireKey(onPress: (String) -> Unit, onRelease: (String) -> Unit, key: Dp) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(key)
            .clip(RoundedCornerShape(14.dp))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, RoundedCornerShape(14.dp))
            .holdable("fire", onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        // "F" is what J2ME Loader writes here, and fire is what MIDP calls
        // it; this key has never been an "OK" button.
        Text("F", color = MobiColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Press and release, so held keys stay held. */
private fun Modifier.holdable(
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    onHeldChange: (Boolean) -> Unit,
): Modifier = this.pointerInput(button) {
    detectTapGestures(
        onPress = {
            onHeldChange(true)
            onPress(button)
            tryAwaitRelease()
            onHeldChange(false)
            onRelease(button)
        },
    )
}
