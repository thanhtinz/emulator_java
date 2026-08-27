package com.mobicore.tests;

import com.mobicore.core.emu.ClipRecorder;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.GifEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * The animated GIF a recorded clip is written as.
 *
 * <p>Written rather than checked off a library, so it is checked by reading
 * it back: the decoder below is a plain GIF decoder, and every frame this
 * suite encodes is decoded again and compared pixel for pixel. A test that
 * only looked at the header would pass on a file no viewer can open.</p>
 */
public final class GifTest extends Test {

    @Override
    public String name() {
        return "Ảnh động GIF";
    }

    @Override
    public void run() throws Exception {
        header();
        roundTrip();
        manyColours();
        recorder();
    }

    // ------------------------------------------------------------ the format

    private void header() {
        byte[] gif = GifEncoder.encode(frames(twoColour(8, 4)), 8, 4, 10);
        eq("GIF89a", ascii(gif, 0, 6), "the file says what it is");
        eq(8, gif[6] & 0xFF | (gif[7] & 0xFF) << 8, "the width is in the header");
        eq(4, gif[8] & 0xFF | (gif[9] & 0xFF) << 8, "and the height");
        check((gif[10] & 0x80) != 0, "and it carries a colour table of its own");
        eq(0x3B, gif[gif.length - 1] & 0xFF, "and ends where a GIF ends");

        // The Netscape block is how a GIF says "play me again"; without it a
        // clip of play stops after one pass.
        check(indexOf(gif, "NETSCAPE2.0") > 0, "it is marked to loop forever");

        // An empty recording is refused rather than written as a file with
        // nothing in it.
        boolean refused = false;
        try {
            GifEncoder.encode(new ArrayList<int[]>(), 8, 4, 10);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "a clip with no frames is refused");
    }

    /** Encode, decode, compare: the only check that means the file works. */
    private void roundTrip() {
        int width = 23;
        int height = 17;
        List<int[]> frames = new ArrayList<int[]>();
        for (int f = 0; f < 3; f++) {
            frames.add(gradient(width, height, f));
        }
        byte[] gif = GifEncoder.encode(frames, width, height, 10);

        Gif decoded = Gif.read(gif);
        eq(width, decoded.width, "the decoded width matches");
        eq(height, decoded.height, "the decoded height matches");
        eq(3, decoded.frames.size(), "every frame is in the file");
        eq(10, decoded.delayCs, "and each is held for as long as asked");

        for (int f = 0; f < frames.size(); f++) {
            int[] source = frames.get(f);
            int[] back = decoded.frames.get(f);
            int wrong = 0;
            for (int i = 0; i < source.length; i++) {
                if ((source[i] & 0xFFFFFF) != (back[i] & 0xFFFFFF)) {
                    wrong++;
                }
            }
            // Under 256 colours the table holds the game's own palette, so
            // the picture comes back exactly rather than approximately.
            eq(0, wrong, "frame " + (f + 1) + " comes back pixel for pixel");
        }
    }

