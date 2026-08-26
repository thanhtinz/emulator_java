package com.mobicore.core.model;

import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Key mapping for one game: which emulator button produces which J2ME key
 * code, plus turbo and macros.
 *
 * <p>Handset makers disagreed about softkey codes, so a game written for a
 * Sony Ericsson may read {@code -6}/{@code -7} where a Nokia game reads
 * {@code -21}/{@code -22}. Remapping per game is the only way to make both
 * play correctly on the same virtual keypad.</p>
 */
public final class InputProfile {

    /** Buttons the virtual phone offers, in the order the keypad shows them. */
    public static final String[] BUTTONS = {
            "up", "down", "left", "right", "fire",
            "softLeft", "softRight",
            "gameLeft", "gameRight",
            "num0", "num1", "num2", "num3", "num4",
            "num5", "num6", "num7", "num8", "num9",
            "star", "hash", "clear",
    };

    /**
     * The corner buttons, and the two directions each one means.
     *
     * <p>Not in {@link #BUTTONS} because they are not keys: MIDP has no
     * diagonal key code and no handset had a diagonal key. A corner on a
     * handset's pad was two directions pressed at once, so a corner button
     * sends both — which is also what any game reading
     * {@code getKeyStates} already understands.</p>
     */
    private static final String[][] DIAGONALS = {
            {"upLeft", "up", "left"},
            {"upRight", "up", "right"},
            {"downLeft", "down", "left"},
            {"downRight", "down", "right"},
    };

    /** @return the two buttons a corner stands for, or null if it is not one */
    public static String[] diagonalOf(String button) {
        for (int i = 0; i < DIAGONALS.length; i++) {
            if (DIAGONALS[i][0].equals(button)) {
                return new String[]{DIAGONALS[i][1], DIAGONALS[i][2]};
            }
        }
        return null;
    }

    private final Map<String, Integer> mappings = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> turbo = new LinkedHashMap<String, Integer>();
    private final List<Macro> macros = new ArrayList<Macro>();
    private String presetName = "Nokia";

    /** A named sequence of button presses replayed on one trigger. */
    public static final class Macro {

        private final String name;
        private final String trigger;
        private final List<String> steps;
        private final int stepDelayMs;

        public Macro(String name, String trigger, List<String> steps, int stepDelayMs) {
            this.name = name;
            this.trigger = trigger;
            this.steps = new ArrayList<String>(steps);
            this.stepDelayMs = stepDelayMs;
        }

        public String name() {
            return name;
        }

        public String trigger() {
            return trigger;
        }

        public List<String> steps() {
            return new ArrayList<String>(steps);
        }

        public int stepDelayMs() {
            return stepDelayMs;
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = Json.object();
            json.put("name", name);
            json.put("trigger", trigger);
            json.put("steps", new ArrayList<Object>(steps));
            json.put("stepDelayMs", Integer.valueOf(stepDelayMs));
            return json;
        }

        static Macro fromJson(Map<String, Object> json) {
            List<String> steps = new ArrayList<String>();
            for (Object step : Json.array(json, "steps")) {
                steps.add(String.valueOf(step));
            }
            return new Macro(Json.string(json, "name", "Macro"),
                    Json.string(json, "trigger", "num0"), steps,
                    Json.integer(json, "stepDelayMs", 60));
        }
    }

    private InputProfile() {
    }

    // ------------------------------------------------------------- presets

    public static InputProfile nokia() {
        InputProfile profile = base("Nokia");
        profile.mappings.put("softLeft", Integer.valueOf(MidpContext.KEY_SOFT_LEFT));
        profile.mappings.put("softRight", Integer.valueOf(MidpContext.KEY_SOFT_RIGHT));
        return profile;
    }

    public static InputProfile sonyEricsson() {
        InputProfile profile = base("Sony Ericsson");
        // Sony Ericsson handsets report the softkeys one slot further down.
        profile.mappings.put("softLeft", Integer.valueOf(-6));
        profile.mappings.put("softRight", Integer.valueOf(-7));
        profile.mappings.put("clear", Integer.valueOf(-8));
        return profile;
    }

    public static InputProfile samsung() {
        InputProfile profile = base("Samsung");
        profile.mappings.put("softLeft", Integer.valueOf(-6));
        profile.mappings.put("softRight", Integer.valueOf(-7));
        profile.mappings.put("fire", Integer.valueOf(-5));
        return profile;
    }

    public static InputProfile forKeypad(int keypad) {
        switch (keypad) {
            case DeviceProfile.KEYPAD_SONY_ERICSSON: return sonyEricsson();
            case DeviceProfile.KEYPAD_SAMSUNG: return samsung();
            default: return nokia();
        }
    }

