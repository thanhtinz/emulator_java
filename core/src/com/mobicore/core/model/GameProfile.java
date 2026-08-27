package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything the user can configure for one game.
 *
 * <p>Kept per game rather than globally: a 128x128 puzzle wants integer
 * scaling and no frame cap, while a 240x320 action game wants the screen
 * filled and 30 fps. One global setting cannot serve both.</p>
 */
public final class GameProfile {

    public static final int SCALE_FIT = 0;
    public static final int SCALE_INTEGER = 1;
    public static final int SCALE_STRETCH = 2;
    public static final int SCALE_ORIGINAL = 3;

    /**
     * Which keys the virtual keypad shows.
     *
     * <p>Straight from J2ME Loader, whose keypad can be switched between a
     * full phone layout, numbers with arrows, either one alone, or hidden
     * altogether. It is not a cosmetic choice: a game that only reads the pad
     * gains the whole bottom of the screen by dropping the numbers, and a
     * touch game wants the keypad out of the way entirely.</p>
     */
    public static final int KEYPAD_FULL = 0;
    public static final int KEYPAD_ARROWS = 1;
    public static final int KEYPAD_NUMBERS = 2;
    public static final int KEYPAD_HIDDEN = 3;

    /**
     * The shape of a virtual key.
     *
     * <p>J2ME Loader offers the same three, and the reason is not decoration:
     * a rounded key and a round key have different edges to aim at, and on a
     * small screen the thumb finds one shape faster than another. Which one
     * is a matter of the hand holding the phone, so it is asked rather than
     * decided.</p>
     */
    public static final int KEY_SHAPE_ROUNDED = 0;
    public static final int KEY_SHAPE_RECT = 1;
    public static final int KEY_SHAPE_ROUND = 2;

    public static final int NETWORK_BLOCKED = 0;
    public static final int NETWORK_ASK = 1;
    public static final int NETWORK_ALLOWED = 2;

    private final String suiteId;
    private DeviceProfile device;
    private InputProfile input;
    private int scaleMode = SCALE_FIT;
    private int orientation = DeviceProfile.ORIENTATION_PORTRAIT;
    private int keypadLayout = KEYPAD_FULL;
    /**
     * How solid the virtual keypad is drawn, in percent.
     *
     * <p>The keypad sits over nothing on an upright screen, but sideways it
     * sits over the game itself. A keypad that can be seen through is the
     * difference between playing a wide game and playing the top half of
     * one.</p>
     */
    private int keypadOpacity = 100;
    private int keypadShape = KEY_SHAPE_ROUNDED;
    /**
     * Seconds of not being touched before the keypad fades back; 0 leaves it.
     *
     * <p>It fades rather than disappears. A keypad that vanishes leaves the
     * player tapping at a screen with nothing on it to find, so what this
     * does is drop it to a ghost of itself — out of the way of the game,
     * still where the thumb left it, and back to full the moment it is
     * touched.</p>
     */
    private int keypadFadeDelay;
    /** Where the player has dragged the keys, and how big they are drawn. */
    private KeypadArrangement keypadArrangement = new KeypadArrangement();
    /** What a real controller's buttons do, when one is connected. */
    private GamepadProfile gamepad = GamepadProfile.defaults();
    /** Whether tilting the phone steers, and how far it has to lean. */
    private TiltProfile tilt = new TiltProfile();
    /**
     * Which MIDlet inside the suite to open, or empty for the first.
     *
     * <p>A JAR often holds more than one: the game, a help screen, a settings
     * screen, sometimes a second game. Remembering the one that was played
     * means the play button reopens what the player thinks of as the game
     * rather than whatever the manifest happened to list first.</p>
     */
    private String midletClass = "";
    private int frameLimit = 30;
    private int volume = 70;
    private boolean muted;
    private boolean showFps;
    /**
     * Whether the phone may buzz for this game.
     *
     * <p>On by default: the buzz was part of the game. Off is a real choice
     * — a game that vibrates on every hit is a game that cannot be played
     * quietly next to someone.</p>
     */
    private boolean vibration = true;
    private boolean keepAspect = true;
    /**
     * Smooth the emulated screen when it is scaled up.
     *
     * <p>On by default, which is the opposite of what "pixel perfect" instinct
     * suggests. A handset packed 240x320 into about two inches, so its pixels
     * were far too small to pick out; reproducing them as visible blocks on a
     * modern display looks markedly more pixelated than the hardware being
     * emulated ever did. The setting stays available for anyone who wants the
     * blocky look deliberately.</p>
     */
    private boolean smoothing = true;
    private int networkMode = NETWORK_ASK;
    private String skin = "classic";
    /**
     * True while the settings are the ones {@link AutoSetup} worked out.
     *
     * <p>Cleared the moment the user changes anything that was detected, so
     * the interface can stop claiming a value was measured when it was
     * chosen. Nothing else depends on it — a hand-set profile is as valid as
     * a detected one.</p>
     */
    private boolean auto;
    /** What the pre-flight scan concluded; see {@link Compatibility}. */
    private int compatibility = Compatibility.LEVEL_FULL;
    private List<String> setupNotes = new ArrayList<String>();
    private boolean favourite;
    private long lastPlayed;
    private int playCount;
    /**
     * Total time spent in this game, in milliseconds.
     *
     * <p>The library already knew when a game was last opened, which answers
     * "what was I playing" and nothing else. How long it has held someone is
     * the thing worth knowing about a collection of eighty: it separates the
     * four games that were actually played from the seventy-six that were
     * opened once.</p>
     */
    private long playedMs;

