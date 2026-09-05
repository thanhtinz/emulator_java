package com.mobicore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.core.model.GameProfile
import com.mobicore.core.model.KeypadArrangement
import com.mobicore.core.model.KeypadPlan

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
private val KEY_DIVISOR_UPRIGHT = KeypadPlan.KEY_DIVISOR_UPRIGHT
private val KEY_DIVISOR_TURNED = KeypadPlan.KEY_DIVISOR_TURNED.toFloat()
private val SOFT_SCALE_Y = KeypadPlan.SOFT_SCALE_Y
/** A hair of daylight between keys; J2ME Loader snaps its keys together. */
private val GAP = KeypadPlan.GAP.dp

/**
 * The shape every key on this keypad is cut to.
 *
 * Carried down rather than passed key by key: eleven signatures would have to
 * grow a parameter that none of them decides anything with, and one of them
 * would eventually be missed.
 */
private val LocalKeyShape = compositionLocalOf { GameProfile.KEY_SHAPE_ROUNDED }

/**
 * Where the keys are, and — while arranging — how to move them.
 *
 * `arrangement` offsets each key from where the standard layout puts it, in
 * units of one key. `onMove` is set only on the arranging screen: while it is
 * set the keys are dragged rather than pressed, because a key cannot be both
 * the thing being moved and the thing being played with.
 */
class KeyPlacement(
    val arrangement: KeypadArrangement? = null,
    val onMove: ((String, Float, Float) -> Unit)? = null,
)

private val LocalKeyPlacement = compositionLocalOf { KeyPlacement() }

/** Where one key sits, and what a touch on it does. */
@Composable
private fun Modifier.placed(
    button: String,
    key: Dp,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    onHeldChange: (Boolean) -> Unit,
): Modifier {
    val placement = LocalKeyPlacement.current
    // Where the key sits is the plan's business — it has already added the
    // player's drag — so all that is left here is what a touch does.
    val onMove = placement.onMove ?: return this.holdable(button, onPress, onRelease, onHeldChange)
    // Dragged in keys rather than pixels: a key is a different number of
    // pixels upright, sideways and on every different phone, and one
    // arrangement has to hold for all of them.
    val keyPx = with(LocalDensity.current) { key.toPx() }
    return this.pointerInput(button, keyPx) {
        detectDragGestures { change, drag ->
            change.consume()
            onMove(button, drag.x / keyPx, drag.y / keyPx)
        }
    }
}

/** True while the keys are being arranged rather than played with. */
@Composable
private fun arranging(): Boolean = LocalKeyPlacement.current.onMove != null

/** A key's outline, at the given corner radius when it is a rounded one. */
@Composable
private fun keyShape(radius: Dp, round: Boolean = false): Shape =
    when (if (round) GameProfile.KEY_SHAPE_ROUND else LocalKeyShape.current) {
        GameProfile.KEY_SHAPE_RECT -> RectangleShape
        // On a square key a full-radius corner is a circle, which is what
        // this setting is for; on the wide softkeys it gives a pill.
        GameProfile.KEY_SHAPE_ROUND -> CircleShape
        else -> RoundedCornerShape(radius)
    }

/**
 * How big one key is drawn, once the player's own size is applied.
 *
 * The screen has the last word. A key size that would push the two pads off
 * the sides is not honoured as asked — the keypad would be unusable and
 * nothing would show that it was the size setting that did it — so it is held
 * to what fits.
 */
@Composable
private fun uprightKeySize(arrangement: KeypadArrangement?): Dp {
    val configuration = LocalConfiguration.current
    val standard = (minOf(configuration.screenWidthDp, configuration.screenHeightDp)
        / KEY_DIVISOR_UPRIGHT).toInt()
    val asked = arrangement?.sizeOf(standard) ?: standard
    val fits = (configuration.screenWidthDp - 36) / 6
    return minOf(asked, fits).dp
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
    /** Which of the three keypads; see `GameProfile.KEYPAD_*`. */
    layout: Int = GameProfile.KEYPAD_FULL,
    /** The key shape; see `GameProfile.KEY_SHAPE_*`. */
    shape: Int = GameProfile.KEY_SHAPE_ROUNDED,
    /**
     * How solid to draw the keypad, in percent.
     *
     * Applied to the keypad as a whole rather than colour by colour, so the
     * keys, their outlines and their lettering all step back together.
     */
    opacity: Int = 100,
    /** Where the keys have been dragged to, and how big they are drawn. */
    placement: KeyPlacement = KeyPlacement(),
) {
    val key = uprightKeySize(placement.arrangement)
    CompositionLocalProvider(
        LocalKeyShape provides shape,
        LocalKeyPlacement provides placement,
    ) {
        BoxWithConstraints(modifier.fillMaxWidth().alpha(opacity / 100f)) {
            // Measured by the core, so this keypad and the one in the
            // screenshots cannot be two different keypads.
            val plan = KeypadPlan.portrait(
                layout, maxWidth.value.toInt(), key.value.toInt(), placement.arrangement,
            )
            Box(Modifier.fillMaxWidth().height(plan.height().dp)) {
                plan.keys().forEach { placed ->
                    PlacedKey(placed, key, leftSoftKey, rightSoftKey, onPress, onRelease)
                }
            }
        }
    }
}

