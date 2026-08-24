package com.mobicore.core.gfx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * PNG decoder covering the subset J2ME games actually ship.
 *
 * <p>Greyscale, truecolour, palette and alpha variants at 1/2/4/8 bits, with
 * {@code tRNS} transparency and all five scanline filters. Interlaced images
 * are rejected rather than decoded wrongly — Adam7 is vanishingly rare in game
 * art, and a clear error beats a scrambled sprite sheet.</p>
 */
public final class PngReader {

    /** Decoded image: ARGB pixels plus dimensions. */
    public static final class Image {

        public final int[] pixels;
        public final int width;
        public final int height;

        Image(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    private PngReader() {
    }

    public static boolean looksLikePng(byte[] data) {
        return data != null && data.length > 8 && (data[0] & 0xFF) == 0x89
                && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }

    public static Image decode(byte[] data) throws IOException {
        if (!looksLikePng(data)) {
            throw new IOException("Not a PNG image");
        }
        int position = 8;
        int width = 0;
        int height = 0;
        int bitDepth = 8;
        int colorType = 6;
        byte[] palette = null;
        byte[] transparency = null;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();

        while (position + 8 <= data.length) {
            int length = readInt(data, position);
            String type = new String(data, position + 4, 4, "ISO-8859-1");
            int body = position + 8;
            if (length < 0 || body + length > data.length) {
                throw new IOException("Truncated PNG chunk " + type);
            }
            if ("IHDR".equals(type)) {
                width = readInt(data, body);
                height = readInt(data, body + 4);
                bitDepth = data[body + 8] & 0xFF;
                colorType = data[body + 9] & 0xFF;
                if ((data[body + 12] & 0xFF) != 0) {
                    throw new IOException("Interlaced PNG images are not supported");
                }
            } else if ("PLTE".equals(type)) {
                palette = slice(data, body, length);
            } else if ("tRNS".equals(type)) {
                transparency = slice(data, body, length);
            } else if ("IDAT".equals(type)) {
                compressed.write(data, body, length);
            } else if ("IEND".equals(type)) {
                break;
            }
            position = body + length + 4;
        }

        if (width <= 0 || height <= 0) {
            throw new IOException("PNG header is missing or invalid");
        }
        byte[] raw = inflate(compressed.toByteArray());
        return new Image(unfilter(raw, width, height, bitDepth, colorType, palette, transparency),
                width, height);
    }

    private static byte[] slice(byte[] data, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(data, offset, out, 0, length);
        return out;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            byte[] buffer = new byte[16384];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Corrupt PNG image data");
        } finally {
            inflater.end();
        }
    }

    private static int channelsFor(int colorType) {
        switch (colorType) {
            case 0: return 1;   // greyscale
            case 2: return 3;   // truecolour
            case 3: return 1;   // palette index
            case 4: return 2;   // greyscale + alpha
            case 6: return 4;   // truecolour + alpha
            default: throw new IllegalArgumentException("Unknown PNG colour type " + colorType);
        }
    }

    private static int[] unfilter(byte[] raw, int width, int height, int bitDepth, int colorType,
                                  byte[] palette, byte[] transparency) throws IOException {
        int channels = channelsFor(colorType);
        int bitsPerPixel = channels * bitDepth;
        int bytesPerPixel = Math.max(1, bitsPerPixel / 8);
        int stride = (width * bitsPerPixel + 7) / 8;
        if (raw.length < height * (stride + 1)) {
            throw new IOException("PNG image data is shorter than the header promises");
        }

        int[] pixels = new int[width * height];
        byte[] previous = new byte[stride];
        byte[] current = new byte[stride];
        int cursor = 0;

        for (int y = 0; y < height; y++) {
            int filter = raw[cursor++] & 0xFF;
            System.arraycopy(raw, cursor, current, 0, stride);
            cursor += stride;
            applyFilter(filter, current, previous, bytesPerPixel);
            expandRow(current, pixels, y, width, bitDepth, colorType, palette, transparency);
            byte[] swap = previous;
            previous = current;
            current = swap;
        }
        return pixels;
    }