    public GameProfile(String suiteId, DeviceProfile device, InputProfile input) {
        this.suiteId = suiteId;
        this.device = device;
        this.input = input;
    }

    /** Sensible defaults for a freshly imported suite. */
    public static GameProfile defaultsFor(MidletSuiteInfo info) {
        DeviceProfile device = DeviceProfile.suggestFor(info);
        return new GameProfile(info.suiteId(), device, InputProfile.forKeypad(device.keypad()));
    }

    public String suiteId() {
        return suiteId;
    }

    public DeviceProfile device() {
        return device;
    }

    public void setDevice(DeviceProfile device) {
        if (!device.id().equals(this.device.id())) {
            auto = false;
        }
        this.device = device;
    }

    public InputProfile input() {
        return input;
    }

    public void setInput(InputProfile input) {
        auto = false;
        this.input = input;
    }

    /** True while every detected setting is still as detected. */
    public boolean isAuto() {
        return auto;
    }

    /** Why the emulator set the game up this way, one line per decision. */
    public List<String> setupNotes() {
        return setupNotes;
    }

    public int compatibility() {
        return compatibility;
    }

    public void setCompatibility(int level) {
        this.compatibility = level;
    }

    public void setAuto(boolean auto, List<String> notes) {
        this.auto = auto;
        this.setupNotes = notes == null ? new ArrayList<String>() : new ArrayList<String>(notes);
    }

    public int scaleMode() {
        return scaleMode;
    }

    public void setScaleMode(int scaleMode) {
        this.scaleMode = scaleMode;
    }

    public String scaleModeName() {
        switch (scaleMode) {
            case SCALE_FIT: return "Fit";
            case SCALE_STRETCH: return "Stretch";
            case SCALE_ORIGINAL: return "Original";
            default: return "Integer";
        }
    }

    public int orientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int keypadLayout() {
        return keypadLayout;
    }

    public void setKeypadLayout(int layout) {
        this.keypadLayout = layout < KEYPAD_FULL || layout > KEYPAD_HIDDEN ? KEYPAD_FULL : layout;
    }

    /** What the menu shows for the current layout. */
    public String keypadLayoutName() {
        switch (keypadLayout) {
            case KEYPAD_ARROWS: return "Chỉ phím hướng";
            case KEYPAD_NUMBERS: return "Chỉ phím số";
            case KEYPAD_HIDDEN: return "Ẩn bàn phím";
            default: return "Đầy đủ";
        }
    }

