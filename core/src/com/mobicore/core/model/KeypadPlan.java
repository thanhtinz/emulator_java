package com.mobicore.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every key of the virtual keypad goes, worked out once.
 *
 * <p>There are two keypads, and they answer two ways of playing: the handset's
 * own — the numbers beside the pad, for a game that asks for a name or reads
 * the digits — and a gamepad, a stick under one thumb with OK and the four
 * digits an action game reads under the other.</p>
 *
 * <p>Both put <em>the two softkeys inside the keypad</em>, not in a bar beside
 * it, the way J2ME Loader does — so they move, resize, fade and get dragged
 * with every other key. Each one sits over the half of the keypad its thumb
 * already covers.</p>
 *
 * <p>This class exists because the same keypad has to be drawn three times —
 * the preview, Android and iOS — and it was being measured three times. Three
 * copies of one grid is three chances for the screenshot to stop describing
 * the phone. Everything here is plain arithmetic on plain fields so that it
 * translates and can be checked without a screen.</p>
 */
public final class KeypadPlan {

    /** The numbers beside the pad, the way a handset had them. */
    public static final int STYLE_FULL = 0;
    /** A stick, OK, and the four digits games read. */
    public static final int STYLE_GAME = 1;

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
    /**
     * The round pad of the game keypad: one key that steers.
     *
     * <p>Not four keys drawn as a circle — a thumb rests on it and leans,
     * and where it leans decides which directions are held, so a corner is
     * reached by leaning into it rather than by finding a separate key.</p>
     */
    public static final int KIND_STICK = 4;

    /** Arrow directions, in the order the pads have always listed them. */
    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int UP_LEFT = 4;
    public static final int UP_RIGHT = 5;
    public static final int DOWN_LEFT = 6;
    public static final int DOWN_RIGHT = 7;

    /**
     * What is written on the key in the middle.
     *
     * <p>J2ME Loader writes "F" on it, for fire, and MIDP calls it the fire
     * key — but nobody playing calls it that. It is the key that says yes, on
     * every one of the three keypads, and it says the same word on all three:
     * a key that renames itself when the keypad changes is a key the thumb has
     * to learn twice.</p>
     */
    public static final String FIRE_LABEL = "OK";

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

