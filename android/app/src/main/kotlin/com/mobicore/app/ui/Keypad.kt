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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The virtual phone keypad.
 *
 * Buttons report press and release separately rather than a single tap: a
 * J2ME game reads held keys through {@code GameCanvas.getKeyStates}, so a
 * button that only ever fires a press would make the player look stuck.
 */
@Composable
fun Keypad(
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DirectionalPad(onPress, onRelease)
            Spacer(Modifier.width(12.dp))
            NumericPad(onPress, onRelease)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KeyButton("Soft 1", "softLeft", onPress, onRelease, width = 84)
            KeyButton("Clear", "clear", onPress, onRelease, width = 84)
            KeyButton("Soft 2", "softRight", onPress, onRelease, width = 84)
        }
    }
}

@Composable
private fun DirectionalPad(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ArrowButton(Icons.Filled.KeyboardArrowUp, "up", onPress, onRelease)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrowButton(Icons.Filled.KeyboardArrowLeft, "left", onPress, onRelease)
            KeyButton("OK", "fire", onPress, onRelease, width = 56, accent = true)
            ArrowButton(Icons.Filled.KeyboardArrowRight, "right", onPress, onRelease)
        }
        ArrowButton(Icons.Filled.KeyboardArrowDown, "down", onPress, onRelease)
    }
}

@Composable
private fun NumericPad(onPress: (String) -> Unit, onRelease: (String) -> Unit) {
    val rows = listOf(
        listOf("1" to "num1", "2" to "num2", "3" to "num3"),
        listOf("4" to "num4", "5" to "num5", "6" to "num6"),
        listOf("7" to "num7", "8" to "num8", "9" to "num9"),
        listOf("*" to "star", "0" to "num0", "#" to "hash"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, button) ->
                    KeyButton(label, button, onPress, onRelease, width = 46)
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(
    icon: ImageVector,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
) {
    Box(
        Modifier
            .size(56.dp, 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, RoundedCornerShape(10.dp))
            .holdable(button, onPress, onRelease),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = button, tint = MobiColors.Accent)
    }
}

@Composable
private fun KeyButton(
    label: String,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    width: Int,
    accent: Boolean = false,
) {
    Box(
        Modifier
            .size(width.dp, 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (accent) MobiColors.AccentDim else MobiColors.SurfaceAlt)
            .border(1.dp, if (accent) MobiColors.Accent else MobiColors.Border, RoundedCornerShape(10.dp))
            .holdable(button, onPress, onRelease),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (accent) MobiColors.Accent else MobiColors.Text,
            fontSize = 14.sp,
        )
    }
}

/** Press and release, so held keys stay held. */
private fun Modifier.holdable(
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
): Modifier = this.pointerInput(button) {
    detectTapGestures(
        onPress = {
            onPress(button)
            tryAwaitRelease()
            onRelease(button)
        },
    )
}
