package com.mobicore.core.model;

import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.jar.SuiteLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks, before a game is played, whether the emulator has what it needs.
 *
 * <p>A missing class in J2ME is not a degraded feature: the class loader
 * fails and the game dies, usually on a black screen with no explanation.
 * The information needed to predict that is sitting in the JAR, so it is read
 * at import and the answer is shown next to the game — "chạy tốt", or exactly
 * which package is missing.</p>
 *
 * <p>The scan reads the class files' constant pools for class references.
 * Only names the emulator is expected to provide are judged: a reference to
 * the game's own classes, or to anything it ships, proves nothing.</p>
 */
public final class Compatibility {

    /** Everything runs. */
    public static final int LEVEL_FULL = 0;
    /** The game runs, but something it uses is emulated incompletely. */
    public static final int LEVEL_PARTIAL = 1;
    /** A package the game needs is not implemented; it will not start. */
    public static final int LEVEL_BROKEN = 2;

    /** The outcome of a scan. */
    public static final class Report {

        private final int level;
        private final List<String> missing;
        private final List<String> notes;

        Report(int level, List<String> missing, List<String> notes) {
            this.level = level;
            this.missing = Collections.unmodifiableList(missing);
            this.notes = Collections.unmodifiableList(notes);
        }

        public int level() {
            return level;
        }

        /** Packages the game uses that the emulator does not implement. */
        public List<String> missing() {
            return missing;
        }

        /** What to tell the user, in their language. */
        public List<String> notes() {
            return notes;
        }

        public boolean playable() {
            return level != LEVEL_BROKEN;
        }

        public String summary() {
            if (level == LEVEL_FULL) {
                return "Chạy tốt";
            }
            return level == LEVEL_PARTIAL ? "Chạy được, thiếu vài thứ" : "Chưa chạy được";
        }
    }

    /**
     * Packages the emulator implements. A class under any of these is
     * expected to work; anything else under {@code javax.microedition} is
     * not implemented at all.
     */
    private static final String[] SUPPORTED = {
            "javax/microedition/midlet/",
            "javax/microedition/lcdui/",
            "javax/microedition/rms/",
            "javax/microedition/io/",
            "javax/microedition/media/",
    };

    /**
     * Optional packages a handset might have had and this does not. Named
     * individually so the user is told what is missing rather than that
     * something is.
     */
    private static final String[][] KNOWN_MISSING = {
            {"javax/microedition/pim/", "danh bạ và lịch của máy"},
            {"javax/microedition/amms/", "hiệu ứng âm thanh nâng cao"},
            {"javax/microedition/m3g/", "đồ hoạ 3D (M3G)"},
            {"javax/microedition/location/", "định vị"},
            {"javax/microedition/apdu/", "thẻ SIM"},
            {"javax/microedition/khronos/", "OpenGL ES"},
            {"javax/microedition/sensor/", "cảm biến"},
            {"javax/microedition/xml/", "bộ đọc XML"},
            {"javax/bluetooth/", "Bluetooth"},
            // Siemens' colour-game package is a library of its own, not a
            // handful of static methods, so it is still missing outright.
            {"com/siemens/mp/color_game/", "API game màu của Siemens"},
    };

    /** Things that run, but not the way the hardware did. */
    private static final String[][] PARTIAL = {
            {"javax/microedition/media/Manager",
                    "Âm thanh: phát WAV, MIDI và chuỗi nốt, không phát MP3"},
            // FullCanvas, DirectGraphics, DirectUtils and DeviceControl are
            // emulated; the rest of Nokia's own packages are not, and a game
            // reaching for those finds them missing at the moment it does.
            {"com/nokia/mid/ui/",
                    "API Nokia: có FullCanvas, DirectGraphics và DeviceControl"},
            {"com/siemens/mp/", "API Siemens: có rung, đèn, tiếng và ExtendedImage"},
            {"com/samsung/util/", "API Samsung: có rung và AudioClip"},
            {"com/motorola/", "API Motorola: có rung"},
    };

    private Compatibility() {
    }

    public static Report scan(SuiteLoader suite) {
        JarArchive archive = suite.archive();
        List<String> referenced = referencedClasses(archive);

        List<String> missing = new ArrayList<String>();
        List<String> notes = new ArrayList<String>();
        int level = LEVEL_FULL;

        for (int i = 0; i < KNOWN_MISSING.length; i++) {
            String prefix = KNOWN_MISSING[i][0];
            if (usesPrefix(referenced, prefix)) {
                missing.add(prefix);
                notes.add("Cần " + KNOWN_MISSING[i][1] + " — chưa hỗ trợ");
                level = LEVEL_BROKEN;
            }
        }

        // Anything else under the standard tree that is not in a package the
        // emulator implements is also fatal, and worth naming even though no
        // friendly description exists for it.
        for (int i = 0; i < referenced.size(); i++) {
            String name = referenced.get(i);
            if (!name.startsWith("javax/microedition/") || isSupported(name)) {
                continue;
            }
            String pkg = packageOf(name);
            if (!missing.contains(pkg)) {
                missing.add(pkg);
                notes.add("Cần " + pkg.replace('/', '.') + " — chưa hỗ trợ");
                level = LEVEL_BROKEN;
            }
        }

        if (level != LEVEL_BROKEN) {
            for (int i = 0; i < PARTIAL.length; i++) {
                String name = PARTIAL[i][0];
                // A whole package counts as well as one class: Nokia's UI is
                // several classes and a game may use any of them.
                boolean used = name.endsWith("/")
                        ? usesPrefix(referenced, name)
                        : referenced.contains(name);
                if (used) {
                    notes.add(PARTIAL[i][1]);
                    level = LEVEL_PARTIAL;
                }
            }
        }

        if (notes.isEmpty()) {
            notes.add("Không thiếu gì — game dùng toàn API đã hỗ trợ");
        }
        return new Report(level, missing, notes);
    }

