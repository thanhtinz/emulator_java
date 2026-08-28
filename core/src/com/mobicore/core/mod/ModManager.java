package com.mobicore.core.mod;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.jar.Zip;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Tên bản mod chứa những gì chính người chơi thay.
     *
     * <p>Một bản mod như mọi bản mod khác — cùng đường cài, cùng chỗ lưu,
     * cùng cách gỡ — nên thứ người chơi tự thay không cần một cơ chế thứ hai
     * đứng song song với cơ chế đã có.</p>
     */
    public static final String PERSONAL = "cua-toi";

    /**
     * Thay một tệp trong game bằng tệp của người chơi.
     *
     * <p>Ghi vào bản mod riêng, dựng lại cả gói mỗi lần: một bản mod là một
     * tệp nén, và sửa một tệp nén tại chỗ thì rắc rối hơn nhiều so với đóng
     * lại một gói vài chục kilobyte.</p>
     *
     * @param path đường dẫn bên trong tệp game, ví dụ {@code res/logo.png}
     */
    public ModPackage replaceResource(String path, byte[] bytes) throws IOException {
        if (path == null || path.trim().length() == 0 || bytes == null) {
            throw new IOException("Thiếu tệp để thay");
        }
        String inside = clean(path);
        Map<String, byte[]> entries = personalEntries();
        entries.put(inside, bytes);
        return savePersonal(entries);
    }

    /** Bỏ một thứ đã thay, trả game về tệp gốc của nó. */
    public boolean restoreResource(String path) throws IOException {
        Map<String, byte[]> entries = personalEntries();
        if (entries.remove(clean(path)) == null) {
            return false;
        }
        if (entries.isEmpty()) {
            // Không còn gì trong đó thì bỏ luôn bản mod, chứ không để lại một
            // cái tên rỗng trong danh sách.
            uninstall(PERSONAL);
            return true;
        }
        savePersonal(entries);
        return true;
    }

    /** Những gì người chơi đã tự thay. */
    public List<String> replacedByPlayer() throws IOException {
        List<String> paths = new ArrayList<String>(personalEntries().keySet());
        Collections.sort(paths);
        return paths;
    }

    private Map<String, byte[]> personalEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        for (String modId : library.mods(suiteId)) {
            if (!PERSONAL.equals(modId)) {
                continue;
            }
            JarArchive archive = library.loadMod(suiteId, modId);
            for (String name : archive.names()) {
                if (!ModPackage.MANIFEST.equals(name)) {
                    entries.put(name, archive.read(name));
                }
            }
        }
        return entries;
    }

    private ModPackage savePersonal(Map<String, byte[]> entries) throws IOException {
        Map<String, Object> manifest = Json.object();
        manifest.put("id", PERSONAL);
        manifest.put("name", "Của tôi");
        manifest.put("version", "1.0");
        manifest.put("author", "Người chơi");
        manifest.put("description", "Những tệp bạn tự thay trong game này");
        manifest.put("target", suiteId);
        Map<String, byte[]> all = new LinkedHashMap<String, byte[]>();
        all.put(ModPackage.MANIFEST, utf8(Json.write(manifest)));
        all.putAll(entries);

        ModPackage mod = install(PERSONAL, Zip.write(all));
        // Bật sẵn: người chơi vừa chọn tệp để thay, không ai làm thế rồi lại
        // đi bật thêm một công tắc nữa.
        setEnabled(PERSONAL, true);
        return mod;
    }

    /** Bỏ mọi lối đi vòng ra khỏi đường dẫn: một mod chỉ ghi vào chính nó. */
    private static String clean(String path) throws IOException {
        String value = path.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.length() == 0 || value.indexOf("..") >= 0) {
            throw new IOException("Đường dẫn không hợp lệ: " + path);
        }
        return value;
    }

    private static byte[] utf8(String text) throws IOException {
        return text.getBytes("UTF-8");
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
