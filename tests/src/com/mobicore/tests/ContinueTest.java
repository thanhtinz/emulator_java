package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.Map;

/**
 * The one game offered on the way in.
 *
 * <p>Opening the app to play the game you were just playing is the most common
 * thing anyone does with it, and without this it costs three taps: find the
 * game, open it, press play.</p>
 */
public final class ContinueTest extends Test {

    private final String fixtureDir;

    public ContinueTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Chơi tiếp";
    }

    @Override
    public void run() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");

        check(!Json.bool(Json.readObject(facade.continueJson()), "has", true),
                "an empty library offers nothing to carry on with");

        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        // Installed is not played. A card offering to continue a game nobody
        // has started is a card about nothing.
        check(!Json.bool(Json.readObject(facade.continueJson()), "has", true),
                "and neither does a game that has only been installed");

        check(!Json.bool(Json.readObject(facade.continueGame()), "ok", true),
                "so there is nothing to press either");

        // Played once, and left without saving: the offer is to start it.
        facade.startGame(suiteId);
        facade.renderFrame();
        facade.stopGame();

        Map<String, Object> card = Json.readObject(facade.continueJson());
        check(Json.bool(card, "has", false), "once a game has been played it is offered");
        eq("Sky Runner", Json.string(Json.child(card, "game"), "title", ""), "by name");
        eq(suiteId, Json.string(card, "suiteId", ""), "and by id, for the button to use");
        check(!Json.bool(card, "resumes", true), "with nothing saved, it says it would start again");
        eq("Chơi lại", Json.string(card, "action", ""), "and the button says so");

        // Left the way a phone actually leaves a game — a call, a flat
        // battery — which saves. Now the offer is to carry on.
        facade.startGame(suiteId);
        facade.renderFrame();
        facade.stopGameSaving();

        card = Json.readObject(facade.continueJson());
        check(Json.bool(card, "resumes", false), "a game left mid-play is carried on");
        eq("Chơi tiếp", Json.string(card, "action", ""), "which is not the same word");
        check(Json.longValue(card, "lastPlayed", 0L) > 0, "and it says when that was");

        Map<String, Object> resumed = Json.readObject(facade.continueGame());
        check(Json.bool(resumed, "ok", false),
                "the button starts it: " + Json.string(resumed, "error", ""));
        check(Json.bool(resumed, "resumed", false), "from where it was left");
        facade.stopGame();

        // A second game, played later, takes the card over: what is offered
        // is what was played last, not what was installed first.
        facade.importSuite(SampleSuite.jar(fixtureDir), otherDescriptor());
        String other = "";
        for (Object game : Json.array(Json.readObject(facade.libraryJson()), "games")) {
            String id = Json.string((Map<String, Object>) game, "suiteId", "");
            if (!suiteId.equals(id)) {
                other = id;
            }
        }
        Map<String, Object> secondStart = Json.readObject(facade.startGame(other));
        check(Json.bool(secondStart, "ok", false),
                "the second game starts: " + Json.string(secondStart, "error", ""));
        facade.stopGame();
        eq(other, Json.string(Json.readObject(facade.continueJson()), "suiteId", ""),
                "the newest game played is the one offered");

        // And a game that is gone is not offered: the card falls back to
        // whatever is still installed and was played.
        facade.uninstall(other, false);
        eq(suiteId, Json.string(Json.readObject(facade.continueJson()), "suiteId", ""),
                "uninstalling what was offered falls back to the one before it");
    }

    /**
     * A descriptor that makes the same JAR a second, different game.
     *
     * <p>A suite is identified by its name, vendor and version, and a
     * descriptor's attributes beat the manifest's — so the same bytecode
     * installs twice under two names, which is what "most recent" needs
     * something to be recent of.</p>
     */
    private byte[] otherDescriptor() throws Exception {
        return SampleSuite.utf8("MIDlet-Name: Tile Rush\n"
                + "MIDlet-Version: 1.0.4\n"
                + "MIDlet-Vendor: MobiCore Samples\n"
                + "MIDlet-Jar-URL: SkyRunner.jar\n"
                + "MIDlet-1: Tile Rush,,demo.SkyRunner\n");
    }
}
