package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.library.CollectionStore;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.SampleSuite;

import java.util.List;
import java.util.Map;

/**
 * Shelves the player puts their games on.
 *
 * <p>Search finds a game whose name is remembered. A library of eighty games
 * is mostly games whose names are not — "that racing one", "the ones I play on
 * the bus" — and a shelf is how a person finds those.</p>
 */
public final class CollectionTest extends Test {

    private final String fixtureDir;

    public CollectionTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Bộ sưu tập";
    }

    @Override
    public void run() throws Exception {
        store();
        throughTheBridge();
    }

    // ---------------------------------------------------------------- store

    private void store() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("MobiCore");
        CollectionStore shelves = new CollectionStore(vfs, layout);

        eq(0, shelves.names().size(), "a new library has no shelves");
        check(shelves.create("Đua xe"), "one can be made");
        check(!shelves.create("Đua xe"), "but not twice");
        check(!shelves.create("  "), "and a shelf needs a name");
        // The name is the key, so it is trimmed once rather than at every
        // call site: "Đua xe" and "Đua xe " are the same shelf to whoever
        // typed them.
        check(!shelves.create("  Đua xe  "), "spaces around a name do not make a new shelf");

        check(shelves.add("Đua xe", "a.racer.1-0"), "a game goes on a shelf");
        check(!shelves.add("Đua xe", "a.racer.1-0"), "and does not go on twice");
        check(shelves.add("Đua xe", "b.rally.2-0"), "a second game goes on too");
        eq(2, shelves.gamesOn("Đua xe").size(), "which is what the shelf holds");
        eq("a.racer.1-0", shelves.gamesOn("Đua xe").get(0),
                "in the order they were put there");

        // A shelf is made by putting something on it, because that is what a
        // player does: they do not make an empty shelf and then fill it.
        check(shelves.add("Chơi trên xe buýt", "a.racer.1-0"),
                "putting a game somewhere new makes the shelf");
        eq(2, shelves.shelvesOf("a.racer.1-0").size(), "a game can be on more than one");

        check(shelves.remove("Đua xe", "b.rally.2-0"), "a game comes off again");
        check(!shelves.remove("Đua xe", "b.rally.2-0"), "and only once");
        check(shelves.toggle("Đua xe", "b.rally.2-0"), "toggling puts it back");
        check(!shelves.toggle("Đua xe", "b.rally.2-0"), "and toggling again takes it off");

        check(shelves.rename("Đua xe", "Đua xe & bay"), "a shelf can be renamed");
        eq(1, shelves.gamesOn("Đua xe & bay").size(), "keeping what is on it");
        eq("Đua xe & bay", shelves.names().get(0), "and its place in the list");
        check(!shelves.rename("Không có", "Gì đó"), "a shelf that does not exist cannot be renamed");
        check(!shelves.rename("Đua xe & bay", "Chơi trên xe buýt"),
                "and a name already taken is refused");

        // Written down, because a shelf someone spent an evening filling
        // should still be there tomorrow.
        CollectionStore reopened = new CollectionStore(vfs, layout);
        eq(2, reopened.names().size(), "shelves survive a restart");
        eq(1, reopened.gamesOn("Đua xe & bay").size(), "with what was on them");

        check(reopened.forget("a.racer.1-0"), "a game that is gone is taken off every shelf");
        eq(0, reopened.shelvesOf("a.racer.1-0").size(), "so no shelf still names it");
        check(reopened.delete("Đua xe & bay"), "and a shelf can be thrown away");
        check(!reopened.delete("Đua xe & bay"), "once");
        eq(1, reopened.names().size(), "leaving the others alone");
    }

    // --------------------------------------------------------------- bridge

    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        eq(0, Json.array(Json.readObject(facade.collectionsJson(suiteId)), "collections").size(),
                "a new library has no shelves");

        check(Json.bool(Json.readObject(facade.createCollection("Chơi trên xe buýt")),
                "ok", false), "one can be made");
        check(!Json.bool(Json.readObject(facade.createCollection("Chơi trên xe buýt")),
                "ok", true), "but not twice");

        Map<String, Object> shelf = first(facade.collectionsJson(suiteId));
        eq("Chơi trên xe buýt", Json.string(shelf, "name", ""), "and it comes back by name");
        eq(0, Json.integer(shelf, "games", -1), "empty to begin with");
        check(!Json.bool(shelf, "holds", true), "and not holding this game");

        check(Json.bool(Json.readObject(facade.toggleCollection("Chơi trên xe buýt", suiteId)),
                "holds", false), "a game goes on the shelf");
        shelf = first(facade.collectionsJson(suiteId));
        eq(1, Json.integer(shelf, "games", 0), "which now holds one game");
        check(Json.bool(shelf, "holds", false), "and says so for this game");

        // The games on a shelf come back in the same shape the whole library
        // does, so a list of games needs only one way of being drawn.
        Map<String, Object> listing = Json.readObject(
                facade.collectionJson("Chơi trên xe buýt"));
        List<Object> games = Json.array(listing, "games");
        eq(1, games.size(), "the shelf lists its games");
        eq("Sky Runner", Json.string((Map<String, Object>) games.get(0), "title", ""),
                "as full entries, not just ids");

        check(!Json.bool(Json.readObject(facade.toggleCollection("Chơi trên xe buýt", suiteId)),
                "holds", true), "toggling again takes it off");
        check(Json.bool(Json.readObject(
                facade.renameCollection("Chơi trên xe buýt", "Trên xe buýt")), "ok", false),
                "a shelf can be renamed");

        // Uninstalling forgets the game: a shelf holding the name of
        // something that is gone shows a count nobody can reach.
        facade.toggleCollection("Trên xe buýt", suiteId);
        eq(1, Json.integer(first(facade.collectionsJson(suiteId)), "games", 0),
                "with the game back on it");
        facade.uninstall(suiteId, false);
        eq(0, Json.integer(first(facade.collectionsJson(suiteId)), "games", -1),
                "uninstalling a game takes it off every shelf");

        check(Json.bool(Json.readObject(facade.deleteCollection("Trên xe buýt")), "ok", false),
                "and a shelf can be thrown away");
        check(!Json.bool(Json.readObject(facade.deleteCollection("Trên xe buýt")), "ok", true),
                "once");
    }

    /** The first shelf in a listing, which is the one these tests make. */
    private Map<String, Object> first(String response) {
        List<Object> shelves = Json.array(Json.readObject(response), "collections");
        return shelves.isEmpty() ? Json.object() : (Map<String, Object>) shelves.get(0);
    }
}
