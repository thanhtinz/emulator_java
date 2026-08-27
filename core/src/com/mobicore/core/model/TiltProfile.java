package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Steering a game by tilting the phone.
 *
 * <p>No J2ME handset could do this — the accelerometer arrived after these
 * games did — so it is not emulation, it is a way to play them. It suits the
 * ones it suits: a racing game steered with left and right, a maze that tips a
 * ball. It is off by default because for everything else it is a game that
 * moves when the bus does.</p>
 *
 * <p>What makes it usable is not the threshold but the <em>pair</em> of them.
 * With one, a phone held right at the edge sends press, release, press,
 * release many times a second, and the game reads that as a player hammering
 * the key. So a direction is taken at one angle and given back at a smaller
 * one, and the gap between the two is what keeps a hand that is nearly still
 * from being read as a hand that is shaking.</p>
 */
public final class TiltProfile {

    /** Which directions tilting is allowed to press. */
    public static final int AXES_BOTH = 0;
    public static final int AXES_LEFT_RIGHT = 1;
    public static final int AXES_UP_DOWN = 2;

    /** How far the phone must lean before a direction is taken, at 100%. */
    private static final float PRESS_AT = 0.35f;
    /** And how far back before it is given up, which is deliberately less. */
    private static final float RELEASE_AT = 0.22f;

    public static final int MIN_SENSITIVITY = 50;
    public static final int MAX_SENSITIVITY = 200;

    private boolean enabled;
    private int sensitivity = 100;
    private int axes = AXES_LEFT_RIGHT;
    private boolean inverted;

    /**
     * True while tilting does anything at all.
     *
     * <p>Off by default. A game that is not steered by tilting is a game that
     * moves when the bus does.</p>
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** How far the phone has to lean, as a percentage of the standard angle. */
    public int sensitivity() {
        return sensitivity;
    }

    public void setSensitivity(int percent) {
        this.sensitivity = percent < MIN_SENSITIVITY ? MIN_SENSITIVITY
                : (percent > MAX_SENSITIVITY ? MAX_SENSITIVITY : percent);
    }

    public int axes() {
        return axes;
    }

    public void setAxes(int axes) {
        this.axes = axes < AXES_BOTH || axes > AXES_UP_DOWN ? AXES_LEFT_RIGHT : axes;
    }

    public String axesName() {
        switch (axes) {
            case AXES_BOTH: return "Bốn hướng";
            case AXES_UP_DOWN: return "Chỉ lên xuống";
            default: return "Chỉ trái phải";
        }
    }

    /** True when leaning left should press right, which some games want. */
    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    /** The angle at which a direction is taken, given the chosen sensitivity. */
    public float pressAt() {
        // More sensitive means a smaller lean is enough, so the threshold
        // goes down as the number goes up.
        return PRESS_AT * 100f / sensitivity;
    }

    /** And the smaller angle at which it is given up again. */
    public float releaseAt() {
        return RELEASE_AT * 100f / sensitivity;
    }

    /**
     * Which directions a lean should be holding now.
     *
     * @param x how far the phone leans right, from -1 to 1
     * @param y how far the phone leans away from the player, from -1 to 1
     * @param held what is already held, so a direction is only given up at
     *     the smaller angle
     */
    public Set<String> directions(float x, float y, Set<String> held) {
        Set<String> out = new LinkedHashSet<String>();
        if (!enabled) {
            return out;
        }
        float leanX = inverted ? -x : x;
        float leanY = inverted ? -y : y;
        if (axes != AXES_UP_DOWN) {
            add(out, held, "left", -leanX);
            add(out, held, "right", leanX);
        }
        if (axes != AXES_LEFT_RIGHT) {
            add(out, held, "up", -leanY);
            add(out, held, "down", leanY);
        }
        return out;
    }

    /**
     * Keeps one direction if it is held and still leaning, takes it if the
     * lean has gone past the larger angle.
     */
    private void add(Set<String> out, Set<String> held, String button, float lean) {
        boolean already = held != null && held.contains(button);
        if (lean >= (already ? releaseAt() : pressAt())) {
            out.add(button);
        }
    }

    // ------------------------------------------------------------------ JSON

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("enabled", Boolean.valueOf(enabled));
        json.put("sensitivity", Integer.valueOf(sensitivity));
        json.put("axes", Integer.valueOf(axes));
        json.put("inverted", Boolean.valueOf(inverted));
        return json;
    }

    public static TiltProfile fromJson(Map<String, Object> json) {
        TiltProfile profile = new TiltProfile();
        if (json == null) {
            return profile;
        }
        profile.setEnabled(Json.bool(json, "enabled", false));
        profile.setSensitivity(Json.integer(json, "sensitivity", 100));
        profile.setAxes(Json.integer(json, "axes", AXES_LEFT_RIGHT));
        profile.setInverted(Json.bool(json, "inverted", false));
        return profile;
    }

    /** The directions tilting can press, for a settings screen to name. */
    public static List<String> axesNames() {
        List<String> names = new ArrayList<String>();
        names.add("Bốn hướng");
        names.add("Chỉ trái phải");
        names.add("Chỉ lên xuống");
        return names;
    }
}
