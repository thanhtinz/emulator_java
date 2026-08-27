package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Exercises the flat bridge the iOS build talks to.
 *
 * <p>Every call crosses the same boundary J2ObjC generates, so testing it here
 * means a translation problem cannot hide behind untested Java.</p>
 */
public final class FacadeTest extends Test {

    private final String fixtureDir;

    public FacadeTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Bridge facade";
    }

    @Override
    public void run() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        check(!facade.isOpen(), "a fresh facade is closed");
        check(!Json.bool(Json.readObject(facade.libraryJson()), "ok", true),
                "calls before open report an error instead of crashing");

        Map<String, Object> opened = Json.readObject(facade.open("/data"));
        check(Json.bool(opened, "ok", false), "open succeeds");
        eq("/data/MobiCore", Json.string(opened, "root", ""), "the storage root is namespaced");
        check(facade.isOpen(), "the facade reports it is open");

        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        check(Json.bool(imported, "ok", false), "import succeeds");
        check(!Json.bool(imported, "replaced", true), "a first import is not a replacement");
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");
        eq("mobicore-samples.sky-runner.1-2-0", suiteId, "the import returns the suite id");

        Map<String, Object> library = Json.readObject(facade.libraryJson());
        List<Object> games = Json.array(library, "games");
        eq(1, games.size(), "the library lists the installed game");
        @SuppressWarnings("unchecked")
        Map<String, Object> game = (Map<String, Object>) games.get(0);
        eq("Sky Runner", Json.string(game, "title", ""), "the entry carries the title");
        check(Json.child(game, "settings").size() > 0, "each entry embeds its settings");
        eq("MIDP-2.0", Json.string(game, "profile", ""), "the MIDP profile string is not shadowed");

        check(facade.artwork(suiteId).length > 0, "artwork crosses the bridge as bytes");
        eq(0, facade.artwork("nonexistent").length, "a missing game yields no artwork");

        // Renaming and cover art across the bridge ------------------------
        Map<String, Object> renamed = Json.readObject(
                facade.renameGame(suiteId, "Người Chạy Trên Mây"));
        check(Json.bool(renamed, "ok", false), "a game can be renamed through the bridge");
        eq("Người Chạy Trên Mây", Json.string(renamed, "title", ""), "the new title comes back");
        @SuppressWarnings("unchecked")
        Map<String, Object> afterRename = (Map<String, Object>)
                Json.array(Json.readObject(facade.libraryJson()), "games").get(0);
        eq("Người Chạy Trên Mây", Json.string(afterRename, "title", ""),
                "the library lists the new name");
        eq("Sky Runner", Json.string(afterRename, "originalTitle", ""),
                "and still carries the manifest name");
        check(Json.bool(afterRename, "renamed", false),
                "the entry says it was renamed, so the phone need not compare strings");
        check(!Json.bool(Json.readObject(facade.renameGame(suiteId, "  ")), "ok", true),
                "a blank name is refused with an error, not stored");
        check(Json.bool(Json.readObject(facade.resetTitle(suiteId)), "ok", false),
                "the manifest title can be put back");

        Framebuffer chosenCover = new Framebuffer(20, 20);
        chosenCover.fill(0xFF3366AA);
        byte[] chosen = PngWriter.encode(chosenCover);
        check(Json.bool(Json.readObject(facade.setArtwork(suiteId, chosen)), "ok", false),
                "a chosen cover crosses the bridge");
        eq(chosen.length, facade.artwork(suiteId).length, "and is what later reads return");
        check(!Json.bool(Json.readObject(facade.setArtwork(suiteId, new byte[]{9, 9})), "ok", true),
                "a file that is not a PNG is refused");
        check(Json.bool(Json.readObject(facade.resetArtwork(suiteId)), "ok", false),
                "the suite's own icon can be put back");

        // Automatic setup: the player configures nothing, and can see why ---
        Map<String, Object> imported0 = Json.readObject(facade.profileJson(suiteId));
        check(Json.bool(imported0, "auto", false), "an imported game is configured automatically");
        check(Json.array(imported0, "setupNotes").size() >= 4,
                "and says in words what it decided");

        check(Json.bool(Json.readObject(facade.setInputPreset(suiteId, "Sony Ericsson")), "ok", false),
                "a setting can still be changed by hand");
        check(!Json.bool(Json.readObject(facade.profileJson(suiteId)), "auto", true),
                "which stops the emulator claiming it measured that");

        Map<String, Object> redone = Json.readObject(facade.autoSetup(suiteId));
        check(Json.bool(redone, "ok", false), "setup can be run again from the game itself");
        check(Json.array(redone, "notes").size() >= 4, "and reports what it found");
        Map<String, Object> afterAuto = Json.readObject(facade.profileJson(suiteId));
        check(Json.bool(afterAuto, "auto", false), "the profile is automatic again");
        eq("qvga-240x320", Json.string(Json.child(afterAuto, "device"), "id", ""),
                "and the hand-set device was replaced by the detected one");

        // Which way the phone is held --------------------------------------
        eq(0, Json.integer(afterAuto, "orientation", -1),
                "a game written for a tall screen is played upright");
        Map<String, Object> turned = Json.readObject(facade.toggleOrientation(suiteId));
        check(Json.bool(turned, "landscape", false), "and can be turned by hand");
        eq(1, Json.integer(Json.readObject(facade.profileJson(suiteId)), "orientation", -1),
                "which is remembered with the game");
        facade.toggleOrientation(suiteId);
        eq(0, Json.integer(Json.readObject(facade.profileJson(suiteId)), "orientation", -1),
                "and turned back again");
        check(!Json.bool(Json.readObject(facade.toggleOrientation("nope")), "ok", true),
                "a game that is not there cannot be turned");

        // Which keys the keypad shows -------------------------------------
        eq(0, Json.integer(Json.readObject(facade.profileJson(suiteId)), "keypadLayout", -1),
                "the whole keypad is there to begin with");
        Map<String, Object> cycled = Json.readObject(facade.cycleKeypadLayout(suiteId));
        eq(1, Json.integer(cycled, "keypadLayout", -1), "and switches to the pad alone");
        eq("Chỉ phím hướng", Json.string(cycled, "name", ""), "with a name for the menu");
        facade.cycleKeypadLayout(suiteId);
        facade.cycleKeypadLayout(suiteId);
        eq(3, Json.integer(Json.readObject(facade.profileJson(suiteId)), "keypadLayout", -1),
                "then the numbers alone, then out of the way entirely");
        facade.cycleKeypadLayout(suiteId);
        eq(0, Json.integer(Json.readObject(facade.profileJson(suiteId)), "keypadLayout", -1),
                "and round again");

        // How the keypad looks --------------------------------------------
        keypadLook(facade, suiteId);

        // And where its keys are ------------------------------------------
        keypadArrangement(facade, suiteId);

        // What a real controller does -------------------------------------
        gamepad(facade, suiteId);

        // How long a game has held someone ----------------------------------
        playTime(facade, suiteId);

        // Picking which MIDlet in the suite to open --------------------------
        midlets(facade, suiteId);

        // Pointing a button at a different key ------------------------------
        keyMapping(facade, suiteId);

        // Settings worked out once, applied to the rest --------------------
        presets(facade, suiteId);

        // Searching across the bridge --------------------------------------
        facade.renameGame(suiteId, "Người Chạy Trên Mây");
        Map<String, Object> hit = Json.readObject(facade.searchJson("nguoi chay", 0));
        check(Json.bool(hit, "ok", false), "search answers");
        eq(1, Json.array(hit, "games").size(), "a query without marks finds the marked name");
        eq("nguoi chay", Json.string(hit, "query", ""), "and says what it searched for");
        eq(0, Json.array(Json.readObject(facade.searchJson("tetris", 0)), "games").size(),
                "a query that matches nothing finds nothing");
        eq(1, Json.array(Json.readObject(facade.searchJson("", 0)), "games").size(),
                "an empty query lists the library");
        check(Json.bool(Json.readObject(facade.setLibrarySort(1)), "ok", false),
                "the sort order can be remembered");
        eq(1, Json.integer(Json.readObject(facade.appSettingsJson()), "librarySort", -1),
                "and it is");
        facade.resetTitle(suiteId);

        // The interface theme: the one setting people change often ---------
        Map<String, Object> appearance = Json.readObject(facade.appSettingsJson());
        eq(0, Json.integer(appearance, "theme", -1), "the interface starts light");
        eq("Sáng", Json.string(appearance, "themeName", ""), "and says so in words");

        check(Json.bool(Json.readObject(facade.setTheme(1)), "ok", false), "dark can be chosen");
        eq(1, Json.integer(Json.readObject(facade.appSettingsJson()), "theme", -1),
                "and the choice is remembered");
        eq(2, Json.integer(Json.readObject(facade.cycleTheme()), "theme", -1),
                "one tap moves dark to following the phone");
        eq(0, Json.integer(Json.readObject(facade.cycleTheme()), "theme", -1),
                "and then back round to light");
        facade.setTheme(9);
        eq(0, Json.integer(Json.readObject(facade.appSettingsJson()), "theme", -1),
                "a theme that does not exist falls back to light rather than breaking");

        Map<String, Object> profile = Json.readObject(facade.profileJson(suiteId));
        // There is one screen now, so the profile carries it and offers no
        // catalog to pick a different one from.
        eq("qvga-240x320", Json.string(Json.child(profile, "device"), "id", ""),
                "every game runs on the one 240x320 screen");
        check(!profile.containsKey("devices"), "and there is no catalog to choose from");

        check(Json.bool(Json.readObject(facade.setInputPreset(suiteId, "Sony Ericsson")), "ok", false),
                "the input preset can be switched");
        eq("Sony Ericsson", Json.string(Json.child(
                        Json.readObject(facade.profileJson(suiteId)), "input"), "preset", ""),
                "the preset persisted");

        eq("true", Json.string(Json.readObject(facade.toggleFavourite(suiteId)), "favourite", ""),
                "favourite toggles on");
        eq("false", Json.string(Json.readObject(facade.toggleFavourite(suiteId)), "favourite", ""),
                "favourite toggles back off");

        Map<String, Object> inspect = Json.readObject(facade.inspectJson(suiteId));
        check(Json.child(inspect, "attributes").size() > 5, "the inspector exposes the descriptor");
        eq(9, Json.array(inspect, "midlets").size(), "the inspector lists every MIDlet");
        check(Json.array(inspect, "resources").size() > 0, "the inspector lists resources");
        check(facade.resource(suiteId, "res/level1.dat").length == 1024, "resources read back");

        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }

        Map<String, Object> started = Json.readObject(facade.startGame(suiteId));
        check(Json.bool(started, "ok", false), "the game starts: " + Json.string(started, "error", ""));
        eq(240, Json.integer(started, "width", 0), "the screen width crosses the bridge");
        eq(320, Json.integer(started, "height", 0), "the screen height crosses the bridge");
        eq("demo.SkyRunner", Json.string(started, "midlet", ""), "the started MIDlet is reported");
        check(facade.isRunning(), "the facade reports the game is running");
        eq(suiteId, facade.activeSuiteId(), "the active suite is reported");

        check(facade.renderFrame(), "a frame renders");
        int[] pixels = facade.framePixels();
        eq(240 * 320, pixels.length, "the pixel buffer is screen sized");
        // Row 2 is inside the system title bar, which proves the handset
        // chrome crossed the bridge along with the game's own drawing.
        check(pixels[240 * 2 + 120] != 0xFF000000, "the system chrome is in the pixel buffer");

        facade.pressButton("right");
        facade.releaseButton("right");
        facade.pointerPressed(10, 10);
        facade.pointerReleased(10, 10);
        check(facade.screenshotPng().length > 100, "a screenshot crosses the bridge as PNG bytes");

        // Keeping one is the point: a MIDlet cannot show anyone what it did.
        Map<String, Object> shot = Json.readObject(facade.takeScreenshot());
        check(Json.bool(shot, "ok", false), "and can be kept");
        check(Json.string(shot, "path", "").indexOf("screenshots") >= 0,
                "under the game's own folder: " + Json.string(shot, "path", ""));

        // A picture nothing can show again is a dead end, so it comes back.
        Map<String, Object> gallery = Json.readObject(facade.screenshotsJson(suiteId));
        List<Object> shots = Json.array(gallery, "screenshots");
        eq(1, shots.size(), "the gallery lists what was taken");
        Map<String, Object> first = (Map<String, Object>) shots.get(0);
        String name = Json.string(first, "name", "");
        check(Json.integer(first, "bytes", 0) > 100, "with its size");
        check(Json.string(first, "name", "").endsWith(".png"), "and its name: " + name);
        eq(facade.screenshotPng().length, facade.screenshot(suiteId, name).length,
                "and the picture itself reads back");
        eq(0, facade.screenshot(suiteId, "../library.json").length,
                "a name that tries to leave the folder reads nothing");
        check(Json.bool(Json.readObject(facade.deleteScreenshot(suiteId, name)), "ok", false),
                "and one can be thrown away");
        eq(0, Json.array(Json.readObject(facade.screenshotsJson(suiteId)), "screenshots").size(),
                "after which the gallery is empty");

        // A pad press has to reach the running game, which is the point of
        // the whole feature.
        check(Json.bool(Json.readObject(facade.pressPad("padA")), "pressed", false),
                "a pad press crosses the bridge to the running game");
        facade.releasePad("padA");
        check(!Json.bool(Json.readObject(facade.pressPad("padL2")), "pressed", true),
                "and a control bound to nothing presses nothing");

        // A picture says where the player got to; a clip says how.
        clip(facade, suiteId);

        facade.pauseGame();
        facade.resumeGame();
        check(facade.logText().length() > 0, "the log crosses the bridge as text");
        check(Json.array(Json.readObject(facade.logJson()), "lines").size() > 0,
                "the log also crosses as structured JSON");

        // A save written while running must show up through the saves API.
        facade.stopGame();
        check(!facade.isRunning(), "stopping ends the session");
        eq("", facade.activeSuiteId(), "no suite is active after stopping");

        Map<String, Object> saves = Json.readObject(facade.savesJson(suiteId));
        eq(0, Json.array(saves, "stores").size(), "the demo saved nothing on its own");

        Map<String, Object> backup = Json.readObject(facade.backup(suiteId));
        check(Json.bool(backup, "ok", false), "a backup can be taken");
        eq(1, Json.array(Json.readObject(facade.savesJson(suiteId)), "backups").size(),
                "the backup is listed");
        check(Json.bool(Json.readObject(facade.restoreLatest(suiteId)), "ok", false),
                "the latest backup restores");
        check(Json.bool(Json.readObject(facade.resetGameData(suiteId)), "ok", false),
                "reset works and reports where it backed up to");

        check(Json.bool(Json.readObject(facade.uninstall(suiteId, false)), "ok", false),
                "uninstall succeeds");
        eq(0, Json.array(Json.readObject(facade.libraryJson()), "games").size(),
                "the library is empty again");
    }
    /**
     * Play time: the library knew when a game was last opened, which answers
     * "what was I playing" and nothing else.
     */
    private void playTime(MobiCoreFacade facade, String suiteId) throws Exception {
        eq(0, Json.integer(Json.readObject(facade.profileJson(suiteId)), "playedMs", -1),
                "a game nobody has played has no time on it");
        eq("chưa chơi",
                Json.string(Json.readObject(facade.profileJson(suiteId)), "playedName", ""),
                "and says so in words rather than a bare zero");

        facade.startGame(suiteId);
        for (int i = 0; i < 5; i++) {
            facade.renderFrame();
        }
        Thread.sleep(30);
        facade.stopGame();
        long first = Json.longValue(Json.readObject(facade.profileJson(suiteId)), "playedMs", 0L);
        check(first > 0, "leaving a game adds the time it was open");

        facade.startGame(suiteId);
        Thread.sleep(30);
        facade.stopGame();
        long second = Json.longValue(Json.readObject(facade.profileJson(suiteId)), "playedMs", 0L);
        check(second > first, "and a second session adds to the first rather than replacing it");

        eq("12 phút", com.mobicore.core.model.GameProfile.playedName(12 * 60_000L),
                "minutes read as minutes");
        eq("2 giờ 5 phút", com.mobicore.core.model.GameProfile.playedName(125 * 60_000L),
                "and hours as hours");
        eq("dưới một phút", com.mobicore.core.model.GameProfile.playedName(5_000L),
                "a short session is not rounded away to nothing");
    }

    /**
     * A JAR often holds more than one MIDlet — the game, a help screen, a
     * settings screen, sometimes a second game — and only the first could
     * ever run.
     */
    private void midlets(MobiCoreFacade facade, String suiteId) throws Exception {
        Map<String, Object> listed = Json.readObject(facade.midletsJson(suiteId));
        List<Object> midlets = Json.array(listed, "midlets");
        check(midlets.size() > 1, "the sample suite holds several MIDlets");
        Map<String, Object> first = (Map<String, Object>) midlets.get(0);
        check(Json.bool(first, "chosen", false),
                "with the manifest's first marked until someone chooses");

        Map<String, Object> second = (Map<String, Object>) midlets.get(2);
        String other = Json.string(second, "className", "");
        check(Json.bool(Json.readObject(facade.startGame(suiteId, other)), "ok", false),
                "another one in the suite can be started: " + other);
        facade.stopGame();

        eq(other, Json.string(Json.readObject(facade.profileJson(suiteId)), "midletClass", ""),
                "and the choice is remembered with the game");
        check(Json.bool((Map<String, Object>) Json.array(
                        Json.readObject(facade.midletsJson(suiteId)), "midlets").get(2),
                "chosen", false), "which the list then shows");

        check(Json.bool(Json.readObject(facade.startGame(suiteId)), "ok", false),
                "playing again opens what was played last");
        facade.stopGame();

        // A name that is no longer in the suite starts the game anyway: a
        // reinstall from a different build must not leave it unopenable.
        Map<String, Object> profile = Json.readObject(facade.profileJson(suiteId));
        profile.put("midletClass", "demo.LongGone");
        facade.updateProfile(Json.write(profile));
        check(Json.bool(Json.readObject(facade.startGame(suiteId)), "ok", false),
                "a stale name falls back to the first MIDlet rather than failing");
        eq("", Json.string(Json.readObject(facade.profileJson(suiteId)), "midletClass", "x"),
                "and stops remembering something that is not there");
        facade.stopGame();
    }

    /**
     * Remapping: the presets are a guess, and a wrong guess looks like a
     * broken emulator rather than a wrong key.
     */
    private void keyMapping(MobiCoreFacade facade, String suiteId) throws Exception {
        Map<String, Object> keys = Json.readObject(facade.keyChoicesJson());
        eq(19, Json.array(keys, "keys").size(),
                "the choices are what a MIDlet of the era might read, and nothing else");

        Map<String, Object> mapped = Json.readObject(facade.setKeyMapping(suiteId, "up", '2'));
        check(Json.bool(mapped, "ok", false), "a button can be pointed at another key");
        eq("2", Json.string(mapped, "keyName", ""), "which is named for the player");
        eq("Tuỳ chỉnh", Json.string(mapped, "preset", ""),
                "and the keypad stops calling itself Nokia once it is not one");

        Map<String, Object> input = Json.child(
                Json.readObject(facade.profileJson(suiteId)), "input");
        eq('2', Json.integer(Json.child(input, "mappings"), "up", 0),
                "the change is saved with the game");

        check(!Json.bool(Json.readObject(facade.setKeyMapping(suiteId, "up", 4242)), "ok", true),
                "a code no handset ever sent is refused");

        // Back to a preset, which is the way out of a mess.
        facade.setInputPreset(suiteId, "Nokia");
        Map<String, Object> back = Json.child(
                Json.readObject(facade.profileJson(suiteId)), "input");
        eq(-1, Json.integer(Json.child(back, "mappings"), "up", 0),
                "picking a preset puts every key back");
        eq("Nokia", Json.string(back, "preset", ""), "and names it again");
    }

    /**
     * Dragging the keys to where the hand holding the phone wants them.
     *
     * <p>Offsets cross the bridge in thousandths of a key rather than pixels:
     * a key is a different number of pixels upright, sideways and on every
     * different phone, and one arrangement has to hold for all of them.</p>
     */
    private void keypadArrangement(MobiCoreFacade facade, String suiteId) throws Exception {
        Map<String, Object> standard = Json.readObject(facade.keypadArrangementJson(suiteId));
        check(!Json.bool(standard, "custom", true), "the keypad starts as the standard one");
        eq(100, Json.integer(standard, "scale", 0), "at the standard size");
        eq(0, Json.array(standard, "keys").size(), "with nothing moved");

        Map<String, Object> moved = Json.readObject(facade.moveKey(suiteId, "fire", 250, -1500));
        check(Json.bool(moved, "custom", false), "dragging a key makes it their own keypad");
        eq(1, Json.array(moved, "keys").size(), "and it is the one key that moved");
        Map<String, Object> fire = (Map<String, Object>) Json.array(moved, "keys").get(0);
        eq("fire", Json.string(fire, "button", ""), "named as the button it is");
        eq(-1500, Json.integer(fire, "y", 0), "with the offset it was given");

        eq(6000, Json.integer((Map<String, Object>) Json.array(
                        Json.readObject(facade.moveKey(suiteId, "num1", 90000, 0)),
                        "keys").get(1), "x", 0),
                "a drag off the screen stops at the edge");

        eq(130, Json.integer(Json.readObject(facade.setKeyScale(suiteId, 130)), "scale", 0),
                "the keys can be made bigger");
        eq(160, Json.integer(Json.readObject(facade.setKeyScale(suiteId, 900)), "scale", 0),
                "but not any size at all");

        // It survives a restart, because this is a setting someone spends
        // time on and would not spend twice.
        Map<String, Object> profile = Json.readObject(facade.profileJson(suiteId));
        eq(160, Json.integer(Json.child(profile, "keypadArrangement"), "scale", 0),
                "the arrangement is part of the profile");

        Map<String, Object> reset = Json.readObject(facade.resetKeypad(suiteId));
        check(!Json.bool(reset, "custom", true), "and it can all be put back");
        eq(0, Json.array(reset, "keys").size(), "with every key where the layout has it");
        eq(100, Json.integer(reset, "scale", 0), "and the standard size");
    }

    /**
     * What a real controller's buttons do.
     *
     * <p>Playing on glass is the one thing an emulator cannot fix: there is
     * no edge to feel for, so a player looks down instead of at the game. A
     * controller gives the edges back.</p>
     */
    private void gamepad(MobiCoreFacade facade, String suiteId) throws Exception {
        Map<String, Object> pad = Json.readObject(facade.gamepadJson(suiteId));
        check(Json.bool(pad, "enabled", false), "a pad works as soon as it is connected");
        check(!Json.bool(pad, "custom", true), "with the arrangement a J2ME game expects");
        eq(14, Json.array(pad, "pads").size(),
                "and every control is listed, bound or not");
        Map<String, Object> first = (Map<String, Object>) Json.array(pad, "pads").get(0);
        eq("Lên", Json.string(first, "padName", ""), "each named for the screen");
        eq("Lên", Json.string(first, "buttonName", ""), "along with what it presses");

        Map<String, Object> mapped =
                Json.readObject(facade.setPadMapping(suiteId, "padL2", "num7"));
        check(Json.bool(mapped, "custom", false), "any control can be pointed anywhere");
        eq("Phím 7", padButtonName(mapped, "padL2"), "and the screen says what it now does");

        eq("Không dùng", padButtonName(
                        Json.readObject(facade.setPadMapping(suiteId, "padY", "")), "padY"),
                "a control can be left doing nothing");

        // Off means off at the moment a button is pressed, but the screen
        // still shows what everything is mapped to.
        Map<String, Object> off = Json.readObject(facade.setGamepadEnabled(suiteId, false));
        check(!Json.bool(off, "enabled", true), "the whole pad can be switched off");
        eq("Bắn", padButtonName(off, "padA"), "without forgetting what its buttons do");
        facade.setGamepadEnabled(suiteId, true);

        check(!Json.bool(Json.readObject(facade.pressPad("padA")), "ok", true),
                "with no game running there is nothing for a pad to press");
        check(!Json.bool(Json.readObject(facade.setPadMapping("khong co", "padA", "fire")),
                "ok", true), "a game that is not installed maps nothing");

        Map<String, Object> back = Json.readObject(facade.resetGamepad(suiteId));
        check(!Json.bool(back, "custom", true), "and it can all be put back");
        eq("Bắn", padButtonName(back, "padA"), "with A back on fire");
    }

    /** What one control on the pad is said to do, out of the pad listing. */
    private String padButtonName(Map<String, Object> pad, String name) {
        List<Object> pads = Json.array(pad, "pads");
        for (int i = 0; i < pads.size(); i++) {
            Map<String, Object> entry = (Map<String, Object>) pads.get(i);
            if (name.equals(Json.string(entry, "pad", ""))) {
                return Json.string(entry, "buttonName", "");
            }
        }
        return "";
    }

    /**
     * Recording a few seconds of play as an animation.
     *
     * <p>Saved beside the screenshots, because to a player a clip is a
     * screenshot that moves and two galleries would mean choosing which one to
     * open before remembering which one they took.</p>
     */
    private void clip(MobiCoreFacade facade, String suiteId) throws Exception {
        check(!Json.bool(Json.readObject(facade.recordingJson()), "recording", true),
                "nothing is being recorded to begin with");
        check(!Json.bool(Json.readObject(facade.stopRecording()), "ok", true),
                "and there is nothing to save");

        Map<String, Object> started = Json.readObject(facade.startRecording());
        check(Json.bool(started, "recording", false), "recording starts when asked");
        eq(10, Json.integer(started, "maxSeconds", 0), "and says how long it may run");
        for (int i = 0; i < 40; i++) {
            facade.renderFrame();
            Thread.sleep(6);
        }
        check(Json.integer(Json.readObject(facade.recordingJson()), "frames", 0) > 1,
                "frames pile up while the game runs");

        Map<String, Object> saved = Json.readObject(facade.stopRecording());
        check(Json.bool(saved, "ok", false), "the clip saves: "
                + Json.string(saved, "error", ""));
        check(Json.string(saved, "path", "").endsWith(".gif"),
                "as a GIF: " + Json.string(saved, "path", ""));
        check(Json.integer(saved, "bytes", 0) > 100, "with something in it");

        List<Object> gallery = Json.array(
                Json.readObject(facade.screenshotsJson(suiteId)), "screenshots");
        eq(1, gallery.size(), "and shows up in the same gallery as the pictures");
        Map<String, Object> entry = (Map<String, Object>) gallery.get(0);
        check(Json.bool(entry, "clip", false), "marked as the moving kind");
        String name = Json.string(entry, "name", "");
        byte[] gif = facade.screenshot(suiteId, name);
        check(gif.length > 100 && gif[0] == 'G' && gif[1] == 'I' && gif[2] == 'F',
                "and reads back as a GIF");
        facade.deleteScreenshot(suiteId, name);

        // Cancelling throws the frames away rather than saving them.
        facade.startRecording();
        facade.renderFrame();
        facade.cancelRecording();
        check(!Json.bool(Json.readObject(facade.recordingJson()), "recording", true),
                "a cancelled recording stops");
        eq(0, Json.integer(Json.readObject(facade.recordingJson()), "frames", -1),
                "and keeps nothing");
    }

    /**
     * How solid the keypad is, what shape its keys are, and when it fades.
     *
     * <p>Sideways the keypad sits over the game, so these are not decoration:
     * they are the difference between playing a wide game and playing the
     * part of one the keypad leaves showing.</p>
     */
    private void keypadLook(MobiCoreFacade facade, String suiteId) throws Exception {
        Map<String, Object> look = Json.readObject(facade.keypadJson(suiteId));
        eq(100, Json.integer(look, "opacity", 0), "the keypad starts solid");
        eq("Bo góc", Json.string(look, "shapeName", ""), "with rounded keys");
        eq("Luôn rõ", Json.string(look, "fadeDelayName", ""), "and stays that way");

        eq(60, Json.integer(Json.readObject(facade.setKeypadOpacity(suiteId, 60)), "opacity", 0),
                "it can be made see-through");
        eq(20, Json.integer(Json.readObject(facade.setKeypadOpacity(suiteId, 3)), "opacity", 0),
                "but not so far that the keys cannot be found");
        eq(100, Json.integer(Json.readObject(facade.setKeypadOpacity(suiteId, 400)), "opacity", 0),
                "and no more solid than solid");
        facade.setKeypadOpacity(suiteId, 60);

        eq("Vuông", Json.string(Json.readObject(facade.cycleKeypadShape(suiteId)), "shapeName", ""),
                "the keys go square");
        eq("Tròn", Json.string(Json.readObject(facade.cycleKeypadShape(suiteId)), "shapeName", ""),
                "then round");
        eq("Bo góc", Json.string(Json.readObject(facade.cycleKeypadShape(suiteId)), "shapeName", ""),
                "then back to where they started");

        Map<String, Object> fading = Json.readObject(facade.setKeypadFadeDelay(suiteId, 5));
        eq(5, Json.integer(fading, "fadeDelay", 0), "it can be told to step back when idle");
        eq("Sau 5 giây", Json.string(fading, "fadeDelayName", ""), "and says so in words");
        eq(60, Json.integer(Json.readObject(facade.setKeypadFadeDelay(suiteId, 999)),
                "fadeDelay", 0), "a delay past a minute is the same as never");
        facade.setKeypadFadeDelay(suiteId, 0);

        // Settings survive a restart, because the keypad is not something a
        // player wants to set up again every time they open a game.
        eq(60, Json.integer(Json.readObject(facade.profileJson(suiteId)), "keypadOpacity", 0),
                "the profile carries it all");
    }

    /**
     * Presets: one answer to "how big, how loud, how many frames", saved
     * under a name and applied to every other game.
     */
    private void presets(MobiCoreFacade facade, String suiteId) throws Exception {
        // Set the game up by hand first, so the preset carries something.
        Map<String, Object> tuned = Json.readObject(facade.profileJson(suiteId));
        tuned.put("volume", Integer.valueOf(35));
        tuned.put("frameLimit", Integer.valueOf(24));
        facade.updateProfile(Json.write(tuned));

        check(Json.bool(Json.readObject(facade.savePreset("Dien thoai cua toi", suiteId)),
                "ok", false), "settings can be saved under a name");
        eq(1, Json.array(Json.readObject(facade.presetsJson()), "presets").size(),
                "and the list shows it");

        // Applied to another game, which is the whole point.
        facade.importSuite(SampleSuite.zip(otherGame()), null);
        String other = null;
        List<Object> games = Json.array(Json.readObject(facade.libraryJson()), "games");
        for (int i = 0; i < games.size(); i++) {
            Map<String, Object> game = (Map<String, Object>) games.get(i);
            String id = Json.string(game, "suiteId", "");
            if (!suiteId.equals(id)) {
                other = id;
            }
        }
        check(other != null, "a second game is installed to apply it to");
        check(Json.bool(Json.readObject(facade.applyPreset("Dien thoai cua toi", other)),
                "ok", false), "the preset applies to another game");

        Map<String, Object> after = Json.readObject(facade.profileJson(other));
        eq(35, Json.integer(after, "volume", 0), "which takes the saved volume");
        eq(24, Json.integer(after, "frameLimit", 0), "and the saved frame cap");
        eq("qvga-240x320", Json.string(Json.child(after, "device"), "id", ""),
                "and the one screen every game runs on");
        eq(other, Json.string(after, "suiteId", ""), "but stays the game it is");
        check(!Json.bool(after, "auto", true),
                "and no longer claims the emulator measured all that");

        check(!Json.bool(Json.readObject(facade.applyPreset("khong co", other)), "ok", true),
                "a preset that does not exist applies nothing");
        check(!Json.bool(Json.readObject(facade.setDefaultPreset("khong co")), "ok", true),
                "and cannot be made the default");
        check(Json.bool(Json.readObject(facade.setDefaultPreset("Dien thoai cua toi")),
                "ok", false), "one that does exist can");
        eq("Dien thoai cua toi",
                Json.string(Json.readObject(facade.appSettingsJson()), "defaultPreset", ""),
                "and is remembered between sessions");

        check(Json.bool(Json.readObject(facade.deletePreset("Dien thoai cua toi")), "ok", false),
                "a preset can be thrown away");
        eq(0, Json.array(Json.readObject(facade.presetsJson()), "presets").size(),
                "after which none are listed");
        facade.setDefaultPreset("");
        facade.uninstall(other, false);
        facade.autoSetup(suiteId);
    }

    /** A second suite, so a preset has somewhere to be applied. */
    private Map<String, byte[]> otherGame() throws Exception {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", SampleSuite.utf8("Manifest-Version: 1.0\n"
                + "MIDlet-Name: Tile Rush\n"
                + "MIDlet-Version: 1.0\n"
                + "MIDlet-Vendor: Test Games\n"
                + "MIDlet-1: Tile Rush,,demo.TileRush\n"
                + "MicroEdition-Configuration: CLDC-1.1\n"
                + "MicroEdition-Profile: MIDP-2.0\n"));
        entries.put("demo/TileRush.class",
                new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        return entries;
    }
}