    /** True when the pad half of the keypad is drawn. */
    public boolean showsArrows() {
        return keypadLayout == KEYPAD_FULL || keypadLayout == KEYPAD_ARROWS;
    }

    /** True when the 3x4 grid is drawn. */
    public boolean showsNumbers() {
        return keypadLayout == KEYPAD_FULL || keypadLayout == KEYPAD_NUMBERS;
    }

    /** How solid the keypad is drawn, 20–100 percent. */
    public int keypadOpacity() {
        return keypadOpacity;
    }

    public void setKeypadOpacity(int percent) {
        // Below a fifth the keys stop being findable at all, which is not a
        // setting so much as a way to lose the keypad without meaning to.
        this.keypadOpacity = percent < 20 ? 20 : (percent > 100 ? 100 : percent);
    }

    public int keypadShape() {
        return keypadShape;
    }

    public void setKeypadShape(int shape) {
        this.keypadShape = shape < KEY_SHAPE_ROUNDED || shape > KEY_SHAPE_ROUND
                ? KEY_SHAPE_ROUNDED : shape;
    }

    public String keypadShapeName() {
        switch (keypadShape) {
            case KEY_SHAPE_RECT: return "Vuông";
            case KEY_SHAPE_ROUND: return "Tròn";
            default: return "Bo góc";
        }
    }

    /** Seconds of not being touched before the keypad fades; 0 means never. */
    public int keypadFadeDelay() {
        return keypadFadeDelay;
    }

    public void setKeypadFadeDelay(int seconds) {
        // A minute is already long enough that nobody sees it happen; past
        // that the setting is indistinguishable from off.
        this.keypadFadeDelay = seconds < 0 ? 0 : (seconds > 60 ? 60 : seconds);
    }

    public String keypadFadeDelayName() {
        return keypadFadeDelay == 0 ? "Luôn rõ" : ("Sau " + keypadFadeDelay + " giây");
    }

    /**
     * How solid the keypad should be drawn, given how long since it was last
     * touched.
     *
     * <p>One answer, in one place, so the phone and the preview cannot draw
     * the same keypad two different ways.</p>
     */
    public int keypadOpacityAfter(long idleMillis) {
        if (keypadFadeDelay == 0 || idleMillis < keypadFadeDelay * 1000L) {
            return keypadOpacity;
        }
        // A third of what it was, and never past the point of being findable.
        int faded = keypadOpacity / 3;
        return faded < 20 ? 20 : faded;
    }

    /**
     * Where the keys have been dragged to, and how big.
     *
     * <p>Handed out rather than copied: moving a key is a small, frequent
     * edit, and a setter taking a whole arrangement would mean rebuilding one
     * on every drag.</p>
     */
    public KeypadArrangement keypadArrangement() {
        return keypadArrangement;
    }

    /**
     * What a real controller's buttons do.
     *
     * <p>Handed out rather than copied, like the keypad arrangement: mapping
     * one button is a small edit and a setter taking a whole profile would
     * mean rebuilding one for each.</p>
     */
    public GamepadProfile gamepad() {
        return gamepad;
    }

    /**
     * Whether tilting the phone steers the game.
     *
     * <p>Handed out rather than copied, like the pad: changing the
     * sensitivity is a small edit and a setter taking a whole profile would
     * mean rebuilding one for each.</p>
     */
    public TiltProfile tilt() {
        return tilt;
    }

    /** Replaces the whole pad mapping, which is what "put it back" does. */
    public void setGamepad(GamepadProfile gamepad) {
        this.gamepad = gamepad == null ? GamepadProfile.defaults() : gamepad;
    }

    public String midletClass() {
        return midletClass;
    }

    public void setMidletClass(String midletClass) {
        this.midletClass = midletClass == null ? "" : midletClass;
    }

