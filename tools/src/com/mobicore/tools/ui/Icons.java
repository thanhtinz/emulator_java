package com.mobicore.tools.ui;

import com.mobicore.core.gfx.Framebuffer;
import java.util.Base64;

/**
 * Material icons for the interface previews.
 *
 * <p>Nothing here is drawn by hand. Each glyph is the Material icon of the
 * same name, rasterised offline from the SVG in {@code assets/icons} by
 * {@code codegen/IconGen.java} — the same set the Android build shows through
 * {@code Icons.Filled} and the closest match to what iOS shows through SF
 * Symbols. A shape invented in code would be one more thing to keep in step
 * with two platforms, and it would never quite look like either.</p>
 *
 * <p>Coverage is stored at 64 square and averaged down to whatever size a
 * screen asks for, so an icon is always reduced, never enlarged.</p>
 */
public final class Icons {

    public static final String HOME = "home";
    public static final String LIBRARY = "videogame_asset";
    public static final String TOOLS = "build";
    public static final String SETTINGS = "settings";
    public static final String BACK = "chevron_left";
    public static final String STAR = "star";
    public static final String STAR_OUTLINE = "star_border";
    public static final String IMPORT = "download";
    public static final String SEARCH = "search";
    public static final String DELETE = "delete";
    public static final String PLAY = "play_arrow";
    public static final String TUNE = "tune";
    public static final String UP = "keyboard_arrow_up";
    public static final String DOWN = "keyboard_arrow_down";
    public static final String LEFT = "keyboard_arrow_left";
    public static final String RIGHT = "keyboard_arrow_right";
    public static final String ADD = "add";
    public static final String CLOSE = "close";
    public static final String SAVE = "save";
    public static final String FOLDER = "folder_open";
    public static final String EDIT = "edit";
    public static final String PHOTO = "photo_library";
    public static final String CAMERA = "photo_camera";
    public static final String UNDO = "undo";
    public static final String CHECK = "check_circle";
    /** The sun and the moon: what a theme toggle looks like everywhere. */
    /** The four corners of a d-pad, each meaning two directions at once. */
    public static final String UP_LEFT = "north_west";
    public static final String UP_RIGHT = "north_east";
    public static final String DOWN_LEFT = "south_west";
    public static final String DOWN_RIGHT = "south_east";
    /** Shown when the game wants text and the phone's keyboard can be used. */
    public static final String KEYBOARD = "keyboard";
    /** In-game menu: turn the screen, cap the frame rate, leave the game. */
    public static final String ROTATE = "screen_rotation";
    public static final String SPEED = "speed";
    public static final String EXIT = "exit_to_app";
    /** The toolbar's own three: search, sort, and everything else. */
    public static final String SORT = "sort";
    public static final String MORE = "more_vert";
    public static final String LIGHT_MODE = "brightness_high";
    public static final String DARK_MODE = "brightness_4";

    private static final byte[][] CACHE = new byte[IconData.NAMES.length][];

    /**
     * Draws {@code name} in {@code color}, {@code size} pixels square, with
     * its top left corner at {@code x, y}.
     */
    public static void draw(Framebuffer frame, String name, int x, int y, int size, int color) {
        byte[] alpha = coverage(index(name));
        int source = IconData.SIZE;
        int rgb = color & 0x00FFFFFF;
        int tint = (color >>> 24) & 0xFF;
        for (int row = 0; row < size; row++) {
            int fromY = row * source / size;
            int toY = Math.max(fromY + 1, (row + 1) * source / size);
            for (int column = 0; column < size; column++) {
                int fromX = column * source / size;
                int toX = Math.max(fromX + 1, (column + 1) * source / size);
                // Box average, because every screen draws these smaller than
                // they are stored: picking one sample would drop the thin
                // strokes an icon is mostly made of.
                int total = 0;
                int count = 0;
                for (int sy = fromY; sy < toY; sy++) {
                    for (int sx = fromX; sx < toX; sx++) {
                        total += alpha[sy * source + sx] & 0xFF;
                        count++;
                    }
                }
                int coverage = total / count * tint / 255;
                if (coverage > 0) {
                    frame.blendPixel(x + column, y + row, (coverage << 24) | rgb);
                }
            }
        }
    }

    /** Draws the icon centred on {@code cx, cy}. */
    public static void drawCentred(Framebuffer frame, String name, int cx, int cy, int size,
            int color) {
        draw(frame, name, cx - size / 2, cy - size / 2, size, color);
    }

    /** True if the generator has produced this icon. */
    public static boolean has(String name) {
        for (int i = 0; i < IconData.NAMES.length; i++) {
            if (IconData.NAMES[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static int index(String name) {
        for (int i = 0; i < IconData.NAMES.length; i++) {
            if (IconData.NAMES[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("no such icon: " + name
                + " (add assets/icons/" + name + ".svg and run codegen/IconGen)");
    }

    /** Unpacks the two-pixels-per-byte coverage map, once per icon. */
    private static byte[] coverage(int index) {
        if (CACHE[index] == null) {
            byte[] packed = Base64.getDecoder().decode(IconData.data(index));
            byte[] alpha = new byte[IconData.SIZE * IconData.SIZE];
            for (int i = 0; i < packed.length; i++) {
                alpha[i * 2] = (byte) (((packed[i] >>> 4) & 0x0F) * 17);
                alpha[i * 2 + 1] = (byte) ((packed[i] & 0x0F) * 17);
            }
            CACHE[index] = alpha;
        }
        return CACHE[index];
    }

    private Icons() {
    }
}