        /** What is written on it: a digit, "OK", "L", "R", or nothing. */
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
        if (plan.style == STYLE_GAME) {
            plan.gameUpright(width, key, arrangement);
        } else {
            plan.fullUpright(width, key, arrangement);
        }
        return plan;
    }

    /** The numbers on one side, the pad on the other, a softkey over each. */
    private void fullUpright(int width, int key, KeypadArrangement arrangement) {
        int tall = padKeyHeight(key);
        int padWidth = key * 3 + GAP * 2;
        int margin = (width - padWidth * 2) / 3;
        int numX = margin;
        int padX = width - margin - padWidth;
        int softHeight = (int) (key * SOFT_SCALE_Y);
        // One over each half rather than both over the pad: they are the two
        // keys a game labels, and each belongs over the thumb that reaches
        // it. Stacking both on one side leaves the other thumb crossing the
        // keypad for a key that was always under its own.
        soft(arrangement, key, "softLeft", "L", numX, 0, padWidth, softHeight);
        soft(arrangement, key, "softRight", "R", padX, 0, padWidth, softHeight);

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
     * The gamepad: a stick under one thumb, fire and four digits under the
     * other, and the two softkeys across the top.
     *
     * <p>This is the layout of every action game written for a touchscreen
     * since, and it is not a fashion: a keypad drawn as a grid asks the thumb
     * to find an edge it cannot feel, while a stick only has to be leaned on.
     * The digits are 1, 3, 7 and 9 — the four a game reads, sitting where the
     * corners of a handset's keypad were — curved around the fire key at
     * arm's length from it so that none of them is hit by accident.</p>
     */
    private void gameUpright(int width, int key, KeypadArrangement arrangement) {
        // The whole thing is wider than a grid of the same key size, so when
        // the screen is narrow the key gives way rather than the layout: a
        // stick pushed off the side is not a keypad at all.
        int room = width - MARGIN * 2 - GAP * 2;
        if (key * GAME_WIDE / 100 > room) {
            key = keySize(room * 100 / GAME_WIDE);
        }
        int softHeight = (int) (key * SOFT_SCALE_Y);
        int softWidth = (width - MARGIN * 2) * 2 / 5;
        // Each one centred in its own half rather than pushed out to the
        // margin: they are a pair, and a pair that hugs the edges reads as
        // two keys that got away from each other.
        int softLeftX = width / 4 - softWidth / 2;
        soft(arrangement, key, "softLeft", "L", softLeftX, 0, softWidth, softHeight);
        soft(arrangement, key, "softRight", "R", width - softLeftX - softWidth, 0,
                softWidth, softHeight);

        int top = softHeight + GAP * 2;
        int stick = key * STICK_KEYS / 100;
        int band = key * CLUSTER_TALL / 100;
        if (stick > band) {
            band = stick;
        }
        add(arrangement, key, "stick", "", KIND_STICK, -1, true,
                MARGIN, top + (band - stick) / 2, stick, stick);
        actionCluster(arrangement, key, width - MARGIN - key * CLUSTER_WIDE / 100,
                top + (band - key * CLUSTER_TALL / 100) / 2);
        height = top + band;
    }

    /**
     * Fire, and the four digits curved around it.
     *
     * <p>Placed on an arc rather than in a row: a thumb pivots from one
     * knuckle, so the keys it can reach without moving the hand lie on a
     * curve, and a row of them puts the far end out of reach.</p>
     */
    private void actionCluster(KeypadArrangement arrangement, int key, int left, int top) {
        int fire = key * FIRE_SIZE / 100;
        int radius = key * ARC_RADIUS / 100;
        // The cluster is measured from its own leftmost and topmost key, so
        // the middle sits that far in from the corner it was given.
        int cx = left + key * CLUSTER_LEFT / 100;
        int cy = top + key * CLUSTER_TOP / 100;

        add(arrangement, key, "fire", FIRE_LABEL, KIND_FIRE, -1, true,
                cx - fire / 2, cy - fire / 2, fire, fire);
        for (int i = 0; i < ARC_BUTTONS.length; i++) {
            int x = cx + ARC_X[i] * radius / 1000 - key / 2;
            int y = cy + ARC_Y[i] * radius / 1000 - key / 2;
            add(arrangement, key, ARC_BUTTONS[i], ARC_LABELS[i], KIND_NUMBER, -1, true,
                    x, y, key, key);
        }
    }

    /** The four digits, from the lower left round to the upper right. */
    private static final String[] ARC_BUTTONS = {"num1", "num3", "num7", "num9"};
    private static final String[] ARC_LABELS = {"1", "3", "7", "9"};
    /** Where each sits on the arc, in thousandths of the radius. */
    private static final int[] ARC_X = {-966, -819, -174, 500};
    private static final int[] ARC_Y = {259, -574, -985, -866};

    /** Everything below is in hundredths of one key. */
    private static final int FIRE_SIZE = 150;
    private static final int ARC_RADIUS = 175;
    private static final int STICK_KEYS = 300;
    private static final int CLUSTER_LEFT = 219;
    private static final int CLUSTER_TOP = 222;
    private static final int CLUSTER_WIDE = 357;
    private static final int CLUSTER_TALL = 317;
    /** Stick, gap and cluster together, which is what has to fit across. */
    private static final int GAME_WIDE = STICK_KEYS + CLUSTER_WIDE;

    /**
     * Which directions a thumb leaning this far off the stick's middle holds.
     *
     * <p>Asked of the core so that leaning into a corner means the same thing
     * on the phone as it does in the preview. The middle third is a rest: a
     * thumb sitting still on the stick is not steering, and without that the
     * character walks off on its own.</p>
     *
     * @param dx     how far right of the middle, in pixels
     * @param dy     how far below the middle
     * @param radius half the stick's width
     */
    public static List<String> stickDirections(float dx, float dy, float radius) {
        List<String> held = new ArrayList<String>();
        float reach = (float) Math.sqrt(dx * dx + dy * dy);
        if (radius <= 0 || reach < radius * 0.35f) {
            return held;
        }
        // Within 22.5 degrees of an axis is that axis alone; past that the
        // lean holds both, which is how a corner of the old pad was reached.
        float edge = 0.3827f * reach;
        if (dy < -edge) {
            held.add("up");
        }
        if (dy > edge) {
            held.add("down");
        }
        if (dx < -edge) {
            held.add("left");
        }
        if (dx > edge) {
            held.add("right");
        }
        return held;
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
            stack = plan.style == STYLE_GAME
                    ? key * STICK_KEYS / 100 : padKeyHeight(key) * 3 + GAP * 2;
        } else if (plan.style == STYLE_FULL) {
            stack = key * 4 + GAP * 3;
        } else {
            stack = plan.style == STYLE_GAME ? key * CLUSTER_TALL / 100 : 0;
        }
        int top = stack == 0 ? 0 : Math.max(0, (height - softHeight - 14 - stack) / 2);

        if (directional) {
            if (plan.style == STYLE_GAME) {
                int stick = key * STICK_KEYS / 100;
                plan.add(arrangement, key, "stick", "", KIND_STICK, -1, true,
                        (width - stick) / 2, top, stick, stick);
            } else {
                plan.directions(arrangement, key, padX, top, key, padKeyHeight(key), true);
            }
        } else if (plan.style == STYLE_FULL) {
            plan.numbers(arrangement, key, padX, top);
        } else if (plan.style == STYLE_GAME) {
            plan.actionCluster(arrangement, key,
                    (width - key * CLUSTER_WIDE / 100) / 2, top);
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
        add(arrangement, key, "fire", FIRE_LABEL, KIND_FIRE, -1, false, midX, midY, wide, tall);
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
        if (style == STYLE_GAME) {
            // The gamepad shrinks its keys to fit instead, so all it needs is
            // room for the pair of softkeys.
            return MARGIN * 2 + GAP * 2 + 8 * GAME_WIDE / 100;
        }
        return pad * 2 + MARGIN * 3;
    }

    private static int clamp(int style) {
        return style < STYLE_FULL || style > STYLE_GAME ? STYLE_FULL : style;
    }
}
