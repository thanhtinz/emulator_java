package com.mobicore.core.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AudioSink} that records instead of playing.
 *
 * <p>Used by the test suite and by the desktop preview, neither of which has
 * a speaker. It still keeps time — a voice reports itself playing for exactly
 * as long as the clip lasts — so a game that waits for a sound to finish
 * behaves the same here as it does on a phone.</p>
 */
public final class AudioLog implements AudioSink {

    /** One thing the game played. */
    public static final class Entry {

        public final AudioClip clip;
        public final int loops;
        public final int volume;
        public final long startedAt;
        long stoppedAt;

        Entry(AudioClip clip, int loops, int volume, long startedAt) {
            this.clip = clip;
            this.loops = loops;
            this.volume = volume;
            this.startedAt = startedAt;
            this.stoppedAt = -1;
        }

        public boolean stopped() {
            return stoppedAt >= 0;
        }
    }

    /** Where the log reads the time from, so a test can hold it still. */
    public interface Clock {
        long nowMs();
    }

    /** The wall clock, for a host that has no clock of its own to lend. */
    public static Clock systemClock() {
        return new Clock() {
            public long nowMs() {
                return System.currentTimeMillis();
            }
        };
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final Clock clock;
    private int[] volumes = new int[8];

    public AudioLog(Clock clock) {
        this.clock = clock;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Entry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    public void clear() {
        entries.clear();
    }

    public int start(AudioClip clip, int loops, int volume) {
        entries.add(new Entry(clip, loops, volume, clock.nowMs()));
        int voice = entries.size() - 1;
        if (voice >= volumes.length) {
            int[] grown = new int[volumes.length * 2];
            System.arraycopy(volumes, 0, grown, 0, volumes.length);
            volumes = grown;
        }
        volumes[voice] = volume;
        return voice;
    }

    public void stop(int voice) {
        Entry entry = entryAt(voice);
        if (entry != null && !entry.stopped()) {
            entry.stoppedAt = clock.nowMs();
        }
    }

    public void setVolume(int voice, int volume) {
        if (voice >= 0 && voice < volumes.length) {
            volumes[voice] = volume;
        }
    }

    /** The volume a voice is at now, which a test can check after a change. */
    public int volumeOf(int voice) {
        return voice >= 0 && voice < volumes.length ? volumes[voice] : 0;
    }

    public boolean isPlaying(int voice) {
        Entry entry = entryAt(voice);
        if (entry == null || entry.stopped()) {
            return false;
        }
        if (entry.loops == 0) {
            return true;
        }
        long total = entry.clip.durationMs() * entry.loops;
        return clock.nowMs() - entry.startedAt < total;
    }

    public long positionMs(int voice) {
        Entry entry = entryAt(voice);
        if (entry == null) {
            return 0;
        }
        long end = entry.stopped() ? entry.stoppedAt : clock.nowMs();
        long elapsed = end - entry.startedAt;
        long length = entry.clip.durationMs();
        return length == 0 ? 0 : Math.min(elapsed, length * Math.max(1, entry.loops));
    }

    private Entry entryAt(int voice) {
        return voice >= 0 && voice < entries.size() ? entries.get(voice) : null;
    }
}
