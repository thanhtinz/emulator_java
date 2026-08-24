package com.mobicore.core.audio;

/**
 * Turns notes into sound.
 *
 * <p>This is what most J2ME audio actually was: {@code Manager.playTone} for
 * a beep, and a {@code ToneControl} sequence for a tune. Handsets played them
 * on a tone generator, which is why the era's games sound the way they do.</p>
 *
 * <p>The wave here is a square softened by a short fade in and out. A square
 * is what the hardware produced; the fade is not authentic but is necessary —
 * cutting a waveform off mid-cycle produces a click on every note, and on a
 * modern speaker that click is louder than the note.</p>
 */
public final class ToneSynth {

    /** Sample rate for everything the synthesiser makes. */
    public static final int SAMPLE_RATE = 22050;

    /** MIDI note 69 is concert A, and the scale is twelve steps to a double. */
    private static final double A4_FREQUENCY = 440.0;
    private static final int A4_NOTE = 69;

    /** Fade length at each end of a note, in samples. */
    private static final int FADE = SAMPLE_RATE / 400;

    private ToneSynth() {
    }

    /** Frequency of a MIDI note number, 0..127. */
    public static double frequencyOf(int note) {
        return A4_FREQUENCY * Math.pow(2.0, (note - A4_NOTE) / 12.0);
    }

    /**
     * One note.
     *
     * @param note MIDI note number, 0..127
     * @param durationMs how long it sounds
     * @param volume 0..100
     */
    public static AudioClip tone(int note, int durationMs, int volume) {
        int frames = Math.max(1, SAMPLE_RATE * Math.max(0, durationMs) / 1000);
        byte[] pcm = new byte[frames * 2];
        write(pcm, 0, frames, note, volume);
        return new AudioClip(pcm, SAMPLE_RATE);
    }

    /**
     * A run of notes and rests, as a tone sequence plays them.
     *
     * @param notes MIDI note numbers; {@link #REST} for silence
     * @param durations how long each lasts, in milliseconds
     */
    public static AudioClip sequence(int[] notes, int[] durations, int volume) {
        int frames = 0;
        for (int i = 0; i < notes.length; i++) {
            frames += Math.max(1, SAMPLE_RATE * Math.max(0, durations[i]) / 1000);
        }
        byte[] pcm = new byte[frames * 2];
        int at = 0;
        for (int i = 0; i < notes.length; i++) {
            int length = Math.max(1, SAMPLE_RATE * Math.max(0, durations[i]) / 1000);
            write(pcm, at, length, notes[i], volume);
            at += length;
        }
        return new AudioClip(pcm, SAMPLE_RATE);
    }

    /** The note number a tone sequence uses for silence. */
    public static final int REST = -1;

    /** Silence of a given length, for a gap between clips. */
    public static AudioClip silence(int durationMs) {
        int frames = Math.max(1, SAMPLE_RATE * Math.max(0, durationMs) / 1000);
        return new AudioClip(new byte[frames * 2], SAMPLE_RATE);
    }

    private static void write(byte[] pcm, int startFrame, int frames, int note, int volume) {
        if (note == REST || volume <= 0) {
            return;
        }
        double period = SAMPLE_RATE / frequencyOf(Math.max(0, Math.min(127, note)));
        int peak = 32767 * Math.min(100, volume) / 100;
        // Kept well under full scale: a square wave at full amplitude is
        // painfully loud, and several voices may end up mixed by the host.
        peak = peak * 3 / 5;
        for (int i = 0; i < frames; i++) {
            double phase = (i % period) / period;
            int value = phase < 0.5 ? peak : -peak;
            int fade = Math.min(i, frames - 1 - i);
            if (fade < FADE) {
                value = value * fade / FADE;
            }
            int at = (startFrame + i) * 2;
            pcm[at] = (byte) (value & 0xFF);
            pcm[at + 1] = (byte) ((value >> 8) & 0xFF);
        }
    }
}
