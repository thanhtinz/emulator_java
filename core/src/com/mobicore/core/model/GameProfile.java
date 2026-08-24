package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

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

    public static final int NETWORK_BLOCKED = 0;
    public static final int NETWORK_ASK = 1;
    public static final int NETWORK_ALLOWED = 2;

    private final String suiteId;
    private DeviceProfile device;
    private InputProfile input;
    private int scaleMode = SCALE_INTEGER;
    private int orientation = DeviceProfile.ORIENTATION_PORTRAIT;
    private int frameLimit = 30;
    private int volume = 70;
    private boolean muted;
    private boolean showFps;
    private boolean keepAspect = true;
    private int networkMode = NETWORK_ASK;
    private String skin = "classic";
    private boolean favourite;
    private long lastPlayed;
    private int playCount;

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
        this.device = device;
    }

    public InputProfile input() {
        return input;
    }

    public void setInput(InputProfile input) {
        this.input = input;
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
        json.put("frameLimit", Integer.valueOf(frameLimit));
        json.put("volume", Integer.valueOf(volume));
        json.put("muted", Boolean.valueOf(muted));
        json.put("showFps", Boolean.valueOf(showFps));
        json.put("keepAspect", Boolean.valueOf(keepAspect));
        json.put("networkMode", Integer.valueOf(networkMode));
        json.put("skin", skin);
        json.put("favourite", Boolean.valueOf(favourite));
        json.put("lastPlayed", Long.valueOf(lastPlayed));
        json.put("playCount", Integer.valueOf(playCount));
        return json;
    }

    public static GameProfile fromJson(Map<String, Object> json) {
        GameProfile profile = new GameProfile(
                Json.string(json, "suiteId", "unknown"),
                DeviceProfile.fromJson(Json.child(json, "device")),
                InputProfile.fromJson(Json.child(json, "input")));
        profile.scaleMode = Json.integer(json, "scaleMode", SCALE_INTEGER);
        profile.orientation = Json.integer(json, "orientation", DeviceProfile.ORIENTATION_PORTRAIT);
        profile.frameLimit = Json.integer(json, "frameLimit", 30);
        profile.volume = Json.integer(json, "volume", 70);
        profile.muted = Json.bool(json, "muted", false);
        profile.showFps = Json.bool(json, "showFps", false);
        profile.keepAspect = Json.bool(json, "keepAspect", true);
        profile.networkMode = Json.integer(json, "networkMode", NETWORK_ASK);
        profile.skin = Json.string(json, "skin", "classic");
        profile.favourite = Json.bool(json, "favourite", false);
        profile.lastPlayed = Json.longValue(json, "lastPlayed", 0L);
        profile.playCount = Json.integer(json, "playCount", 0);
        return profile;
    }
}
