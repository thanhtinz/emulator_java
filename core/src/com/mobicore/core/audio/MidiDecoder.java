package com.mobicore.core.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Standard MIDI files, rendered to the same PCM everything else here plays.
 *
 * <p>Music in a J2ME game is nearly always a {@code .mid}: it was the only
 * format that fitted, and the handset had a synthesiser in hardware to play
 * it. Without one, every game that ships music is silent — which is not a
 * small loss, because the tune is half of what anyone remembers about these
 * games.</p>
 *
 * <p>What this is: a file reader and a very small synthesiser. It reads the
 * header, merges the tracks into one timeline of note-on and note-off events,
 * follows tempo changes, and sounds each note as a square wave for exactly as
 * long as it is held. What it is not is a soundbank — a handset's chip had
 * instruments, and a piano and a flute here are the same wave. That is the
 * honest trade: the melody, the rhythm and the harmony are the game's, and the
 * timbre is the emulator's.</p>
 *
 * <p>The percussion channel is left silent rather than sounded. Channel 10 in
 * MIDI does not carry pitches, it carries drum numbers, so playing it as
 * pitches turns a snare into a note that was never in the tune — noise in the
 * literal sense, over the top of the melody.</p>
 */
public final class MidiDecoder {

    /** The longest piece rendered, so one long file cannot eat the heap. */
    private static final int MAX_SECONDS = 180;
    /** MIDI's own default when a file states no tempo: 120 beats a minute. */
    private static final int DEFAULT_TEMPO_US = 500_000;
    /** Drums, which carry numbers rather than pitches. */
    private static final int PERCUSSION_CHANNEL = 9;

    private MidiDecoder() {
    }

    /** True when the bytes begin the way a Standard MIDI File does. */
    public static boolean looksLikeMidi(byte[] data) {
        return data != null && data.length > 14 && data[0] == 'M' && data[1] == 'T'
                && data[2] == 'h' && data[3] == 'd';
    }

    /** One note, once the timeline has been worked out. */
    private static final class Note {

        int note;
        int velocity;
        long startTick;
        long endTick;
    }

    private static final class Event {

        long tick;
        int order;
        int status;
        int data1;
        int data2;
        int tempoUs;
    }

    /**
     * Renders a MIDI file.
     *
     * @param volume 0..100, applied on top of each note's own velocity
     */
    public static AudioClip decode(byte[] data, int volume) throws IOException {
        if (!looksLikeMidi(data)) {
            throw new IOException("Không phải tệp MIDI");
        }
        Cursor head = new Cursor(data, 4);
        int headerLength = head.int32();
        int format = head.int16();
        int trackCount = head.int16();
        int division = head.int16();
        if (format > 2 || trackCount <= 0) {
            throw new IOException("Tệp MIDI không đọc được");
        }
        if ((division & 0x8000) != 0) {
            // SMPTE timing states frames per second rather than ticks per
            // beat. No J2ME game ships one, and guessing at it would play the
            // tune at the wrong speed rather than not at all.
            throw new IOException("MIDI theo khung hình (SMPTE) chưa hỗ trợ");
        }
        int ticksPerBeat = division == 0 ? 96 : division;

        List<Event> events = new ArrayList<Event>();
        int at = 8 + headerLength;
        for (int track = 0; track < trackCount && at + 8 <= data.length; track++) {
            Cursor cursor = new Cursor(data, at);
            int tag = cursor.int32();
            int length = cursor.int32();
            int end = Math.min(data.length, cursor.at + length);
            if (tag == 0x4D54726B) {
                readTrack(cursor, end, events);
            }
            at = cursor.at + length;
        }
        if (events.isEmpty()) {
            throw new IOException("Tệp MIDI không có nốt nào");
        }

        // A stable order matters: two events on the same tick must resolve
        // the same way every time, or the same file renders differently.
        Collections.sort(events, new Comparator<Event>() {
            public int compare(Event left, Event right) {
                if (left.tick != right.tick) {
                    return left.tick < right.tick ? -1 : 1;
                }
                return left.order - right.order;
            }
        });

        List<Note> notes = collectNotes(events);
        if (notes.isEmpty()) {
            throw new IOException("Tệp MIDI không có nốt nào nghe được");
        }
        return render(notes, events, ticksPerBeat, volume);
    }

