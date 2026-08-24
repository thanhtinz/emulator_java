package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A simulated handset: screen size, keypad layout and the capabilities a MIDlet
 * can ask about.
 *
 * <p>Games written for a 128x128 Nokia lay their HUD out for that screen and
 * will look wrong stretched to a modern display, so the profile is chosen per
 * game at import time and stored with it.</p>
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

    public static final DeviceProfile S40_128 =
            new DeviceProfile("s40-128x128", "Nokia Series 40", 128, 128, KEYPAD_NOKIA, 16, false);
    public static final DeviceProfile S40_128x160 =
            new DeviceProfile("s40-128x160", "Nokia 128x160", 128, 160, KEYPAD_NOKIA, 16, false);
    public static final DeviceProfile S60_176x208 =
            new DeviceProfile("s60-176x208", "Nokia Series 60", 176, 208, KEYPAD_NOKIA, 16, false);
    public static final DeviceProfile SE_176x220 =
            new DeviceProfile("se-176x220", "Sony Ericsson 176x220", 176, 220,
                    KEYPAD_SONY_ERICSSON, 16, false);
    public static final DeviceProfile QVGA_240x320 =
            new DeviceProfile("qvga-240x320", "QVGA 240x320", 240, 320, KEYPAD_NOKIA, 24, false);
    public static final DeviceProfile QVGA_LANDSCAPE =
            new DeviceProfile("qvga-320x240", "QVGA landscape", 320, 240, KEYPAD_NOKIA, 24, false);
    public static final DeviceProfile TOUCH_240x400 =
            new DeviceProfile("touch-240x400", "Touch 240x400", 240, 400, KEYPAD_SAMSUNG, 24, true);

    private static final List<DeviceProfile> CATALOG = Collections.unmodifiableList(
            new ArrayList<DeviceProfile>(java.util.Arrays.asList(
                    S40_128, S40_128x160, S60_176x208, SE_176x220,
                    QVGA_240x320, QVGA_LANDSCAPE, TOUCH_240x400)));

    /** The profiles offered at import time, in the order they are shown. */
    public static List<DeviceProfile> catalog() {
        return CATALOG;
    }

    public static DeviceProfile byId(String id) {
        for (DeviceProfile profile : CATALOG) {
            if (profile.id().equals(id)) {
                return profile;
            }
        }
        return QVGA_240x320;
    }

    public static DeviceProfile custom(int width, int height) {
        return new DeviceProfile("custom-" + width + "x" + height, "Custom " + width + "x" + height,
                width, height, KEYPAD_NOKIA, 24, true);
    }

    /**
     * Best profile for a suite whose descriptor names a canvas size, falling
     * back to QVGA — the most common target for late J2ME games.
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
                    for (DeviceProfile profile : CATALOG) {
                        if (profile.width() == width && profile.height() == height) {
                            return profile;
                        }
                    }
                    return custom(width, height);
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
