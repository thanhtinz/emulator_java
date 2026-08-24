package com.mobicore.core.audio;

/**
 * A block of sound ready to play: 16-bit signed samples, one channel, little
 * endian.
 *
 * <p>One format for everything the emulator produces, so a host has one thing
 * to implement. Stereo and eight-bit sources are converted while they are
 * decoded rather than pushing the choice out to Android and iOS separately —
 * a J2ME game's audio is a few seconds of effects, and the memory saved by
 * keeping the original format is not worth two conversions written twice.</p>
 */
public final class AudioClip {

    private final byte[] pcm;
    private final int sampleRate;

    public AudioClip(byte[] pcm, int sampleRate) {
        this.pcm = pcm;
        this.sampleRate = sampleRate;
    }

    /** Signed 16-bit little-endian mono samples. */
    public byte[] pcm() {
        return pcm;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int frames() {
        return pcm.length / 2;
    }

    /**
     * Playing time in milliseconds, to the nearest millisecond.
     *
     * <p>Rounded rather than truncated: a clip is built from a whole number
     * of samples, so a tone asked for in round milliseconds comes back one
     * short of what the caller asked for often enough to matter — and it is a
     * duration a game compares against.</p>
     */
    public long durationMs() {
        return sampleRate == 0 ? 0 : ((long) frames() * 1000L + sampleRate / 2) / sampleRate;
    }

    /** The sample at {@code frame}, for tests and for mixing. */
    public int sample(int frame) {
        int at = frame * 2;
        return (short) ((pcm[at] & 0xFF) | (pcm[at + 1] << 8));
    }
}
