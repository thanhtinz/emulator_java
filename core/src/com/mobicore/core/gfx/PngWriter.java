package com.mobicore.core.gfx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Minimal PNG encoder.
 *
 * <p>Screenshots must work identically on every platform, so the emulator
 * encodes them itself instead of reaching for {@code android.graphics.Bitmap}
 * or {@code UIImage}. Output is 8-bit RGBA, which keeps per-pixel alpha from
 * translucent overlays intact.</p>
 */
public final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngWriter() {
    }

    public static byte[] encode(Framebuffer frame) throws IOException {
        return encode(frame.pixels(), frame.width(), frame.height());
    }

    /** Encodes an ARGB pixel array in row-major order. */
    public static byte[] encode(int[] argb, int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException("Refusing to encode an empty image");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SIGNATURE);

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width);
        writeInt(header, height);
        header.write(8);    // bit depth
        header.write(6);    // colour type: truecolour with alpha
        header.write(0);    // deflate
        header.write(0);    // adaptive filtering
        header.write(0);    // no interlace
        writeChunk(out, "IHDR", header.toByteArray());

        // Each scanline is prefixed with filter type 0 (None). Real photos
        // compress better with adaptive filters, but J2ME screens are flat
        // pixel art where the extra pass is not worth the cost.
        byte[] raw = new byte[height * (width * 4 + 1)];
        int cursor = 0;
        for (int y = 0; y < height; y++) {
            raw[cursor++] = 0;
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = argb[row + x];
                raw[cursor++] = (byte) (pixel >> 16);
                raw[cursor++] = (byte) (pixel >> 8);
                raw[cursor++] = (byte) pixel;
                raw[cursor++] = (byte) (pixel >>> 24);
            }
        }
        writeChunk(out, "IDAT", deflate(raw));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 64);
            byte[] buffer = new byte[16384];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            typeBytes[i] = (byte) type.charAt(i);
        }
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
