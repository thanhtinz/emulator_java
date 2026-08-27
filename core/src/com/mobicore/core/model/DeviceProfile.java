package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The screen a game is given: its size, the keypad layout and the capabilities
 * a MIDlet can ask about.
 *
 * <p>There is one screen now, and it is 240x320 — the size most J2ME games
 * were written for and the one every later handset could show. The catalog of
 * seven handsets that used to be here was a question nobody could answer:
 * picking the wrong one made a game look wrong, and picking the right one was
 * guesswork about hardware the player never owned. A game that reads
 * {@code getWidth} adapts, which is what nearly all of them do, and one that
 * assumes a smaller screen is scaled up to fill this one.</p>
 *
 * <p>The landscape size is the same screen turned, not a second device.</p>
 */
public final class DeviceProfile {

    /** Keypad layouts differ mainly in which key codes the softkeys report. */
    public static final int KEYPAD_NOKIA = 0;
    public static final int KEYPAD_SONY_ERICSSON = 1;
    public static final int KEYPAD_SAMSUNG = 2;
    public static final int KEYPAD_MOTOROLA = 3;

    public static final int ORIENTATION_PORTRAIT = 0;
    public static final int ORIENTATION_LANDSCAPE = 1;

    private final String id;
    private final String name;
    private final int width;
    private final int height;
    private final int keypad;
    private final int colorDepth;
    private final boolean touch;

    public DeviceProfile(String id, String name, int width, int height, int keypad,
                         int colorDepth, boolean touch) {
        this.id = id;
        this.name = name;
        this.width = width;
        this.height = height;
        this.keypad = keypad;
        this.colorDepth = colorDepth;
        this.touch = touch;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int keypad() {
        return keypad;
    }

    public int colorDepth() {
        return colorDepth;
    }

    public boolean hasTouch() {
        return touch;
    }

    public int orientation() {
        return width > height ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT;
    }

    public String resolution() {
        return width + "x" + height;
    }

    public String keypadName() {
        switch (keypad) {
            case KEYPAD_SONY_ERICSSON: return "Sony Ericsson";
            case KEYPAD_SAMSUNG: return "Samsung";
            case KEYPAD_MOTOROLA: return "Motorola";
            default: return "Nokia";
        }
    }

    /** Rotated copy, used when the user flips a game to landscape. */
    public DeviceProfile rotated() {
        return new DeviceProfile(id + "-rot", name + " (rotated)", height, width, keypad,
                colorDepth, touch);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("id", id);
        json.put("name", name);
        json.put("width", Integer.valueOf(width));
        json.put("height", Integer.valueOf(height));
        json.put("keypad", Integer.valueOf(keypad));
        json.put("colorDepth", Integer.valueOf(colorDepth));
        json.put("touch", Boolean.valueOf(touch));
        return json;
    }

    public static DeviceProfile fromJson(Map<String, Object> json) {
        return new DeviceProfile(
                Json.string(json, "id", "custom"),
                Json.string(json, "name", "Custom"),
                Json.integer(json, "width", 240),
                Json.integer(json, "height", 320),
                Json.integer(json, "keypad", KEYPAD_NOKIA),
                Json.integer(json, "colorDepth", 24),
                Json.bool(json, "touch", false));
    }

    // ------------------------------------------------------------- catalog

    /**
     * The one screen: 240x320, what most of these games were written for.
     */
    public static final DeviceProfile QVGA_240x320 =
            new DeviceProfile("qvga-240x320", "Màn hình chuẩn 240x320", 240, 320,
                    KEYPAD_NOKIA, 24, true);

    /** The same screen turned, for a game written to be played sideways. */
    public static final DeviceProfile QVGA_LANDSCAPE =
            new DeviceProfile("qvga-320x240", "Màn hình ngang 320x240", 320, 240,
                    KEYPAD_NOKIA, 24, true);

    private static final List<DeviceProfile> CATALOG = Collections.unmodifiableList(
            new ArrayList<DeviceProfile>(java.util.Arrays.asList(QVGA_240x320)));

    /**
     * The screen, as a list of one.
     *
     * <p>Still a list because the JSON the apps read has always carried one,
     * and a screen a game declares for itself still has to be describable —
     * but nothing offers a choice any more.</p>
     */
    public static List<DeviceProfile> catalog() {
        return CATALOG;
    }

    /** Landscape asks for the turned screen; everything else gets the one. */
    public static DeviceProfile byId(String id) {
        return QVGA_LANDSCAPE.id().equals(id) ? QVGA_LANDSCAPE : QVGA_240x320;
    }

    public static DeviceProfile custom(int width, int height) {
        return new DeviceProfile("custom-" + width + "x" + height, "Custom " + width + "x" + height,
                width, height, KEYPAD_NOKIA, 24, true);
    }

    /**
     * Best profile for a suite whose descriptor names a canvas size, falling
     * back to QVGA — the most common target for late J2ME games.
     */
    /**
     * The screen a suite gets.
     *
     * <p>One screen, with one exception that is not a choice: a suite that
     * declares itself to be a landscape game is given the turned screen. That
     * is the game stating how it is held, not the player picking hardware.</p>
     */
    public static DeviceProfile suggestFor(MidletSuiteInfo info) {
        String declared = info.attributes().get("Nokia-MIDlet-Original-Display-Size");
        if (declared == null) {
            declared = info.attributes().get("MIDlet-Screen-Size");
        }
        if (declared != null) {
            String[] parts = declared.replace('x', ',').split(",");
            if (parts.length == 2) {
                try {
                    int width = Integer.parseInt(parts[0].trim());
                    int height = Integer.parseInt(parts[1].trim());
                    return width > height ? QVGA_LANDSCAPE : QVGA_240x320;
                } catch (NumberFormatException e) {
                    return QVGA_240x320;
                }
            }
        }
        return QVGA_240x320;
    }

    @Override
    public String toString() {
        return name + " (" + resolution() + ")";
    }
}
