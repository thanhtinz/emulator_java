package com.mobicore.core.gfx;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a run of frames as an animated GIF.
 *
 * <p>A screenshot cannot show what a game <em>does</em> — how the jump feels,
 * what the boss pattern is, why the run ended. A short animation can, and GIF
 * is the one moving format that plays wherever a picture plays: in a chat, in
 * a forum post, in the phone's own gallery. No player has to install anything
 * to watch it.</p>
 *
 * <p>It is written here rather than handed to the platform because the
 * emulator has no platform: the same code runs on Android's JVM and, through
 * J2ObjC, on iOS. That means a colour quantiser and an LZW coder of our own,
 * which is what the rest of this file is.</p>
 *
 * <p>GIF holds 256 colours, and these games rarely used more — a J2ME handset
 * had a 12- or 16-bit screen and artists drew for it. So a clip of one comes
 * back looking like the game rather than like a compressed copy of it.</p>
 */
public final class GifEncoder {

    /** GIF measures delays in hundredths of a second, and so does its own clock. */
    public static final int MIN_DELAY_CS = 2;

    private GifEncoder() {
    }

    /**
     * Encodes frames into one looping GIF.
     *
     * @param frames pixels of each frame, ARGB, all the same size
     * @param width frame width
     * @param height frame height
     * @param delayCs how long each frame is held, in hundredths of a second
     */
    public static byte[] encode(List<int[]> frames, int width, int height, int delayCs) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Không có khung hình nào để ghi");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Kích thước khung hình không hợp lệ");
        }
        // Under two hundredths a second every viewer substitutes its own
        // speed, so a smaller number is a promise nothing keeps.
        int delay = delayCs < MIN_DELAY_CS ? MIN_DELAY_CS : delayCs;

        Palette palette = Palette.of(frames, width * height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, width, height, palette);
        // Loop forever: a clip of play is a few seconds long and is watched
        // by watching it several times.
        writeLoopExtension(out);
        for (int i = 0; i < frames.size(); i++) {
            writeFrame(out, frames.get(i), width, height, palette, delay);
        }
        out.write(0x3B); // trailer
        return out.toByteArray();
    }

    // ------------------------------------------------------------- structure

    private static void writeHeader(ByteArrayOutputStream out, int width, int height,
                                    Palette palette) {
        writeAscii(out, "GIF89a");
        writeShort(out, width);
        writeShort(out, height);
        // Global colour table, present, of 2^(bits) entries.
        out.write(0x80 | 0x70 | (palette.bits - 1));
        out.write(0); // background colour
        out.write(0); // pixel aspect ratio: square
        for (int i = 0; i < (1 << palette.bits); i++) {
            int rgb = i < palette.size ? palette.colors[i] : 0;
            out.write((rgb >> 16) & 0xFF);
            out.write((rgb >> 8) & 0xFF);
            out.write(rgb & 0xFF);
        }
    }

    /** The Netscape block, which is how a GIF says "play me again". */
    private static void writeLoopExtension(ByteArrayOutputStream out) {
        out.write(0x21);
        out.write(0xFF);
        out.write(11);
        writeAscii(out, "NETSCAPE2.0");
        out.write(3);
        out.write(1);
        writeShort(out, 0); // repeat count: forever
        out.write(0);
    }

    private static void writeFrame(ByteArrayOutputStream out, int[] argb, int width, int height,
                                   Palette palette, int delayCs) {
        // Graphic control extension: how long this frame is held.
        out.write(0x21);
        out.write(0xF9);
        out.write(4);
        out.write(0); // no transparency, no disposal: every frame is complete
        writeShort(out, delayCs);
        out.write(0);
        out.write(0);

        out.write(0x2C); // image descriptor
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, width);
        writeShort(out, height);
        out.write(0); // no local colour table, not interlaced

        byte[] indexed = new byte[width * height];
        for (int i = 0; i < indexed.length && i < argb.length; i++) {
            indexed[i] = (byte) palette.indexOf(argb[i]);
        }
        lzw(out, indexed, palette.bits);
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) {
            out.write(text.charAt(i) & 0xFF);
        }
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    // ------------------------------------------------------------------ LZW

    /**
     * GIF's variable-width LZW, written straight out as sub-blocks.
     *
     * <p>The dictionary starts as the colours themselves plus a clear code and
     * an end code, grows a code per new string, and is thrown away when it
     * fills. This is the compression GIF specifies; there is no other one it
     * accepts.</p>
     */
    private static void lzw(ByteArrayOutputStream out, byte[] indexed, int bits) {
        int codeSize = bits < 2 ? 2 : bits;
        int clearCode = 1 << codeSize;
        int endCode = clearCode + 1;
        out.write(codeSize);

        BlockWriter blocks = new BlockWriter(out);
        int codeWidth = codeSize + 1;
        int next = endCode + 1;
        // Keyed by prefix and suffix together, which is the only kind of
        // string this dictionary ever holds.
        int[] table = new int[HASH_SIZE];
        int[] codes = new int[HASH_SIZE];
        clearTable(table);

        blocks.write(clearCode, codeWidth);
        int prefix = indexed.length > 0 ? (indexed[0] & 0xFF) : 0;
        for (int i = 1; i < indexed.length; i++) {
            int suffix = indexed[i] & 0xFF;
            int key = (prefix << 8) | suffix;
            int slot = find(table, key);
            if (table[slot] == key) {
                prefix = codes[slot];
                continue;
            }
            blocks.write(prefix, codeWidth);
            if (next < 4096) {
                table[slot] = key;
                codes[slot] = next;
                // The code width grows the moment the next code would not fit.
                if (next == (1 << codeWidth) && codeWidth < 12) {
                    codeWidth++;
                }
                next++;
            } else {
                // Full: start again, which is what a decoder expects to see.
                blocks.write(clearCode, codeWidth);
                clearTable(table);
                codeWidth = codeSize + 1;
                next = endCode + 1;
            }
            prefix = suffix;
        }
        blocks.write(prefix, codeWidth);
        blocks.write(endCode, codeWidth);
        blocks.finish();
        out.write(0); // block terminator
    }

    /** Open addressing; twice the 4096 codes a GIF dictionary can hold. */
    private static final int HASH_SIZE = 8192;

    private static void clearTable(int[] table) {
        for (int i = 0; i < table.length; i++) {
            table[i] = -1;
        }
    }

    private static int find(int[] table, int key) {
        int slot = (key * 0x9E3779B1) >>> 19 & (HASH_SIZE - 1);
        while (table[slot] != -1 && table[slot] != key) {
            slot = (slot + 1) & (HASH_SIZE - 1);
        }
        return slot;
    }

    /** Packs codes into bits, and bits into GIF's 255-byte sub-blocks. */
    private static final class BlockWriter {

        private final ByteArrayOutputStream out;
        private final byte[] block = new byte[255];
        private int filled;
        private int bitBuffer;
        private int bitCount;

        BlockWriter(ByteArrayOutputStream out) {
            this.out = out;
        }

        void write(int code, int width) {
            bitBuffer |= code << bitCount;
            bitCount += width;
            while (bitCount >= 8) {
                push((byte) (bitBuffer & 0xFF));
                bitBuffer >>>= 8;
                bitCount -= 8;
            }
        }

        void finish() {
            if (bitCount > 0) {
                push((byte) (bitBuffer & 0xFF));
                bitBuffer = 0;
                bitCount = 0;
            }
            flush();
        }

        private void push(byte value) {
            block[filled++] = value;
            if (filled == block.length) {
                flush();
            }
        }

        private void flush() {
            if (filled == 0) {
                return;
            }
            out.write(filled);
            out.write(block, 0, filled);
            filled = 0;
        }
    }

    // ------------------------------------------------------------- quantiser

    /**
     * The 256 colours a whole clip is drawn with.
     *
     * <p>One table for every frame rather than one per frame: a per-frame
     * table costs 768 bytes a frame and makes the palette flicker where two
     * frames quantise differently.</p>
     *
     * <p>Colours are counted at five bits a channel. A game of this era drew
     * with far fewer colours than that, so in practice the table ends up
     * holding the game's own palette exactly; the median cut below only does
     * anything when a clip really carries more than 256.</p>
     */
    static final class Palette {

        /** RGB of each entry, and how many there are. */
        final int[] colors;
        final int size;
        /** Bits per index, 1 to 8: what the GIF header states. */
        final int bits;
        /**
         * Colour to index, when the clip fits in the table exactly.
         *
         * <p>Most of these games do: a J2ME sprite sheet is a handful of
         * colours, and when the whole clip holds 256 or fewer the table is
         * those colours and the picture comes back byte for byte. Null when
         * the clip held more and the colours had to be chosen.</p>
         */
        private final java.util.Map<Integer, Integer> exact;
        /** Reduced colour to palette index, filled in as pixels are asked for. */
        private final short[] cache = new short[1 << 15];

        private Palette(int[] colors, int size) {
            this(colors, size, null);
        }

        private Palette(int[] colors, int size, java.util.Map<Integer, Integer> exact) {
            this.exact = exact;
            this.colors = colors;
            this.size = size;
            // Never fewer than four entries: LZW's smallest code size is two
            // bits, and a decoder reading two-bit codes expects a table that
            // holds them all.
            int width = 2;
            while ((1 << width) < size && width < 8) {
                width++;
            }
            this.bits = width;
            for (int i = 0; i < cache.length; i++) {
                cache[i] = -1;
            }
        }

        static Palette of(List<int[]> frames, int pixelsPerFrame) {
            Palette whole = exactly(frames, pixelsPerFrame);
            if (whole != null) {
                return whole;
            }
            // Counted in 15-bit space: two colours a channel apart are the
            // same colour to anyone watching, and collapsing them first is
            // what keeps this cheap enough to run on a phone.
            int[] counts = new int[1 << 15];
            for (int f = 0; f < frames.size(); f++) {
                int[] frame = frames.get(f);
                for (int i = 0; i < frame.length && i < pixelsPerFrame; i++) {
                    counts[reduce(frame[i])]++;
                }
            }
            List<int[]> distinct = new ArrayList<int[]>();
            for (int key = 0; key < counts.length; key++) {
                if (counts[key] > 0) {
                    distinct.add(new int[]{expand(key), counts[key]});
                }
            }
            if (distinct.size() <= 256) {
                int[] colors = new int[distinct.size()];
                for (int i = 0; i < distinct.size(); i++) {
                    colors[i] = distinct.get(i)[0];
                }
                return new Palette(colors, colors.length);
            }
            return new Palette(medianCut(distinct, 256), 256);
        }

        /**
         * The clip's own colours, when there are few enough to keep them all.
         *
         * @return a palette of exactly those colours, or null past 256
         */
        private static Palette exactly(List<int[]> frames, int pixelsPerFrame) {
            java.util.Map<Integer, Integer> index =
                    new java.util.LinkedHashMap<Integer, Integer>();
            for (int f = 0; f < frames.size(); f++) {
                int[] frame = frames.get(f);
                for (int i = 0; i < frame.length && i < pixelsPerFrame; i++) {
                    Integer color = Integer.valueOf(frame[i] & 0xFFFFFF);
                    if (!index.containsKey(color)) {
                        if (index.size() == 256) {
                            return null;
                        }
                        index.put(color, Integer.valueOf(index.size()));
                    }
                }
            }
            int[] colors = new int[index.size()];
            for (java.util.Map.Entry<Integer, Integer> entry : index.entrySet()) {
                colors[entry.getValue().intValue()] = entry.getKey().intValue();
            }
            return new Palette(colors, colors.length, index);
        }

        /**
         * Median cut: split the box of colours along its longest side, again
         * and again, until there are as many boxes as the table holds.
         *
         * <p>Splitting the widest side is what keeps a clip's rare bright
         * colours — a health bar, an explosion — from being averaged into the
         * background they sit on.</p>
         */
        private static int[] medianCut(List<int[]> distinct, int wanted) {
            List<List<int[]>> boxes = new ArrayList<List<int[]>>();
            boxes.add(distinct);
            while (boxes.size() < wanted) {
                int widest = -1;
                int widestSpread = 0;
                int widestChannel = 0;
                for (int i = 0; i < boxes.size(); i++) {
                    List<int[]> box = boxes.get(i);
                    if (box.size() < 2) {
                        continue;
                    }
                    for (int channel = 0; channel < 3; channel++) {
                        int spread = spreadOf(box, channel);
                        if (spread > widestSpread) {
                            widestSpread = spread;
                            widest = i;
                            widestChannel = channel;
                        }
                    }
                }
                if (widest < 0) {
                    break;
                }
                List<int[]> box = boxes.remove(widest);
                sortBy(box, widestChannel);
                int middle = box.size() / 2;
                boxes.add(new ArrayList<int[]>(box.subList(0, middle)));
                boxes.add(new ArrayList<int[]>(box.subList(middle, box.size())));
            }
            int[] colors = new int[boxes.size()];
            for (int i = 0; i < boxes.size(); i++) {
                colors[i] = averageOf(boxes.get(i));
            }
            return colors;
        }

        private static int spreadOf(List<int[]> box, int channel) {
            int low = 255;
            int high = 0;
            for (int i = 0; i < box.size(); i++) {
                int value = channelOf(box.get(i)[0], channel);
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            return high - low;
        }

        /** Insertion sort: the boxes are small by the time this matters. */
        private static void sortBy(List<int[]> box, final int channel) {
            for (int i = 1; i < box.size(); i++) {
                int[] value = box.get(i);
                int key = channelOf(value[0], channel);
                int j = i - 1;
                while (j >= 0 && channelOf(box.get(j)[0], channel) > key) {
                    box.set(j + 1, box.get(j));
                    j--;
                }
                box.set(j + 1, value);
            }
        }

        /** Weighted by how often each colour appears, not by how many there are. */
        private static int averageOf(List<int[]> box) {
            long red = 0;
            long green = 0;
            long blue = 0;
            long weight = 0;
            for (int i = 0; i < box.size(); i++) {
                int color = box.get(i)[0];
                int count = box.get(i)[1];
                red += (long) ((color >> 16) & 0xFF) * count;
                green += (long) ((color >> 8) & 0xFF) * count;
                blue += (long) (color & 0xFF) * count;
                weight += count;
            }
            if (weight == 0) {
                return 0;
            }
            return (int) ((red / weight) << 16 | (green / weight) << 8 | (blue / weight));
        }

        private static int channelOf(int color, int channel) {
            switch (channel) {
                case 0: return (color >> 16) & 0xFF;
                case 1: return (color >> 8) & 0xFF;
                default: return color & 0xFF;
            }
        }

        /** ARGB down to five bits a channel, which is what a handset screen had. */
        private static int reduce(int argb) {
            return ((argb >> 9) & 0x7C00) | ((argb >> 6) & 0x03E0) | ((argb >> 3) & 0x001F);
        }

        /** Back out again, with the low bits carried up so white stays white. */
        private static int expand(int key) {
            int red = (key >> 10) & 0x1F;
            int green = (key >> 5) & 0x1F;
            int blue = key & 0x1F;
            return (red << 3 | red >> 2) << 16 | (green << 3 | green >> 2) << 8
                    | (blue << 3 | blue >> 2);
        }

        /** The entry nearest one pixel; remembered, because frames repeat. */
        int indexOf(int argb) {
            if (exact != null) {
                Integer known = exact.get(Integer.valueOf(argb & 0xFFFFFF));
                if (known != null) {
                    return known.intValue();
                }
            }
            int key = reduce(argb);
            int known = cache[key];
            if (known >= 0) {
                return known;
            }
            int color = expand(key);
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;
            int best = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                int candidate = colors[i];
                long dr = red - ((candidate >> 16) & 0xFF);
                long dg = green - ((candidate >> 8) & 0xFF);
                long db = blue - (candidate & 0xFF);
                long distance = dr * dr + dg * dg + db * db;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                    if (distance == 0) {
                        break;
                    }
                }
            }
            cache[key] = (short) best;
            return best;
        }
    }
}