/** One key of a plan, wherever the plan put it. */
@Composable
private fun PlacedKey(
    placed: KeypadPlan.Key,
    key: Dp,
    leftSoftKey: String?,
    rightSoftKey: String?,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
) {
    val slot = Modifier
        .offset(x = placed.x().dp, y = placed.y().dp)
        .size(placed.width().dp, placed.height().dp)
    when (placed.kind()) {
        KeypadPlan.KIND_SOFT -> SoftKey(
            label = if (placed.button() == "softLeft") leftSoftKey else rightSoftKey,
            button = placed.button(),
            onPress = onPress,
            onRelease = onRelease,
            key = key,
            modifier = slot,
        )
        KeypadPlan.KIND_STICK -> StickKey(placed.button(), key, onPress, onRelease, slot)
        KeypadPlan.KIND_FIRE -> FireKey(onPress, onRelease, key, placed.round(), slot)
        KeypadPlan.KIND_ARROW -> ArrowKey(
            icon = arrowIcon(placed.arrow()),
            button = placed.button(),
            description = arrowName(placed.arrow()),
            onPress = onPress,
            onRelease = onRelease,
            key = key,
            corner = placed.arrow() >= KeypadPlan.UP_LEFT,
            round = placed.round(),
            modifier = slot,
        )
        else -> NumberKey(
            placed.label(), placed.button(), onPress, onRelease, key, placed.round(), slot,
        )
    }
}

private fun arrowIcon(direction: Int): ImageVector = when (direction) {
    KeypadPlan.UP -> Icons.Filled.KeyboardArrowUp
    KeypadPlan.DOWN -> Icons.Filled.KeyboardArrowDown
    KeypadPlan.LEFT -> Icons.Filled.KeyboardArrowLeft
    KeypadPlan.RIGHT -> Icons.Filled.KeyboardArrowRight
    KeypadPlan.UP_LEFT -> Icons.Filled.NorthWest
    KeypadPlan.UP_RIGHT -> Icons.Filled.NorthEast
    KeypadPlan.DOWN_LEFT -> Icons.Filled.SouthWest
    else -> Icons.Filled.SouthEast
}

