package com.mobicore.core.model;

import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.jar.SuiteLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out how a game should be set up by looking at the game.
 *
 * <p>A player wants to play, not to fill in a form. Everything a settings
 * screen used to ask for — what size the handset's screen was, which
 * manufacturer's keypad the game was written for, whether it needs the
 * network — is already stated or implied by the suite itself, so it is read
 * out of the suite instead of asked for.</p>
 *
 * <p>Every conclusion is recorded as a line of plain language. A guess the
 * user cannot see is a guess they cannot correct, and these are guesses: the
 * evidence is good but a JAR is not obliged to be honest about itself.</p>
 */
public final class AutoSetup {

    /** What was decided, and why. */
    public static final class Result {

        private final GameProfile profile;
        private final List<String> notes;

        Result(GameProfile profile, List<String> notes) {
            this.profile = profile;
            this.notes = notes;
        }

        public GameProfile profile() {
            return profile;
        }

        /** One line per decision, in the user's language. */
        public List<String> notes() {
            return notes;
        }
    }

    private AutoSetup() {
    }

    /**
     * Configures a freshly imported suite.
     *
     * @param suite the loaded suite; its archive is read, not just its manifest
     */
    public static Result configure(SuiteLoader suite) {
        List<String> notes = new ArrayList<String>();
        JarArchive archive = suite.archive();

        DeviceProfile device = detectDevice(suite, archive, notes);
        InputProfile input = detectKeypad(suite, device, notes);

        GameProfile profile = new GameProfile(suite.info().suiteId(), device, input);
        profile.setScaleMode(GameProfile.SCALE_FIT);

        // A game written for a wide screen is played with the phone turned.
        // Nobody should have to find a setting for that: the screen the game
        // asks for already says which way round it is meant to be held.
        if (device.orientation() == DeviceProfile.ORIENTATION_LANDSCAPE) {
            profile.setOrientation(DeviceProfile.ORIENTATION_LANDSCAPE);
            notes.add("Màn hình ngang — tự xoay khi chơi");
        }

        // A game that never opens a connection should never make the user
        // answer a question about connections.
        if (uses(archive, "javax/microedition/io/HttpConnection")
                || uses(archive, "javax/microedition/io/Connector")) {
            profile.setNetworkMode(GameProfile.NETWORK_ASK);
            notes.add("Có dùng mạng — sẽ hỏi trước khi kết nối");
        } else {
            profile.setNetworkMode(GameProfile.NETWORK_BLOCKED);
            notes.add("Không dùng mạng — đã tắt, không hỏi gì thêm");
        }

        if (uses(archive, "javax/microedition/media/Manager")) {
            notes.add("Có âm thanh — bật sẵn ở mức " + profile.volume() + "%");
        } else {
            notes.add("Game này không phát tiếng");
        }

        // A game drawing its own frames wants a frame cap that matches what a
        // handset managed; a menu-driven game is not redrawing at all between
        // key presses, so a cap only wastes battery.
        if (uses(archive, "javax/microedition/lcdui/game/GameCanvas")) {
            profile.setFrameLimit(30);
            notes.add("Game hành động — giới hạn 30 hình/giây");
        } else {
            profile.setFrameLimit(20);
            notes.add("Game theo màn hình — 20 hình/giây, đỡ tốn pin");
        }

        Compatibility.Report report = Compatibility.scan(suite);
        if (report.level() != Compatibility.LEVEL_FULL) {
            // Worth saying up front: a game that cannot start says nothing
            // useful itself — it just fails to appear.
            notes.addAll(report.notes());
        }
        profile.setCompatibility(report.level());

        profile.setAuto(true, notes);
        return new Result(profile, notes);
    }

    // -------------------------------------------------------------- screen

    private static DeviceProfile detectDevice(SuiteLoader suite, JarArchive archive,
                                              List<String> notes) {
        // What the suite declares about itself, when it declares anything.
        String declared = suite.info().attributes().get("Nokia-MIDlet-Original-Display-Size");
        if (declared == null) {
            declared = suite.info().attributes().get("MIDlet-Screen-Size");
        }
        DeviceProfile fromAttributes = parse(declared);
        if (fromAttributes != null) {
            notes.add("Màn hình " + fromAttributes.resolution() + " — theo khai báo của game");
            return fromAttributes;
        }

        // Otherwise the pictures give it away: a game's background or splash
        // is drawn for one screen and is usually exactly that size.
        DeviceProfile fromArt = fromLargestImage(archive);
        if (fromArt != null) {
            notes.add("Màn hình " + fromArt.resolution() + " — suy từ ảnh trong game");
            return fromArt;
        }

        notes.add("Màn hình " + DeviceProfile.QVGA_240x320.resolution() + " — cỡ phổ biến nhất");
        return DeviceProfile.QVGA_240x320;
    }

