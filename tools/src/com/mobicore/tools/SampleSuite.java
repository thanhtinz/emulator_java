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
            + "MIDlet-8: Crash Demo,,demo.CrashDemo\n"
            + "MIDlet-9: Device Demo,,demo.DeviceDemo\n"
            + "MIDlet-10: Hang Demo,,demo.HangDemo\n"
            + "MIDlet-11: Photo Demo,,demo.PhotoDemo\n"
            + "MIDlet-12: Socket Demo,,demo.SocketDemo\n"
            + "MIDlet-13: Piggy Bank,,demo.PiggyBank\n"
            + "MIDlet-14: Loop Demo,,demo.LoopDemo\n"
            + "MIDlet-15: Thread Demo,,demo.ThreadDemo\n"
            + "MIDlet-16: Clock Demo,,demo.ClockDemo\n"
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
            + "MIDlet-8: Crash Demo,,demo.CrashDemo\n"
            + "MIDlet-9: Device Demo,,demo.DeviceDemo\n"
            + "MIDlet-10: Hang Demo,,demo.HangDemo\n"
            + "MIDlet-11: Photo Demo,,demo.PhotoDemo\n"
            + "MIDlet-12: Socket Demo,,demo.SocketDemo\n"
            + "MIDlet-13: Piggy Bank,,demo.PiggyBank\n"
            + "MIDlet-14: Loop Demo,,demo.LoopDemo\n"
            + "MIDlet-15: Thread Demo,,demo.ThreadDemo\n"
            + "MIDlet-16: Clock Demo,,demo.ClockDemo\n"
            + "MicroEdition-Configuration: CLDC-1.1\n"
            + "MicroEdition-Profile: MIDP-2.0\n";

    /**
     * Ảnh mở đầu của bộ cài mẫu, ở dạng JPEG.
     *
     * <p>Là JPEG có chủ ý: MIDP chỉ bắt buộc máy đọc được PNG, còn game đời
     * ấy vẫn đóng gói ảnh to nhiều màu bằng JPEG vì máy thật đọc được. Bộ cài
     * mẫu mang cả hai để đường đọc ảnh nào cũng có thứ để chạy thật.</p>
     */
    private static final String PHOTO =
            "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof"
            + "Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwh"
            + "MjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAAR"
            + "CAB4AHgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAA"
            + "AgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkK"
            + "FhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWG"
            + "h4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl"
            + "5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREA"
            + "AgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYk"
            + "NOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOE"
            + "hYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk"
            + "5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDg9lGyrGyjZXocx5XKV9lGyrGyjZRzBylf"
            + "ZRsqxso2UcwcpX2UbKsbKNlHMHKV9lGyrGyjZRzBylfZRsqxso2UcwcpX2UbKsbKNlHMHKV9"
            + "lFWNlFHMHKWNlGyp9lGyubmNuUg2UbKn2UbKOYOUg2UbKn2UbKOYOUg2UbKn2UbKOYOUg2Vr"
            + "6F4W1PxFPssocRDO64lyI1IA4LYPPI4GTz6c1n7K988MafBpvhuxhgijQtCkkpjYMHcqCzbg"
            + "TnnvnGMY4xWVWs4LQ1pUlJ6nmUnwq15IndZ7CRlUkIsrZY+gyoGfqRXG3FnPaTtBcwyQzLjd"
            + "HIpVhkZGQfavpavL/ipY28d5p93HCqzzrIsrjq4Xbtz7jJ5+noKzpYiUpWkaVaEUro812UbK"
            + "n2UbK6eY5uUg2UVPsoo5g5Sxso2VY2UbK5+Y35Svso2VY2UbKOYOUr7KNlWNlGyjmDlK+yjZ"
            + "VjZRso5g5Svsr1zwP4mttQ0y20yZ44r2BPLSMZ/eIoGGGeM46jOeCenTyvZRsqJpTVmXBuLu"
            + "j6BkkSKN5JHVI0BZmY4CgdST6V5B448Qw6/qMMdrta0tgfLlwQXLAFjg4wBgDp2J78c7so2V"
            + "MIKLuVObkrFfZRsqxso2VrzGXKV9lFWNlFHMHKT7KNlWNlGyufmN+Ur7KNlWNlGyjmDlK+yj"
            + "ZVjZRso5g5Svso2VY2UbKOYOUr7KNlWNlGyjmDlK+yjZVjZRso5g5Svso2VY2UbKOYOUr7KK"
            + "c1xCtwLcNvmPVF5Kjjk+g57/AIZoo5g5S9so2VY2UbK5uY35Svso2VY2UbKOYOUr7KNlZHiL"
            + "WEsoWtLdz9qcclT/AKsf4kf4+mcPS/EVzY+XDN++tlwMEfMo9j/j6Y4rRRk1czcop2Oz2UbK"
            + "ZYX9rqUJktpN23G5SMFSfUf5FW9lQ5W3NEr7FfZRsqxso2UuYOUr7KNlVtQ1ex03KzS7pR/y"
            + "yTlu35dc84rlb/xLe3eVhP2aP0Q/Men8X+GOtaRjKREpRidJfarZaflZpd0n/PNOW7fl171i"
            + "x3+pa7c+Ra/6NCMF3U5Kj3PrxwBiqGjaLLqs+TlLZD88n9B7/wAv59za2MFlCIbeMRpnOBzk"
            + "+5705OMNOpMVKevQrWen29jCI4EC8YLfxN9T3orQ2UVnzmvKWNlGyp9ntRs9q5uY35SDZWVr"
            + "mrJo9qrbd88mREp6cdSfYZH+eRo6lew6ZYSXUxGFHyqTje3ZR9f/AK9eYX9/caldtc3L7nbg"
            + "AdFHoB6VvRhzu72Ma0+RWW5DLK88zyyHc7sWY4xknk0yiiu44h8UskEgkikaNx0ZDgj8a6vS"
            + "/FysVi1JQvH+vQH07qPXnkflWJpugahquGgh2xH/AJayfKvfp68jHGa7LTfCNhZYecfa5fWR"
            + "flHXov4989O1c9adNaS3N6UKm62HXmv6XZxhjcpMT0SAhyfy4HXua5TUPFF9eZSE/ZYvSM/M"
            + "enVvw7Y6967rUNGstTTFzAC+MLIvDL16H8eh4rhdX8LXulqZU/0m37uinK4GSWHYdeeenas6"
            + "Mqb33NKyqLbYw63dB8OSarmecvFaDIDL95z7Z7D1/D6WtA8KPfJHeXpaO3JBWLHzSL657Dp9"
            + "fbg13aRJGioiBUUYVVGAB6Cqq10vdiRSoN6yKkFrFawJDDGEjQYVR2qXZU+z2o2e1cnOdfKQ"
            + "bKKn2e1FHMHKWNlQ3M0NnbSXFxIscUYyzHtV7ZXl/irxJ/bMwtrYYsom3KSOZG6bvYcnA/P0"
            + "E0YOrKy2KrTVONyhrusPrGotN86wLxDGx+6PX6nr+meKy61tK8N6nrGGt4NsJ/5bS/Knfoep"
            + "5GOAcd67rSvBOm2GHuB9sm9ZV+Qdei9Oh756cYrvnXp0lynDCjUqvmOD0vw9qWrYa3h2wn/l"
            + "tJ8qd+nryMcZrttM8G6fY4ecfa5vWRfkHXov0PfPTtXU7KNlcNTFyntojshhox31ZX2UbKsb"
            + "KNlYcxvylfZRsqxso2UcwcpX2UbKsbKNlHMHKV9lGyrGyjZRzBylfZRVjZRRzBynPeIF1bWZ"
            + "W0vSFVLXGLm6Ziqk8goDjnpg7c8nBxzl+k+BtM07ElyPts3rKvyDr0Tp0PfPTIxRRRKrKK5I"
            + "6IUaUZPnlqzpdlGyiisLm9g2UbKKKLhYNlGyiii4WDZRsooouFg2UbKKKLhYNlGyiii4WDZR"
            + "RRRcLH//2Q==";

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
        entries.put("res/theme.mid", midi());
        // Một tệp chữ thật trong gói, để game đọc bằng InputStreamReader.
        entries.put("message.txt", "Chúc một ngày lành".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("res/photo.jpg", java.util.Base64.getDecoder().decode(PHOTO));
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

    /**
     * Một tệp MIDI ngắn nhưng thật.
     *
     * <p>Trước đây chỗ này là năm nghìn byte số 0 mang tên .mid — đủ để đếm
     * dung lượng, nhưng bảng tài nguyên đọc ruột tệp chứ không đọc cái tên,
     * nên nó thấy đúng thứ nó là: một khối dữ liệu vô nghĩa. Một tệp thật thì
     * cả phần phát nhạc lẫn bảng tài nguyên đều có cái để làm việc.</p>
     */
    private static byte[] midi() {
        byte[] track = {
            0x00, (byte) 0x90, 0x3C, 0x64,   // bật nốt Đô
            (byte) 0x60, (byte) 0x80, 0x3C, 0x40,  // tắt nốt sau một nhịp
            0x00, (byte) 0xFF, 0x2F, 0x00,   // hết bản
        };
        byte[] file = new byte[14 + 8 + track.length];
        int at = 0;
        at = put(file, at, new byte[]{'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, (byte) 96});
        at = put(file, at, new byte[]{'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
        put(file, at, track);
        return file;
    }

    private static int put(byte[] target, int at, byte[] source) {
        System.arraycopy(source, 0, target, at, source.length);
        return at + source.length;
    }

    public static byte[] utf8(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