    /**
     * More colours than a GIF can hold, which is where the quantiser earns
     * its place: 256 entries chosen from thousands, and nothing left
     * unrecognisable.
     */
    private void manyColours() {
        int width = 64;
        int height = 64;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = 0xFF000000 | (x * 4) << 16 | (y * 4) << 8 | (x + y);
            }
        }
        byte[] gif = GifEncoder.encode(frames(pixels), width, height, 10);
        Gif decoded = Gif.read(gif);
        eq(1, decoded.frames.size(), "a still is a one-frame animation");

        long error = 0;
        int[] back = decoded.frames.get(0);
        for (int i = 0; i < pixels.length; i++) {
            error += Math.abs(((pixels[i] >> 16) & 0xFF) - ((back[i] >> 16) & 0xFF));
            error += Math.abs(((pixels[i] >> 8) & 0xFF) - ((back[i] >> 8) & 0xFF));
            error += Math.abs((pixels[i] & 0xFF) - (back[i] & 0xFF));
        }
        long perChannel = error / (pixels.length * 3L);
        check(perChannel <= 4, "4096 colours in 256 stay recognisable: off by "
                + perChannel + " per channel");
    }

    // ---------------------------------------------------------- the recorder

    private void recorder() {
        ClipRecorder recorder = new ClipRecorder();
        Framebuffer screen = new Framebuffer(16, 12);
        check(!recorder.isRecording(), "nothing is being recorded to begin with");
        eq(null, recorder.stop(), "and stopping records nothing");

        long now = 5_000L;
        recorder.start(now);
        check(recorder.isRecording(), "a clip starts when it is asked to");

        // Frames are kept off the game's own clock, so a game slowed down
        // records slowly rather than dropping every other frame.
        for (int i = 0; i < 5; i++) {
            screen.fill(0xFF000000 | (i * 40) << 8);
            recorder.tick(screen, now);
            // Half an interval: too soon for a second frame.
            recorder.tick(screen, now + ClipRecorder.FRAME_INTERVAL_MS / 2);
            now += ClipRecorder.FRAME_INTERVAL_MS;
        }
        eq(5, recorder.frameCount(), "one frame per interval, not one per tick");
        eq(5, recorder.tenths(), "which is half a second of play");

        byte[] gif = recorder.stop();
        check(gif != null && gif.length > 0, "and it encodes to something");
        Gif decoded = Gif.read(gif);
        eq(5, decoded.frames.size(), "with every frame in it");
        eq(16, decoded.width, "at the size of the emulated screen");

        check(!recorder.isRecording(), "stopping stops it");
        eq(0, recorder.frameCount(), "and lets go of the frames it was holding");

        // The cap exists because frames are held in memory until the clip is
        // whole: without it, holding a button runs a phone out of memory.
        recorder.start(now);
        for (int i = 0; i < ClipRecorder.MAX_FRAMES + 20; i++) {
            recorder.tick(screen, now);
            now += ClipRecorder.FRAME_INTERVAL_MS;
        }
        eq(ClipRecorder.MAX_FRAMES, recorder.frameCount(), "a clip stops at its length");
        check(recorder.isFull(), "and says it is full");
        check(!recorder.isRecording(), "and is no longer recording");
        check(recorder.stop() != null, "but what it did record is still there to save");

        recorder.start(now);
        recorder.tick(screen, now);
        recorder.cancel();
        eq(0, recorder.frameCount(), "a cancelled clip is thrown away");
        eq(null, recorder.stop(), "and there is nothing left to save");
    }

    // ------------------------------------------------------------- fixtures

    private List<int[]> frames(int[] pixels) {
        List<int[]> frames = new ArrayList<int[]>();
        frames.add(pixels);
        return frames;
    }

    private int[] twoColour(int width, int height) {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (i % 3 == 0) ? 0xFFFF0000 : 0xFF0000FF;
        }
        return pixels;
    }

    /** A handful of colours in bands, moved along by one each frame. */
    private int[] gradient(int width, int height, int shift) {
        int[] palette = {0xFF000000, 0xFF204080, 0xFF40C040, 0xFFF0F0F0, 0xFFCC2200};
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = palette[(x + y + shift) % palette.length];
            }
        }
        return pixels;
    }

    private String ascii(byte[] data, int at, int length) {
        StringBuilder text = new StringBuilder();
        for (int i = at; i < at + length && i < data.length; i++) {
            text.append((char) (data[i] & 0xFF));
        }
        return text.toString();
    }

    private int indexOf(byte[] data, String text) {
        for (int i = 0; i + text.length() <= data.length; i++) {
            if (ascii(data, i, text.length()).equals(text)) {
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------- decoder

    /**
     * A GIF reader, written for this test alone.
     *
     * <p>The emulator does not read GIFs — nothing asks it to — so this lives
     * here rather than in the core. It is what makes the encoder's output
     * checkable: a file that this decodes back to the pixels that went in is
     * a file a viewer can open.</p>
     */
    private static final class Gif {

        int width;
        int height;
        int delayCs;
        final List<int[]> frames = new ArrayList<int[]>();

        static Gif read(byte[] data) {
            Gif gif = new Gif();
            gif.width = u16(data, 6);
            gif.height = u16(data, 8);
            int packed = data[10] & 0xFF;
            int tableSize = 1 << ((packed & 0x07) + 1);
            int at = 13;
            int[] table = new int[tableSize];
            for (int i = 0; i < tableSize; i++) {
                table[i] = (data[at] & 0xFF) << 16 | (data[at + 1] & 0xFF) << 8
                        | (data[at + 2] & 0xFF);
                at += 3;
            }
            while (at < data.length) {
                int block = data[at] & 0xFF;
                if (block == 0x3B) {
                    break;
                }
                if (block == 0x21) {
                    int label = data[at + 1] & 0xFF;
                    if (label == 0xF9) {
                        gif.delayCs = u16(data, at + 4);
                    }
                    at = skipBlocks(data, at + 2);
                    continue;
                }
                if (block != 0x2C) {
                    break;
                }
                int frameWidth = u16(data, at + 5);
                int frameHeight = u16(data, at + 7);
                at += 10;
                int codeSize = data[at++] & 0xFF;
                byte[] packedData = collect(data, at);
                at = skipBlocks(data, at);
                byte[] indices = inflate(packedData, codeSize, frameWidth * frameHeight);
                int[] pixels = new int[frameWidth * frameHeight];
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = 0xFF000000 | table[indices[i] & 0xFF];
                }
                gif.frames.add(pixels);
            }
            return gif;
        }

        private static int u16(byte[] data, int at) {
            return (data[at] & 0xFF) | (data[at + 1] & 0xFF) << 8;
        }

        /** Past a run of length-prefixed sub-blocks. */
        private static int skipBlocks(byte[] data, int at) {
            while (at < data.length && (data[at] & 0xFF) != 0) {
                at += (data[at] & 0xFF) + 1;
            }
            return at + 1;
        }

        /** The sub-blocks joined back into one run of bytes. */
        private static byte[] collect(byte[] data, int at) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while (at < data.length && (data[at] & 0xFF) != 0) {
                int length = data[at] & 0xFF;
                out.write(data, at + 1, length);
                at += length + 1;
            }
            return out.toByteArray();
        }

        /** GIF's variable-width LZW, unpacked. */
        private static byte[] inflate(byte[] data, int codeSize, int expected) {
            int clearCode = 1 << codeSize;
            int endCode = clearCode + 1;
            int[] prefix = new int[4096];
            int[] suffix = new int[4096];
            for (int i = 0; i < clearCode; i++) {
                prefix[i] = -1;
                suffix[i] = i;
            }
            int next = endCode + 1;
            int width = codeSize + 1;
            int previous = -1;
            byte[] out = new byte[expected];
            int written = 0;
            int bitAt = 0;
            byte[] stack = new byte[4096];

            while (written < expected && (bitAt + width) <= data.length * 8) {
                int code = 0;
                for (int bit = 0; bit < width; bit++) {
                    int index = bitAt + bit;
                    int value = (data[index >> 3] >> (index & 7)) & 1;
                    code |= value << bit;
                }
                bitAt += width;
                if (code == clearCode) {
                    next = endCode + 1;
                    width = codeSize + 1;
                    previous = -1;
                    continue;
                }
                if (code == endCode) {
                    break;
                }
                int current = code;
                int depth = 0;
                if (code >= next && previous >= 0) {
                    // The one case a decoder has to guess: a code for a string
                    // that has not been added yet is that string plus its own
                    // first character.
                    stack[depth++] = (byte) firstOf(prefix, suffix, previous);
                    current = previous;
                }
                while (current >= 0 && depth < stack.length) {
                    stack[depth++] = (byte) suffix[current];
                    current = prefix[current];
                }
                for (int i = depth - 1; i >= 0 && written < expected; i--) {
                    out[written++] = stack[i];
                }
                if (previous >= 0 && next < 4096) {
                    prefix[next] = previous;
                    suffix[next] = firstOf(prefix, suffix, code >= next ? previous : code);
                    next++;
                    if (next == (1 << width) && width < 12) {
                        width++;
                    }
                }
                previous = code;
            }
            return out;
        }

        private static int firstOf(int[] prefix, int[] suffix, int code) {
            while (prefix[code] >= 0) {
                code = prefix[code];
            }
            return suffix[code];
        }
    }
}
