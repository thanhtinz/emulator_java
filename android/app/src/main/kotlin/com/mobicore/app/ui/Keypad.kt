package com.mobicore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextAlign
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftKey(leftSoftKey, "softLeft", TextAlign.Start, onPress, onRelease,
                Modifier.weight(1f))
            SoftKey(rightSoftKey, "softRight", TextAlign.End, onPress, onRelease,
                Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PhoneKey("Gọi", "send", MobiColors.Good, onPress, onRelease, Modifier.weight(1f))
            PhoneKey("Xóa", "clear", MobiColors.Text, onPress, onRelease, Modifier.weight(1f))
            PhoneKey("Kết thúc", "end", MobiColors.Bad, onPress, onRelease, Modifier.weight(1f))
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
 * A softkey. Blank until the running screen registers a Command, and then
 * showing that command's label — which is the only way a player can reach a
 * MIDlet's menu.
 */
@Composable
private fun SoftKey(
    label: String?,
    button: String,
    align: TextAlign,
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
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.CenterEnd,
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

/** The call, clear and end trio every J2ME handset carried. */
@Composable
private fun PhoneKey(
    label: String,
    button: String,
    tint: androidx.compose.ui.graphics.Color,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (held) MobiColors.AccentDim else MobiColors.SurfaceAlt)
            .border(1.dp, if (held) MobiColors.Accent else MobiColors.Border,
                RoundedCornerShape(10.dp))
            .holdable(button, onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontSize = 13.sp)
    }
}

/** The 3x4 grid, laid out the way a handset does. */
@Composable
private fun NumericPad(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    val rows = listOf(
        listOf(Triple("1", "num1", ""), Triple("2", "num2", "abc"), Triple("3", "num3", "def")),
        listOf(Triple("4", "num4", "ghi"), Triple("5", "num5", "jkl"), Triple("6", "num6", "mno")),
        listOf(Triple("7", "num7", "pqrs"), Triple("8", "num8", "tuv"), Triple("9", "num9", "wxyz")),
        listOf(Triple("*", "star", ""), Triple("0", "num0", "+"), Triple("#", "hash", "")),
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { (label, button, hint) ->
                    NumberKey(label, hint, button, onPress, onRelease)
                }
            }
        }
    }
}

/** The directional cluster, with fire in the middle. */
@Composable
private fun DirectionalPad(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ArrowKey(Icons.Filled.KeyboardArrowUp, "up", "Lên", onPress, onRelease)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowKey(Icons.Filled.KeyboardArrowLeft, "left", "Trái", onPress, onRelease)
            FireKey(onPress, onRelease)
            ArrowKey(Icons.Filled.KeyboardArrowRight, "right", "Phải", onPress, onRelease)
        }
        ArrowKey(Icons.Filled.KeyboardArrowDown, "down", "Xuống", onPress, onRelease)
    }
}

@Composable
private fun NumberKey(
    label: String,
    hint: String,
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
        if (hint.isNotEmpty()) {
            Text(hint, color = MobiColors.TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (held) MobiColors.AccentDim else MobiColors.SurfaceAlt)
            .border(1.dp, if (held) MobiColors.Accent else MobiColors.Border, RoundedCornerShape(12.dp))
            .holdable(button, onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MobiColors.Text, fontSize = 15.sp)
    }
}

@Composable
private fun ArrowKey(
    icon: ImageVector,
    button: String,
    description: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
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
        Icon(icon, contentDescription = description, tint = MobiColors.Accent,
            modifier = Modifier.size(30.dp))
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
