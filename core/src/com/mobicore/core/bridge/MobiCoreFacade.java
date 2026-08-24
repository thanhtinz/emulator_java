package com.mobicore.core.bridge;

import com.mobicore.core.emu.EmulatorLog;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.mod.ModManager;
import com.mobicore.core.mod.ModPackage;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.tools.CrashReport;
import com.mobicore.core.tools.JadEditor;
import com.mobicore.core.tools.RmsEditor;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.VmError;
import com.mobicore.core.vm.VmHost;
import com.mobicore.core.vm.VmThrow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flat, primitive-only entry point to the whole emulator.
 *
 * <p>iOS reaches the core through J2ObjC, and every Java type that crosses
 * that boundary becomes a generated Objective-C class the Swift layer has to
 * know about. Keeping the whole surface to strings, byte arrays, int arrays
 * and JSON means the bridge is a handful of selectors instead of a hundred —
 * and it stays stable when the internals change.</p>
 *
 * <p>The Android app talks to the rich API directly; this facade exists for
 * the translated build and for any future desktop or scripting host.</p>
 */
public final class MobiCoreFacade {

    private final Vfs vfs;
    private GameLibrary library;
    private StorageLayout layout;
    private EmulatorSession session;
    private String activeSuiteId;
    private VmHost host;

    public MobiCoreFacade() {
        this(new LocalVfs());
    }

    public MobiCoreFacade(Vfs vfs) {
        this.vfs = vfs;
    }

    /** Overrides platform services; iOS supplies its own clock and console. */
    public void setHost(VmHost host) {
        this.host = host;
    }

    // ------------------------------------------------------------- library

    /** Opens (and creates) the storage tree under {@code root}. */
    public String open(String root) {
        try {
            layout = new StorageLayout(StorageLayout.join(root, "MobiCore"));
            library = new GameLibrary(vfs, layout);
            library.setClock(now());
            library.open();
            return ok("root", layout.root());
        } catch (IOException e) {
            return error("Cannot open storage: " + e.getMessage());
        }
    }

    public boolean isOpen() {
        return library != null;
    }

    public String storageRoot() {
        return layout == null ? "" : layout.root();
    }

