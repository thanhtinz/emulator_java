package com.mobicore.tests;

import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.jar.AttributeSet;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProfileTest extends Test {

    @Override
    public String name() {
        return "Profiles + JSON";
    }

    @Override
    public void run() {
        json();
        devices();
        input();
        game();
    }

    private void json() {
        Map<String, Object> object = Json.object();
        object.put("name", "Sky Runner");
        object.put("width", Integer.valueOf(240));
        object.put("muted", Boolean.FALSE);
        object.put("tags", new ArrayList<Object>(java.util.Arrays.asList("action", "demo")));
        Map<String, Object> nested = Json.object();
        nested.put("escaped", "line\nbreak \"quoted\"");
        object.put("nested", nested);

        String text = Json.write(object);
        Map<String, Object> parsed = Json.readObject(text);
        eq("Sky Runner", Json.string(parsed, "name", ""), "strings round-trip");
        eq(240, Json.integer(parsed, "width", 0), "numbers round-trip");
        check(!Json.bool(parsed, "muted", true), "booleans round-trip");
        eq(2, Json.array(parsed, "tags").size(), "arrays round-trip");
        eq("line\nbreak \"quoted\"", Json.string(Json.child(parsed, "nested"), "escaped", ""),
                "escapes round-trip");
        eq(0, Json.readObject("not json at all").size(), "malformed input yields an empty object");
        eq(7, Json.integer(parsed, "missing", 7), "missing keys fall back");
    }

    private void devices() {
        eq(7, DeviceProfile.catalog().size(), "the catalog covers the documented resolutions");
        eq("240x320", DeviceProfile.QVGA_240x320.resolution(), "resolution is formatted");
        eq(DeviceProfile.ORIENTATION_LANDSCAPE, DeviceProfile.QVGA_LANDSCAPE.orientation(),
                "a wide profile is landscape");
        eq("Nokia", DeviceProfile.S40_128.keypadName(), "keypad layouts are named");
        eq(DeviceProfile.QVGA_240x320.id(), DeviceProfile.byId("qvga-240x320").id(), "lookup by id");
        eq(DeviceProfile.QVGA_240x320.id(), DeviceProfile.byId("nonsense").id(), "unknown ids fall back");

        DeviceProfile rotated = DeviceProfile.S60_176x208.rotated();
        eq(208, rotated.width(), "rotation swaps width");
        eq(176, rotated.height(), "rotation swaps height");

        DeviceProfile restored = DeviceProfile.fromJson(DeviceProfile.SE_176x220.toJson());
        eq(176, restored.width(), "device profiles round-trip through JSON");
        eq(DeviceProfile.KEYPAD_SONY_ERICSSON, restored.keypad(), "keypad survives the round trip");

        AttributeSet attributes = AttributeSet.parse("MIDlet-1: Demo,,demo.Main\n"
                + "Nokia-MIDlet-Original-Display-Size: 176,208\n");
        MidletSuiteInfo declared = MidletSuiteInfo.merge(attributes, null);
        eq("s60-176x208", DeviceProfile.suggestFor(declared).id(), "a declared screen size is honoured");

        MidletSuiteInfo silent = MidletSuiteInfo.merge(
                AttributeSet.parse("MIDlet-1: Demo,,demo.Main\n"), null);
        eq("qvga-240x320", DeviceProfile.suggestFor(silent).id(), "QVGA is the fallback");
    }

    private void input() {
        InputProfile nokia = InputProfile.nokia();
        eq(MidpContext.KEY_UP, nokia.keyCodeFor("up"), "the d-pad maps to Nokia codes");
        eq('5', nokia.keyCodeFor("num5"), "digits map to their ASCII codes");
        eq(MidpContext.KEY_SOFT_LEFT, nokia.keyCodeFor("softLeft"), "Nokia softkeys");
        eq(-6, InputProfile.sonyEricsson().keyCodeFor("softLeft"), "Sony Ericsson softkeys differ");
        eq(0, nokia.keyCodeFor("nonexistent"), "an unbound button reports zero");
        eq(22, InputProfile.BUTTONS.length, "every virtual button is declared");
        eq(MidpContext.KEY_SEND, nokia.keyCodeFor("send"), "the call key is mapped");
        eq(MidpContext.KEY_END, nokia.keyCodeFor("end"), "the end key is mapped");

        nokia.remap("fire", '5');
        eq('5', nokia.keyCodeFor("fire"), "remap takes effect");
        eq("Tùy chỉnh", nokia.presetName(), "remapping marks the profile custom");
        nokia.unbind("star");
        eq(0, nokia.keyCodeFor("star"), "unbind removes the mapping");

        nokia.setTurbo("fire", 80);
        eq(80, nokia.turboFor("fire"), "turbo interval is stored");
        nokia.setTurbo("fire", 0);
        eq(0, nokia.turboFor("fire"), "zero disables turbo");

        List<String> steps = new ArrayList<String>(java.util.Arrays.asList("up", "up", "fire"));
        nokia.addMacro(new InputProfile.Macro("Super jump", "num1", steps, 40));
        check(nokia.macroFor("num1") != null, "a macro is found by its trigger");
        eq(3, nokia.macroFor("num1").steps().size(), "macro steps are kept");
        eq(null, nokia.macroFor("num2"), "an unbound trigger has no macro");

        InputProfile restored = InputProfile.fromJson(nokia.toJson());
        eq('5', restored.keyCodeFor("fire"), "mappings survive JSON");
        eq(1, restored.macros().size(), "macros survive JSON");
        eq("Super jump", restored.macros().get(0).name(), "macro names survive JSON");
        nokia.removeMacro("Super jump");
        eq(0, nokia.macros().size(), "macros can be removed");
    }

    private void game() {
        MidletSuiteInfo info = MidletSuiteInfo.merge(
                AttributeSet.parse("MIDlet-Name: Demo\nMIDlet-Vendor: Test\nMIDlet-1: Demo,,demo.Main\n"),
                null);
        GameProfile profile = GameProfile.defaultsFor(info);
        eq("test.demo.1-0", profile.suiteId(), "the profile is keyed by suite id");
        eq(240, profile.device().width(), "defaults pick QVGA");
        eq("Fit", profile.scaleModeName(), "filling the screen is the default, as a handset did");
        check(profile.smoothing(), "smoothing is on by default so scaling does not look blocky");
        eq("Ask", profile.networkModeName(), "network access defaults to asking");

        profile.setVolume(150);
        eq(100, profile.volume(), "volume is clamped to 100");
        profile.setVolume(-4);
        eq(0, profile.volume(), "volume is clamped at zero");
        profile.setFrameLimit(999);
        eq(120, profile.frameLimit(), "the frame limit is capped");

        // Integer scaling must never crop: 240x320 into 500x700 is 2x.
        profile.setScaleMode(GameProfile.SCALE_INTEGER);
        int[] viewport = profile.viewport(500, 700);
        eq(480, viewport[2], "integer scale doubles the width");
        eq(640, viewport[3], "integer scale doubles the height");
        eq(10, viewport[0], "the result is centred horizontally");
        eq(30, viewport[1], "the result is centred vertically");

        profile.setScaleMode(GameProfile.SCALE_ORIGINAL);
        eq(240, profile.viewport(500, 700)[2], "original scale keeps the native size");
        profile.setScaleMode(GameProfile.SCALE_STRETCH);
        eq(500, profile.viewport(500, 700)[2], "stretch fills the widget");
        profile.setScaleMode(GameProfile.SCALE_FIT);
        int[] fit = profile.viewport(500, 700);
        eq(500, fit[2], "fit uses the full width");
        eq(667, fit[3], "fit keeps the aspect ratio");

        profile.setFavourite(true);
        profile.markPlayed(1234567L);
        eq(1, profile.playCount(), "playing increments the counter");

        profile.setSmoothing(false);
        GameProfile restored = GameProfile.fromJson(profile.toJson());
        check(!restored.smoothing(), "the smoothing choice survives JSON");
        check(restored.isFavourite(), "favourite survives JSON");
        eq(1234567L, restored.lastPlayed(), "the play timestamp survives JSON as a long");
        eq(GameProfile.SCALE_FIT, restored.scaleMode(), "the scale mode survives JSON");
    }
}
