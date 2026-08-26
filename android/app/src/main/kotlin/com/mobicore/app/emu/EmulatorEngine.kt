package com.mobicore.app.emu

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobicore.core.emu.EmulatorSession
import com.mobicore.core.emu.SaveState
import com.mobicore.core.gfx.PngWriter
import com.mobicore.core.jar.SuiteLoader
import com.mobicore.core.model.GameProfile
import com.mobicore.core.storage.LocalVfs
import com.mobicore.core.storage.StorageLayout
import com.mobicore.core.vm.VmError
import com.mobicore.core.vm.VmThrow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives one running game on Android.
 *
 * The MIDlet runs on its own thread rather than on the UI thread: a J2ME game
 * loop blocks and sleeps freely, and letting it do that on the main thread
 * would freeze the whole app. Compose is woken by a frame counter, and the
 * bitmap is only touched under [frameLock].
 */
class EmulatorEngine(
    private val filesDir: String,
) {

    /** Bumped after each painted frame so Compose knows to redraw. */
    var frameCounter by mutableIntStateOf(0)
        private set

    var running by mutableStateOf(false)
        private set

    var paused by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var measuredFps by mutableIntStateOf(0)
        private set

    /** Bumped whenever the running screen's commands change. */
    var commandRevision by mutableIntStateOf(0)
        private set

    val frameLock = Any()

    var bitmap: Bitmap? = null
        private set

    private var session: EmulatorSession? = null
    private val audio = AudioTrackSink()
    private var loop: Thread? = null
    private val stopRequested = AtomicBoolean(false)
    private var pixelBuffer: IntArray = IntArray(0)

    fun session(): EmulatorSession? = session

    fun screenWidth(): Int = session?.screen()?.width() ?: 0

    fun screenHeight(): Int = session?.screen()?.height() ?: 0

    /** Boots a suite and starts the game loop. */
    fun start(suite: SuiteLoader, profile: GameProfile) {
        stop()
        lastError = null
        val layout = StorageLayout(StorageLayout.join(filesDir, "MobiCore"))
        val created = EmulatorSession.create(suite, profile, LocalVfs(), layout, AndroidHost())
        // Sound goes to the device rather than to the recorder the core
        // defaults to; the profile's volume is already applied inside the
        // emulator, so the track only has to play what it is handed.
        created.setAudio(audio)
        session = created
        val width = created.screen().width()
        val height = created.screen().height()
        pixelBuffer = IntArray(width * height)
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        stopRequested.set(false)
        running = true
        paused = false
        loop = Thread({ runLoop(created, profile) }, "mobicore-midlet").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    private fun runLoop(active: EmulatorSession, profile: GameProfile) {
        try {
            active.start()
            val limit = profile.frameLimit()
            val frameNanos = if (limit > 0) 1_000_000_000L / limit else 0L
            var framesThisSecond = 0
            var secondMark = System.nanoTime()

            while (!stopRequested.get() && !active.isFinished) {
                val frameStart = System.nanoTime()
                if (!paused && active.renderFrame()) {
                    publish(active)
                }
                framesThisSecond++
                val now = System.nanoTime()
                if (now - secondMark >= 1_000_000_000L) {
                    measuredFps = framesThisSecond
                    framesThisSecond = 0
                    secondMark = now
                }
                if (frameNanos > 0) {
                    val elapsed = System.nanoTime() - frameStart
                    val remaining = frameNanos - elapsed
                    if (remaining > 0) {
                        Thread.sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
                    }
                } else {
                    // Uncapped still yields, so the UI thread is never starved.
                    Thread.sleep(1)
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (thrown: VmThrow) {
            lastError = "Trò chơi ném ${thrown.type()?.binaryName() ?: "một ngoại lệ"}: ${thrown.message}"
        } catch (error: VmError) {
            lastError = error.message
        } catch (unexpected: RuntimeException) {
            lastError = unexpected.toString()
        } finally {
            running = false
        }
    }

    /** Copies the emulated framebuffer into the bitmap Compose draws. */
    private fun publish(active: EmulatorSession) {
        val target = bitmap ?: return
        val pixels = active.screen().pixels()
        synchronized(frameLock) {
            System.arraycopy(pixels, 0, pixelBuffer, 0, pixelBuffer.size)
            target.setPixels(pixelBuffer, 0, target.width, 0, 0, target.width, target.height)
        }
        frameCounter++
    }

    fun pause() {
        val active = session ?: return
        if (!paused) {
            paused = true
            active.pause()
        }
    }

    fun resume() {
        val active = session ?: return
        if (paused) {
            paused = false
            active.resume()
        }
    }

    // ------------------------------------------------------------ text entry

    /** True while the game is showing a TextBox or a focused text field. */
    fun isTextInputActive(): Boolean = session?.isTextInputActive ?: false

    /** What that field holds now, so the keyboard opens on it. */
    fun textInput(): String = session?.textInput() ?: ""

    /** Puts what the phone's keyboard produced into the field. */
    fun setTextInput(value: String) {
        session?.setTextInput(value)
    }

    /**
     * Captures the running game.
     *
     * Returns null when the game holds something that cannot be written down
     * — an open connection, a sound being played. The caller carries on: not
     * being able to save a position is a disappointment, and refusing to
     * close the game over it would be worse.
     */
    fun captureState(): ByteArray? {
        val active = session ?: return null
        return runCatching { SaveState.capture(active) }.getOrNull()
    }

    /** Puts a captured game back. The session must already be started. */
    fun restoreState(state: ByteArray): Boolean {
        val active = session ?: return false
        return runCatching {
            SaveState.restore(active, state)
            publish(active)
            true
        }.getOrDefault(false)
    }

    /** The screen as it stands, as PNG bytes, for the saved state's picture. */
    fun screenshot(): ByteArray? {
        val active = session ?: return null
        return runCatching { PngWriter.encode(active.screen()) }.getOrNull()
    }

    fun stop() {
        stopRequested.set(true)
        loop?.interrupt()
        loop?.join(750)
        loop = null
        session?.destroy()
        session = null
        audio.releaseAll()
        running = false
        paused = false
    }

    /** Restarts the current suite from scratch, discarding VM state only. */
    fun restart(suite: SuiteLoader, profile: GameProfile) {
        stop()
        start(suite, profile)
    }

    fun pressButton(button: String) {
        session?.pressButton(button)
        // Any key can swap the screen, and with it the softkey labels: picking
        // a row on a List opens the next screen just as a softkey command does,
        // and the labels have to follow.
        commandRevision++
    }

    fun releaseButton(button: String) {
        session?.releaseButton(button)
    }

    fun pointerDown(x: Int, y: Int) {
        session?.pointerPressed(x, y)
    }

    fun pointerMove(x: Int, y: Int) {
        session?.pointerDragged(x, y)
    }

    fun pointerUp(x: Int, y: Int) {
        session?.pointerReleased(x, y)
    }

    /**
     * Labels the two softkeys should show. A handset leaves them blank until a
     * MIDlet registers a Command, and shows whatever it registered.
     */
    fun leftSoftKeyLabel(): String? = session?.leftSoftKeyLabel()

    fun rightSoftKeyLabel(): String? = session?.rightSoftKeyLabel()

    fun screenshot(): ByteArray? = session?.screenshotPng()

    fun logLines(): List<String> =
        session?.log()?.entries()?.map { it.toString() } ?: emptyList()
}
