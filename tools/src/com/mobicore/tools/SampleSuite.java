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
            + "MIDlet-3: Menu Demo,,demo.MenuDemo\n"
            + "MIDlet-4: Sound Demo,,demo.SoundDemo\n"
            + "MIDlet-5: Timer Demo,,demo.TimerDemo\n"
            + "MIDlet-6: Nokia Demo,,demo.NokiaDemo\n"
            + "MIDlet-7: File Demo,,demo.FileDemo\n"
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
            + "MIDlet-3: Menu Demo,,demo.MenuDemo\n"
            + "MIDlet-4: Sound Demo,,demo.SoundDemo\n"
            + "MIDlet-5: Timer Demo,,demo.TimerDemo\n"
            + "MIDlet-6: Nokia Demo,,demo.NokiaDemo\n"
            + "MIDlet-7: File Demo,,demo.FileDemo\n"
            + "MicroEdition-Configuration: CLDC-1.1\n"
            + "MicroEdition-Profile: MIDP-2.0\n";

    private SampleSuite() {
    }

    public static byte[] jar() throws IOException {
        return jar(null);
    }

    /**
     * Packages the demo suite. When {@code fixtureDir} points at the compiled
     * fixtures the JAR contains real bytecode and the emulator can run it;
     * otherwise placeholders keep the metadata previews working.
     */
    public static byte[] jar(String fixtureDir) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", utf8(MANIFEST));
        entries.put("icon.png", iconPng());
        if (fixtureDir != null) {
            addCompiledClasses(entries, fixtureDir, "demo");
        }
        if (!entries.containsKey("demo/SkyRunner.class")) {
            entries.put("demo/SkyRunner.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        }
        entries.put("res/level1.dat", new byte[1024]);
        entries.put("res/level2.dat", new byte[2048]);
        entries.put("res/theme.mid", new byte[5120]);
        return zip(entries);
    }

    private static void addCompiledClasses(Map<String, byte[]> entries, String root, String packageDir)
            throws IOException {
        com.mobicore.core.storage.Vfs vfs = new com.mobicore.core.storage.LocalVfs();
        String directory = root + "/" + packageDir;
        if (!vfs.isDirectory(directory)) {
            return;
        }
        for (String name : vfs.list(directory)) {
            if (name.endsWith(".class")) {
                entries.put(packageDir + "/" + name, vfs.read(directory + "/" + name));
            }
        }
    }

    public static byte[] jad() {
        return utf8(JAD);
    }

    private static byte[] iconPng() throws IOException {
        // Larger than any tile shows it, so the screens that scale it up have
        // pixels to work with rather than blocks to enlarge.
        int size = 192;
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
