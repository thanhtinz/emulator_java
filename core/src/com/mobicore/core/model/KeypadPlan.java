package com.mobicore.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every key of the virtual keypad goes, worked out once.
 *
 * <p>There are three keypads, and a player picks the one that suits the game
 * in front of them: a game played with the pad alone wants nothing else on
 * screen, a game that asks for a name wants the numbers, and an action game
 * wants a handful of the digits it actually reads and nothing more.</p>
 *
 * <p>The layouts follow J2ME Loader's, including the part that is easy to get
 * wrong: <em>the two softkeys are keys of the keypad</em>, not a bar beside
 * it. J2ME Loader snaps them west and east of the pad in its arrows layout and
 * north of it in the numbers-and-arrows layout, and that is what happens here
 * too — so they move, resize, fade and get dragged with every other key.</p>
 *
 * <p>This class exists because the same keypad has to be drawn three times —
 * the preview, Android and iOS — and it was being measured three times. Three
 * copies of one grid is three chances for the screenshot to stop describing
 * the phone. Everything here is plain arithmetic on plain fields so that it
 * translates and can be checked without a screen.</p>
 */
public final class KeypadPlan {

    /** Arrows and fire only, with a softkey to either side. */
    public static final int STYLE_ARROWS = 0;
    /** The numbers beside the pad, the way a handset had them. */
    public static final int STYLE_FULL = 1;
    /** Fire, the four directions, and the four digits games read. */
    public static final int STYLE_GAME = 2;

    /**
     * Key size against the screen, as J2ME Loader sizes it: the short edge
     * over 6.5 upright, the long edge over 12 when the phone is turned.
     */
    public static final float KEY_DIVISOR_UPRIGHT = 6.5f;
    public static final int KEY_DIVISOR_TURNED = 12;
    /** A softkey is wider than it is tall: it carries a word, not a digit. */
    public static final float SOFT_SCALE_X = 2.0f;
    public static final float SOFT_SCALE_Y = 0.75f;
    /** A hair of daylight between keys; J2ME Loader snaps its keys together. */
    public static final int GAP = 4;
    /** Down each side of the keypad, and between the two pads. */
    public static final int MARGIN = 12;

    /** What a key is, which is all a front end needs to draw it. */
    public static final int KIND_NUMBER = 0;
    public static final int KIND_ARROW = 1;
    public static final int KIND_FIRE = 2;
    public static final int KIND_SOFT = 3;

    /** Arrow directions, in the order the pads have always listed them. */
    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int UP_LEFT = 4;
    public static final int UP_RIGHT = 5;
    public static final int DOWN_LEFT = 6;
    public static final int DOWN_RIGHT = 7;

    private static final String[] ARROW_BUTTONS = {
            "up", "down", "left", "right",
            "upLeft", "upRight", "downLeft", "downRight",
    };

    /**
     * How tall a pad key is, given the size of a numeric key.
     *
     * <p>The pad has three rows against the grid's four, so at one key size it
     * ends up markedly shorter than the numbers beside it and reads as the
     * lesser of the two. It is the other way round: the pad is what a game is
     * played with. So the pad keeps the width and gains the height, three of
     * its rows coming to exactly four of theirs.</p>
     */
    public static int padKeyHeight(int key) {
        return (key * 4 + GAP) / 3;
    }

    /** One key, placed. */
    public static final class Key {