    /**
     * Tên tiếng Việt của phần điện thoại mà một lớp thuộc về, hoặc rỗng khi
     * lớp đó không nằm trong danh sách đã biết.
     *
     * <p>Cùng một bảng tên dùng cho hai chỗ: lời cảnh báo trước khi chơi và
     * lời giải thích sau khi game chết. Nếu tách ra thì cùng một thiếu sót sẽ
     * được gọi bằng hai cái tên khác nhau.</p>
     *
     * @param internalName tên lớp dạng {@code javax/microedition/m3g/World}
     */
    public static String describe(String internalName) {
        if (internalName == null) {
            return "";
        }
        for (int i = 0; i < KNOWN_MISSING.length; i++) {
            if (internalName.startsWith(KNOWN_MISSING[i][0])) {
                return KNOWN_MISSING[i][1];
            }
        }
        return "";
    }

    private static boolean isSupported(String name) {
        for (int i = 0; i < SUPPORTED.length; i++) {
            if (name.startsWith(SUPPORTED[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesPrefix(List<String> referenced, String prefix) {
        for (int i = 0; i < referenced.size(); i++) {
            if (referenced.get(i).startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String packageOf(String internalName) {
        int cut = internalName.lastIndexOf('/');
        return cut < 0 ? internalName : internalName.substring(0, cut + 1);
    }

    // ------------------------------------------------------ constant pools

    /**
     * Every class name the suite's classes refer to.
     *
     * <p>Read out of each constant pool rather than by searching the bytes:
     * a UTF-8 entry that happens to contain a package name proves nothing on
     * its own, and a game's own string constants would otherwise be mistaken
     * for API it uses. The pool is walked with a plain reader — the class file
     * format fixes each entry's length, so no full parse is needed.</p>
     */
    private static List<String> referencedClasses(JarArchive archive) {
        List<String> found = new ArrayList<String>();
        List<String> names = archive.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.endsWith(".class")) {
                continue;
            }
            byte[] data = archive.read(name);
            if (data != null) {
                collect(data, found);
            }
        }
        return found;
    }

    private static void collect(byte[] data, List<String> found) {
        if (data.length < 10 || readInt(data, 0) != 0xCAFEBABE) {
            return;
        }
        int count = readShort(data, 8);
        // Entry i of the pool, as a byte offset; index 0 is unused.
        int[] offsets = new int[count];
        int[] kinds = new int[count];
        int at = 10;
        for (int index = 1; index < count && at < data.length; index++) {
            int tag = data[at] & 0xFF;
            kinds[index] = tag;
            offsets[index] = at + 1;
            int size = entrySize(data, at, tag);
            if (size <= 0) {
                return;
            }
            at += size;
            if (tag == 5 || tag == 6) {
                // Longs and doubles take two slots, and the second is unused.
                index++;
            }
        }
        for (int index = 1; index < count; index++) {
            if (kinds[index] != 7) {
                continue;
            }
            int nameIndex = readShort(data, offsets[index]);
            if (nameIndex <= 0 || nameIndex >= count || kinds[nameIndex] != 1) {
                continue;
            }
            String value = utf8(data, offsets[nameIndex]);
            if (value != null && !found.contains(value)) {
                found.add(value);
            }
        }
    }

    /** Size of a constant pool entry, tag included. */
    private static int entrySize(byte[] data, int at, int tag) {
        switch (tag) {
            case 1: return 3 + readShort(data, at + 1);
            case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                return 5;
            case 5: case 6: return 9;
            case 7: case 8: case 16: case 19: case 20: return 3;
            case 15: return 4;
            default: return -1;
        }
    }

    private static String utf8(byte[] data, int at) {
        int length = readShort(data, at);
        if (at + 2 + length > data.length) {
            return null;
        }
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // Class names are ASCII in practice; anything else is not a name
            // this scan cares about.
            text.append((char) (data[at + 2 + i] & 0xFF));
        }
        return text.toString();
    }

    private static int readShort(byte[] data, int at) {
        if (at + 1 >= data.length) {
            return 0;
        }
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }

    private static int readInt(byte[] data, int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
                | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }
}
