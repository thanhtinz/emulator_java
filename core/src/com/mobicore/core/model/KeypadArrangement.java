package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a player has moved the keys of the virtual keypad, and how big.
 *
 * <p>The keypad is laid out the way a handset was, because that is what the
 * thumbs of anyone who played these games are trained on. But no two hands are
 * the same, and a phone is much bigger than a handset was: the fire key that
 * sat right under one player's thumb is a stretch for the next. J2ME Loader
 * lets its keys be dragged for exactly this reason, and this is that.</p>
 *
 * <p>Held as an <em>offset from where the standard layout puts each key</em>,
 * in units of one key, rather than as an absolute position. Three things fall
 * out of that and all three matter: the standard layout stays the standard
 * layout, so nothing has to be re-measured when it changes; the same
 * arrangement works upright and sideways, where the keypad is a different
 * shape and size; and "put it back" is a set of offsets going to zero rather
 * than a layout being rebuilt from a guess.</p>
 */
public final class KeypadArrangement {

    /** How far a key may be dragged, in keys, in any direction. */
    public static final float MAX_OFFSET = 6.0f;
    /** How small and how large a key may be made, in percent. */
    public static final int MIN_SCALE = 60;
    public static final int MAX_SCALE = 160;

    private final Map<String, float[]> offsets = new LinkedHashMap<String, float[]>();
    private int scale = 100;

    /** True when anything has been moved or resized. */
    public boolean isCustom() {
        return scale != 100 || !offsets.isEmpty();
    }

    /** How big the keys are drawn, as a percentage of the standard size. */
    public int scale() {
        return scale;
    }

    public void setScale(int percent) {
        this.scale = percent < MIN_SCALE ? MIN_SCALE : (percent > MAX_SCALE ? MAX_SCALE : percent);
    }

    /**
     * A key's size, given what the standard layout would have made it.
     *
     * <p>Asked here rather than worked out by each front end, so the phone and
     * the preview cannot size the same keypad two different ways.</p>
     */
    public int sizeOf(int standard) {
        int sized = standard * scale / 100;
        // Never nothing: a key scaled to zero is a key that cannot be pressed
        // and cannot be dragged back.
        return sized < 8 ? 8 : sized;
    }

    /** How far right this key has been dragged, in keys. */
    public float offsetX(String button) {
        float[] offset = offsets.get(button);
        return offset == null ? 0f : offset[0];
    }

    /** How far down this key has been dragged, in keys. */
    public float offsetY(String button) {
        float[] offset = offsets.get(button);
        return offset == null ? 0f : offset[1];
    }

    /** True when this key in particular has been moved. */
    public boolean isMoved(String button) {
        return offsets.containsKey(button);
    }

    /**
     * Moves one key to an offset from where the standard layout puts it.
     *
     * <p>Clamped rather than refused: a drag that runs off the screen should
     * leave the key at the edge, not leave it where it was and look broken.</p>
     */
    public void move(String button, float x, float y) {
        if (button == null || button.length() == 0) {
            return;
        }
        float clampedX = clamp(x);
        float clampedY = clamp(y);
        if (clampedX == 0f && clampedY == 0f) {
            // Back where it started is not a custom position; forgetting it
            // keeps "has anything been moved" honest.
            offsets.remove(button);
            return;
        }
        offsets.put(button, new float[]{clampedX, clampedY});
    }

    /** Puts one key back where the standard layout has it. */
    public void resetKey(String button) {
        offsets.remove(button);
    }

    /** Puts every key back, and the size with them. */
    public void reset() {
        offsets.clear();
        scale = 100;
    }

    /** Which keys have been moved, in the order they were. */
    public List<String> movedKeys() {
        return new ArrayList<String>(offsets.keySet());
    }

    private static float clamp(float value) {
        if (value != value) {
            // Not a number: a front end dividing by a zero-height keypad.
            return 0f;
        }
        return value < -MAX_OFFSET ? -MAX_OFFSET : (value > MAX_OFFSET ? MAX_OFFSET : value);
    }

    // ------------------------------------------------------------------ JSON

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("scale", Integer.valueOf(scale));
        Map<String, Object> keys = Json.object();
        for (Map.Entry<String, float[]> entry : offsets.entrySet()) {
            Map<String, Object> place = Json.object();
            // Written as thousandths of a key: JSON numbers here are read back
            // as integers, and a key dragged half a key across should still be
            // half a key across after a restart.
            place.put("x", Integer.valueOf(Math.round(entry.getValue()[0] * 1000)));
            place.put("y", Integer.valueOf(Math.round(entry.getValue()[1] * 1000)));
            keys.put(entry.getKey(), place);
        }
        json.put("keys", keys);
        return json;
    }

    public static KeypadArrangement fromJson(Map<String, Object> json) {
        KeypadArrangement arrangement = new KeypadArrangement();
        if (json == null) {
            return arrangement;
        }
        arrangement.setScale(Json.integer(json, "scale", 100));
        Map<String, Object> keys = Json.child(json, "keys");
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> place = (Map<String, Object>) entry.getValue();
            arrangement.move(entry.getKey(),
                    Json.integer(place, "x", 0) / 1000f,
                    Json.integer(place, "y", 0) / 1000f);
        }
        return arrangement;
    }
}