    /**
     * The whole library as JSON: every installed game with its profile,
     * plus the recently played and favourite orderings the home screen needs.
     */
    public String libraryJson() {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            Map<String, GameProfile> profiles = library.allProfiles();
            Map<String, Object> root = Json.object();
            List<Object> games = new ArrayList<Object>();
            for (LibraryEntry entry : library.sort(library.all(), GameLibrary.SORT_TITLE, profiles)) {
                Map<String, Object> game = entry.toJson();
                GameProfile profile = profiles.get(entry.suiteId());
                if (profile != null) {
                    // Deliberately not "profile": the entry already uses that
                    // key for the MIDP profile string.
                    game.put("settings", profile.toJson());
                }
                game.put("stores", library.records(entry.suiteId()).listStoreNames().size());
                games.add(game);
            }
            root.put("games", games);
            root.put("recent", idsOf(library.sort(library.all(), GameLibrary.SORT_RECENT, profiles),
                    profiles, true));
            root.put("favourites", idsOf(library.favourites(profiles), profiles, false));
            return Json.write(root);
        } catch (IOException e) {
            return error("Cannot read the library: " + e.getMessage());
        }
    }

    private List<Object> idsOf(List<LibraryEntry> entries, Map<String, GameProfile> profiles,
                               boolean playedOnly) {
        List<Object> ids = new ArrayList<Object>();
        for (LibraryEntry entry : entries) {
            GameProfile profile = profiles.get(entry.suiteId());
            if (playedOnly && (profile == null || profile.lastPlayed() == 0)) {
                continue;
            }
            ids.add(entry.suiteId());
        }
        return ids;
    }

    /** Artwork bytes for a game, or an empty array when it has none. */
    public byte[] artwork(String suiteId) {
        try {
            byte[] data = library == null ? null : library.artwork(suiteId);
            return data == null ? new byte[0] : data;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public String importSuite(byte[] jar, byte[] jad) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            library.setClock(now());
            GameLibrary.InstallResult result = library.install(jar, jad == null || jad.length == 0
                    ? null : jad);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("replaced", Boolean.valueOf(result.replaced()));
            json.put("game", result.entry().toJson());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String uninstall(String suiteId, boolean keepData) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return ok("removed", String.valueOf(library.uninstall(suiteId, keepData)));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    // ------------------------------------------------------------ profiles

    public String profileJson(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            Map<String, Object> json = profile.toJson();
            json.put("devices", deviceCatalog());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    private List<Object> deviceCatalog() {
        List<Object> devices = new ArrayList<Object>();
        for (DeviceProfile device : DeviceProfile.catalog()) {
            devices.add(device.toJson());
        }
        return devices;
    }

    /** Applies a profile edited by the UI; the JSON is what profileJson returns. */
    public String updateProfile(String json) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            GameProfile profile = GameProfile.fromJson(Json.readObject(json));
            library.saveProfile(profile);
            return ok("suiteId", profile.suiteId());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Switches a game to one of the catalog profiles by id. */
    public String setDeviceProfile(String suiteId, String deviceId) {
        try {
            GameProfile profile = library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setDevice(DeviceProfile.byId(deviceId));
            library.saveProfile(profile);
            return ok("device", profile.device().resolution());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String setInputPreset(String suiteId, String preset) {
        try {
            GameProfile profile = library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            if ("Sony Ericsson".equalsIgnoreCase(preset)) {
                profile.setInput(InputProfile.sonyEricsson());
            } else if ("Samsung".equalsIgnoreCase(preset)) {
                profile.setInput(InputProfile.samsung());
            } else {
                profile.setInput(InputProfile.nokia());
            }
            library.saveProfile(profile);
            return ok("preset", profile.input().presetName());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String toggleFavourite(String suiteId) {
        try {
            GameProfile profile = library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setFavourite(!profile.isFavourite());
            library.saveProfile(profile);
            return ok("favourite", String.valueOf(profile.isFavourite()));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    // -------------------------------------------------------------- saves

    public String savesJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            RecordStoreManager records = library.records(suiteId);
            Map<String, Object> root = Json.object();
            List<Object> stores = new ArrayList<Object>();
            for (String name : records.listStoreNames()) {
                RecordStoreManager.Store store = records.openStore(name, false);
                Map<String, Object> entry = Json.object();
                entry.put("name", name);
                entry.put("records", Integer.valueOf(store == null ? 0 : store.size()));
                entry.put("bytes", Integer.valueOf(store == null ? 0 : store.byteSize()));
                entry.put("version", Integer.valueOf(store == null ? 0 : store.version()));
                stores.add(entry);
            }
            root.put("stores", stores);
            root.put("backups", new ArrayList<Object>(library.backupsFor(suiteId)));
            return Json.write(root);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String backup(String suiteId) {
        try {
            library.setClock(now());
            return ok("path", library.backup(suiteId));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Restores the newest snapshot for a game. */
    public String restoreLatest(String suiteId) {
        try {
            List<String> backups = library.backupsFor(suiteId);
            if (backups.isEmpty()) {
                return error("There is no backup to restore");
            }
            String name = backups.get(backups.size() - 1);
            String path = StorageLayout.join(layout.backupDir(suiteId), name);
            library.restore(vfs.read(path));
            return ok("restored", name);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String resetGameData(String suiteId) {
        try {
            library.setClock(now());
            return ok("backup", library.resetGameData(suiteId));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    // --------------------------------------------------------------- tools

    /** Descriptor, MIDlets, classes and resources, for the inspector screens. */
    public String inspectJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            SuiteLoader suite = library.load(suiteId);
            Map<String, Object> root = Json.object();

            Map<String, Object> attributes = Json.object();
            for (String key : suite.info().attributes().keys()) {
                attributes.put(key, suite.info().attributes().get(key));
            }
            root.put("attributes", attributes);

            List<Object> midlets = new ArrayList<Object>();
            for (MidletEntry midlet : suite.info().midlets()) {
                Map<String, Object> entry = Json.object();
                entry.put("name", midlet.name());
                entry.put("className", midlet.className());
                entry.put("icon", midlet.iconPath());
                midlets.add(entry);
            }
            root.put("midlets", midlets);
            root.put("classes", new ArrayList<Object>(suite.archive().classNames()));

            List<Object> resources = new ArrayList<Object>();
            for (String name : suite.archive().names()) {
                if (name.endsWith(".class")) {
                    continue;
                }
                Map<String, Object> entry = Json.object();
                entry.put("name", name);
                byte[] data = suite.archive().read(name);
                entry.put("bytes", Integer.valueOf(data == null ? 0 : data.length));
                resources.add(entry);
            }
            root.put("resources", resources);
            root.put("uncompressed", Long.valueOf(suite.archive().uncompressedSize()));
            return Json.write(root);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Raw bytes of a resource, for the image and audio viewers. */
    public byte[] resource(String suiteId, String path) {
        try {
            byte[] data = library.load(suiteId).archive().read(path);
            return data == null ? new byte[0] : data;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    // ------------------------------------------------------------ emulator

    public String startGame(String suiteId) {
        stopGame();
        if (library == null) {
            return error("The library is not open");
        }
        try {
            GameProfile profile = library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            SuiteLoader suite = library.load(suiteId);
            session = EmulatorSession.create(suite, profile, vfs, layout, host);
            session.start();
            activeSuiteId = suiteId;
            profile.markPlayed(now());
            library.saveProfile(profile);

            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("width", Integer.valueOf(session.screen().width()));
            json.put("height", Integer.valueOf(session.screen().height()));
            json.put("midlet", session.midletClass());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        } catch (VmThrow e) {
            return error("The MIDlet threw " + e);
        } catch (VmError e) {
            return error(e.getMessage());
        }
    }

    public boolean isRunning() {
        return session != null && session.state() == EmulatorSession.STATE_ACTIVE;
    }

    public String activeSuiteId() {
        return activeSuiteId == null ? "" : activeSuiteId;
    }

    public int screenWidth() {
        return session == null ? 0 : session.screen().width();
    }

    public int screenHeight() {
        return session == null ? 0 : session.screen().height();
    }

    /**
     * Advances one frame.
     *
     * @return true when the screen changed and the pixels are worth reading
     */
    public boolean renderFrame() {
        if (session == null) {
            return false;
        }
        try {
            return session.renderFrame();
        } catch (VmThrow e) {
            session.log().error("Frame aborted: " + e);
            return false;
        } catch (VmError e) {
            session.log().error("Frame aborted: " + e.getMessage());
            return false;
        }
    }

    /** The current frame as ARGB pixels, row by row. */
    public int[] framePixels() {
        if (session == null) {
            return new int[0];
        }
        Framebuffer screen = session.screen();
        int[] copy = new int[screen.pixels().length];
        System.arraycopy(screen.pixels(), 0, copy, 0, copy.length);
        return copy;
    }

    public byte[] screenshotPng() {
        try {
            return session == null ? new byte[0] : session.screenshotPng();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public void pressButton(String button) {
        if (session != null) {
            session.pressButton(button);
        }
    }

    public void releaseButton(String button) {
        if (session != null) {
            session.releaseButton(button);
        }
    }

    public void pointerPressed(int x, int y) {
        if (session != null) {
            session.pointerPressed(x, y);
        }
    }

    public void pointerDragged(int x, int y) {
        if (session != null) {
            session.pointerDragged(x, y);
        }
    }

    public void pointerReleased(int x, int y) {
        if (session != null) {
            session.pointerReleased(x, y);
        }
    }

    public void pauseGame() {
        if (session != null) {
            session.pause();
        }
    }

    public void resumeGame() {
        if (session != null) {
            session.resume();
        }
    }

    public void stopGame() {
        if (session != null) {
            session.destroy();
            session = null;
            activeSuiteId = null;
        }
    }

    /** True once the MIDlet has ended itself. */
    public boolean isFinished() {
        return session == null || session.isFinished();
    }

    public String logText() {
        return session == null ? "" : session.log().render();
    }

    public String logJson() {
        Map<String, Object> root = Json.object();
        List<Object> lines = new ArrayList<Object>();
        if (session != null) {
            for (EmulatorLog.Entry entry : session.log().entries()) {
                Map<String, Object> line = Json.object();
                line.put("level", entry.levelName());
                line.put("text", entry.text);
                lines.add(line);
            }
        }
        root.put("lines", lines);
        return Json.write(root);
    }

    // -------------------------------------------------------------- network

    /** Recorded connections plus the current policy, for the network monitor. */
    public String networkJson() {
        if (session == null) {
            return Json.write(Json.object());
        }
        Map<String, Object> root = Json.object();
        List<Object> exchanges = new ArrayList<Object>();
        for (NetworkMonitor.Exchange exchange : session.network().monitor().exchanges()) {
            exchanges.add(exchange.toJson());
        }
        root.put("exchanges", exchanges);
        root.put("policy", session.network().policy().toJson());
        int[] totals = session.network().monitor().totals();
        root.put("bytesSent", Integer.valueOf(totals[0]));
        root.put("bytesReceived", Integer.valueOf(totals[1]));
        return Json.write(root);
    }

    public String allowHost(String host) {
        if (session == null) {
            return error("No game is running");
        }
        session.network().policy().allowHost(host);
        return ok("allowed", host);
    }

    public String denyHost(String host) {
        if (session == null) {
            return error("No game is running");
        }
        session.network().policy().denyHost(host);
        return ok("denied", host);
    }

    // ----------------------------------------------------------------- mods

    public String modsJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return new ModManager(library, suiteId).toJson();
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String installMod(String suiteId, String modId, byte[] archive) {
        try {
            ModPackage mod = new ModManager(library, suiteId).install(modId, archive);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("mod", mod.toJson());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String setModEnabled(String suiteId, String modId, boolean enabled) {
        if (library == null) {
            return error("The library is not open");
        }
        new ModManager(library, suiteId).setEnabled(modId, enabled);
        return ok("modId", modId);
    }

    public String uninstallMod(String suiteId, String modId) {
        if (library == null) {
            return error("The library is not open");
        }
        return ok("removed", String.valueOf(new ModManager(library, suiteId).uninstall(modId)));
    }

    // ---------------------------------------------------------- JAD and RMS

    /** The descriptor plus any problems the validator found. */
    public String descriptorJson(String suiteId) {
        try {
            JadEditor editor = new JadEditor(library.load(suiteId).info().attributes());
            Map<String, Object> root = Json.object();
            Map<String, Object> attributes = Json.object();
            for (String key : editor.keys()) {
                attributes.put(key, editor.get(key));
            }
            root.put("attributes", attributes);
            List<Object> problems = new ArrayList<Object>();
            for (JadEditor.Problem problem : editor.validate()) {
                Map<String, Object> entry = Json.object();
                entry.put("severity", problem.isError() ? "error" : "warning");
                entry.put("attribute", problem.attribute());
                entry.put("message", problem.message());
                problems.add(entry);
            }
            root.put("problems", problems);
            root.put("valid", Boolean.valueOf(editor.isValid()));
            root.put("text", editor.toDescriptor());
            return Json.write(root);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Records of one store, rendered for the RMS editor. */
    public String recordsJson(String suiteId, String storeName) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            RmsEditor editor = new RmsEditor(library.records(suiteId), now());
            Map<String, Object> root = Json.object();
            List<Object> records = new ArrayList<Object>();
            for (RmsEditor.Record record : editor.records(storeName)) {
                Map<String, Object> entry = Json.object();
                entry.put("id", Integer.valueOf(record.id()));
                entry.put("size", Integer.valueOf(record.size()));
                entry.put("hex", record.asHex());
                entry.put("text", record.asText());
                entry.put("int", Integer.valueOf(record.asInt()));
                records.add(entry);
            }
            root.put("store", storeName);
            root.put("records", records);
            return Json.write(root);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Rewrites one record from hex, as typed into the editor. */
    public String setRecordHex(String suiteId, String storeName, int recordId, String hex) {
        try {
            RmsEditor editor = new RmsEditor(library.records(suiteId), now());
            boolean changed = editor.setRecord(storeName, recordId, RmsEditor.parseHex(hex));
            return changed ? ok("recordId", String.valueOf(recordId))
                    : error("No record " + recordId + " in " + storeName);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String deleteRecord(String suiteId, String storeName, int recordId) {
        try {
            RmsEditor editor = new RmsEditor(library.records(suiteId), now());
            return editor.deleteRecord(storeName, recordId)
                    ? ok("recordId", String.valueOf(recordId))
                    : error("No record " + recordId + " in " + storeName);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Builds a crash report for the running session. */
    public String crashReportText(String message) {
        if (session == null) {
            return "";
        }
        return CrashReport.from(session,
                new VmError(message == null ? "Reported by the user" : message)).render();
    }

    // ------------------------------------------------------------- helpers

    private long now() {
        return host == null ? System.currentTimeMillis() : host.currentTimeMillis();
    }

    private static String ok(String key, String value) {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put(key, value);
        return Json.write(json);
    }

    private static String error(String message) {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.FALSE);
        json.put("error", message == null ? "Unknown error" : message);
        return Json.write(json);
    }
}
