package com.mobicore.core.emu;

/**
 * The last few seconds of a game, kept so they can be taken back.
 *
 * <p>These games are hard in a way that was fair on a bus and is not fair
 * now: one mistake and a level restarts from the beginning, because a handset
 * had nowhere to keep anything else. The emulator does. A snapshot a second,
 * a handful of seconds deep, turns "start the level again" into "try that
 * jump again".</p>
 *
 * <p>Deliberately shallow and coarse. Every snapshot is a full heap capture —
 * the same one a save state writes — so a snapshot per frame would spend more
 * time saving the game than running it, and an hour of history would spend
 * more memory than the game itself. A second apart and a dozen deep costs a
 * few megabytes and covers the mistake that was just made, which is the only
 * mistake anyone wants back.</p>
 */
public final class Rewind {

    /** How far apart snapshots are taken, in milliseconds of game time. */
    public static final int INTERVAL_MS = 1000;
    /** How many are kept: a dozen seconds of history. */
    public static final int DEPTH = 12;

    private final byte[][] snapshots = new byte[DEPTH][];
    /** Where the next snapshot goes. */
    private int head;
    private int count;
    private long lastCapture;
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Turns history on or off.
     *
     * <p>Off throws away what was kept: leaving several megabytes of heap
     * captures around after someone has said they do not want the feature is
     * the opposite of what they asked for.</p>
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    /** How many seconds can be taken back. */
    public int depth() {
        return count;
    }

    public void clear() {
        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i] = null;
        }
        head = 0;
        count = 0;
        lastCapture = 0;
    }

    /**
     * Takes a snapshot if one is due.
     *
     * <p>Called from the frame loop. A game that cannot be snapshotted — one
     * holding an open connection, say — simply keeps no history rather than
     * interrupting play to say so; the player finds out when they try to
     * rewind, which is the first moment it matters to them.</p>
     *
     * @param now the game's own clock, so history follows the speed control
     * @return true when a snapshot was taken
     */
    public boolean tick(EmulatorSession session, long now) {
        if (!enabled || session == null) {
            return false;
        }
        if (lastCapture != 0 && now - lastCapture < INTERVAL_MS) {
            return false;
        }
        byte[] snapshot;
        try {
            snapshot = SaveState.capture(session);
        } catch (SaveState.NotSavable e) {
            return false;
        }
        lastCapture = now;
        snapshots[head] = snapshot;
        head = (head + 1) % DEPTH;
        if (count < DEPTH) {
            count++;
        }
        return true;
    }

    /**
     * Puts the game back to the newest snapshot and drops it.
     *
     * <p>Dropping it is what makes holding rewind walk backwards: the next
     * step lands a second further back rather than on the same moment
     * again.</p>
     *
     * @return true when the game moved back
     */
    public boolean stepBack(EmulatorSession session) {
        if (count == 0 || session == null) {
            return false;
        }
        int index = (head - 1 + DEPTH) % DEPTH;
        byte[] snapshot = snapshots[index];
        if (snapshot == null) {
            return false;
        }
        try {
            SaveState.restore(session, snapshot);
        } catch (SaveState.NotSavable e) {
            return false;
        }
        snapshots[index] = null;
        head = index;
        count--;
        // The moment restored to is now the present: the next snapshot is due
        // a full interval after it, not immediately.
        lastCapture = 0;
        return true;
    }
}
