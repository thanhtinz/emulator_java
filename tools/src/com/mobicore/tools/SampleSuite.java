package com.mobicore.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the "Sky Runner" demo suite used by previews and manual testing.
 *
 * <p>Shipping a synthetic suite keeps the repository free of third-party game
 * binaries while still exercising the full import path.</p>
 */
public final class SampleSuite {

    public static final String MANIFEST =
            "Manifest-Version: 1.0\n"
            + "MIDlet-Name: Sky Runner\n"
            + "MIDlet-Version: 1.2.0\n"
            + "MIDlet-Vendor: MobiCore Samples\n"
            + "MIDlet-Description: An endless runner used to exercise the emulator\n"
            + "MIDlet-Icon: /icon.png\n"
            + "MIDlet-1: Sky Runner,/icon.png,demo.SkyRunner\n"
            + "MIDlet-2: Level Editor,,demo.Editor\n"
            + "MicroEdition-Configuration: CLDC-1.1\n"
            + "MicroEdition-Profile: MIDP-2.0\n";

    public static final String JAD =
            "MIDlet-Name: Sky Runner\n"
            + "MIDlet-Version: 1.2.0\n"
            + "MIDlet-Vendor: MobiCore Samples\n"
            + "MIDlet-Jar-URL: SkyRunner.jar\n"
            + "MIDlet-Jar-Size: 24576\n"
            + "MIDlet-1: Sky Runner,/icon.png,demo.SkyRunner\n"
            + "MIDlet-2: Level Editor,,demo.Editor\n"
            + "MicroEdition-Configuration: CLDC-1.1\n"
            + "MicroEdition-Profile: MIDP-2.0\n";

    private SampleSuite() {
    }

    public static byte[] jar() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", utf8(MANIFEST));
        entries.put("icon.png", iconPng());
        entries.put("demo/SkyRunner.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        entries.put("demo/Editor.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        entries.put("demo/Sprites.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        entries.put("res/level1.dat", new byte[1024]);
        entries.put("res/level2.dat", new byte[2048]);
        entries.put("res/tiles.png", new byte[3072]);
        entries.put("res/theme.mid", new byte[5120]);
        return zip(entries);
    }

    public static byte[] jad() {
        return utf8(JAD);
    }

    private static byte[] iconPng() throws IOException {
        int size = 48;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int shade = 0x30 + (y * 120 / size);
                pixels[y * size + x] = 0xFF000000 | (shade << 16) | (0x80 << 8) | 0xC0;
            }
        }
        return com.mobicore.core.gfx.PngWriter.encode(pixels, size, size);
    }

    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            zip.putNextEntry(new ZipEntry(entry.getKey()));
            zip.write(entry.getValue());
            zip.closeEntry();
        }
        zip.close();
        return out.toByteArray();
    }

    public static byte[] utf8(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
