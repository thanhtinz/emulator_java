package com.mobicore.core.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a MIDP tone sequence — the byte array a game hands to
 * {@code ToneControl.setSequence} — into sound.
 *
 * <p>The format is pairs of (note, duration) with a handful of commands mixed
 * in: a version, a tempo, a resolution, named blocks that can be defined once
 * and played several times, a volume change and a repeat count. It is how a
 * J2ME game shipped a tune in a few dozen bytes, and it is the only music
 * format the specification requires a device to understand.</p>
 */
public final class ToneSequence {

    public static final byte VERSION = -2;
    public static final byte TEMPO = -3;
    public static final byte RESOLUTION = -4;
    public static final byte BLOCK_START = -5;
    public static final byte BLOCK_END = -6;
    public static final byte PLAY_BLOCK = -7;
    public static final byte SET_VOLUME = -8;
    public static final byte REPEAT = -9;
    public static final byte SILENCE = -1;

    /** What the specification uses when a sequence says nothing. */
    private static final int DEFAULT_TEMPO = 30;
    private static final int DEFAULT_RESOLUTION = 64;

    private ToneSequence() {
    }

    /**
     * @param sequence the bytes as {@code ToneControl.setSequence} receives them
     * @param volume 0..100, the volume the player is set to
     * @throws IOException if the sequence is malformed — a game may hand over
     *     anything, and half-playing a broken tune is worse than refusing it
     */
    public static AudioClip render(byte[] sequence, int volume) throws IOException {
        if (sequence == null || sequence.length < 2) {
            throw new IOException("Tone sequence is too short");
        }
        if (sequence[0] != VERSION || sequence[1] != 1) {
            throw new IOException("Unsupported tone sequence version");
        }

        // Blocks are defined inline and played by number, so the definitions
        // are collected first and the playback pass then refers to them.
        List<int[]> blocks = new ArrayList<int[]>();
        for (int i = 0; i < 128; i++) {
            blocks.add(null);
        }

        int tempo = DEFAULT_TEMPO;
        int resolution = DEFAULT_RESOLUTION;
        int currentVolume = volume;
        List<int[]> notes = new ArrayList<int[]>();

        int at = 2;
        int blockNumber = -1;
        int blockStart = -1;
        while (at < sequence.length) {
            int command = sequence[at];
            if (command == TEMPO || command == RESOLUTION || command == SET_VOLUME
                    || command == PLAY_BLOCK || command == BLOCK_START || command == REPEAT) {
                if (at + 1 >= sequence.length) {
                    throw new IOException("Tone sequence ends mid-command");
                }
                int value = sequence[at + 1];
                if (command == TEMPO) {
                    tempo = Math.max(1, value);
                } else if (command == RESOLUTION) {
                    resolution = Math.max(1, value);
                } else if (command == SET_VOLUME) {
                    currentVolume = Math.max(0, Math.min(100, value)) * volume / 100;
                } else if (command == BLOCK_START) {
                    blockNumber = value;
                    blockStart = notes.size();
                } else if (command == PLAY_BLOCK) {
                    int[] block = value >= 0 && value < blocks.size() ? blocks.get(value) : null;
                    if (block == null) {
                        throw new IOException("Tone sequence plays an undefined block");
                    }
                    for (int i = 0; i < block.length; i += 3) {
                        notes.add(new int[]{block[i], block[i + 1], block[i + 2]});
                    }
                } else {
                    // REPEAT applies to the note that follows it.
                    int repeats = Math.max(1, value);
                    if (at + 3 >= sequence.length) {
                        throw new IOException("Tone sequence ends mid-repeat");
                    }
                    for (int i = 0; i < repeats; i++) {
                        notes.add(new int[]{sequence[at + 2], sequence[at + 3], currentVolume});
                    }
                    at += 2;
                }
                at += 2;
                continue;
            }
            if (command == BLOCK_END) {
                if (blockNumber < 0 || blockNumber >= blocks.size()) {
                    throw new IOException("Tone sequence ends a block it never started");
                }
                int[] block = new int[(notes.size() - blockStart) * 3];
                for (int i = blockStart; i < notes.size(); i++) {
                    int[] note = notes.get(i);
                    block[(i - blockStart) * 3] = note[0];
                    block[(i - blockStart) * 3 + 1] = note[1];
                    block[(i - blockStart) * 3 + 2] = note[2];
                }
                blocks.set(blockNumber, block);
                // A definition is not itself played; it is kept for PLAY_BLOCK.
                while (notes.size() > blockStart) {
                    notes.remove(notes.size() - 1);
                }
                blockNumber = -1;
                at += 2;
                continue;
            }
            if (at + 1 >= sequence.length) {
                throw new IOException("Tone sequence ends mid-note");
            }
            notes.add(new int[]{command, sequence[at + 1], currentVolume});
            at += 2;
        }

        if (notes.isEmpty()) {
            return ToneSynth.silence(0);
        }
        return renderNotes(notes, unitMs(tempo, resolution));
    }

    /**
     * How long one duration unit lasts.
     *
     * <p>Tempo is carried as quarter-notes-per-minute divided by four, and
     * the resolution is how many units make up a whole note — so a whole note
     * is four quarters and the arithmetic falls out of that.</p>
     */
    private static double unitMs(int tempo, int resolution) {
        double beatsPerMinute = tempo * 4.0;
        double wholeNoteMs = 4.0 * 60000.0 / beatsPerMinute;
        return wholeNoteMs / resolution;
    }

    private static AudioClip renderNotes(List<int[]> notes, double unitMs) {
        int frames = 0;
        int[] lengths = new int[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            int durationMs = (int) Math.round(notes.get(i)[1] * unitMs);
            lengths[i] = Math.max(1, ToneSynth.SAMPLE_RATE * Math.max(0, durationMs) / 1000);
            frames += lengths[i];
        }
        byte[] pcm = new byte[frames * 2];
        int at = 0;
        for (int i = 0; i < notes.size(); i++) {
            int[] note = notes.get(i);
            int value = note[0] == SILENCE ? ToneSynth.REST : note[0];
            AudioClip piece = ToneSynth.tone(value,
                    (int) Math.round(note[1] * unitMs), note[2]);
            byte[] source = piece.pcm();
            int copy = Math.min(source.length, pcm.length - at * 2);
            System.arraycopy(source, 0, pcm, at * 2, Math.max(0, copy));
            at += lengths[i];
        }
        return new AudioClip(pcm, ToneSynth.SAMPLE_RATE);
    }
}
