package com.mobicore.core.library;

import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Named settings, saved once and applied to any game.
 *
 * <p>Somebody with eighty games and one phone has one answer to "how big is
 * the screen, how loud, how many frames" — and would otherwise give it eighty
 * times. A preset is that answer with a name on it: worked out on the game
 * that made you work it out, then applied to the rest, or set as the one that
 * greets every new import.</p>
 *
 * <p>What a preset holds is deliberately only the settings, never the
 * identity: it carries the device, the keypad, scaling, sound and the rest,
 * and never a suite id, a play count or a favourite mark. Applying one to a
 * game must not tell that game it is a different game.</p>
 */
public final class PresetStore {

    private final Vfs vfs;
    private final StorageLayout layout;

    public PresetStore(Vfs vfs, StorageLayout layout) {
        this.vfs = vfs;
        this.layout = layout;
    }

    /** Every preset's name, in the order they read. */
    public List<String> names() throws IOException {
        String dir = layout.dir(StorageLayout.PRESETS);
        if (!vfs.exists(dir)) {
            return new ArrayList<String>();
        }
        List<String> names = new ArrayList<String>();
        List<String> files = vfs.list(dir);
        for (int i = 0; i < files.size(); i++) {
            String file = files.get(i);
            if (file.endsWith(".json")) {
                names.add(file.substring(0, file.length() - 5));
            }
        }
        Collections.sort(names);
        return names;
    }

    public boolean exists(String name) {
        return vfs.exists(path(name));
    }

    /**
     * Saves {@code profile}'s settings under {@code name}, replacing any
     * preset already there.
     */
    public void save(String name, GameProfile profile) throws IOException {
        String key = key(name);
        if (key.length() == 0) {
            throw new IOException("A preset needs a name");
        }
        Map<String, Object> json = profile.toJson();
        // The identity of the game it was taken from has no business
        // travelling to the next game the preset is applied to.
        json.remove("suiteId");
        json.remove("favourite");
        json.remove("lastPlayed");
        json.remove("playCount");
        json.remove("auto");
        json.remove("setupNotes");
        json.remove("compatibility");
        json.put("presetName", name);
        vfs.mkdirs(layout.dir(StorageLayout.PRESETS));
        vfs.write(path(name), Json.write(json).getBytes("UTF-8"));
    }

    /** The settings stored under {@code name}, or null if there are none. */
    public Map<String, Object> read(String name) throws IOException {
        String path = path(name);
        if (!vfs.exists(path)) {
            return null;
        }
        return Json.readObject(new String(vfs.read(path), "UTF-8"));
    }

    public boolean delete(String name) throws IOException {
        String path = path(name);
        if (!vfs.exists(path)) {
            return false;
        }
        vfs.delete(path);
        return true;
    }

    /**
     * Applies a preset over one game's settings.
     *
     * <p>The game keeps everything that is about it — which suite it is, when
     * it was last played, whether it is a favourite — and takes everything
     * that is about how it runs. It stops counting as automatically
     * configured, because it no longer is.</p>
     *
     * @return the game's profile with the preset applied, or null when there
     *     is no such preset
     */
    public GameProfile apply(String name, GameProfile game) throws IOException {
        Map<String, Object> stored = read(name);
        if (stored == null) {
            return null;
        }
        Map<String, Object> merged = game.toJson();
        merged.putAll(stored);
        merged.put("suiteId", game.suiteId());
        merged.put("favourite", Boolean.valueOf(game.isFavourite()));
        merged.put("lastPlayed", Long.valueOf(game.lastPlayed()));
        merged.put("playCount", Integer.valueOf(game.playCount()));
        merged.remove("presetName");
        GameProfile applied = GameProfile.fromJson(merged);
        applied.setAuto(false, null);
        applied.setCompatibility(game.compatibility());
        return applied;
    }

    private String path(String name) {
        return StorageLayout.join(layout.dir(StorageLayout.PRESETS), key(name) + ".json");
    }

    /**
     * A file name and nothing else.
     *
     * <p>The name is typed by the user and crosses the bridge as a string, so
     * it must never be able to name a path of its own.</p>
     */
    private static String key(String name) {
        String text = name == null ? "" : name.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '.' || c < ' ') {
                out.append('-');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
