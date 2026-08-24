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

        check(Json.bool(Json.readObject(facade.setDeviceProfile(suiteId, "s40-128x128")), "ok", false),
                "the device can still be set by hand");
        check(!Json.bool(Json.readObject(facade.profileJson(suiteId)), "auto", true),
                "which stops the emulator claiming it measured that");

        Map<String, Object> redone = Json.readObject(facade.autoSetup(suiteId));
        check(Json.bool(redone, "ok", false), "setup can be run again from the game itself");
        check(Json.array(redone, "notes").size() >= 4, "and reports what it found");
        Map<String, Object> afterAuto = Json.readObject(facade.profileJson(suiteId));
        check(Json.bool(afterAuto, "auto", false), "the profile is automatic again");
        eq("qvga-240x320", Json.string(Json.child(afterAuto, "device"), "id", ""),
                "and the hand-set device was replaced by the detected one");

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
        eq(7, Json.array(profile, "devices").size(), "the device catalog rides along");
        eq("qvga-240x320", Json.string(Json.child(profile, "device"), "id", ""),
                "the default device is QVGA");

        check(Json.bool(Json.readObject(facade.setDeviceProfile(suiteId, "s60-176x208")), "ok", false),
                "the device profile can be switched by id");
        eq("s60-176x208", Json.string(Json.child(
                        Json.readObject(facade.profileJson(suiteId)), "device"), "id", ""),
                "the switch persisted");

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
        eq(5, Json.array(inspect, "midlets").size(), "the inspector lists every MIDlet");
        check(Json.array(inspect, "resources").size() > 0, "the inspector lists resources");
        check(facade.resource(suiteId, "res/level1.dat").length == 1024, "resources read back");

        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }

        // Put the device back before running, so the screen size is known.
        facade.setDeviceProfile(suiteId, "qvga-240x320");
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
}
