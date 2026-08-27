package com.mobicore.core.library;

import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shelves the player puts their games on.
 *
 * <p>Search finds a game whose name is remembered. A library of eighty games
 * is mostly games whose names are not: "that racing one", "the ones I play on
 * the bus", "the ones my brother left on here". A shelf is how a person finds
 * those — by having put them somewhere themselves.</p>
 *
 * <p>Kept beside the library index rather than inside each game's profile.
 * A shelf is a fact about the collection, not about a game: emptying one
 * should not mean rewriting eighty files, and a game removed from the phone
 * should not take the shelf with it.</p>
 */
public final class CollectionStore {

    /** How long a name may be. Longer than this is a description. */
    public static final int MAX_NAME = 40;
    /** How many shelves there can be, which is more than anyone will make. */
    public static final int MAX_COLLECTIONS = 50;

    private final Vfs vfs;
    private final StorageLayout layout;
    /** Name to the suite ids on it, in the order they were put there. */
    private final Map<String, List<String>> shelves = new LinkedHashMap<String, List<String>>();

    public CollectionStore(Vfs vfs, StorageLayout layout) {
        this.vfs = vfs;
        this.layout = layout;
        load();
    }

    /** Every shelf, in the order they were made. */
    public List<String> names() {
        return new ArrayList<String>(shelves.keySet());
    }

    /** Which games are on one shelf, in the order they were put there. */
    public List<String> gamesOn(String name) {
        List<String> games = shelves.get(clean(name));
        return games == null ? new ArrayList<String>() : new ArrayList<String>(games);
    }

    /** Which shelves one game is on. */
    public List<String> shelvesOf(String suiteId) {
        List<String> found = new ArrayList<String>();
        for (Map.Entry<String, List<String>> shelf : shelves.entrySet()) {
            if (shelf.getValue().contains(suiteId)) {
                found.add(shelf.getKey());
            }
        }
        return found;
    }

    public boolean has(String name) {
        return shelves.containsKey(clean(name));
    }

    /**
     * Makes a shelf.
     *
     * @return false when the name is empty or already taken, or there are
     *     already more shelves than anyone is going to use
     */
    public boolean create(String name) throws IOException {
        String key = clean(name);
        if (key.length() == 0 || shelves.containsKey(key)
                || shelves.size() >= MAX_COLLECTIONS) {
            return false;
        }
        shelves.put(key, new ArrayList<String>());
        save();
        return true;
    }

    /** Puts a game on a shelf, making the shelf if it is not there yet. */
    public boolean add(String name, String suiteId) throws IOException {
        String key = clean(name);
        if (key.length() == 0 || suiteId == null || suiteId.length() == 0) {
            return false;
        }
        List<String> games = shelves.get(key);
        if (games == null) {
            if (shelves.size() >= MAX_COLLECTIONS) {
                return false;
            }
            games = new ArrayList<String>();
            shelves.put(key, games);
        }
        if (games.contains(suiteId)) {
            return false;
        }
        games.add(suiteId);
        save();
        return true;
    }

    /** Takes a game off a shelf, leaving the shelf and the game alone. */
    public boolean remove(String name, String suiteId) throws IOException {
        List<String> games = shelves.get(clean(name));
        if (games == null || !games.remove(suiteId)) {
            return false;
        }
        save();
        return true;
    }

    /** Puts a game on a shelf or takes it off, whichever it is not. */
    public boolean toggle(String name, String suiteId) throws IOException {
        return gamesOn(name).contains(suiteId)
                ? !remove(name, suiteId) : add(name, suiteId);
    }

    /** Throws away a shelf. The games on it are not touched. */
    public boolean delete(String name) throws IOException {
        if (shelves.remove(clean(name)) == null) {
            return false;
        }
        save();
        return true;
    }

    /** Renames a shelf, keeping what is on it and where it sits in the list. */
    public boolean rename(String from, String to) throws IOException {
        String oldKey = clean(from);
        String newKey = clean(to);
        if (!shelves.containsKey(oldKey) || newKey.length() == 0
                || shelves.containsKey(newKey)) {
            return false;
        }
        // Rebuilt rather than re-put, so a renamed shelf keeps its place in
        // the list instead of jumping to the end.
        Map<String, List<String>> rebuilt = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> shelf : shelves.entrySet()) {
            rebuilt.put(oldKey.equals(shelf.getKey()) ? newKey : shelf.getKey(),
                    shelf.getValue());
        }
        shelves.clear();
        shelves.putAll(rebuilt);
        save();
        return true;
    }

    /**
     * Forgets a game that is no longer installed.
     *
     * <p>Called when a game is uninstalled: a shelf holding the name of
     * something that is gone shows a count nobody can reach.</p>
     */
    public boolean forget(String suiteId) throws IOException {
        boolean changed = false;
        for (Map.Entry<String, List<String>> shelf : shelves.entrySet()) {
            changed |= shelf.getValue().remove(suiteId);
        }
        if (changed) {
            save();
        }
        return changed;
    }

    /**
     * A name with the spaces trimmed and the length held.
     *
     * <p>The name is also the key, so it is cleaned once here rather than at
     * every call site: "Đua xe" and "Đua xe " are the same shelf to the
     * person who typed them.</p>
     */
    private static String clean(String name) {
        String value = name == null ? "" : name.trim();
        return value.length() > MAX_NAME ? value.substring(0, MAX_NAME) : value;
    }

    // ------------------------------------------------------------------ JSON

    private String path() {
        return StorageLayout.join(layout.dir(StorageLayout.PRESETS), "collections.json");
    }

    private void load() {
        String path = path();
        if (!vfs.exists(path)) {
            return;
        }
        try {
            Map<String, Object> json = Json.readObject(new String(vfs.read(path), "UTF-8"));
            Map<String, Object> stored = Json.child(json, "collections");
            for (Map.Entry<String, Object> shelf : stored.entrySet()) {
                if (!(shelf.getValue() instanceof List)) {
                    continue;
                }
                List<String> games = new ArrayList<String>();
                for (Object game : (List<Object>) shelf.getValue()) {
                    if (game instanceof String) {
                        games.add((String) game);
                    }
                }
                shelves.put(clean(shelf.getKey()), games);
            }
        } catch (IOException e) {
            // A shelf list that cannot be read is not worth stopping the app
            // for: the games are all still there, on no shelf.
            shelves.clear();
        }
    }

    private void save() throws IOException {
        Map<String, Object> stored = Json.object();
        for (Map.Entry<String, List<String>> shelf : shelves.entrySet()) {
            stored.put(shelf.getKey(), new ArrayList<Object>(shelf.getValue()));
        }
        Map<String, Object> json = Json.object();
        json.put("collections", stored);
        vfs.mkdirs(layout.dir(StorageLayout.PRESETS));
        vfs.write(path(), Json.write(json).getBytes("UTF-8"));
    }
}