        private final String button;
        private final String label;
        private final int kind;
        private final int arrow;
        private final boolean round;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        Key(String button, String label, int kind, int arrow, boolean round,
            int x, int y, int width, int height) {
            this.button = button;
            this.label = label;
            this.kind = kind;
            this.arrow = arrow;
            this.round = round;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /** The name this key presses, as {@link InputProfile} spells it. */
        public String button() {
            return button;
        }

        /** What is written on it: a digit, "F", "L", "R", or nothing. */
        public String label() {
            return label;
        }

        public int kind() {
            return kind;
        }

        /** Which way the arrow points, or -1 when this is not an arrow. */
        public int arrow() {
            return arrow;
        }

        /**
         * True when this key is round whatever shape the profile asks for.
         *
         * <p>The game keypad is round because it is a gamepad: the four digits
         * sit where a handset's corner keys were, and a circle is what the
         * thumb expects to find there.</p>
         */
        public boolean round() {
            return round;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }

    private final List<Key> keys = new ArrayList<Key>();
    private final int style;
    private int height;

    private KeypadPlan(int style) {
        this.style = style;
    }

    public List<Key> keys() {
        return keys;
    }

    public int style() {
        return style;
    }

    /** How tall the whole keypad came out. */
    public int height() {
        return height;
    }

    /** The key that presses this button, or null when the style has none. */
    public Key find(String button) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).button().equals(button)) {
                return keys.get(i);
            }
        }
        return null;
    }

    public boolean has(String button) {
        return find(button) != null;
    }

    // --------------------------------------------------------------- upright

    /**
     * The keypad as it sits under the game with the phone held upright.
     *
     * @param style       one of the {@code STYLE_} constants
     * @param width       how wide the strip is
     * @param key         the size of one numeric key, already scaled
     * @param arrangement where the player has dragged the keys, or null
     */
    public static KeypadPlan portrait(int style, int width, int key,
                                      KeypadArrangement arrangement) {
        KeypadPlan plan = new KeypadPlan(clamp(style));
        key = keySize(key);
        width = Math.max(width, roomNeeded(plan.style, key));
        switch (plan.style) {
            case STYLE_ARROWS:
                plan.arrowsUpright(width, key, arrangement);
                break;
            case STYLE_GAME:
                plan.gameUpright(width, key, arrangement);
                break;
            default:
                plan.fullUpright(width, key, arrangement);
                break;
        }
        return plan;
    }

    /**
     * Arrows alone, with a softkey in each margin.
     *
     * <p>J2ME Loader snaps them west and east of the top corners of the pad;
     * the margins either side of a lone pad are empty and exactly where the
     * thumbs already are.</p>
     */
    private void arrowsUpright(int width, int key, KeypadArrangement arrangement) {
        int tall = padKeyHeight(key);
        int padWidth = key * 3 + GAP * 2;
        int padX = (width - padWidth) / 2;
        int softHeight = (int) (key * SOFT_SCALE_Y);
        int softWidth = padX - GAP - MARGIN;
        if (softWidth < key) {
            softWidth = key;
        }
        int softY = (tall - softHeight) / 2;
        soft(arrangement, key, "softLeft", "L", MARGIN, softY, softWidth, softHeight);
        soft(arrangement, key, "softRight", "R", width - MARGIN - softWidth, softY,
                softWidth, softHeight);
        directions(arrangement, key, padX, 0, key, tall, true);
        height = tall * 3 + GAP * 2;
    }

    /** The numbers on one side, the pad on the other, softkeys over the pad. */
    private void fullUpright(int width, int key, KeypadArrangement arrangement) {
        int tall = padKeyHeight(key);
        int padWidth = key * 3 + GAP * 2;
        int margin = (width - padWidth * 2) / 3;
        int numX = margin;
        int padX = width - margin - padWidth;
        int softHeight = (int) (key * SOFT_SCALE_Y);
        int softWidth = (padWidth - GAP) / 2;

        soft(arrangement, key, "softLeft", "L", padX, 0, softWidth, softHeight);
        soft(arrangement, key, "softRight", "R", padX + padWidth - softWidth, 0,
                softWidth, softHeight);

        int padY = softHeight + GAP;
        directions(arrangement, key, padX, padY, key, tall, true);

        // The numbers hang from the bottom of the pad rather than the top of
        // the strip: four rows of numbers and three of pad come to the same
        // height by construction, so both grids end on the same line.
        int numY = padY + tall * 3 + GAP * 2 - (key * 4 + GAP * 3);
        if (numY < 0) {
            numY = 0;
        }
        numbers(arrangement, key, numX, numY);
        height = Math.max(padY + tall * 3 + GAP * 2, numY + key * 4 + GAP * 3);
    }

    /**
     * Fire, four directions, and the four digits a game actually reads.
     *
     * <p>1, 3, 7 and 9 sit in the corners because that is where they were on
     * the handset this is imitating — a game that reads them reads them as
     * corners, and a thumb that learned them learned them there.</p>
     */
    private void gameUpright(int width, int key, KeypadArrangement arrangement) {
        int padWidth = key * 3 + GAP * 2;
        int padX = (width - padWidth) / 2;
        int softHeight = (int) (key * SOFT_SCALE_Y);
        int softWidth = (padWidth - GAP) / 2;
        soft(arrangement, key, "softLeft", "L", padX, 0, softWidth, softHeight);
        soft(arrangement, key, "softRight", "R", padX + padWidth - softWidth, 0,
                softWidth, softHeight);

        int top = softHeight + GAP;
        gameBlock(arrangement, key, padX, top);
        height = top + key * 3 + GAP * 2;
    }

    // ------------------------------------------------------------- sideways

    /**
     * One of the two columns the keypad splits into when the phone is turned.
     *
     * <p>Sideways both thumbs are already at the edges, so the game keeps the
     * middle and the keys go down the sides: the pad under one thumb, the
     * numbers under the other, and each column's softkey at the bottom corner
     * where that thumb rests.</p>
     *
     * @param directional the pad column when true, the numbers column when false
     */
    public static KeypadPlan column(int style, boolean directional, int width, int height,
                                    int key, KeypadArrangement arrangement) {
        KeypadPlan plan = new KeypadPlan(clamp(style));
        key = keySize(key);
        width = Math.max(width, key * 3 + GAP * 2);
        height = Math.max(height, key * 5);
        int softHeight = (int) (key * SOFT_SCALE_Y);
        int softWidth = (int) (key * SOFT_SCALE_X);
        if (softWidth > width) {
            softWidth = width;
        }
        int padWidth = key * 3 + GAP * 2;
        int padX = (width - padWidth) / 2;

        int stack;
        if (directional) {
            stack = plan.style == STYLE_GAME ? key * 3 + GAP * 2 : padKeyHeight(key) * 3 + GAP * 2;
        } else {
            stack = plan.style == STYLE_FULL ? key * 4 + GAP * 3 : 0;
        }
        int top = stack == 0 ? 0 : Math.max(0, (height - softHeight - 14 - stack) / 2);

        if (directional) {
            if (plan.style == STYLE_GAME) {
                plan.gameBlock(arrangement, key, padX, top);
            } else {
                plan.directions(arrangement, key, padX, top, key, padKeyHeight(key), true);
            }
        } else if (plan.style == STYLE_FULL) {
            plan.numbers(arrangement, key, padX, top);
        }

        int softY = height - softHeight - 14;
        if (softY < top + stack + GAP) {
            softY = top + stack + GAP;
        }
        plan.soft(arrangement, key, directional ? "softLeft" : "softRight",
                directional ? "L" : "R", (width - softWidth) / 2, softY, softWidth, softHeight);
        plan.height = softY + softHeight;
        return plan;
    }

    // ----------------------------------------------------------- the clusters

    /** The 3x4 grid, in the order a handset laid it out. */
    private void numbers(KeypadArrangement arrangement, int key, int x, int y) {
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        String[] buttons = {"num1", "num2", "num3", "num4", "num5", "num6",
                "num7", "num8", "num9", "star", "num0", "hash"};
        for (int i = 0; i < labels.length; i++) {
            add(arrangement, key, buttons[i], labels[i], KIND_NUMBER, -1, false,
                    x + (i % 3) * (key + GAP), y + (i / 3) * (key + GAP), key, key);
        }
    }

    /**
     * The directional pad: eight ways, with fire in the middle.
     *
     * <p>The corners are not keys of their own on a handset. MIDP has no
     * diagonal key code — a corner of the pad was two directions held at once,
     * which is exactly what these send.</p>
     */
    private void directions(KeypadArrangement arrangement, int key, int x, int y,
                            int wide, int tall, boolean corners) {
        int midX = x + wide + GAP;
        int rightX = midX + wide + GAP;
        int midY = y + tall + GAP;
        int lowY = midY + tall + GAP;

        if (corners) {
            arrow(arrangement, key, UP_LEFT, x, y, wide, tall);
            arrow(arrangement, key, UP_RIGHT, rightX, y, wide, tall);
            arrow(arrangement, key, DOWN_LEFT, x, lowY, wide, tall);
            arrow(arrangement, key, DOWN_RIGHT, rightX, lowY, wide, tall);
        }
        arrow(arrangement, key, UP, midX, y, wide, tall);
        arrow(arrangement, key, LEFT, x, midY, wide, tall);
        arrow(arrangement, key, RIGHT, rightX, midY, wide, tall);
        arrow(arrangement, key, DOWN, midX, lowY, wide, tall);
        add(arrangement, key, "fire", "F", KIND_FIRE, -1, false, midX, midY, wide, tall);
    }

    /** The gamepad: the four directions with 1, 3, 7, 9 in the corners. */
    private void gameBlock(KeypadArrangement arrangement, int key, int x, int y) {
        int midX = x + key + GAP;
        int rightX = midX + key + GAP;
        int midY = y + key + GAP;
        int lowY = midY + key + GAP;

        digit(arrangement, key, "num1", "1", x, y);
        digit(arrangement, key, "num3", "3", rightX, y);
        digit(arrangement, key, "num7", "7", x, lowY);
        digit(arrangement, key, "num9", "9", rightX, lowY);

        arrowRound(arrangement, key, UP, midX, y);
        arrowRound(arrangement, key, LEFT, x, midY);
        arrowRound(arrangement, key, RIGHT, rightX, midY);
        arrowRound(arrangement, key, DOWN, midX, lowY);
        add(arrangement, key, "fire", "F", KIND_FIRE, -1, true, midX, midY, key, key);
    }

    // ------------------------------------------------------------- one key

    private void digit(KeypadArrangement arrangement, int key, String button,
                       String label, int x, int y) {
        add(arrangement, key, button, label, KIND_NUMBER, -1, true, x, y, key, key);
    }

    private void arrow(KeypadArrangement arrangement, int key, int direction,
                       int x, int y, int wide, int tall) {
        add(arrangement, key, ARROW_BUTTONS[direction], "", KIND_ARROW, direction, false,
                x, y, wide, tall);
    }

    private void arrowRound(KeypadArrangement arrangement, int key, int direction,
                            int x, int y) {
        add(arrangement, key, ARROW_BUTTONS[direction], "", KIND_ARROW, direction, true,
                x, y, key, key);
    }

    private void soft(KeypadArrangement arrangement, int key, String button, String mark,
                      int x, int y, int width, int height) {
        add(arrangement, key, button, mark, KIND_SOFT, -1, false, x, y, width, height);
    }

    /**
     * Places one key, moved by however far the player has dragged it.
     *
     * <p>Offsets are in units of one numeric key for every key alike, softkeys
     * included: one unit has to mean the same distance whichever key was
     * dragged, or a saved arrangement means something different on each.</p>
     */
    private void add(KeypadArrangement arrangement, int key, String button, String label,
                     int kind, int arrow, boolean round, int x, int y, int width, int height) {
        if (arrangement != null) {
            x += Math.round(arrangement.offsetX(button) * key);
            y += Math.round(arrangement.offsetY(button) * key);
        }
        keys.add(new Key(button, label, kind, arrow, round, x, y, width, height));
    }

    /**
     * A usable key size, whatever was asked for.
     *
     * <p>A front end measuring itself hands over a zero on its first layout
     * pass, and a keypad built at zero comes out as a pile of keys in one
     * corner. Better a keypad too small to like than a keypad off the
     * screen.</p>
     */
    private static int keySize(int key) {
        return key < 8 ? 8 : key;
    }

    /** The narrowest strip this keypad can be laid out in without spilling. */
    private static int roomNeeded(int style, int key) {
        int pad = key * 3 + GAP * 2;
        if (style == STYLE_ARROWS) {
            // The pad, plus a margin either side wide enough for a softkey.
            return pad + (key + GAP + MARGIN) * 2;
        }
        return style == STYLE_FULL ? pad * 2 + MARGIN * 3 : pad + MARGIN * 2;
    }

    private static int clamp(int style) {
        return style < STYLE_ARROWS || style > STYLE_GAME ? STYLE_FULL : style;
    }
}
