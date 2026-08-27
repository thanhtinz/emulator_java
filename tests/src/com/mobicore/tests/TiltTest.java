package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.TiltProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Steering a game by tilting the phone.
 *
 * <p>No J2ME handset could do this, so it is not emulation but a way to play.
 * What makes it usable is the pair of thresholds: with one, a phone held right
 * at the edge sends press, release, press, release many times a second, and
 * the game reads a player hammering the key.</p>
 */
public final class TiltTest extends Test {

    private final String fixtureDir;

    public TiltTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Nghiêng máy để lái";
    }

    @Override
    public void run() throws Exception {
        defaults();
        hysteresis();
        axesAndInvert();
        throughTheBridge();
    }

    // ------------------------------------------------------------- defaults

    private void defaults() {
        TiltProfile tilt = new TiltProfile();
        check(!tilt.isEnabled(),
                "tilting is off until it is asked for: everything else is a game "
                        + "that moves when the bus does");
        eq(0, tilt.directions(1f, 1f, none()).size(), "so leaning does nothing at all");
        eq("Chỉ trái phải", tilt.axesName(),
                "and when it is on it steers, which is what it suits");

        tilt.setEnabled(true);
        eq(1, tilt.directions(1f, 0f, none()).size(), "a full lean presses one direction");
        check(tilt.directions(1f, 0f, none()).contains("right"), "the one it is leaning into");
        eq(0, tilt.directions(0.1f, 0f, none()).size(), "a hand that is nearly still presses nothing");

        // More sensitive means a smaller lean is enough, so the angle a
        // direction is taken at goes down as the number goes up.
        float standard = tilt.pressAt();
        tilt.setSensitivity(200);
        check(tilt.pressAt() < standard, "turning it up means leaning less far");
        tilt.setSensitivity(50);
        check(tilt.pressAt() > standard, "and turning it down means leaning further");
        tilt.setSensitivity(9999);
        eq(TiltProfile.MAX_SENSITIVITY, tilt.sensitivity(), "it cannot be turned up past useful");
        tilt.setSensitivity(0);
        eq(TiltProfile.MIN_SENSITIVITY, tilt.sensitivity(), "nor down past it");
    }

    // ----------------------------------------------------------- hysteresis

    /**
     * The whole reason there are two thresholds rather than one.
     *
     * <p>A phone is held by a hand, and a hand moves. Held at the angle a
     * direction is taken at, a single threshold turns the smallest wobble into
     * a stream of presses and releases — which a game reads as a player
     * hammering the key.</p>
     */
    private void hysteresis() {
        TiltProfile tilt = new TiltProfile();
        tilt.setEnabled(true);
        float press = tilt.pressAt();
        float release = tilt.releaseAt();
        check(release < press, "a direction is given up at a smaller angle than it is taken at");

        Set<String> held = none();
        held = tilt.directions(press + 0.01f, 0f, held);
        check(held.contains("right"), "leaning past the angle takes the direction");

        // The hand drifts back to just under the angle it was taken at. A
        // single threshold would let go here; this must not.
        held = tilt.directions(press - 0.02f, 0f, held);
        check(held.contains("right"), "and drifting back a little does not give it up");
        held = tilt.directions(release + 0.01f, 0f, held);
        check(held.contains("right"), "nor does drifting most of the way back");
        held = tilt.directions(release - 0.01f, 0f, held);
        check(!held.contains("right"), "only levelling off properly gives it up");

        // Once given up, it is not taken again until the larger angle: the
        // gap works in both directions or it is not a gap.
        held = tilt.directions(release + 0.05f, 0f, held);
        check(!held.contains("right"), "and it is not taken again on the way back up");

        // A hand shaking around the edge, which is the case this exists for.
        int changes = 0;
        boolean was = false;
        Set<String> shaking = none();
        for (int i = 0; i < 40; i++) {
            float wobble = press + (i % 2 == 0 ? 0.01f : -0.01f);
            shaking = tilt.directions(wobble, 0f, shaking);
            boolean now = shaking.contains("right");
            if (now != was) {
                changes++;
                was = now;
            }
        }
        eq(1, changes, "a shaking hand presses once, not twenty times");
    }

    // ------------------------------------------------------- axes and invert

    private void axesAndInvert() {
        TiltProfile tilt = new TiltProfile();
        tilt.setEnabled(true);
        eq(0, tilt.directions(0f, 1f, none()).size(),
                "leaning forward does nothing while only steering is allowed");

        tilt.setAxes(TiltProfile.AXES_BOTH);
        check(tilt.directions(0f, 1f, none()).contains("down"),
                "with all four, leaning away presses down");
        check(tilt.directions(0f, -1f, none()).contains("up"), "and leaning back presses up");
        eq(2, tilt.directions(1f, 1f, none()).size(),
                "a corner lean presses two, the way a corner of a pad does");

        tilt.setAxes(TiltProfile.AXES_UP_DOWN);
        eq(0, tilt.directions(1f, 0f, none()).size(),
                "and with up and down only, steering does nothing");
        tilt.setAxes(99);
        eq(TiltProfile.AXES_LEFT_RIGHT, tilt.axes(), "an axis choice that does not exist falls back");

        tilt.setInverted(true);
        check(tilt.directions(1f, 0f, none()).contains("left"),
                "inverted, leaning right presses left");

        // Through JSON, because this is a setting someone tunes to their own
        // hand and would not tune twice.
        GameProfile profile = GameProfile.defaultsFor(
                com.mobicore.core.model.MidletSuiteInfo.merge(
                        com.mobicore.core.jar.AttributeSet.parse(
                                "MIDlet-Name: Demo\nMIDlet-Vendor: Test\n"
                                        + "MIDlet-1: Demo,,demo.Main\n"), null));
        profile.tilt().setEnabled(true);
        profile.tilt().setSensitivity(150);
        profile.tilt().setAxes(TiltProfile.AXES_BOTH);
        profile.tilt().setInverted(true);
        GameProfile restored = GameProfile.fromJson(
                Json.readObject(Json.write(profile.toJson())));
        check(restored.tilt().isEnabled(), "tilting survives a restart");
        eq(150, restored.tilt().sensitivity(), "with the angle it was tuned to");
        eq(TiltProfile.AXES_BOTH, restored.tilt().axes(), "and the directions it was given");
        check(restored.tilt().isInverted(), "and which way round it was");
    }

    // ---------------------------------------------------------------- bridge

    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        check(!Json.bool(Json.readObject(facade.tiltJson(suiteId)), "enabled", true),
                "a game starts with tilting off");
        check(Json.bool(Json.readObject(facade.setTiltEnabled(suiteId, true)), "enabled", false),
                "it can be switched on");
        eq(150, Json.integer(Json.readObject(facade.setTiltSensitivity(suiteId, 150)),
                "sensitivity", 0), "and tuned");
        eq("Bốn hướng", Json.string(Json.readObject(
                        facade.setTiltAxes(suiteId, TiltProfile.AXES_BOTH)), "axesName", ""),
                "given more directions");
        check(Json.bool(Json.readObject(facade.setTiltInverted(suiteId, true)), "inverted", false),
                "and turned the other way round");
        facade.setTiltInverted(suiteId, false);

        check(!Json.bool(Json.readObject(facade.tilted(900, 0)), "ok", true),
                "with no game running a tilt presses nothing");

        facade.startGame(suiteId);
        facade.renderFrame();
        eq("1", Json.string(Json.readObject(facade.tilted(900, 0)), "held", ""),
                "a lean reaches the running game");
        eq("1", Json.string(Json.readObject(facade.tilted(400, 0)), "held", ""),
                "and drifting back a little does not let go");
        eq("0", Json.string(Json.readObject(facade.tilted(0, 0)), "held", ""),
                "levelling off does");

        // Switching it off mid-game lets go of what it was holding, rather
        // than leaving the game pressed against a wall.
        facade.tilted(900, 0);
        facade.setTiltEnabled(suiteId, false);
        eq("0", Json.string(Json.readObject(facade.tilted(900, 0)), "held", ""),
                "and switching it off mid-game lets go");

        facade.stopGame();
    }

    private Set<String> none() {
        return new LinkedHashSet<String>();
    }
}