    private static DeviceProfile parse(String declared) {
        if (declared == null) {
            return null;
        }
        String[] parts = declared.replace('x', ',').split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return match(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The largest PNG in the suite that looks like a screen.
     *
     * <p>Two passes, because size alone is not evidence. An image whose
     * dimensions match a handset the emulator knows about is almost certainly
     * a background drawn for that handset; any other large image is weaker
     * evidence and is only used when nothing matched. Files named like icons
     * are skipped outright — a 192 square icon is not a 192 square screen,
     * and treating it as one gets the game a screen no handset ever had.</p>
     *
     * <p>Only the PNG header is read, so this stays cheap on a JAR full of
     * artwork.</p>
     */
    private static DeviceProfile fromLargestImage(JarArchive archive) {
        DeviceProfile bestKnown = null;
        long bestKnownArea = 0;
        int bestWidth = 0;
        int bestHeight = 0;
        long bestArea = 0;

        List<String> names = archive.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String lower = name.toLowerCase();
            if (!lower.endsWith(".png") || isIconName(lower)) {
                continue;
            }
            byte[] data = archive.read(name);
            if (data == null || data.length < 24) {
                continue;
            }
            int width = readInt(data, 16);
            int height = readInt(data, 20);
            if (width < 96 || height < 96 || width > 640 || height > 640) {
                // Too small to be a screen — a sprite or a tile — or too
                // large to be a handset's.
                continue;
            }
            long area = (long) width * height;
            DeviceProfile known = catalogDevice(width, height);
            if (known != null && area > bestKnownArea) {
                bestKnownArea = area;
                bestKnown = known;
            }
            if (area > bestArea) {
                bestArea = area;
                bestWidth = width;
                bestHeight = height;
            }
        }

        if (bestKnown != null) {
            return bestKnown;
        }
        return bestArea == 0 ? null : DeviceProfile.custom(bestWidth, bestHeight);
    }

    /** Icons are named as icons, and every J2ME suite has one. */
    private static boolean isIconName(String lower) {
        return lower.indexOf("icon") >= 0 || lower.indexOf("logo") >= 0
                || lower.indexOf("thumb") >= 0;
    }

    /** The catalog entry of exactly this size, or null. */
    private static DeviceProfile catalogDevice(int width, int height) {
        List<DeviceProfile> catalog = DeviceProfile.catalog();
        for (int i = 0; i < catalog.size(); i++) {
            DeviceProfile candidate = catalog.get(i);
            if (candidate.width() == width && candidate.height() == height) {
                return candidate;
            }
        }
        return null;
    }

    private static int readInt(byte[] data, int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
                | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }

    /** A catalog device of that size, or a custom one when nothing matches. */
    private static DeviceProfile match(int width, int height) {
        DeviceProfile known = catalogDevice(width, height);
        return known == null ? DeviceProfile.custom(width, height) : known;
    }

    // -------------------------------------------------------------- keypad

    /**
     * Which manufacturer's key codes the game expects.
     *
     * <p>The attributes are the giveaway: a game built for a Nokia carries
     * {@code Nokia-} attributes, and one built for a Sony Ericsson carries
     * theirs. Getting this wrong is the difference between the d-pad working
     * and the game ignoring it entirely, which is exactly the sort of thing a
     * player should never have to diagnose.</p>
     */
    private static InputProfile detectKeypad(SuiteLoader suite, DeviceProfile device,
                                             List<String> notes) {
        String vendor = suite.info().vendor() == null ? "" : suite.info().vendor().toLowerCase();
        boolean nokia = hasPrefix(suite, "Nokia-") || vendor.indexOf("nokia") >= 0;
        boolean sonyEricsson = hasPrefix(suite, "SonyEricsson-") || hasPrefix(suite, "SEMC-")
                || vendor.indexOf("sony") >= 0 || vendor.indexOf("ericsson") >= 0;
        boolean samsung = hasPrefix(suite, "Samsung-") || vendor.indexOf("samsung") >= 0;

        if (sonyEricsson) {
            notes.add("Bàn phím Sony Ericsson — theo thuộc tính trong game");
            return InputProfile.sonyEricsson();
        }
        if (samsung) {
            notes.add("Bàn phím Samsung — theo thuộc tính trong game");
            return InputProfile.samsung();
        }
        if (nokia) {
            notes.add("Bàn phím Nokia — theo thuộc tính trong game");
            return InputProfile.nokia();
        }
        notes.add("Bàn phím Nokia — chuẩn của phần lớn game J2ME");
        return InputProfile.forKeypad(device.keypad());
    }

    private static boolean hasPrefix(SuiteLoader suite, String prefix) {
        List<String> keys = suite.info().attributes().keys();
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ contents

    /**
     * True if any class in the suite refers to {@code internalName}.
     *
     * <p>A class file stores the names it uses as plain UTF-8, so searching
     * the bytes finds them without parsing the constant pool. That is a
     * deliberate trade: this runs once per install over every class, and a
     * full parse would cost far more for an answer that is used to pick a
     * default.</p>
     */
    private static boolean uses(JarArchive archive, String internalName) {
        byte[] needle = bytes(internalName);
        List<String> names = archive.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.endsWith(".class")) {
                continue;
            }
            byte[] data = archive.read(name);
            if (data != null && contains(data, needle)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] bytes(String text) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            out[i] = (byte) text.charAt(i);
        }
        return out;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        int limit = haystack.length - needle.length;
        for (int at = 0; at <= limit; at++) {
            int i = 0;
            while (i < needle.length && haystack[at + i] == needle[i]) {
                i++;
            }
            if (i == needle.length) {
                return true;
            }
        }
        return false;
    }
}
