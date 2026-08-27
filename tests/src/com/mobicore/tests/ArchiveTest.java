package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.Map;

/**
 * The whole library in one file, and back out again.
 *
 * <p>What has to survive is everything that is the player's: the games, the
 * names and covers they chose, every setting, what the games saved, the save
 * states, the screenshots and the presets. The test moves an entire library
 * into a second, empty phone and checks each of those on the far side —
 * anything less proves only that bytes were written.</p>
 */
public final class ArchiveTest extends Test {

    private final String fixtureDir;

    public ArchiveTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Whole-library backup";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        movingPhones();
        refusals();
    }

    private void movingPhones() throws Exception {
        // The old phone: a game, played, renamed, configured, saved.
        MobiCoreFacade old = new MobiCoreFacade(new MemoryVfs());
        old.open("/data");
        Map<String, Object> imported = Json.readObject(
                old.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        old.renameGame(suiteId, "Người Chạy Trên Mây");
        old.setInputPreset(suiteId, "Sony Ericsson");
        old.savePreset("Điện thoại cũ", suiteId);
        old.setTheme(1);
        old.startGame(suiteId);
        for (int i = 0; i < 20; i++) {
            old.renderFrame();
        }
        old.saveState(2);
        old.takeScreenshot();
        old.stopGame();

        byte[] archive = old.exportLibrary();
        check(archive.length > 1000, "the library exports as one file");

        // The new phone: empty, then not.
        MobiCoreFacade fresh = new MobiCoreFacade(new MemoryVfs());
        fresh.open("/data");
        eq(0, Json.array(Json.readObject(fresh.libraryJson()), "games").size(),
                "the new phone starts with nothing");

        Map<String, Object> restored = Json.readObject(fresh.importLibrary(archive));
        check(Json.bool(restored, "ok", false), "and takes the file");
        eq(1, Json.integer(restored, "games", 0), "with the game in it");
        check(Json.string(restored, "summary", "").length() > 0,
                "and a line to show: " + Json.string(restored, "summary", ""));

        Map<String, Object> game = (Map<String, Object>) Json.array(
                Json.readObject(fresh.libraryJson()), "games").get(0);
        eq("Người Chạy Trên Mây", Json.string(game, "title", ""),
                "the name the player gave it comes with it");

        Map<String, Object> profile = Json.readObject(fresh.profileJson(suiteId));
        eq("Sony Ericsson", Json.string(Json.child(profile, "input"), "preset", ""),
                "and the keypad they set");

        check(fresh.hasSaveState(suiteId, 2), "what they saved is there");
        check(fresh.saveStateThumbnail(suiteId, 2).length > 0,
                "with the picture that went with it");
        eq(1, Json.array(Json.readObject(fresh.screenshotsJson(suiteId)), "screenshots").size(),
                "so are the screenshots they took");
        eq(1, Json.array(Json.readObject(fresh.presetsJson()), "presets").size(),
                "and the presets they made");
        eq(1, Json.integer(Json.readObject(fresh.appSettingsJson()), "theme", -1),
                "down to the theme they chose");

        // And it plays: the point of carrying the files at all.
        check(Json.bool(Json.readObject(fresh.resumeGame(suiteId, 2)), "ok", false),
                "the restored game starts");
        fresh.stopGame();
    }

    private void refusals() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        check(!Json.bool(Json.readObject(facade.importLibrary(new byte[0])), "ok", true),
                "an empty file is not a backup");
        check(!Json.bool(Json.readObject(facade.importLibrary(
                        "hello there".getBytes("UTF-8"))), "ok", true),
                "nor is something else entirely");
    }
}
