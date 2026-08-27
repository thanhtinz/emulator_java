package com.mobicore.core.emu;

import com.mobicore.core.vm.VmHost;

/**
 * The host clock, running faster or slower than the real one.
 *
 * <p>A J2ME game paces itself: it reads {@code System.currentTimeMillis} and
 * sleeps between frames. So changing how fast a game runs is not a matter of
 * calling it more often — it is a matter of what the game is told the time
 * is. Hand it a clock running at double speed and it moves twice as far per
 * real second, all by itself, with its own logic and its own animations
 * intact.</p>
 *
 * <p>Why anyone wants this: these games were written for a commute. A level
 * that takes four minutes of walking across an empty map is four minutes on a
 * handset and four minutes now, and the player has already seen it. Slower is
 * worth as much in the other direction — a game whose timing was tuned to a
 * slower handset than the one it is emulated on becomes playable again.</p>
 *
 * <p>The scaled clock never runs backwards, whatever the speed is changed to
 * mid-game: a game that sees time move backwards can compute a negative frame
 * delta, and a negative frame delta is how a sprite ends up somewhere it
 * cannot be.</p>
 */
public final class SpeedClock implements VmHost {

    /** Normal speed, as a percentage — what a handset ran at. */
    public static final int NORMAL = 100;
    /** What the in-game control steps through. */
    public static final int[] STEPS = {50, 100, 200, 300};

    private final VmHost delegate;

    private int speed = NORMAL;
    /** Where the real clock was when the current speed took effect. */
    private long realBase;
    /** What the game had been told the time was at that point. */
    private long gameBase;

    public SpeedClock(VmHost delegate) {
        this.delegate = delegate;
        this.realBase = delegate.currentTimeMillis();
        this.gameBase = realBase;
    }

    public int speed() {
        return speed;
    }

    /**
     * Changes the speed from this moment on.
     *
     * <p>The change is rebased rather than applied to the whole history: a
     * game that has been running for an hour must not have its clock jump an
     * hour forward because the player pressed "faster".</p>
     */
    public void setSpeed(int percent) {
        int wanted = percent < 10 ? 10 : (percent > 800 ? 800 : percent);
        if (wanted == speed) {
            return;
        }
        long now = delegate.currentTimeMillis();
        gameBase = scaled(now);
        realBase = now;
        speed = wanted;
    }

    /** The next speed in the cycle, for a one-tap control. */
    public int nextSpeed() {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == speed) {
                return STEPS[(i + 1) % STEPS.length];
            }
        }
        return NORMAL;
    }

    /** What the game is told the time is. */
    public long currentTimeMillis() {
        return scaled(delegate.currentTimeMillis());
    }

    private long scaled(long real) {
        long elapsed = real - realBase;
        if (elapsed < 0) {
            elapsed = 0;
        }
        return gameBase + elapsed * speed / NORMAL;
    }

    public void print(boolean error, String text) {
        delegate.print(error, text);
    }

    public void exit(int code) {
        delegate.exit(code);
    }

    public String property(String name) {
        return delegate.property(name);
    }

    /**
     * Sleeps for what the game asked, measured in its own faster time.
     *
     * <p>At double speed a game asking to wait 40 ms is asking to wait 40 ms
     * of game time, which is 20 ms of real time. Left unscaled, the sleep
     * alone would hold the game to its original pace however fast its clock
     * ran.</p>
     */
    public void sleep(long millis) throws InterruptedException {
        long real = millis * NORMAL / speed;
        delegate.sleep(real < 0 ? 0 : real);
    }
}