    public int frameLimit() {
        return frameLimit;
    }

    public void setFrameLimit(int frameLimit) {
        this.frameLimit = Math.max(0, Math.min(120, frameLimit));
    }

    public int volume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean vibration() {
        return vibration;
    }

    public void setVibration(boolean vibration) {
        this.vibration = vibration;
    }

    public boolean showFps() {
        return showFps;
    }

    public void setShowFps(boolean showFps) {
        this.showFps = showFps;
    }

    public boolean keepAspect() {
        return keepAspect;
    }

    public void setKeepAspect(boolean keepAspect) {
        this.keepAspect = keepAspect;
    }

    public boolean smoothing() {
        return smoothing;
    }

    public void setSmoothing(boolean smoothing) {
        this.smoothing = smoothing;
    }

    public String smoothingName() {
        return smoothing ? "Mượt" : "Sắc cạnh";
    }

    public int networkMode() {
        return networkMode;
    }

    public void setNetworkMode(int networkMode) {
        this.networkMode = networkMode;
    }

    public String networkModeName() {
        switch (networkMode) {
            case NETWORK_BLOCKED: return "Blocked";
            case NETWORK_ALLOWED: return "Allowed";
            default: return "Ask";
        }
    }

    public String skin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public long lastPlayed() {
        return lastPlayed;
    }

    public int playCount() {
        return playCount;
    }

    /** Records a play session; drives the Recently Played row on Home. */
    public void markPlayed(long timestamp) {
        this.lastPlayed = timestamp;
        this.playCount++;
    }

    public long playedMs() {
        return playedMs;
    }

    /**
     * Adds a session's length.
     *
     * <p>Measured on the wall clock rather than the game's own: at triple
     * speed the player still spent the minutes they spent, and a total that
     * shrank because someone used fast-forward would be measuring the wrong
     * thing.</p>
     */
    public void addPlayedMs(long millis) {
        if (millis > 0) {
            playedMs += millis;
        }
    }

