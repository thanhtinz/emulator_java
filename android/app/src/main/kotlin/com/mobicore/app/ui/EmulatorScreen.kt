package com.mobicore.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.app.emu.EmulatorEngine

/**
 * The running game plus its on-screen controls.
 *
 * The framebuffer is blitted with filtering disabled and, by default, at an
 * integer scale: classic games are pixel art and smoothing them is the one
 * thing an emulator must not do.
 */
@Composable
fun EmulatorScreen(
    library: LibraryRepository,
    filesDir: String,
    suiteId: String,
    onExit: () -> Unit,
) {
    val engine = remember(suiteId) { EmulatorEngine(filesDir) }
    val profiles by library.profiles.collectAsState()
    val profile = profiles[suiteId]

    LaunchedEffect(suiteId) {
        val loaded = library.load(suiteId)
        val active = library.profile(suiteId) ?: return@LaunchedEffect
        library.markPlayed(suiteId)
        engine.start(loaded, active)
    }

    DisposableEffect(suiteId) {
        onDispose { engine.stop() }
    }

    Column(Modifier.fillMaxSize().background(MobiColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Exit", color = MobiColors.Accent, fontSize = 14.sp,
                modifier = Modifier.clickableText { engine.stop(); onExit() })
            if (profile?.showFps() == true) {
                Text("${engine.measuredFps} fps", color = MobiColors.TextDim, fontSize = 12.sp)
            }
            Text(
                text = if (engine.paused) "Resume" else "Pause",
                color = MobiColors.Accent,
                fontSize = 14.sp,
                modifier = Modifier.clickableText {
                    if (engine.paused) engine.resume() else engine.pause()
                },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            GameSurface(engine, library, suiteId)
        }

        val error = engine.lastError
        if (error != null) {
            Text(
                text = error,
                color = MobiColors.Bad,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Keypad(
            onPress = { engine.pressButton(it) },
            onRelease = { engine.releaseButton(it) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GameSurface(engine: EmulatorEngine, library: LibraryRepository, suiteId: String) {
    val profiles by library.profiles.collectAsState()
    val profile = profiles[suiteId]

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(suiteId) {
                detectTapGestures(
                    onPress = { offset ->
                        val point = toGameCoordinates(engine, profile, offset, size.width, size.height)
                        if (point != null) {
                            engine.pointerDown(point.first, point.second)
                            tryAwaitRelease()
                            engine.pointerUp(point.first, point.second)
                        }
                    },
                )
            }
            .pointerInput(suiteId) {
                detectDragGestures { change, _ ->
                    val point = toGameCoordinates(engine, profile, change.position, size.width, size.height)
                    if (point != null) {
                        engine.pointerMove(point.first, point.second)
                    }
                }
            },
    ) {
        // Reading the counter inside the draw scope is what ties a repaint to
        // a finished emulator frame.
        @Suppress("UNUSED_EXPRESSION")
        engine.frameCounter

        val bitmap = engine.bitmap ?: return@Canvas
        val viewport = profile?.viewport(size.width.toInt(), size.height.toInt())
            ?: intArrayOf(0, 0, bitmap.width, bitmap.height)

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isFilterBitmap = false
                isAntiAlias = false
                isDither = false
            }
            val destination = android.graphics.Rect(
                viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3],
            )
            synchronized(engine.frameLock) {
                canvas.nativeCanvas.drawBitmap(bitmap, null, destination, paint)
            }
        }
    }
}

/** Maps a touch on the scaled surface back to emulated screen coordinates. */
private fun toGameCoordinates(
    engine: EmulatorEngine,
    profile: com.mobicore.core.model.GameProfile?,
    offset: Offset,
    width: Int,
    height: Int,
): Pair<Int, Int>? {
    val screenWidth = engine.screenWidth()
    val screenHeight = engine.screenHeight()
    if (screenWidth == 0 || screenHeight == 0) return null
    val viewport = profile?.viewport(width, height) ?: intArrayOf(0, 0, width, height)
    if (viewport[2] <= 0 || viewport[3] <= 0) return null
    val x = ((offset.x - viewport[0]) * screenWidth / viewport[2]).toInt()
    val y = ((offset.y - viewport[1]) * screenHeight / viewport[3]).toInt()
    if (x < 0 || y < 0 || x >= screenWidth || y >= screenHeight) return null
    return x to y
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
