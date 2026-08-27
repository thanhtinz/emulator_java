package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a real controller's buttons do, when one is plugged in or paired.
 *
 * <p>These games were made for a keypad a thumb could hammer, and playing them
 * on glass is the one thing an emulator cannot fix: there is no edge to feel
 * for, so a player looks down instead of at the game. A controller gives the
 * edges back, and most people who still play these have one — a phone
 * controller, a console pad over Bluetooth, or a keyboard on a tablet.</p>
 *
 * <p>The names here are the emulator's own, not any platform's. Android calls
 * a face button {@code KEYCODE_BUTTON_A}, iOS calls it {@code buttonA} and a
 * keyboard calls it {@code Space}; each front end translates its own event
 * into one of these names, and everything after that — which emulator button
 * it presses, what the player remapped it to — happens once, here, for all of
 * them.</p>
 */
public final class GamepadProfile {

    /**
     * The controls a pad is expected to have.
     *
     * <p>Deliberately the set every pad really carries. A layout with more
     * than this — paddles, a second stick's click — is a layout most pads
     * cannot fill, and an unfillable row in a settings screen is a row that
     * teaches the player nothing.</p>
     */
    public static final String[] PADS = {
            "padUp", "padDown", "padLeft", "padRight",
            "padA", "padB", "padX", "padY",
            "padL1", "padR1", "padL2", "padR2",
            "padStart", "padSelect",
    };

    /** What each control is called on the screen that maps them. */
    public static String padName(String pad) {
        if ("padUp".equals(pad)) return "Lên";
        if ("padDown".equals(pad)) return "Xuống";
        if ("padLeft".equals(pad)) return "Trái";
        if ("padRight".equals(pad)) return "Phải";
        if ("padA".equals(pad)) return "A";
        if ("padB".equals(pad)) return "B";
        if ("padX".equals(pad)) return "X";
        if ("padY".equals(pad)) return "Y";
        if ("padL1".equals(pad)) return "L1";
        if ("padR1".equals(pad)) return "R1";
        if ("padL2".equals(pad)) return "L2";
        if ("padR2".equals(pad)) return "R2";
        if ("padStart".equals(pad)) return "Start";
        if ("padSelect".equals(pad)) return "Select";
        return pad;
    }

    private final Map<String, String> mappings = new LinkedHashMap<String, String>();
    private boolean enabled = true;

    private GamepadProfile() {
    }

    /**
     * The arrangement a J2ME game expects, on a modern pad.
     *
     * <p>The stick and the d-pad both drive the four directions because a
     * game of this era has four directions and nothing else; A is fire
     * because that is the button under the thumb at rest; and the two
     * shoulders are the softkeys, which is where a player already reaches for
     * "menu" and "back". The numbers stay on the touchscreen: a pad has
     * nowhere to put twelve of them, and a game that needs the numbers needs
     * them labelled.</p>
     */
    public static GamepadProfile defaults() {
        GamepadProfile profile = new GamepadProfile();
        profile.mappings.put("padUp", "up");
        profile.mappings.put("padDown", "down");
        profile.mappings.put("padLeft", "left");
        profile.mappings.put("padRight", "right");
        profile.mappings.put("padA", "fire");
        // B is the other thing a game reads constantly — a second action, a
        // jump — and most of these games map it onto key 5, which is fire's
        // neighbour on a handset.
        profile.mappings.put("padB", "num5");
        profile.mappings.put("padX", "num1");
        profile.mappings.put("padY", "num3");
        profile.mappings.put("padL1", "softLeft");
        profile.mappings.put("padR1", "softRight");
        profile.mappings.put("padStart", "softLeft");
        profile.mappings.put("padSelect", "softRight");
        return profile;
    }

    /** True while controller input is delivered to the game at all. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The emulator button one control presses, or empty when it does nothing.
     *
     * <p>Empty while the pad is switched off, because that is what "off"
     * means at the moment a button is pressed.</p>
     *
     * @param pad one of {@link #PADS}
     */
    public String buttonFor(String pad) {
        return enabled ? mapping(pad) : "";
    }

    /**
     * What one control is mapped to, whether or not the pad is switched on.
     *
     * <p>What the screen that maps them shows: switching the pad off should
     * not read as every button having been unbound one at a time.</p>
     */
    public String mapping(String pad) {
        String button = mappings.get(pad);
        return button == null ? "" : button;
    }

    /** Points one control at an emulator button, or at nothing to unbind it. */
    public void map(String pad, String button) {
        if (pad == null || pad.length() == 0) {
            return;
        }
        if (button == null || button.length() == 0) {
            mappings.remove(pad);
            return;
        }
        mappings.put(pad, button);
    }

    /** True when this is no longer the arrangement {@link #defaults} makes. */
    public boolean isCustom() {
        GamepadProfile standard = defaults();
        if (standard.mappings.size() != mappings.size()) {
            return true;
        }
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (!entry.getValue().equals(standard.mappings.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    /** Which controls are bound to something, in pad order. */
    public List<String> boundPads() {
        List<String> bound = new ArrayList<String>();
        for (int i = 0; i < PADS.length; i++) {
            if (mappings.containsKey(PADS[i])) {
                bound.add(PADS[i]);
            }
        }
        return bound;
    }

    // ------------------------------------------------------------------ JSON

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("enabled", Boolean.valueOf(enabled));
        Map<String, Object> keys = Json.object();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            keys.put(entry.getKey(), entry.getValue());
        }
        json.put("pads", keys);
        return json;
    }

    public static GamepadProfile fromJson(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return defaults();
        }
        GamepadProfile profile = new GamepadProfile();
        profile.enabled = Json.bool(json, "enabled", true);
        Map<String, Object> pads = Json.child(json, "pads");
        for (Map.Entry<String, Object> entry : pads.entrySet()) {
            if (entry.getValue() instanceof String) {
                profile.map(entry.getKey(), (String) entry.getValue());
            }
        }
        // A stored profile with nothing in it is a profile from before this
        // existed, not a player who unbound every button one at a time.
        return profile.mappings.isEmpty() && !json.containsKey("pads")
                ? defaults() : profile;
    }
}