    /** "3 giờ 12 phút", "12 phút", "chưa chơi" — never a bare number. */
    public static String playedName(long millis) {
        if (millis < 60_000L) {
            return millis <= 0 ? "chưa chơi" : "dưới một phút";
        }
        long minutes = millis / 60_000L;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours == 0) {
            return minutes + " phút";
        }
        return minutes == 0 ? hours + " giờ" : hours + " giờ " + minutes + " phút";
    }

    /**
     * Viewport for the emulated screen inside a widget of the given size,
     * as {x, y, width, height}.
     */
    public int[] viewport(int widgetWidth, int widgetHeight) {
        int sourceWidth = device.width();
        int sourceHeight = device.height();
        int width;
        int height;
        switch (scaleMode) {
            case SCALE_ORIGINAL:
                width = sourceWidth;
                height = sourceHeight;
                break;
            case SCALE_STRETCH:
                width = widgetWidth;
                height = widgetHeight;
                break;
            case SCALE_INTEGER: {
                int factor = Math.max(1, Math.min(widgetWidth / sourceWidth, widgetHeight / sourceHeight));
                width = sourceWidth * factor;
                height = sourceHeight * factor;
                break;
            }
            default: {
                if (keepAspect) {
                    double scale = Math.min((double) widgetWidth / sourceWidth,
                            (double) widgetHeight / sourceHeight);
                    width = (int) Math.round(sourceWidth * scale);
                    height = (int) Math.round(sourceHeight * scale);
                } else {
                    width = widgetWidth;
                    height = widgetHeight;
                }
                break;
            }
        }
        return new int[]{(widgetWidth - width) / 2, (widgetHeight - height) / 2, width, height};
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("suiteId", suiteId);
        json.put("device", device.toJson());
        json.put("input", input.toJson());
        json.put("scaleMode", Integer.valueOf(scaleMode));
        json.put("orientation", Integer.valueOf(orientation));
        json.put("keypadLayout", Integer.valueOf(keypadLayout));
        json.put("keypadOpacity", Integer.valueOf(keypadOpacity));
        json.put("keypadShape", Integer.valueOf(keypadShape));
        json.put("keypadFadeDelay", Integer.valueOf(keypadFadeDelay));
        json.put("keypadArrangement", keypadArrangement.toJson());
        json.put("gamepad", gamepad.toJson());
        json.put("tilt", tilt.toJson());
        json.put("midletClass", midletClass);
        json.put("frameLimit", Integer.valueOf(frameLimit));
        json.put("volume", Integer.valueOf(volume));
        json.put("muted", Boolean.valueOf(muted));
        json.put("showFps", Boolean.valueOf(showFps));
        json.put("vibration", Boolean.valueOf(vibration));
        json.put("keepAspect", Boolean.valueOf(keepAspect));
        json.put("smoothing", Boolean.valueOf(smoothing));
        json.put("networkMode", Integer.valueOf(networkMode));
        json.put("skin", skin);
        json.put("auto", Boolean.valueOf(auto));
        json.put("compatibility", Integer.valueOf(compatibility));
        json.put("setupNotes", new ArrayList<Object>(setupNotes));
        json.put("favourite", Boolean.valueOf(favourite));
        json.put("lastPlayed", Long.valueOf(lastPlayed));
        json.put("playCount", Integer.valueOf(playCount));
        json.put("playedMs", Long.valueOf(playedMs));
        json.put("playedName", playedName(playedMs));
        return json;
    }

    public static GameProfile fromJson(Map<String, Object> json) {
        GameProfile profile = new GameProfile(
                Json.string(json, "suiteId", "unknown"),
                DeviceProfile.fromJson(Json.child(json, "device")),
                InputProfile.fromJson(Json.child(json, "input")));
        profile.scaleMode = Json.integer(json, "scaleMode", SCALE_FIT);
        profile.orientation = Json.integer(json, "orientation", DeviceProfile.ORIENTATION_PORTRAIT);
        profile.keypadLayout = Json.integer(json, "keypadLayout", KEYPAD_FULL);
        profile.setKeypadOpacity(Json.integer(json, "keypadOpacity", 100));
        profile.setKeypadShape(Json.integer(json, "keypadShape", KEY_SHAPE_ROUNDED));
        profile.setKeypadFadeDelay(Json.integer(json, "keypadFadeDelay", 0));
        profile.keypadArrangement = KeypadArrangement.fromJson(
                Json.child(json, "keypadArrangement"));
        profile.gamepad = GamepadProfile.fromJson(Json.child(json, "gamepad"));
        profile.tilt = TiltProfile.fromJson(Json.child(json, "tilt"));
        profile.midletClass = Json.string(json, "midletClass", "");
        profile.frameLimit = Json.integer(json, "frameLimit", 30);
        profile.volume = Json.integer(json, "volume", 70);
        profile.muted = Json.bool(json, "muted", false);
        profile.showFps = Json.bool(json, "showFps", false);
        profile.vibration = Json.bool(json, "vibration", true);
        profile.keepAspect = Json.bool(json, "keepAspect", true);
        profile.smoothing = Json.bool(json, "smoothing", true);
        profile.networkMode = Json.integer(json, "networkMode", NETWORK_ASK);
        profile.skin = Json.string(json, "skin", "classic");
        profile.auto = Json.bool(json, "auto", false);
        profile.compatibility = Json.integer(json, "compatibility", Compatibility.LEVEL_FULL);
        List<Object> notes = Json.array(json, "setupNotes");
        for (int i = 0; i < notes.size(); i++) {
            profile.setupNotes.add(String.valueOf(notes.get(i)));
        }
        profile.favourite = Json.bool(json, "favourite", false);
        profile.lastPlayed = Json.longValue(json, "lastPlayed", 0L);
        profile.playCount = Json.integer(json, "playCount", 0);
        profile.playedMs = Json.longValue(json, "playedMs", 0L);
        return profile;
    }
}
