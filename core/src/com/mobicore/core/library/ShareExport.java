package com.mobicore.core.library;

import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.IOException;
import java.util.List;

/**
 * Prepares a picture or a clip to leave the app.
 *
 * <p>A screenshot nobody can send is half a screenshot. The gallery can show
 * one and throw it away, but the reason a player took it — showing someone
 * else — needs the file to exist somewhere another app can open, under a name
 * that means something when it lands in a chat.</p>
 *
 * <p>Inside the app a picture is called {@code 1700000000000.png}, because a
 * number that sorts is the right name for a file the app itself reads. Sent to
 * someone, that name says nothing: what belongs on it is the game and when.
 * So a copy is made in the cache under a readable name, and it is the copy
 * that leaves.</p>
 *
 * <p>The cache is where it goes on purpose. What is handed to another app is a
 * copy the player did not ask to keep, and the phone is allowed to clear it
 * whenever it needs the room.</p>
 */
public final class ShareExport {

    /** How many prepared copies are kept before the oldest go. */
    public static final int KEEP = 20;

    private final Vfs vfs;
    private final StorageLayout layout;

    public ShareExport(Vfs vfs, StorageLayout layout) {
        this.vfs = vfs;
        this.layout = layout;
    }

    /** Where prepared copies live: the cache, because they are copies. */
    public String directory() {
        return StorageLayout.join(layout.dir(StorageLayout.CACHE), "share");
    }

    /**
     * Copies one picture or clip out under a name worth sending.
     *
     * @param title what the player calls the game
     * @param name the file's name inside the app, which carries its time
     * @param bytes the file itself
     * @return where the copy is, for the platform to hand to another app
     */
    public String prepare(String title, String name, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Không có gì để chia sẻ");
        }
        String directory = directory();
        vfs.mkdirs(directory);
        String path = StorageLayout.join(directory, fileNameFor(title, name));
        vfs.write(path, bytes);
        cleanUp();
        return path;
    }

    /**
     * "Sky Runner 2023-11-14 22-13.gif" — the game, the moment, the kind.
     *
     * <p>Colons and slashes are not in it because a file name carrying either
     * is a file name some phone, some chat app or some desktop will refuse;
     * the hyphen in place of the colon is what every screenshot tool ended up
     * doing for the same reason.</p>
     */
    public String fileNameFor(String title, String name) {
        String stamp = stampOf(name);
        String base = safeTitle(title);
        return base + (stamp.length() == 0 ? "" : " " + stamp) + extensionOf(name);
    }

    /** True for a clip rather than a still, which decides what it is sent as. */
    public static boolean isClip(String name) {
        return name != null && name.toLowerCase().endsWith(".gif");
    }

    /** What another app should be told this is. */
    public static String mimeOf(String name) {
        return isClip(name) ? "image/gif" : "image/png";
    }

    // ------------------------------------------------------------------ names

    /**
     * A title that can be a file name.
     *
     * <p>The title is the player's — they can rename a game to anything,
     * including a path. Everything that could steer a write somewhere else,
     * or that a file system refuses, comes out.</p>
     */
    public static String safeTitle(String title) {
        String value = title == null ? "" : title.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < 60; i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c < ' ') {
                continue;
            }
            if (c == '.' && out.length() == 0) {
                // A leading dot hides the file on every desktop it lands on,
                // and ".." is how a name tries to become a path.
                continue;
            }
            out.append(c);
        }
        String cleaned = out.toString().trim();
        return cleaned.length() == 0 ? "MobiCore" : cleaned;
    }

    /** The extension the file already has, or {@code .png} if it has none. */
    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? ".png" : name.substring(dot).toLowerCase();
    }

    /**
     * The moment out of a file's own name, as {@code 2023-11-14 22-13}.
     *
     * <p>Worked out here rather than with a date formatter: the core has no
     * dependencies so that it can be translated for iOS, and the arithmetic
     * for a civil date out of a count of milliseconds is a dozen lines.</p>
     *
     * @return the stamp, or an empty string when the name carries no time
     */
    public static String stampOf(String name) {
        String digits = name == null ? "" : name;
        int dot = digits.indexOf('.');
        if (dot > 0) {
            digits = digits.substring(0, dot);
        }
        long millis;
        try {
            millis = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return "";
        }
        if (millis <= 0) {
            return "";
        }
        long seconds = millis / 1000;
        long days = seconds / 86400;
        int minuteOfDay = (int) ((seconds % 86400) / 60);
        int[] date = civilFromDays(days);
        return pad(date[0], 4) + "-" + pad(date[1], 2) + "-" + pad(date[2], 2)
                + " " + pad(minuteOfDay / 60, 2) + "-" + pad(minuteOfDay % 60, 2);
    }

    /**
     * Days since 1970 into a year, month and day.
     *
     * <p>Howard Hinnant's civil-from-days: the calendar is shifted to start in
     * March so that the leap day falls at the end of the year and the month
     * lengths become a straight line rather than a table of exceptions.</p>
     */
    private static int[] civilFromDays(long days) {
        long shifted = days + 719468;
        long era = (shifted >= 0 ? shifted : shifted - 146096) / 146097;
        long dayOfEra = shifted - era * 146097;
        long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        long year = yearOfEra + era * 400;
        long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        long monthPrime = (5 * dayOfYear + 2) / 153;
        long day = dayOfYear - (153 * monthPrime + 2) / 5 + 1;
        long month = monthPrime + (monthPrime < 10 ? 3 : -9);
        return new int[]{(int) (year + (month <= 2 ? 1 : 0)), (int) month, (int) day};
    }

    private static String pad(int value, int width) {
        StringBuilder out = new StringBuilder(String.valueOf(value));
        while (out.length() < width) {
            out.insert(0, '0');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ cache

    /**
     * Keeps the newest copies and drops the rest.
     *
     * <p>Sharing the same picture twice should not leave two of it, and a
     * folder that only ever grows is a folder that will one day be the reason
     * a phone is out of space.</p>
     */
    private void cleanUp() {
        List<String> names = vfs.list(directory());
        int extra = names.size() - KEEP;
        if (extra <= 0) {
            return;
        }
        // The listing is sorted by name, and a name starts with the game's
        // title — so which are oldest has to come from the files themselves.
        String[] paths = new String[names.size()];
        long[] times = new long[names.size()];
        for (int i = 0; i < names.size(); i++) {
            paths[i] = StorageLayout.join(directory(), names.get(i));
            times[i] = vfs.modifiedAt(paths[i]);
        }
        for (int removed = 0; removed < extra; removed++) {
            int oldest = -1;
            for (int i = 0; i < paths.length; i++) {
                if (paths[i] != null && (oldest < 0 || times[i] < times[oldest])) {
                    oldest = i;
                }
            }
            if (oldest < 0) {
                return;
            }
            vfs.delete(paths[oldest]);
            paths[oldest] = null;
        }
    }
}
