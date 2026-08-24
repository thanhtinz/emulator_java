package com.mobicore.core.mod;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs, enables and applies mods for one game.
 *
 * <p>The original JAR is never modified. Mods are stored beside it and layered
 * in front at load time, so disabling a mod restores the stock game exactly and
 * a backup is taken before anything changes.</p>
 */
public final class ModManager {

    private final Vfs vfs;
    private final StorageLayout layout;
    private final GameLibrary library;
    private final String suiteId;

    public ModManager(GameLibrary library, String suiteId) {
        this.library = library;
        this.vfs = library.storage();
        this.layout = library.layout();
        this.suiteId = suiteId;
    }

    private String statePath() {
        return StorageLayout.join(layout.modDir(suiteId), "enabled.json");
    }

    /**
     * Installs a mod archive.
     *
     * <p>A backup of the game is taken first: the specification requires that
     * every modding action is reversible.</p>
     */
    public ModPackage install(String modId, byte[] archiveBytes) throws IOException {
        JarArchive archive = JarArchive.read(new ByteArrayInputStream(archiveBytes));
        ModPackage mod = ModPackage.read(modId, archive);
        if (!mod.appliesTo(suiteId)) {
            throw new IOException("This mod targets " + mod.targetSuiteId() + ", not " + suiteId);
        }
        library.backup(suiteId);
        library.installMod(suiteId, mod.modId(), archiveBytes);
        return mod;
    }

    public boolean uninstall(String modId) {
        setEnabled(modId, false);
        return vfs.delete(StorageLayout.join(layout.modDir(suiteId), modId + ".mod"));
    }

    /** Every installed mod, with its enabled flag resolved. */
    public List<ModPackage> installed() throws IOException {
        Map<String, Boolean> state = enabledState();
        List<ModPackage> mods = new ArrayList<ModPackage>();
        for (String modId : library.mods(suiteId)) {
            ModPackage mod = ModPackage.read(modId, library.loadMod(suiteId, modId));
            Boolean enabled = state.get(mod.modId());
            mod.setEnabled(enabled != null && enabled.booleanValue());
            mods.add(mod);
        }
        return mods;
    }

    public void setEnabled(String modId, boolean enabled) {
        try {
            Map<String, Boolean> state = enabledState();
            state.put(modId, Boolean.valueOf(enabled));
            Map<String, Object> json = Json.object();
            for (Map.Entry<String, Boolean> entry : state.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            vfs.write(statePath(), Json.write(json).getBytes("UTF-8"));
        } catch (IOException e) {
            // Failing to persist the flag must not take the game down; the mod
            // simply stays in its previous state.
        }
    }

    private Map<String, Boolean> enabledState() throws IOException {
        Map<String, Boolean> state = new LinkedHashMap<String, Boolean>();
        if (!vfs.exists(statePath())) {
            return state;
        }
        Map<String, Object> json = Json.readObject(new String(vfs.read(statePath()), "UTF-8"));
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            state.put(entry.getKey(), Boolean.valueOf(Json.bool(json, entry.getKey(), false)));
        }
        return state;
    }

    /**
     * Layers every enabled mod onto a running session, in installation order so
     * a later mod wins.
     *
     * @return how many mods were applied
     */
    public int applyTo(EmulatorSession session) throws IOException {
        int applied = 0;
        for (ModPackage mod : installed()) {
            if (!mod.isEnabled()) {
                continue;
            }
            session.addModOverlay(mod.archive());
            applied++;
        }
        return applied;
    }

    public String toJson() throws IOException {
        Map<String, Object> root = Json.object();
        List<Object> mods = new ArrayList<Object>();
        for (ModPackage mod : installed()) {
            mods.add(mod.toJson());
        }
        root.put("mods", mods);
        return Json.write(root);
    }
}
