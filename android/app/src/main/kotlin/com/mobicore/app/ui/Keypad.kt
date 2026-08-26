package com.mobicore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Directly under the screen, so they line up with the labels the
        // system draws along its bottom edge, as they do on a handset.
        // The two softkeys and nothing else. The call, end and clear keys a
        // handset carried are gone from the pad: they were there because the
        // device was a phone, and on screen they crowd the keys games read.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftKey(leftSoftKey, "softLeft", onPress, onRelease, Modifier.weight(1f))
            SoftKey(rightSoftKey, "softRight", onPress, onRelease, Modifier.weight(1f))
        }
        // L and R at the outer edges, clear of both pads: they are the two
        // extra actions MIDP calls GAME_A and GAME_B, and a thumb should never
        // land on one by accident on its way to the numbers.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ShoulderKey("L", "gameLeft", onPress, onRelease)
            ShoulderKey("R", "gameRight", onPress, onRelease)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumericPad(onPress, onRelease)
            DirectionalPad(onPress, onRelease)
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
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        ShoulderKey(
            label = if (directional) "L" else "R",
            button = if (directional) "gameLeft" else "gameRight",
            onPress = onPress,
            onRelease = onRelease,
        )
        if (directional) {
            DirectionalPad(onPress, onRelease)
        } else {
            NumericPad(onPress, onRelease)
        }
        SoftKey(
            label = softKeyLabel,
            button = if (directional) "softLeft" else "softRight",
            onPress = onPress,
            onRelease = onRelease,
            modifier = Modifier.fillMaxWidth(0.86f),
        )
    }
}

/**
 * L or R: what MIDP calls GAME_A and GAME_B.
 *
 * No handset had shoulder buttons — the runtime read those actions off keys 7
 * and 9 — but a key labelled "7" tells a player nothing about what it does in
 * a racing game, and every player knows where an L and an R are.
 */
@Composable
private fun ShoulderKey(
    label: String,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(96.dp, 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, RoundedCornerShape(12.dp))
            .holdable(button, onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MobiColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    val bound = !label.isNullOrEmpty()
    Box(
        modifier
            .height(44.dp)
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
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (bound) label!! else "—",
            color = if (bound) MobiColors.Text else MobiColors.TextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
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
private fun NumericPad(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    val rows = listOf(
        listOf("1" to "num1", "2" to "num2", "3" to "num3"),
        listOf("4" to "num4", "5" to "num5", "6" to "num6"),
        listOf("7" to "num7", "8" to "num8", "9" to "num9"),
        listOf("*" to "star", "0" to "num0", "#" to "hash"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { (label, button) ->
                    NumberKey(label, button, onPress, onRelease)
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
private fun DirectionalPad(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ArrowKey(Icons.Filled.NorthWest, "upLeft", "Lên trái", onPress, onRelease, true)
            ArrowKey(Icons.Filled.KeyboardArrowUp, "up", "Lên", onPress, onRelease)
            ArrowKey(Icons.Filled.NorthEast, "upRight", "Lên phải", onPress, onRelease, true)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowKey(Icons.Filled.KeyboardArrowLeft, "left", "Trái", onPress, onRelease)
            FireKey(onPress, onRelease)
            ArrowKey(Icons.Filled.KeyboardArrowRight, "right", "Phải", onPress, onRelease)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ArrowKey(Icons.Filled.SouthWest, "downLeft", "Xuống trái", onPress, onRelease, true)
            ArrowKey(Icons.Filled.KeyboardArrowDown, "down", "Xuống", onPress, onRelease)
            ArrowKey(Icons.Filled.SouthEast, "downRight", "Xuống phải", onPress, onRelease, true)
        }
    }
}

@Composable
private fun NumberKey(
    label: String,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    Column(
        Modifier
            .size(62.dp, 46.dp)
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
    corner: Boolean = false,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(68.dp, 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, RoundedCornerShape(14.dp))
            .holdable(button, onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        // The corners are quieter than the four main directions: there when a
        // game needs them, not competing for the thumb.
        Icon(icon, contentDescription = description, tint = MobiColors.Accent,
            modifier = Modifier.size(if (corner) 22.dp else 30.dp))
    }
}

@Composable
private fun FireKey(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(68.dp, 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, RoundedCornerShape(14.dp))
            .holdable("fire", onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        Text("OK", color = MobiColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
