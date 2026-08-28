package com.mobicore.app.emu

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobicore.core.emu.CrashDiagnosis
import com.mobicore.core.emu.EmulatorSession
import com.mobicore.core.emu.SaveState
import com.mobicore.core.gfx.PngWriter
import com.mobicore.core.jar.SuiteLoader
import com.mobicore.core.model.GameProfile
import com.mobicore.core.storage.LocalVfs
import com.mobicore.core.storage.StorageLayout
import com.mobicore.core.vm.VmCancelled
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
    /** The phone's motor, when the app has one to offer. */
    private val vibration: com.mobicore.core.haptics.VibrationSink? = null,
) {

    /** Bumped after each painted frame so Compose knows to redraw. */
    var frameCounter by mutableIntStateOf(0)
        private set

    var running by mutableStateOf(false)
        private set

    var paused by mutableStateOf(false)
        private set

    /**
     * Lần hỏng gần nhất, đã đọc thành lời.
     *
     * Trước đây chỗ này là một dòng chữ đỏ ghi tên lớp ngoại lệ — đúng với
     * người viết máy ảo và vô nghĩa với người đang chơi. Bản đọc hiểu nói hỏng
     * cái gì, vì sao và làm gì tiếp; tên ngoại lệ vẫn còn, trong phần kỹ thuật.
     */
    var crash by mutableStateOf<CrashDiagnosis?>(null)
        private set

    /** Ngăn xếp lúc chết, để gửi kèm báo lỗi. */
    var crashStack by mutableStateOf("")
        private set

    var lastError by mutableStateOf<String?>(null)
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
        crash = null
        crashStack = ""
        val layout = StorageLayout(StorageLayout.join(filesDir, "MobiCore"))
        val created = EmulatorSession.create(suite, profile, LocalVfs(), layout, AndroidHost())
        // Sound goes to the device rather than to the recorder the core
        // defaults to; the profile's volume is already applied inside the
        // emulator, so the track only has to play what it is handed.
        created.setAudio(audio)
        // Múi giờ của chiếc máy này, giờ mùa hè đã tính sẵn trong độ lệch.
        // Phần lõi không mang bảng múi giờ của thế giới, nên chỗ biết thì nói.
        val zone = java.util.TimeZone.getDefault()
        created.vm().setTimeZone(zone.id, zone.getOffset(System.currentTimeMillis()))
        // A J2ME game's only physical feedback was the handset shaking.
        vibration?.let { created.setVibration(it) }
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

    private fun suiteHolds(session: EmulatorSession, className: String): Boolean =
        session.info().midlets().any { it.className() == className }

    private fun runLoop(active: EmulatorSession, profile: GameProfile) {
        try {
            // The MIDlet the player chose, when the suite still holds it: a
            // JAR often carries a help screen and a settings screen beside
            // the game, and a stale name must not leave the game unopenable.
            val wanted = profile.midletClass()
            if (wanted.isNotEmpty() && suiteHolds(active, wanted)) {
                active.start(wanted)
            } else {
                active.start()
            }
            val limit = profile.frameLimit()

            while (!stopRequested.get() && !active.isFinished) {
                val frameStart = System.nanoTime()
                if (!paused && active.renderFrame()) {
                    publish(active)
                }
                // The frame budget follows the speed control: at double speed
                // the game's own clock runs twice as fast, and drawing at the
                // old rate would show half of what it does.
                val frameNanos = if (limit > 0) {
                    1_000_000_000L * 100L / (limit.toLong() * active.speed().coerceAtLeast(10))
                } else {
                    0L
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
            noteCrash(active, thrown)
        } catch (stopped: VmCancelled) {
            // Người chơi dừng, không phải game hỏng: không có gì để báo.
        } catch (error: VmError) {
            noteCrash(active, error)
        } catch (unexpected: RuntimeException) {
            noteCrash(active, unexpected)
        } finally {
            running = false
        }
    }

    /**
     * Ghi lại một lần hỏng, kèm chỗ nó chết.
     *
     * Ngăn xếp phải lấy ngay tại đây: phiên chạy sắp bị dọn, và câu hỏi "chết
     * ở đâu" chỉ trả lời được khi nó còn.
     */
    private fun noteCrash(active: EmulatorSession, failure: Throwable) {
        val read = CrashDiagnosis.of(failure)
        crash = read
        crashStack = active.vm().interpreter().crashTrace()
        lastError = read.reason()
    }

    /** Người chơi đã đọc xong lời báo hỏng. */
    fun dismissCrash() {
        crash = null
        crashStack = ""
        lastError = null
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

    // ------------------------------------------------------------- gamepad

    /**
     * A control on a real pad was pressed.
     *
     * The name is the emulator's own; the profile decides what it does, so a
     * remapped pad is remapped for the phone and the preview alike.
     *
     * @return true when the control was bound to something
     */
    fun pressPad(pad: String): Boolean {
        val active = session ?: return false
        if (active.profile().gamepad().buttonFor(pad).isEmpty()) {
            return false
        }
        active.pressPad(pad)
        commandRevision++
        return true
    }

    fun releasePad(pad: String) {
        session?.releasePad(pad)
    }

    /** Directions a stick is pushing, so a change can be told from a repeat. */
    var stickHeld = emptySet<String>()
        private set

    /**
     * A stick moved.
     *
     * Android reports a stick as a stream of positions, not as presses, so the
     * change is worked out here: what is newly pushed gets pressed, what is no
     * longer pushed gets released. Without that a game reading held keys would
     * see one press and then nothing.
     */
    fun stickMoved(directions: Set<String>) {
        if (directions == stickHeld) {
            return
        }
        for (gone in stickHeld - directions) {
            releasePad(gone)
        }
        for (fresh in directions - stickHeld) {
            pressPad(fresh)
        }
        stickHeld = directions
    }

    /**
     * The phone was tilted.
     *
     * A sensor reports a position many times a second, not a press; the
     * session works out the change and holds or lets go of the directions.
     */
    fun tilted(x: Float, y: Float) {
        session?.tilted(x, y)
    }

    /** Lets go of whatever tilting was holding, for a game being left. */
    fun releaseTilt() {
        session?.releaseTilt()
    }

    fun stop() {
        stopRequested.set(true)
        // Một game kẹt trong vòng lặp của chính nó không bao giờ đọc tới cờ
        // dừng ở trên: lệnh này xuyên thẳng vào máy ảo, nên rời một game treo
        // là chuyện tức thì chứ không phải chờ hết giờ.
        session?.requestStop()
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

    /**
     * How solid the keypad should be drawn right now, in percent.
     *
     * The session keeps the clock and the profile holds the setting, so the
     * answer comes from there rather than being worked out again here.
     */
    fun keypadOpacity(): Int = session?.keypadOpacity() ?: 100

    /** Brings a faded keypad back, for a touch that is not a key press. */
    fun noteKeypadUse() {
        session?.noteKeypadUse()
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
    /**
     * True while the emulated screen draws the command bar itself.
     *
     * A touchscreen ran these games with that bar as the button, so while it
     * is there the keypad leaves its two softkeys out rather than repeating
     * the same two words underneath.
     */
    fun showsSoftKeyBar(): Boolean = session?.showsSoftKeyBar() ?: false

    fun leftSoftKeyLabel(): String? = session?.leftSoftKeyLabel()

    fun rightSoftKeyLabel(): String? = session?.rightSoftKeyLabel()

    fun logLines(): List<String> =
        session?.log()?.entries()?.map { it.toString() } ?: emptyList()
}