    private static void applyFilter(int filter, byte[] row, byte[] previous, int bytesPerPixel) {
        switch (filter) {
            case 0:
                break;
            case 1:
                for (int i = bytesPerPixel; i < row.length; i++) {
                    row[i] = (byte) (row[i] + row[i - bytesPerPixel]);
                }
                break;
            case 2:
                for (int i = 0; i < row.length; i++) {
                    row[i] = (byte) (row[i] + previous[i]);
                }
                break;
            case 3:
                for (int i = 0; i < row.length; i++) {
                    int left = i >= bytesPerPixel ? row[i - bytesPerPixel] & 0xFF : 0;
                    row[i] = (byte) (row[i] + ((left + (previous[i] & 0xFF)) >> 1));
                }
                break;
            case 4:
                for (int i = 0; i < row.length; i++) {
                    int left = i >= bytesPerPixel ? row[i - bytesPerPixel] & 0xFF : 0;
                    int up = previous[i] & 0xFF;
                    int upLeft = i >= bytesPerPixel ? previous[i - bytesPerPixel] & 0xFF : 0;
                    row[i] = (byte) (row[i] + paeth(left, up, upLeft));
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown PNG filter " + filter);
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        return pb <= pc ? b : c;
    }

    private static void expandRow(byte[] row, int[] pixels, int y, int width, int bitDepth,
                                  int colorType, byte[] palette, byte[] transparency) {
        int offset = y * width;
        for (int x = 0; x < width; x++) {
            pixels[offset + x] = pixelAt(row, x, bitDepth, colorType, palette, transparency);
        }
    }

    private static int pixelAt(byte[] row, int x, int bitDepth, int colorType,
                               byte[] palette, byte[] transparency) {
        switch (colorType) {
            case 0: {
                int value = scaleToByte(sample(row, x, bitDepth), bitDepth);
                int alpha = isTransparentGrey(transparency, sample(row, x, bitDepth)) ? 0 : 255;
                return (alpha << 24) | (value << 16) | (value << 8) | value;
            }
            case 3: {
                int index = sample(row, x, bitDepth);
                if (palette == null || index * 3 + 2 >= palette.length) {
                    return 0;
                }
                int alpha = transparency != null && index < transparency.length
                        ? transparency[index] & 0xFF : 255;
                return (alpha << 24) | ((palette[index * 3] & 0xFF) << 16)
                        | ((palette[index * 3 + 1] & 0xFF) << 8) | (palette[index * 3 + 2] & 0xFF);
            }
            case 2: {
                int base = x * 3 * (bitDepth / 8);
                int step = bitDepth / 8;
                int r = row[base] & 0xFF;
                int g = row[base + step] & 0xFF;
                int b = row[base + step * 2] & 0xFF;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            case 4: {
                int step = bitDepth / 8;
                int base = x * 2 * step;
                int grey = row[base] & 0xFF;
                int alpha = row[base + step] & 0xFF;
                return (alpha << 24) | (grey << 16) | (grey << 8) | grey;
            }
            default: {
                int step = bitDepth / 8;
                int base = x * 4 * step;
                return ((row[base + step * 3] & 0xFF) << 24) | ((row[base] & 0xFF) << 16)
                        | ((row[base + step] & 0xFF) << 8) | (row[base + step * 2] & 0xFF);
            }
        }
    }

    private static boolean isTransparentGrey(byte[] transparency, int value) {
        return transparency != null && transparency.length >= 2
                && (((transparency[0] & 0xFF) << 8) | (transparency[1] & 0xFF)) == value;
    }

    /** Reads one sub-byte or byte sample from a row. */
    private static int sample(byte[] row, int index, int bitDepth) {
        if (bitDepth == 8) {
            return row[index] & 0xFF;
        }
        if (bitDepth == 16) {
            return row[index * 2] & 0xFF;
        }
        int perByte = 8 / bitDepth;
        int b = row[index / perByte] & 0xFF;
        int shift = 8 - bitDepth * (index % perByte + 1);
        return (b >> shift) & ((1 << bitDepth) - 1);
    }

    private static int scaleToByte(int value, int bitDepth) {
        switch (bitDepth) {
            case 1: return value * 255;
            case 2: return value * 85;
            case 4: return value * 17;
            default: return value;
        }
    }
}
