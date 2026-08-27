package com.mobicore.core.emu;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.GifEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Records what is on the screen as an animated GIF.
 *
 * <p>A screenshot says where the player got to; it cannot say how. A few
 * seconds of the actual game can — the jump that worked, the pattern that
 * kills, the bug worth reporting — and unlike a screen recording of the whole
 * phone it holds only the emulated screen, at the size the game draws.</p>
 *
 * <p>Deliberately short and deliberately coarse. Frames are kept in memory
 * until the recording is stopped, because a GIF's colour table is chosen from
 * the whole clip at once; at 240×320 a frame is 300 KB, so ten seconds at ten
 * frames a second is about thirty megabytes and anything longer would be a way
 * to run a phone out of memory by holding a button. Ten frames a second is
 * also about what a GIF viewer will honour: the format counts its delays in
 * hundredths of a second and most players quietly refuse anything faster than
 * fifty a second.</p>
 */
public final class ClipRecorder {

    /** Frames a second kept. */
    public static final int FPS = 10;
    /** Milliseconds between kept frames. */
    public static final int FRAME_INTERVAL_MS = 1000 / FPS;
    /** How long one clip can run. */
    public static final int MAX_SECONDS = 10;
    /** And how many frames that comes to. */
    public static final int MAX_FRAMES = FPS * MAX_SECONDS;

    private final List<int[]> frames = new ArrayList<int[]>();
    private boolean recording;
    private long nextCapture;
    private int width;
    private int height;

    public boolean isRecording() {
        return recording;
    }

    /** How many frames are held. */
    public int frameCount() {
        return frames.size();
    }

    /** How much of the clip has been recorded, in tenths of a second. */
    public int tenths() {
        return frames.size() * 10 / FPS;
    }

    /** True once the clip is as long as one is allowed to be. */
    public boolean isFull() {
        return frames.size() >= MAX_FRAMES;
    }

    /**
     * Starts a clip, throwing away anything held from a previous one.
     *
     * @param now the emulator's clock, so a game slowed down records slowly
     */
    public void start(long now) {
        frames.clear();
        recording = true;
        // The first frame is taken at the next tick rather than here: this is
        // called from a menu, and the frame under a menu is the menu.
        nextCapture = now;
    }

    /**
     * Keeps this frame if it is time for one.
     *
     * <p>Driven by the clock the game runs on, so a clip of a game played at
     * half speed is a clip of a game played at half speed rather than a clip
     * of every other frame.</p>
     */
    public void tick(Framebuffer screen, long now) {
        if (!recording || screen == null || isFull() || now < nextCapture) {
            return;
        }
        int[] pixels = screen.pixels();
        int[] copy = new int[pixels.length];
        System.arraycopy(pixels, 0, copy, 0, pixels.length);
        frames.add(copy);
        width = screen.width();
        height = screen.height();
        nextCapture = now + FRAME_INTERVAL_MS;
        if (isFull()) {
            // Held frames stay held: stopping here would throw away the clip
            // the player is about to save.
            recording = false;
        }
    }

    /**
     * Ends the clip and encodes it.
     *
     * @return the GIF, or null when nothing was recorded
     */
    public byte[] stop() {
        recording = false;
        if (frames.isEmpty()) {
            return null;
        }
        byte[] gif = GifEncoder.encode(frames, width, height, 100 / FPS);
        frames.clear();
        return gif;
    }

    /** Throws away a clip without encoding it. */
    public void cancel() {
        recording = false;
        frames.clear();
    }
}
