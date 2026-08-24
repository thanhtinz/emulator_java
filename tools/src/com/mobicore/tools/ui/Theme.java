package com.mobicore.tools.ui;

/**
 * The interface palette, in light and dark.
 *
 * <p>Light is the default. A dark interface looks handsome in a screenshot
 * and is tiring to read in daylight, which is where a phone mostly gets used;
 * the person who wants dark can say so once and be done.</p>
 *
 * <p>The fields are assigned rather than final so every screen can keep
 * naming colours by role — {@code Theme.SURFACE}, not {@code palette.surface}
 * — while the palette behind those roles changes. Drawing happens on one
 * thread, so there is nothing here to race.</p>
 */
public final class Theme {

    public static final int LIGHT = 0;
    public static final int DARK = 1;

    public static int BG;
    public static int SURFACE;
    public static int SURFACE_ALT;
    public static int BORDER;
    public static int TEXT;
    public static int TEXT_DIM;
    public static int ACCENT;
    public static int ACCENT_DIM;
    public static int GOOD;
    public static int WARN;
    public static int BAD;
    /** Behind the emulated screen, which is black on every handset. */
    public static int SCREEN;
    /** Tinted backgrounds for status chips, in the same three meanings. */
    public static int GOOD_BG;
    public static int WARN_BG;
    public static int BAD_BG;
    /** Fill of a pressed-looking key on the virtual keypad. */
    public static int KEY;

    private static int mode = LIGHT;

    static {
        setMode(LIGHT);
    }

    public static int mode() {
        return mode;
    }

    public static boolean isDark() {
        return mode == DARK;
    }

    public static void setMode(int newMode) {
        mode = newMode == DARK ? DARK : LIGHT;
        // The emulated device's own title and softkey bars follow the app:
        // a dark strip stapled to the top of a light screen looks like a bug.
        com.mobicore.core.midp.SystemChrome.setDark(mode == DARK);
        if (mode == DARK) {
            BG = 0xFF0E1116;
            SURFACE = 0xFF171C24;
            SURFACE_ALT = 0xFF1F2630;
            BORDER = 0xFF2C3543;
            TEXT = 0xFFE6EDF3;
            TEXT_DIM = 0xFF8B98A8;
            ACCENT = 0xFF4CC2FF;
            ACCENT_DIM = 0xFF1B4E68;
            GOOD = 0xFF56D364;
            WARN = 0xFFE3B341;
            BAD = 0xFFF85149;
            SCREEN = 0xFF0A0C10;
            GOOD_BG = 0xFF12301E;
            WARN_BG = 0xFF3A2E10;
            BAD_BG = 0xFF3A1A1A;
            KEY = 0xFF1D3547;
            return;
        }
        // Not white: a sheet of pure white beside a game's own screen is
        // glaring, and every card would then need a border to be seen at all.
        BG = 0xFFF2F4F7;
        SURFACE = 0xFFFFFFFF;
        SURFACE_ALT = 0xFFE9EDF2;
        BORDER = 0xFFD3DAE3;
        TEXT = 0xFF16202B;
        TEXT_DIM = 0xFF5C6B7A;
        // Darker than the dark theme's accent: the same blue on white is too
        // pale to read, and an accent that cannot be read is decoration.
        ACCENT = 0xFF0A6FA8;
        ACCENT_DIM = 0xFFD7EBF7;
        GOOD = 0xFF1A7F37;
        WARN = 0xFF9A6700;
        BAD = 0xFFC0342B;
        SCREEN = 0xFF0A0C10;
        GOOD_BG = 0xFFDCF3E3;
        WARN_BG = 0xFFFBF0D0;
        BAD_BG = 0xFFFADEDB;
        KEY = 0xFFDCEEF9;
    }

    private Theme() {
    }
}