private fun arrowName(direction: Int): String = when (direction) {
    KeypadPlan.UP -> "Lên"
    KeypadPlan.DOWN -> "Xuống"
    KeypadPlan.LEFT -> "Trái"
    KeypadPlan.RIGHT -> "Phải"
    KeypadPlan.UP_LEFT -> "Lên trái"
    KeypadPlan.UP_RIGHT -> "Lên phải"
    KeypadPlan.DOWN_LEFT -> "Xuống trái"
    else -> "Xuống phải"
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
    /** Which of the three keypads; see `GameProfile.KEYPAD_*`. */
    layout: Int = GameProfile.KEYPAD_FULL,
    /** The key shape; see `GameProfile.KEY_SHAPE_*`. */
    shape: Int = GameProfile.KEY_SHAPE_ROUNDED,
    /** How solid to draw the column, in percent. */
    opacity: Int = 100,
    /** Where the keys have been dragged to, and how big they are drawn. */
    placement: KeyPlacement = KeyPlacement(),
) {
    // Turned, J2ME Loader sizes its keys off the long edge. Its keypad floats
    // over the game, though, and this one has a column to itself, so the size
    // is also held to what the column can hold.
    val configuration = LocalConfiguration.current
    val standardTurned = (maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
        / KEY_DIVISOR_TURNED).toInt()
    val turned = (placement.arrangement?.sizeOf(standardTurned) ?: standardTurned).dp
    CompositionLocalProvider(
        LocalKeyShape provides shape,
        LocalKeyPlacement provides placement,
    ) {
    BoxWithConstraints(modifier.width(turned * 3 + GAP * 2 + 24.dp).alpha(opacity / 100f)) {
        val room = (maxHeight - GAP * 4) / (4 + SOFT_SCALE_Y)
        val key = minOf(turned, room)
        val plan = KeypadPlan.column(
            layout, directional, maxWidth.value.toInt(), maxHeight.value.toInt(),
            key.value.toInt(), placement.arrangement,
        )
        Box(Modifier.fillMaxSize()) {
            plan.keys().forEach { placed ->
                PlacedKey(placed, key, softKeyLabel, softKeyLabel, onPress, onRelease)
            }
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
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    val bound = !label.isNullOrEmpty()
    val mark = if (button == "softLeft") "L" else "R"
    Box(
        modifier
            .clip(keyShape(12.dp))
            .background(
                when {
                    held -> MobiColors.AccentDim
                    bound -> MobiColors.SurfaceAlt
                    else -> MobiColors.Background
                }
            )
            .border(1.dp, if (held) MobiColors.Accent else MobiColors.Border, keyShape(12.dp))
            .placed(button, key, onPress, onRelease) { held = it }
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

@Composable
private fun NumberKey(
    label: String,
    button: String,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    key: Dp,
    round: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(keyShape(12.dp, round))
            .background(if (held) MobiColors.AccentDim else MobiColors.SurfaceAlt)
            .border(1.dp, if (held) MobiColors.Accent else MobiColors.Border, keyShape(12.dp, round))
            .placed(button, key, onPress, onRelease) { held = it },
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
    round: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(keyShape(14.dp, round))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, keyShape(14.dp, round))
            .placed(button, key, onPress, onRelease) { held = it },
        contentAlignment = Alignment.Center,
    ) {
        // The corners are quieter than the four main directions: there when a
        // game needs them, not competing for the thumb.
        Icon(icon, contentDescription = description, tint = MobiColors.Accent,
            modifier = Modifier.size(if (corner) key * 0.4f else key * 0.6f))
    }
}

/**
 * Cần điều khiển: một phím duy nhất, và hướng là chỗ ngón cái tì vào.
 *
 * Not four keys drawn as a circle. A thumb rests on it and leans, and the lean
 * decides which directions are held — so a corner is reached by leaning into
 * it rather than by finding an edge the thumb cannot feel. Which lean means
 * what is the core's to say, so that the phone and the preview steer alike.
 */
@Composable
private fun StickKey(
    button: String,
    key: Dp,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val held = remember { mutableStateListOf<String>() }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val moving = arranging()

    fun rest() {
        held.forEach(onRelease)
        held.clear()
        knob = Offset.Zero
    }

    Box(
        modifier
            .clip(CircleShape)
            .background(if (held.isEmpty()) MobiColors.AccentDim
                        else MobiColors.Accent.copy(alpha = 0.35f))
            .border(1.dp, MobiColors.Accent, CircleShape)
            .then(
                if (moving) {
                    Modifier.placed(button, key, {}, {}) {}
                } else {
                    Modifier.pointerInput(button) {
                        val radius = size.width / 2f
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var change = down
                                while (change.pressed) {
                                    val lean = Offset(change.position.x - radius,
                                                      change.position.y - radius)
                                    val want = KeypadPlan.stickDirections(lean.x, lean.y, radius)
                                    // Released before pressed, so a turn from
                                    // left to right is never both at once.
                                    held.filterNot(want::contains).forEach {
                                        held.remove(it)
                                        onRelease(it)
                                    }
                                    want.filterNot(held::contains).forEach {
                                        held.add(it)
                                        onPress(it)
                                    }
                                    knob = if (want.isEmpty()) Offset.Zero else lean
                                    change.consume()
                                    val event = awaitPointerEvent()
                                    change = event.changes.firstOrNull { it.id == down.id } ?: break
                                }
                                rest()
                            }
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The knob follows the thumb so that the stick shows how far it is
        // being pushed, which a keypad of separate keys never could.
        Box(
            Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        knob.x.toInt() / 2, knob.y.toInt() / 2,
                    )
                }
                .fillMaxSize(0.4f)
                .clip(CircleShape)
                .background(MobiColors.SurfaceAlt)
                .border(1.dp, MobiColors.Accent, CircleShape),
        )
    }
}

@Composable
private fun FireKey(
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit,
    key: Dp,
    round: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(keyShape(14.dp, round))
            .background(if (held) MobiColors.Accent.copy(alpha = 0.35f) else MobiColors.AccentDim)
            .border(1.dp, MobiColors.Accent, keyShape(14.dp, round))
            .placed("fire", key, onPress, onRelease) { held = it },
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
