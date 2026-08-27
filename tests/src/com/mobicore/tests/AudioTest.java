package com.mobicore.tests;

import com.mobicore.core.audio.AudioClip;
import com.mobicore.core.audio.MidiDecoder;
import com.mobicore.core.audio.AudioLog;
import com.mobicore.core.audio.ToneSequence;
import com.mobicore.core.audio.ToneSynth;
import com.mobicore.core.audio.WavDecoder;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.vm.VmHost;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.io.IOException;

/**
 * Sound: the synthesiser, the WAV decoder, the tone sequence renderer, and
 * the MIDP media classes driven by a real MIDlet running as bytecode.
 */
public final class AudioTest extends Test {

    private final String fixtureDir;

    public AudioTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Sound: synth, WAV and media";
    }

    @Override
    public void run() throws Exception {
        synth();
        wav();
        midi();
        sequences();
        log();
        if (!new File(fixtureDir, "demo/SoundDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        midlet();
    }

    // -------------------------------------------------------------- synth

    private void synth() {
        eq(440, (int) Math.round(ToneSynth.frequencyOf(69)), "MIDI note 69 is concert A");
        eq(880, (int) Math.round(ToneSynth.frequencyOf(81)), "an octave up doubles the frequency");

        AudioClip clip = ToneSynth.tone(69, 100, 100);
        eq(ToneSynth.SAMPLE_RATE, clip.sampleRate(), "tones come out at the synth's rate");
        eq(100L, clip.durationMs(), "a 100ms tone lasts 100ms");
        eq(clip.frames() * 2, clip.pcm().length, "samples are sixteen bits");

        // A square wave crosses zero twice a cycle, so counting the crossings
        // is how you check the note is the note that was asked for.
        eq(440, crossingsPerSecond(clip) / 2, "the tone sounds at the note's frequency");

        AudioClip quiet = ToneSynth.tone(69, 100, 20);
        check(peak(quiet) < peak(clip), "a lower volume is quieter");
        eq(0, peak(ToneSynth.tone(69, 100, 0)), "volume zero is silence");
        eq(0, peak(ToneSynth.silence(50)), "a rest is silent");
        eq(50L, ToneSynth.silence(50).durationMs(), "and still takes its time");

        // The ends of a note fade, so a run of notes does not click.
        check(Math.abs(clip.sample(0)) < peak(clip) / 4, "a note fades in");
        check(Math.abs(clip.sample(clip.frames() - 1)) < peak(clip) / 4, "and fades out");
    }

    // ---------------------------------------------------------------- WAV

    private void wav() throws Exception {
        AudioClip source = ToneSynth.tone(72, 60, 90);
        byte[] file = WavDecoder.encode(source);
        check(WavDecoder.looksLikeWav(file), "an encoded clip is recognised as a WAV");
        check(!WavDecoder.looksLikeWav(new byte[]{1, 2, 3}), "and random bytes are not");

        AudioClip decoded = WavDecoder.decode(file);
        eq(source.sampleRate(), decoded.sampleRate(), "the rate survives the round trip");
        eq(source.frames(), decoded.frames(), "so does the length");
        eq(source.sample(100), decoded.sample(100), "and every sample");

        // Eight-bit WAV is unsigned with 128 as silence; sixteen-bit is signed.
        byte[] eightBit = eightBitWav();
        AudioClip widened = WavDecoder.decode(eightBit);
        eq(8000, widened.sampleRate(), "an eight-bit file keeps its rate");
        eq(0, widened.sample(0), "128 is the middle of an unsigned sample");
        check(widened.sample(1) > 20000, "and 255 is near the top");

        AudioClip stereo = WavDecoder.decode(stereoWav());
        eq(2, stereo.frames(), "a stereo file yields one frame per pair");
        eq(1000, stereo.sample(0), "the two channels are averaged, not concatenated");

        expectIo("only PCM is decoded", compressedWav());
        expectIo("a file with no data chunk is refused", headerOnlyWav());
        expectIo("a file that is not a WAV at all is refused", new byte[]{'N', 'O', 'P', 'E'});
    }

    // --------------------------------------------------------------- midi

    /**
     * MIDI: what nearly every J2ME game's music is.
     *
     * <p>The file built here is the smallest one that proves the reader did
     * its job — a tempo, two notes of known length, one of them on the
     * percussion channel — so the checks are about time and pitch rather than
     * about bytes going in and out.</p>
     */
    private void midi() throws Exception {
        check(MidiDecoder.looksLikeMidi(simpleMidi()), "a MIDI file is recognised");
        check(!MidiDecoder.looksLikeMidi(new byte[]{'N', 'O', 'P', 'E'}),
                "and something else is not");

        AudioClip clip = MidiDecoder.decode(simpleMidi(), 80);
        eq(ToneSynth.SAMPLE_RATE, clip.sampleRate(), "it renders at the emulator's rate");
        // Two beats at 120 bpm is a second; the render adds a short tail.
        check(clip.durationMs() >= 1000 && clip.durationMs() < 1600,
                "the tempo is followed rather than guessed: " + clip.durationMs() + " ms");

        // The first note sounds, and the gap after the second is silent.
        check(loudness(clip, 0, 200) > 0, "the first note is audible");
        check(loudness(clip, 1100, 1300) == 0, "and the piece ends where the notes end");

        // Percussion is left silent: channel 10 carries drum numbers, not
        // pitches, so sounding it would put a note in the tune that was never
        // written there.
        AudioClip drumsOnly = null;
        try {
            drumsOnly = MidiDecoder.decode(percussionOnlyMidi(), 80);
            fail("a file with nothing but drums has nothing to sound");
        } catch (java.io.IOException expected) {
            check(true, "a file with nothing but drums is refused rather than rendered as noise");
        }

        expectMidiFailure("a file that is not MIDI is refused", new byte[]{1, 2, 3, 4});
        expectMidiFailure("so is one that ends mid-track", truncatedMidi());
    }

    private void expectMidiFailure(String message, byte[] data) {
        try {
            MidiDecoder.decode(data, 80);
            fail(message);
        } catch (java.io.IOException expected) {
            check(true, message);
        }
    }

    /** Peak sample between two moments, in milliseconds. */
    private int loudness(AudioClip clip, int fromMs, int toMs) {
        int from = fromMs * clip.sampleRate() / 1000;
        int to = Math.min(clip.frames(), toMs * clip.sampleRate() / 1000);
        int peak = 0;
        for (int i = from; i < to; i++) {
            peak = Math.max(peak, Math.abs(clip.sample(i)));
        }
        return peak;
    }

    /**
     * Two crotchets at 120 beats a minute: one melodic, one on the drum
     * channel, so both halves of the reader are exercised.
     */
    private byte[] simpleMidi() {
        java.io.ByteArrayOutputStream track = new java.io.ByteArrayOutputStream();
        // Tempo: 500000 microseconds a beat, which is 120 bpm.
        track.write(0x00);
        track.write(0xFF);
        track.write(0x51);
        track.write(0x03);
        track.write(0x07);
        track.write(0xA1);
        track.write(0x20);
        // Note on, middle C, then off a beat later.
        note(track, 0x90, 60, 100, 0);
        note(track, 0x80, 60, 0, 96);
        // A drum hit over the second beat, which must not be heard.
        note(track, 0x99, 38, 100, 0);
        note(track, 0x89, 38, 0, 96);
        // End of track.
        track.write(0x00);
        track.write(0xFF);
        track.write(0x2F);
        track.write(0x00);
        return midiFile(track.toByteArray());
    }

    private byte[] percussionOnlyMidi() {
        java.io.ByteArrayOutputStream track = new java.io.ByteArrayOutputStream();
        note(track, 0x99, 38, 100, 0);
        note(track, 0x89, 38, 0, 96);
        track.write(0x00);
        track.write(0xFF);
        track.write(0x2F);
        track.write(0x00);
        return midiFile(track.toByteArray());
    }

    private byte[] truncatedMidi() {
        byte[] whole = simpleMidi();
        byte[] cut = new byte[whole.length - 6];
        System.arraycopy(whole, 0, cut, 0, cut.length);
        return cut;
    }

    private void note(java.io.ByteArrayOutputStream out, int status, int note, int velocity,
                      int delta) {
        out.write(delta);
        out.write(status);
        out.write(note);
        out.write(velocity);
    }

    private byte[] midiFile(byte[] track) {
        byte[] file = new byte[14 + 8 + track.length];
        ascii(file, 0, "MThd");
        // MIDI is big-endian, where WAV is little: the same helper cannot
        // serve both.
        writeBigInt(file, 4, 6);
        file[8] = 0;
        file[9] = 0;      // format 0
        file[10] = 0;
        file[11] = 1;     // one track
        file[12] = 0;
        file[13] = 96;    // ticks per beat
        ascii(file, 14, "MTrk");
        writeBigInt(file, 18, track.length);
        System.arraycopy(track, 0, file, 22, track.length);
        return file;
    }

    private void expectIo(String message, byte[] data) {
        try {
            WavDecoder.decode(data);
            fail(message);
        } catch (IOException expected) {
            check(true, message);
        }
    }

    // ---------------------------------------------------------- sequences

    private void sequences() throws Exception {
        byte[] simple = {
                ToneSequence.VERSION, 1,
                ToneSequence.TEMPO, 30,
                60, 8, 62, 8, ToneSequence.SILENCE, 8, 64, 8,
        };
        AudioClip clip = ToneSequence.render(simple, 100);
        // Tempo 30 means 120 beats a minute, and the default resolution puts
        // 64 units in a whole note: eight units is an eighth note, 250ms.
        eq(1000L, clip.durationMs(), "four eighth notes at 120bpm last two seconds' half");

        byte[] withBlock = {
                ToneSequence.VERSION, 1,
                ToneSequence.TEMPO, 30,
                ToneSequence.BLOCK_START, 0,
                60, 8, 62, 8,
                ToneSequence.BLOCK_END, 0,
                ToneSequence.PLAY_BLOCK, 0,
                ToneSequence.PLAY_BLOCK, 0,
        };
        AudioClip blocked = ToneSequence.render(withBlock, 100);
        eq(1000L, blocked.durationMs(), "a block played twice is twice as long as itself");

        byte[] repeated = {
                ToneSequence.VERSION, 1,
                ToneSequence.TEMPO, 30,
                ToneSequence.REPEAT, 3, 60, 8,
        };
        eq(750L, ToneSequence.render(repeated, 100).durationMs(), "repeat plays the note again");

        byte[] quiet = {
                ToneSequence.VERSION, 1,
                ToneSequence.SET_VOLUME, 50,
                60, 8,
        };
        check(peak(ToneSequence.render(quiet, 100)) < peak(ToneSequence.render(new byte[]{
                ToneSequence.VERSION, 1, 60, 8}, 100)), "set volume makes the tune quieter");

        expectBadSequence("a sequence with no version is refused", new byte[]{60, 8});
        expectBadSequence("a sequence that ends mid-note is refused",
                new byte[]{ToneSequence.VERSION, 1, 60});
        expectBadSequence("playing an undefined block is refused",
                new byte[]{ToneSequence.VERSION, 1, ToneSequence.PLAY_BLOCK, 3});
    }

    private void expectBadSequence(String message, byte[] sequence) {
        try {
            ToneSequence.render(sequence, 100);
            fail(message);
        } catch (IOException expected) {
            check(true, message);
        }
    }

    // ----------------------------------------------------------- the sink

    private void log() {
        final long[] now = {1000L};
        AudioLog sink = new AudioLog(new AudioLog.Clock() {
            public long nowMs() {
                return now[0];
            }
        });

        int voice = sink.start(ToneSynth.tone(69, 200, 100), 1, 80);
        check(sink.isPlaying(voice), "a voice that just started is playing");
        eq(80, sink.volumeOf(voice), "the volume it started at is recorded");
        now[0] += 100;
        eq(100L, sink.positionMs(voice), "position follows the clock");
        now[0] += 150;
        check(!sink.isPlaying(voice), "a voice stops when the clip runs out");
        eq(200L, sink.positionMs(voice), "and its position stops at the end");

        int loop = sink.start(ToneSynth.tone(69, 100, 100), 0, 100);
        now[0] += 10_000;
        check(sink.isPlaying(loop), "a loop of zero plays until it is stopped");
        sink.stop(loop);
        check(!sink.isPlaying(loop), "and stops when it is");

        sink.setVolume(loop, 25);
        eq(25, sink.volumeOf(loop), "a running voice can be turned down");
        eq(2, sink.entries().size(), "every sound played is on the record");
    }

    // -------------------------------------------------------- the MIDlet

    private void midlet() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        final long[] now = {5_000L};
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, new VmHost() {
            public long currentTimeMillis() {
                return now[0];
            }

            public void sleep(long millis) {
                now[0] += millis;
            }

            public void print(boolean error, String text) {
            }

            public void exit(int code) {
            }

            public String property(String name) {
                return null;
            }
        });
        session.start("demo.SoundDemo");

        AudioLog sink = (AudioLog) session.audio();
        check(sink.entries().size() >= 3,
                "the MIDlet's beep, tune and effect all reached the speaker");

        AudioLog.Entry beep = sink.entries().get(0);
        eq(120L, beep.clip.durationMs(), "playTone plays for as long as it was asked to");
        eq(1, beep.loops, "and plays once");

        AudioLog.Entry tune = sink.entries().get(1);
        eq(2, tune.loops, "setLoopCount(2) reaches the sink");
        check(tune.clip.durationMs() > 1000L, "the tone sequence rendered a tune, not a blip");

        AudioLog.Entry effect = sink.entries().get(2);
        eq(8000, effect.clip.sampleRate(), "the WAV kept its own sample rate");
        check(effect.volume < beep.volume, "VolumeControl turned the effect down");

        // The screen the MIDlet drew says what happened, including that the
        // MIDI player was refused and the game carried on regardless.
        check(session.renderFrame(), "the sound demo paints");

        // Muting is the user's, not the game's: it scales whatever a game asks
        // for, so a game that plays at full volume still obeys it.
        MidpContext context = session.context();
        context.setMasterVolume(50, false);
        eq(40, context.effectiveVolume(80), "the game's volume is scaled by the user's");
        context.setMasterVolume(100, true);
        eq(0, context.effectiveVolume(100), "muted means silent whatever the game asks");
    }

    // -------------------------------------------------------------- tools

    private int peak(AudioClip clip) {
        int peak = 0;
        for (int i = 0; i < clip.frames(); i++) {
            peak = Math.max(peak, Math.abs(clip.sample(i)));
        }
        return peak;
    }

    private int crossingsPerSecond(AudioClip clip) {
        int crossings = 0;
        for (int i = 1; i < clip.frames(); i++) {
            int previous = clip.sample(i - 1);
            int current = clip.sample(i);
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) {
                crossings++;
            }
        }
        return (int) Math.round(crossings * 1000.0 / clip.durationMs());
    }

    private byte[] eightBitWav() {
        byte[] out = wavHeader(8000, 1, 8, 2);
        out[44] = (byte) 128;
        out[45] = (byte) 255;
        return out;
    }

    private byte[] stereoWav() {
        byte[] out = wavHeader(8000, 2, 16, 8);
        writeShort(out, 44, 500);
        writeShort(out, 46, 1500);
        writeShort(out, 48, -500);
        writeShort(out, 50, -1500);
        return out;
    }

    private byte[] compressedWav() {
        byte[] out = wavHeader(8000, 1, 16, 2);
        // Format 2 is ADPCM, which a handset played and this does not.
        writeShort(out, 20, 2);
        return out;
    }

    private byte[] headerOnlyWav() {
        byte[] full = wavHeader(8000, 1, 16, 0);
        byte[] out = new byte[36];
        System.arraycopy(full, 0, out, 0, 36);
        return out;
    }

    private byte[] wavHeader(int rate, int channels, int bits, int dataBytes) {
        byte[] out = new byte[44 + dataBytes];
        ascii(out, 0, "RIFF");
        writeInt(out, 4, 36 + dataBytes);
        ascii(out, 8, "WAVE");
        ascii(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);
        writeShort(out, 22, channels);
        writeInt(out, 24, rate);
        writeInt(out, 28, rate * channels * bits / 8);
        writeShort(out, 32, channels * bits / 8);
        writeShort(out, 34, bits);
        ascii(out, 36, "data");
        writeInt(out, 40, dataBytes);
        return out;
    }

    private void ascii(byte[] out, int at, String text) {
        for (int i = 0; i < text.length(); i++) {
            out[at + i] = (byte) text.charAt(i);
        }
    }

    private void writeBigInt(byte[] out, int at, int value) {
        out[at] = (byte) (value >> 24);
        out[at + 1] = (byte) (value >> 16);
        out[at + 2] = (byte) (value >> 8);
        out[at + 3] = (byte) value;
    }

    private void writeInt(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }

    private void writeShort(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
    }
}
