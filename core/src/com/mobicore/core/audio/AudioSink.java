package com.mobicore.core.audio;

/**
 * Where sound goes once the core has produced it.
 *
 * <p>The core synthesises and decodes; it never talks to a sound device,
 * because there is no portable way to. Android hands these clips to an
 * {@code AudioTrack} and iOS to an audio queue; the desktop preview and the
 * test suite use {@link AudioLog}, which records instead of playing.</p>
 *
 * <p>A voice id, not a {@code Player} object, is what crosses this line: the
 * host has no business knowing about MIDP's player state machine, and the
 * emulator has no business knowing how a platform tracks a running sound.</p>
 */
public interface AudioSink {

    /** A voice that failed to start, which a caller may safely stop. */
    int NO_VOICE = -1;

    /**
     * Begins playing.
     *
     * @param loops how many times to play; 0 means loop until stopped
     * @param volume 0..100, already scaled by the game's own volume setting
     * @return a voice id for the calls below, or {@link #NO_VOICE}
     */
    int start(AudioClip clip, int loops, int volume);

    /** Stops a voice. Stopping one that already finished does nothing. */
    void stop(int voice);

    /** Changes the volume of a running voice, 0..100. */
    void setVolume(int voice, int volume);

    /** True while the voice is still producing sound. */
    boolean isPlaying(int voice);

    /** How far into the clip the voice has played, in milliseconds. */
    long positionMs(int voice);
}