    // ------------------------------------------------------------- reading

    private static void readTrack(Cursor cursor, int end, List<Event> into) throws IOException {
        long tick = 0;
        int running = 0;
        while (cursor.at < end) {
            tick += cursor.varInt();
            int status = cursor.byteAt();
            if (status < 0x80) {
                // Running status: an event that omits its status byte reuses
                // the last one, and the byte just read is its first data byte.
                cursor.at--;
                status = running;
                if (status == 0) {
                    throw new IOException("Tệp MIDI hỏng");
                }
            } else if (status < 0xF0) {
                running = status;
            }

            if (status == 0xFF) {
                int type = cursor.byteAt();
                int length = cursor.varInt();
                if (type == 0x51 && length == 3) {
                    Event tempo = new Event();
                    tempo.tick = tick;
                    tempo.order = into.size();
                    tempo.status = 0xFF;
                    tempo.tempoUs = (cursor.byteAt() << 16) | (cursor.byteAt() << 8)
                            | cursor.byteAt();
                    into.add(tempo);
                } else {
                    cursor.skip(length);
                }
                continue;
            }
            if (status == 0xF0 || status == 0xF7) {
                cursor.skip(cursor.varInt());
                continue;
            }

            int kind = status & 0xF0;
            int data1 = cursor.byteAt();
            int data2 = kind == 0xC0 || kind == 0xD0 ? 0 : cursor.byteAt();
            if (kind == 0x80 || kind == 0x90) {
                Event event = new Event();
                event.tick = tick;
                event.order = into.size();
                event.status = status;
                event.data1 = data1;
                event.data2 = data2;
                into.add(event);
            }
        }
    }

