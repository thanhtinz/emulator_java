package com.mobicore.core.audio;

import java.io.IOException;

/**
 * Reads uncompressed WAV files, which is the sound format a J2ME game could
 * count on a handset supporting.
 *
 * <p>Only PCM is accepted. The compressed variants a RIFF file may carry
 * (ADPCM, mu-law) were never widely supported on handsets either, and a
 * decoder for them would be a large amount of code for content that does not
 * exist in practice — so they are refused with a clear message instead of
 * being half-decoded into noise.</p>
 *
 * <p>Stereo is mixed down and eight-bit samples are widened, because the rest
 * of the emulator handles exactly one format; see {@link AudioClip}.</p>
 */
public final class WavDecoder {

    private WavDecoder() {
    }

    /** True if the bytes begin like a RIFF/WAVE file. */
    public static boolean looksLikeWav(byte[] data) {
        return data != null && data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    public static AudioClip decode(byte[] data) throws IOException {
        if (!looksLikeWav(data)) {
            throw new IOException("Not a WAV file");
        }
        int channels = 0;
        int sampleRate = 0;
        int bits = 0;
        int format = 0;
        int at = 12;
        while (at + 8 <= data.length) {
            String id = new String(data, at, 4, "ISO-8859-1");
            int size = intAt(data, at + 4);
            int body = at + 8;
            if (size < 0 || body + size > data.length) {
                // A truncated file: take whatever is actually there rather
                // than reading past the end.
                size = data.length - body;
            }
            if ("fmt ".equals(id)) {
                format = shortAt(data, body);
                channels = shortAt(data, body + 2);
                sampleRate = intAt(data, body + 4);
                bits = shortAt(data, body + 14);
            } else if ("data".equals(id)) {
                if (format != 1) {
                    throw new IOException("Only uncompressed PCM WAV is supported");
                }
                if (channels < 1 || sampleRate <= 0 || (bits != 8 && bits != 16)) {
                    throw new IOException("Unsupported WAV format");
                }
                return new AudioClip(toMono16(data, body, size, channels, bits), sampleRate);
            }
            at = body + size + (size & 1);
        }
        throw new IOException("WAV file has no data chunk");
    }

    private static byte[] toMono16(byte[] data, int at, int size, int channels, int bits) {
        int bytesPerSample = bits / 8;
        int frameSize = bytesPerSample * channels;
        int frames = size / frameSize;
        byte[] pcm = new byte[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            int total = 0;
            for (int channel = 0; channel < channels; channel++) {
                int offset = at + frame * frameSize + channel * bytesPerSample;
                if (bits == 8) {
                    // Eight-bit WAV is unsigned, sixteen-bit is signed.
                    total += ((data[offset] & 0xFF) - 128) << 8;
                } else {
                    total += (short) ((data[offset] & 0xFF) | (data[offset + 1] << 8));
                }
            }
            int value = total / channels;
            pcm[frame * 2] = (byte) (value & 0xFF);
            pcm[frame * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return pcm;
    }

    private static int intAt(byte[] data, int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8)
                | ((data[at + 2] & 0xFF) << 16) | ((data[at + 3] & 0xFF) << 24);
    }

    private static int shortAt(byte[] data, int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
    }

    /** Writes a clip back out as a WAV file, for tests and for exports. */
    public static byte[] encode(AudioClip clip) {
        byte[] pcm = clip.pcm();
        byte[] out = new byte[44 + pcm.length];
        writeAscii(out, 0, "RIFF");
        writeInt(out, 4, 36 + pcm.length);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);
        writeShort(out, 22, 1);
        writeInt(out, 24, clip.sampleRate());
        writeInt(out, 28, clip.sampleRate() * 2);
        writeShort(out, 32, 2);
        writeShort(out, 34, 16);
        writeAscii(out, 36, "data");
        writeInt(out, 40, pcm.length);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        return out;
    }

    private static void writeAscii(byte[] out, int at, String text) {
        for (int i = 0; i < text.length(); i++) {
            out[at + i] = (byte) text.charAt(i);
        }
    }

    private static void writeInt(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }

    private static void writeShort(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
    }
}