    private static InputProfile base(String presetName) {
        InputProfile profile = new InputProfile();
        profile.presetName = presetName;
        profile.mappings.put("up", Integer.valueOf(MidpContext.KEY_UP));
        profile.mappings.put("down", Integer.valueOf(MidpContext.KEY_DOWN));
        profile.mappings.put("left", Integer.valueOf(MidpContext.KEY_LEFT));
        profile.mappings.put("right", Integer.valueOf(MidpContext.KEY_RIGHT));
        profile.mappings.put("fire", Integer.valueOf(MidpContext.KEY_FIRE));
        profile.mappings.put("softLeft", Integer.valueOf(MidpContext.KEY_SOFT_LEFT));
        profile.mappings.put("softRight", Integer.valueOf(MidpContext.KEY_SOFT_RIGHT));
        for (int digit = 0; digit <= 9; digit++) {
            profile.mappings.put("num" + digit, Integer.valueOf('0' + digit));
        }
        profile.mappings.put("star", Integer.valueOf('*'));
        profile.mappings.put("hash", Integer.valueOf('#'));
        profile.mappings.put("clear", Integer.valueOf(MidpContext.KEY_CLEAR));
        // L and R: the two game keys MIDP names GAME_A and GAME_B. A handset
        // had no shoulder buttons — it had a keypad whose 7 and 9 the runtime
        // reported as those actions — so that is what these send. A game
        // reading getGameAction sees GAME_A and GAME_B; one reading the raw
        // key code sees the digit it was written against.
        profile.mappings.put("gameLeft", Integer.valueOf(MidpContext.keyCode(MidpContext.ACTION_GAME_A)));
        profile.mappings.put("gameRight", Integer.valueOf(MidpContext.keyCode(MidpContext.ACTION_GAME_B)));
        // The call and end keys are deliberately absent: the handset had them
        // because it was a phone, not because games read them, and a button
        // nobody presses is a button in the way of the ones they do.
        return profile;
    }

    // -------------------------------------------------------------- access

    public String presetName() {
        return presetName;
    }

    public void setPresetName(String presetName) {
        this.presetName = presetName;
    }

    /** J2ME key code for a virtual button, or 0 when the button is unbound. */
    public int keyCodeFor(String button) {
        Integer code = mappings.get(button);
        return code == null ? 0 : code.intValue();
    }

    public void remap(String button, int keyCode) {
        mappings.put(button, Integer.valueOf(keyCode));
        presetName = "Tùy chỉnh";
    }

    public void unbind(String button) {
        mappings.remove(button);
        presetName = "Tùy chỉnh";
    }

    public Map<String, Integer> mappings() {
        return new LinkedHashMap<String, Integer>(mappings);
    }

    /** Auto-repeat interval in milliseconds, or 0 when turbo is off. */
    public int turboFor(String button) {
        Integer interval = turbo.get(button);
        return interval == null ? 0 : interval.intValue();
    }

    public void setTurbo(String button, int intervalMs) {
        if (intervalMs <= 0) {
            turbo.remove(button);
        } else {
            turbo.put(button, Integer.valueOf(intervalMs));
        }
    }

    public List<Macro> macros() {
        return new ArrayList<Macro>(macros);
    }

    public void addMacro(Macro macro) {
        macros.add(macro);
    }

    public void removeMacro(String name) {
        for (int i = macros.size() - 1; i >= 0; i--) {
            if (macros.get(i).name().equals(name)) {
                macros.remove(i);
            }
        }
    }

    public Macro macroFor(String button) {
        for (Macro macro : macros) {
            if (macro.trigger().equals(button)) {
                return macro;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- codec

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("preset", presetName);
        Map<String, Object> keys = Json.object();
        for (Map.Entry<String, Integer> entry : mappings.entrySet()) {
            keys.put(entry.getKey(), entry.getValue());
        }
        json.put("mappings", keys);
        if (!turbo.isEmpty()) {
            Map<String, Object> repeat = Json.object();
            for (Map.Entry<String, Integer> entry : turbo.entrySet()) {
                repeat.put(entry.getKey(), entry.getValue());
            }
            json.put("turbo", repeat);
        }
        if (!macros.isEmpty()) {
            List<Object> list = new ArrayList<Object>();
            for (Macro macro : macros) {
                list.add(macro.toJson());
            }
            json.put("macros", list);
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    public static InputProfile fromJson(Map<String, Object> json) {
        InputProfile profile = nokia();
        profile.presetName = Json.string(json, "preset", "Nokia");
        Map<String, Object> keys = Json.child(json, "mappings");
        if (!keys.isEmpty()) {
            profile.mappings.clear();
            for (Map.Entry<String, Object> entry : keys.entrySet()) {
                profile.mappings.put(entry.getKey(),
                        Integer.valueOf(Json.integer(keys, entry.getKey(), 0)));
            }
        }
        Map<String, Object> repeat = Json.child(json, "turbo");
        for (Map.Entry<String, Object> entry : repeat.entrySet()) {
            profile.turbo.put(entry.getKey(), Integer.valueOf(Json.integer(repeat, entry.getKey(), 0)));
        }
        for (Object item : Json.array(json, "macros")) {
            if (item instanceof Map) {
                profile.macros.add(Macro.fromJson((Map<String, Object>) item));
            }
        }
        return profile;
    }
}