    /** Pairs each note-on with the note-off that ends it. */
    private static List<Note> collectNotes(List<Event> events) {
        List<Note> notes = new ArrayList<Note>();
        // One pending note per channel and pitch: a file that starts the same
        // note twice without ending it is common, and the second start is
        // what a synthesiser hears.
        Note[] pending = new Note[16 * 128];
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.status == 0xFF) {
                continue;
            }
            int channel = event.status & 0x0F;
            int kind = event.status & 0xF0;
            int slot = channel * 128 + event.data1;
            boolean off = kind == 0x80 || event.data2 == 0;
            if (off) {
                Note open = pending[slot];
                if (open != null) {
                    open.endTick = event.tick;
                    pending[slot] = null;
                }
                continue;
            }
            if (channel == PERCUSSION_CHANNEL) {
                continue;
            }
            Note open = pending[slot];
            if (open != null) {
                open.endTick = event.tick;
            }
            Note note = new Note();
            note.note = event.data1;
            note.velocity = event.data2;
            note.startTick = event.tick;
            note.endTick = -1;
            pending[slot] = note;
            notes.add(note);
        }
        // A note left hanging at the end of the file gets a beat, rather than
        // being dropped or held forever.
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            if (note.endTick < 0) {
                note.endTick = note.startTick;
            }
        }
        return notes;
    }

    // ----------------------------------------------------------- rendering

    private static AudioClip render(List<Note> notes, List<Event> events, int ticksPerBeat,
                                    int volume) {
        // Tick to millisecond, following every tempo change in order.
        int rate = ToneSynth.SAMPLE_RATE;
        long lastTick = 0;
        for (int i = 0; i < notes.size(); i++) {
            lastTick = Math.max(lastTick, notes.get(i).endTick);
        }
        double[] tickMs = timeline(events, ticksPerBeat, lastTick);

        long totalMs = (long) millisAt(tickMs, lastTick, ticksPerBeat) + 400;
        int frames = (int) Math.min((long) MAX_SECONDS * rate, totalMs * rate / 1000 + rate / 10);
        if (frames <= 0) {
            frames = rate / 10;
        }
        int[] mix = new int[frames];

        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            double startMs = millisAt(tickMs, note.startTick, ticksPerBeat);
            double endMs = millisAt(tickMs, note.endTick, ticksPerBeat);
            int startFrame = (int) (startMs * rate / 1000);
            int endFrame = (int) (endMs * rate / 1000);
            if (endFrame <= startFrame) {
                endFrame = startFrame + rate / 20;
            }
            if (startFrame >= frames) {
                continue;
            }
            endFrame = Math.min(endFrame, frames);
            add(mix, startFrame, endFrame, note.note, note.velocity, volume);
        }

        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            int value = mix[i];
            // Clipped rather than scaled: a piece with one loud chord would
            // otherwise turn the whole tune down to accommodate it.
            if (value > 32767) {
                value = 32767;
            } else if (value < -32768) {
                value = -32768;
            }
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return new AudioClip(pcm, rate);
    }

    /**
     * Milliseconds per tick, as a running total at each tempo change.
     *
     * <p>Returned as pairs: tick, then the millisecond that tick falls on,
     * then the microseconds per beat from there on.</p>
     */
    private static double[] timeline(List<Event> events, int ticksPerBeat, long lastTick) {
        List<double[]> points = new ArrayList<double[]>();
        double ms = 0;
        long tick = 0;
        int tempo = DEFAULT_TEMPO_US;
        points.add(new double[]{0, 0, tempo});
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.status != 0xFF || event.tempoUs <= 0) {
                continue;
            }
            ms += (event.tick - tick) * (tempo / 1000.0) / ticksPerBeat;
            tick = event.tick;
            tempo = event.tempoUs;
            points.add(new double[]{tick, ms, tempo});
        }
        double[] flat = new double[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            double[] point = points.get(i);
            flat[i * 3] = point[0];
            flat[i * 3 + 1] = point[1];
            flat[i * 3 + 2] = point[2];
        }
        return flat;
    }

    private static double millisAt(double[] timeline, long tick, int ticksPerBeat) {
        int index = 0;
        for (int i = 0; i * 3 < timeline.length; i++) {
            if (timeline[i * 3] <= tick) {
                index = i;
            }
        }
        double baseTick = timeline[index * 3];
        double baseMs = timeline[index * 3 + 1];
        double tempo = timeline[index * 3 + 2];
        return baseMs + (tick - baseTick) * (tempo / 1000.0) / ticksPerBeat;
    }

    /** One voice, added into the mix rather than replacing what is there. */
    private static void add(int[] mix, int startFrame, int endFrame, int note, int velocity,
                            int volume) {
        double period = ToneSynth.SAMPLE_RATE / ToneSynth.frequencyOf(
                Math.max(0, Math.min(127, note)));
        int peak = 32767 * Math.max(0, Math.min(100, volume)) / 100;
        peak = peak * Math.max(1, Math.min(127, velocity)) / 127;
        // Room for several voices at once without clipping on every chord.
        peak = peak / 4;
        int length = endFrame - startFrame;
        int fade = Math.min(length / 2, ToneSynth.SAMPLE_RATE / 200);
        for (int i = 0; i < length; i++) {
            double phase = (i % period) / period;
            int value = phase < 0.5 ? peak : -peak;
            int edge = Math.min(i, length - 1 - i);
            if (fade > 0 && edge < fade) {
                value = value * edge / fade;
            }
            mix[startFrame + i] += value;
        }
    }

    /** A byte reader that keeps its place, MIDI's variable-length ints and all. */
    private static final class Cursor {

        private final byte[] data;
        private int at;

        Cursor(byte[] data, int at) {
            this.data = data;
            this.at = at;
        }

        int byteAt() throws IOException {
            if (at >= data.length) {
                throw new IOException("Tệp MIDI kết thúc giữa chừng");
            }
            return data[at++] & 0xFF;
        }

        int int16() throws IOException {
            return (byteAt() << 8) | byteAt();
        }

        int int32() throws IOException {
            return (byteAt() << 24) | (byteAt() << 16) | (byteAt() << 8) | byteAt();
        }

        /** MIDI's seven-bits-per-byte length, high bit meaning "more". */
        int varInt() throws IOException {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int part = byteAt();
                value = (value << 7) | (part & 0x7F);
                if ((part & 0x80) == 0) {
                    return value;
                }
            }
            throw new IOException("Tệp MIDI hỏng");
        }

        void skip(int count) {
            at = Math.min(data.length, at + Math.max(0, count));
        }
    }
}
