package com.mobicore.core.bridge;

import com.mobicore.core.emu.EmulatorLog;
import com.mobicore.core.audio.AudioSink;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.emu.SaveState;
import com.mobicore.core.emu.SpeedClock;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.library.BatchImport;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryArchive;
import com.mobicore.core.library.PresetStore;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.AppSettings;
import com.mobicore.core.model.AutoSetup;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.model.MidletEntry;
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
    /** Where sound goes once a game starts; recorded if the app sets none. */
    private AudioSink audioSink;

    public MobiCoreFacade() {
        this(new LocalVfs());
    }

    public MobiCoreFacade(Vfs vfs) {
        this.vfs = vfs;
    }

    /** Overrides platform services; iOS supplies its own clock and console. */
    /**
     * Gives the emulator a speaker. Without one it still plays every sound —
     * into a recorder, where the developer tools can read it back — so a
     * platform that has not wired audio up yet runs games rather than
     * crashing on the first beep.
     */
    public void setAudioSink(AudioSink sink) {
        this.audioSink = sink;
        if (session != null) {
            session.setAudio(sink);
        }
    }

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
            applyDefaultPreset(result.entry().suiteId());
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

    /**
     * Renames a game as the library lists it. The name a user gives a game is
     * the one they look for it under; the manifest keeps its own.
     */
    public String renameGame(String suiteId, String title) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return ok("title", library.rename(suiteId, title).title());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Puts the suite's own title back. */
    public String resetTitle(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return ok("title", library.resetTitle(suiteId).title());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Replaces the cover art. PNG only — see
     * {@link GameLibrary#setArtwork(String, byte[])}.
     */
    public String setArtwork(String suiteId, byte[] png) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return ok("artwork", String.valueOf(library.setArtwork(suiteId, png).hasArtwork()));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Puts the icon the suite ships back on the tile. */
    public String resetArtwork(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return ok("artwork", String.valueOf(library.resetArtwork(suiteId).hasArtwork()));
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
            // Volume is the one setting a user changes expecting it to take
            // effect now, mid-game, rather than at the next start.
            if (session != null && profile.suiteId().equals(activeSuiteId)) {
                session.context().setMasterVolume(profile.volume(), profile.isMuted());
            }
            return ok("suiteId", profile.suiteId());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Throws away hand-set values and configures the game from the game
     * again — the escape hatch for anyone who changed a setting, broke the
     * game, and wants out without knowing what they changed.
     */
    public String autoSetup(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            GameProfile current = library.profile(suiteId);
            AutoSetup.Result result = AutoSetup.configure(library.load(suiteId));
            GameProfile fresh = result.profile();
            if (current != null) {
                // Play history and the user's own choices about this game —
                // volume, favourite — are theirs, not detections.
                fresh.setVolume(current.volume());
                fresh.setMuted(current.isMuted());
                fresh.setFavourite(current.isFavourite());
            }
            library.saveProfile(fresh);
            if (session != null && suiteId.equals(activeSuiteId)) {
                session.context().setMasterVolume(fresh.volume(), fresh.isMuted());
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("device", fresh.device().resolution());
            json.put("notes", new ArrayList<Object>(result.notes()));
            return Json.write(json);
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

    /**
     * Turns a game's screen, and remembers which way it was left.
     *
     * <p>Auto-setup already turns a game written for a wide screen, but a
     * handset's own screen was not always the one a game drew on: some drew
     * sideways on a portrait screen and expected the player to turn the
     * phone. That is a decision only the player can make, so it is one
     * button, not a settings page.</p>
     *
     * @param orientation {@link DeviceProfile#ORIENTATION_PORTRAIT} or
     *     {@link DeviceProfile#ORIENTATION_LANDSCAPE}
     */
    public String setOrientation(String suiteId, int orientation) {
        return applyOrientation(suiteId, orientation == DeviceProfile.ORIENTATION_LANDSCAPE
                ? DeviceProfile.ORIENTATION_LANDSCAPE : DeviceProfile.ORIENTATION_PORTRAIT);
    }

    /**
     * Saves a picture of what is on screen right now.
     *
     * <p>The one thing a player wants mid-game that the game itself cannot
     * do: a J2ME MIDlet has no way of showing anyone what just happened in
     * it.</p>
     */
    public String takeScreenshot() {
        if (library == null || session == null || activeSuiteId == null) {
            return error("No game is running");
        }
        try {
            String path = library.writeScreenshot(activeSuiteId, session.screenshotPng());
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("path", path);
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Which keys the keypad shows, cycled the way J2ME Loader's "switch
     * layout" does it: full, arrows only, numbers only, hidden.
     *
     * <p>Worth a place in the in-game menu rather than a settings page: a
     * player works out that a game only uses the pad while playing it, and
     * dropping the numbers hands that space back to the game there and
     * then.</p>
     */
    public String cycleKeypadLayout(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setKeypadLayout((profile.keypadLayout() + 1) % 4);
            library.saveProfile(profile);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("keypadLayout", Integer.valueOf(profile.keypadLayout()));
            json.put("name", profile.keypadLayoutName());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String setKeypadLayout(String suiteId, int layout) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setKeypadLayout(layout);
            library.saveProfile(profile);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("keypadLayout", Integer.valueOf(profile.keypadLayout()));
            json.put("name", profile.keypadLayoutName());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Portrait to landscape and back: what the rotate button calls. */
    public String toggleOrientation(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            return applyOrientation(suiteId,
                    profile.orientation() == DeviceProfile.ORIENTATION_LANDSCAPE
                            ? DeviceProfile.ORIENTATION_PORTRAIT
                            : DeviceProfile.ORIENTATION_LANDSCAPE);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    private String applyOrientation(String suiteId, int orientation) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setOrientation(orientation);
            library.saveProfile(profile);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("orientation", Integer.valueOf(orientation));
            json.put("landscape", Boolean.valueOf(
                    orientation == DeviceProfile.ORIENTATION_LANDSCAPE));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Points one virtual button at a different key code.
     *
     * <p>The presets are a guess, and a wrong guess looks like a broken
     * emulator: the game simply does not respond. This is the way out, and
     * the reason the keypad stops calling itself "Nokia" afterwards.</p>
     */
    public String setKeyMapping(String suiteId, String button, int keyCode) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            boolean known = false;
            int[] choices = InputProfile.keyChoices();
            for (int i = 0; i < choices.length; i++) {
                known = known || choices[i] == keyCode;
            }
            if (!known) {
                return error("No handset ever sent key " + keyCode);
            }
            profile.input().setMapping(button, keyCode);
            library.saveProfile(profile);
            if (session != null && suiteId.equals(activeSuiteId)) {
                session.profile().input().setMapping(button, keyCode);
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("button", button);
            json.put("keyCode", Integer.valueOf(keyCode));
            json.put("keyName", MidpContext.keyName(keyCode));
            json.put("preset", profile.input().presetName());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Every key code a button can be pointed at, with its name. */
    public String keyChoicesJson() {
        List<Object> choices = new ArrayList<Object>();
        int[] codes = InputProfile.keyChoices();
        for (int i = 0; i < codes.length; i++) {
            Map<String, Object> choice = Json.object();
            choice.put("keyCode", Integer.valueOf(codes[i]));
            choice.put("keyName", MidpContext.keyName(codes[i]));
            choices.add(choice);
        }
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("keys", choices);
        return Json.write(json);
    }

    /**
     * Turns auto-repeat on or off for one button.
     *
     * <p>Half the shooters of the era expect a thumb hammering the keypad: a
     * shot per press, no auto-fire, and a level that cannot be won without
     * mashing. Holding a key is not that input — a game reading
     * {@code keyPressed} sees one press however long it is held — so turbo
     * lets go and presses again on the player's behalf.</p>
     *
     * @param intervalMs milliseconds between presses, or 0 to switch it off
     */
    public String setTurbo(String suiteId, String button, int intervalMs) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.input().setTurbo(button, intervalMs);
            library.saveProfile(profile);
            if (session != null && suiteId.equals(activeSuiteId)) {
                // The running game takes it now: a player switching turbo on
                // is switching it on for the fight they are in.
                session.profile().input().setTurbo(button, intervalMs);
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("button", button);
            json.put("turbo", Integer.valueOf(profile.input().turboFor(button)));
            return Json.write(json);
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

    /**
     * Every picture taken of one game, newest first.
     *
     * <p>Saving a screenshot that nothing can show again is a dead end, so
     * the list carries what a gallery needs: the file's name, when it was
     * taken, and how big it is.</p>
     */
    public String screenshotsJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            List<String> names = library.screenshotsFor(suiteId);
            List<Object> shots = new ArrayList<Object>();
            for (int i = names.size() - 1; i >= 0; i--) {
                String name = names.get(i);
                Map<String, Object> shot = Json.object();
                shot.put("name", name);
                shot.put("takenAt", Long.valueOf(takenAt(name)));
                byte[] png = library.readScreenshot(suiteId, name);
                shot.put("bytes", Integer.valueOf(png == null ? 0 : png.length));
                shots.add(shot);
            }
            Map<String, Object> root = Json.object();
            root.put("ok", Boolean.TRUE);
            root.put("screenshots", shots);
            return Json.write(root);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** The moment in the file's own name, or zero when it is not one. */
    private static long takenAt(String name) {
        int dot = name.indexOf('.');
        String digits = dot > 0 ? name.substring(0, dot) : name;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public byte[] screenshot(String suiteId, String name) {
        try {
            byte[] png = library == null ? null : library.readScreenshot(suiteId, name);
            return png == null ? new byte[0] : png;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public String deleteScreenshot(String suiteId, String name) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return library.deleteScreenshot(suiteId, name)
                    ? ok("deleted", name)
                    : error("No such screenshot");
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * The whole library as one file, to carry to the next phone.
     *
     * <p>Per-game backups are the wrong shape for that: eighty games means
     * eighty transfers, and whoever is doing that at eleven at night gets to
     * game sixty and gives up.</p>
     */
    public byte[] exportLibrary() {
        if (library == null) {
            return new byte[0];
        }
        try {
            return LibraryArchive.export(vfs, layout);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Puts such a file back, then reopens the library over it. */
    public String importLibrary(byte[] archive) {
        if (library == null) {
            return error("The library is not open");
        }
        if (archive == null || archive.length == 0) {
            return error("Tệp rỗng");
        }
        try {
            LibraryArchive.Report report = LibraryArchive.restore(vfs, layout, archive);
            // Everything the library holds in memory came from the files that
            // were just written over, so it is read again from scratch.
            library.open();
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("files", Integer.valueOf(report.files()));
            json.put("games", Integer.valueOf(library.size()));
            json.put("bytes", Long.valueOf(report.bytes()));
            json.put("summary", report.summary());
            return Json.write(json);
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
        return startGame(suiteId, null);
    }

    /**
     * Starts one MIDlet out of a suite.
     *
     * <p>A JAR often holds more than one — the game, a help screen, a
     * settings screen, sometimes a second game — and until now only the one
     * the manifest listed first could ever run. Which one was chosen is
     * remembered with the game, so the play button reopens what the player
     * thinks of as the game.</p>
     *
     * @param midletClass the class to start, or null for the remembered one
     */
    public String startGame(String suiteId, String midletClass) {
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
            if (audioSink != null) {
                session.setAudio(audioSink);
            }
            String wanted = midletClass != null && midletClass.length() > 0
                    ? midletClass
                    : profile.midletClass();
            if (wanted.length() > 0 && hasMidlet(suite, wanted)) {
                session.start(wanted);
                profile.setMidletClass(wanted);
            } else {
                // Either nothing was asked for, or the suite no longer holds
                // what was remembered — a game reinstalled from a different
                // build, say. Falling back to the first one starts something
                // rather than failing on a stale name.
                session.start();
                profile.setMidletClass("");
            }
            activeSuiteId = suiteId;
            startedAt = now();
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

    /** True when the suite really holds that MIDlet. */
    private static boolean hasMidlet(SuiteLoader suite, String className) {
        List<MidletEntry> midlets = suite.info().midlets();
        for (int i = 0; i < midlets.size(); i++) {
            if (className.equals(midlets.get(i).className())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every MIDlet inside one suite.
     *
     * <p>Shown only when there is more than one: a picker over a list of one
     * is a question with a single answer.</p>
     */
    public String midletsJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            SuiteLoader suite = library.load(suiteId);
            GameProfile profile = library.profile(suiteId);
            String chosen = profile == null ? "" : profile.midletClass();
            List<Object> list = new ArrayList<Object>();
            List<MidletEntry> midlets = suite.info().midlets();
            for (int i = 0; i < midlets.size(); i++) {
                MidletEntry midlet = midlets.get(i);
                Map<String, Object> entry = Json.object();
                entry.put("name", midlet.name());
                entry.put("className", midlet.className());
                entry.put("icon", midlet.iconPath() == null ? "" : midlet.iconPath());
                entry.put("chosen", Boolean.valueOf(chosen.length() == 0
                        ? i == 0
                        : chosen.equals(midlet.className())));
                list.add(entry);
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("midlets", list);
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
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

    /**
     * Labels the two softkeys should show, as JSON. A handset leaves them blank
     * until a MIDlet registers a Command; these are what it registered.
     */
    public String softKeysJson() {
        Map<String, Object> json = Json.object();
        json.put("left", session == null ? null : session.leftSoftKeyLabel());
        json.put("right", session == null ? null : session.rightSoftKeyLabel());
        // When the screen carries the command bar, tapping it runs the
        // commands and the keypad leaves those two keys out.
        json.put("bar", Boolean.valueOf(session != null && session.showsSoftKeyBar()));
        return Json.write(json);
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

    /**
     * The library, filtered and ordered as the person asked for.
     *
     * <p>Same shape as {@link #libraryJson()} so a screen can swap one for
     * the other: searching is not a different kind of listing.</p>
     *
     * @param query what was typed; marks are ignored, empty lists everything
     * @param sort {@code GameLibrary.SORT_*}
     */
    public String searchJson(String query, int sort) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            Map<String, GameProfile> profiles = library.allProfiles();
            List<LibraryEntry> found = library.sort(library.search(query), sort, profiles);
            Map<String, Object> root = Json.object();
            List<Object> games = new ArrayList<Object>();
            for (int i = 0; i < found.size(); i++) {
                LibraryEntry entry = found.get(i);
                Map<String, Object> game = entry.toJson();
                GameProfile profile = profiles.get(entry.suiteId());
                if (profile != null) {
                    game.put("settings", profile.toJson());
                }
                games.add(game);
            }
            root.put("ok", Boolean.TRUE);
            root.put("query", query == null ? "" : query);
            root.put("sort", Integer.valueOf(sort));
            root.put("games", games);
            return Json.write(root);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Remembers the order the library should open in. */
    public String setLibrarySort(int sort) {
        AppSettings settings = appSettings();
        settings.setLibrarySort(sort);
        return writeAppSettings(settings);
    }

    // -------------------------------------------------------- app settings

    /**
     * Settings that belong to the person, not to a game: the theme, the
     * library's sort order.
     */
    public String appSettingsJson() {
        return Json.write(appSettings().toJson());
    }

    /** Switches the interface between light, dark and following the phone. */
    public String setTheme(int theme) {
        AppSettings settings = appSettings();
        settings.setTheme(theme);
        return writeAppSettings(settings);
    }

    /** Light to dark to system and back, for a one-tap toggle. */
    public String cycleTheme() {
        AppSettings settings = appSettings();
        settings.setTheme(settings.nextTheme());
        return writeAppSettings(settings);
    }

    public String updateAppSettings(String json) {
        try {
            return writeAppSettings(AppSettings.fromJson(Json.readObject(json)));
        } catch (RuntimeException e) {
            return error("Cài đặt không hợp lệ");
        }
    }

    private AppSettings appSettings() {
        try {
            String path = layout.settingsPath();
            if (vfs != null && vfs.exists(path)) {
                return AppSettings.fromJson(
                        Json.readObject(new String(vfs.read(path), "UTF-8")));
            }
        } catch (IOException e) {
            // A settings file that cannot be read is not worth failing over:
            // the defaults are all usable, and the next write repairs it.
        }
        return new AppSettings();
    }

    private String writeAppSettings(AppSettings settings) {
        try {
            vfs.mkdirs(layout.root());
            vfs.write(layout.settingsPath(), Json.write(settings.toJson()).getBytes("UTF-8"));
            Map<String, Object> json = settings.toJson();
            json.put("ok", Boolean.TRUE);
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Imports everything the user picked at once.
     *
     * <p>Nobody with a J2ME collection has one game: they have a folder of
     * eighty, often as {@code .jar} and {@code .jad} pairs and often inside a
     * zip. Each file is reported on separately, because one bad download must
     * not stop the rest and the user should be told which one it was.</p>
     */
    public String importMany(String[] names, byte[][] payloads) {
        if (library == null) {
            return error("The library is not open");
        }
        BatchImport.Report report = BatchImport.run(library, names, payloads);
        applyDefaultPresetToAll();
        List<Object> files = new ArrayList<Object>();
        for (int i = 0; i < report.outcomes().size(); i++) {
            BatchImport.Outcome outcome = report.outcomes().get(i);
            Map<String, Object> file = Json.object();
            file.put("name", outcome.name());
            file.put("status", Integer.valueOf(outcome.status()));
            file.put("detail", outcome.detail());
            files.add(file);
        }
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("installed", Integer.valueOf(report.installed()));
        json.put("failed", Integer.valueOf(report.count(BatchImport.Outcome.FAILED)));
        json.put("skipped", Integer.valueOf(report.count(BatchImport.Outcome.SKIPPED)));
        json.put("summary", report.summary());
        json.put("files", files);
        return Json.write(json);
    }

    // --------------------------------------------------------------- presets

    private PresetStore presets() {
        return new PresetStore(vfs, layout);
    }

    /**
     * Every preset, and which one new games start from.
     */
    public String presetsJson() {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("presets", new ArrayList<Object>(presets().names()));
            json.put("defaultPreset", appSettings().defaultPreset());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Saves one game's settings under a name, to apply to others later. */
    public String savePreset(String name, String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            GameProfile profile = library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            presets().save(name, profile);
            return ok("preset", name);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Puts a saved preset's settings onto a game. */
    public String applyPreset(String name, String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            GameProfile profile = library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            GameProfile applied = presets().apply(name, profile);
            if (applied == null) {
                return error("No preset named " + name);
            }
            library.saveProfile(applied);
            return ok("preset", name);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String deletePreset(String name) {
        try {
            return presets().delete(name) ? ok("deleted", name) : error("No preset named " + name);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Which preset a newly imported game starts from; empty for none. */
    public String setDefaultPreset(String name) {
        if (name != null && name.length() > 0 && !presets().exists(name)) {
            return error("No preset named " + name);
        }
        AppSettings settings = appSettings();
        settings.setDefaultPreset(name);
        String result = writeAppSettings(settings);
        return Json.bool(Json.readObject(result), "ok", false)
                ? ok("defaultPreset", settings.defaultPreset())
                : result;
    }

    /**
     * Puts the default preset on a freshly imported game.
     *
     * <p>Silent by design: a preset that cannot be read must not turn an
     * import into a failure. The game keeps what auto-setup worked out, which
     * is a working configuration either way.</p>
     */
    private void applyDefaultPreset(String suiteId) {
        String name = appSettings().defaultPreset();
        if (name == null || name.length() == 0) {
            return;
        }
        try {
            GameProfile profile = library.profile(suiteId);
            GameProfile applied = profile == null ? null : presets().apply(name, profile);
            if (applied != null) {
                library.saveProfile(applied);
            }
        } catch (IOException e) {
            // Nothing to do about it here; the import itself succeeded.
        }
    }

    private void applyDefaultPresetToAll() {
        String name = appSettings().defaultPreset();
        if (name == null || name.length() == 0) {
            return;
        }
        try {
            List<LibraryEntry> all = library.all();
            for (int i = 0; i < all.size(); i++) {
                GameProfile profile = library.profile(all.get(i).suiteId());
                if (profile != null && profile.isAuto()) {
                    // Only the ones nobody has configured by hand: a preset
                    // applied on import must not undo a setting someone
                    // deliberately changed on a game they already had.
                    GameProfile applied = presets().apply(name, profile);
                    if (applied != null) {
                        library.saveProfile(applied);
                    }
                }
            }
        } catch (IOException e) {
            // As above: the import stands.
        }
    }

    // ----------------------------------------------------------- text entry

    /**
     * True while the game is waiting for text.
     *
     * <p>The app watches this to raise the phone's own keyboard. Multi-tap on
     * a numeric pad was the only way a handset could take letters; asking for
     * that today, with a real keyboard in the user's hand, would be a museum
     * exhibit rather than a feature.</p>
     */
    public boolean isTextInputActive() {
        return session != null && session.isTextInputActive();
    }

    /** What the field holds now, so the keyboard opens on it. */
    public String textInput() {
        return session == null ? "" : session.textInput();
    }

    /** Puts what the keyboard produced into the field. */
    public String setTextInput(String value) {
        if (session == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        return session.setTextInput(value)
                ? ok("text", session.textInput())
                : error("Game không đang chờ nhập chữ");
    }

    // --------------------------------------------------------- save states

    /**
     * Saves the running game where it stands, with a picture of the screen.
     *
     * <p>Called when the player leaves a game as well as on demand: a J2ME
     * game that is closed halfway through a level otherwise loses the level,
     * and on a phone leaving is not always the player's decision.</p>
     */
    public String saveState() {
        return saveState(StorageLayout.SLOT_AUTO);
    }

    /**
     * Saves into one slot.
     *
     * <p>Slot zero is the emulator's own, written on the way out. The
     * numbered ones are the player's: somewhere to stand before a boss and
     * come back to, which one slot per game cannot be — leaving the game
     * would overwrite it.</p>
     */
    public String saveState(int slot) {
        if (session == null || activeSuiteId == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        try {
            byte[] state = SaveState.capture(session);
            byte[] screenshot = PngWriter.encode(session.screen());
            library.writeSaveState(activeSuiteId, slot, state, screenshot);
            return ok("bytes", String.valueOf(state.length));
        } catch (SaveState.NotSavable e) {
            return error(e.getMessage());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Starts a game and puts it back where its saved state left it.
     *
     * <p>The game is started normally first: that loads its classes and
     * builds a working machine, and only then is the heap replaced. A game
     * with no saved state simply starts.</p>
     */
    public String resumeGame(String suiteId) {
        return resumeGame(suiteId, StorageLayout.SLOT_AUTO);
    }

    public String resumeGame(String suiteId, int slot) {
        String started = startGame(suiteId);
        if (!Json.bool(Json.readObject(started), "ok", false)) {
            return started;
        }
        try {
            byte[] state = library.readSaveState(suiteId, slot);
            if (state == null) {
                // Said either way, so the caller never has to guess whether a
                // missing answer means "from the beginning".
                Map<String, Object> json = Json.readObject(started);
                json.put("resumed", Boolean.FALSE);
                return Json.write(json);
            }
            SaveState.restore(session, state);
            Map<String, Object> json = Json.readObject(started);
            json.put("resumed", Boolean.TRUE);
            return Json.write(json);
        } catch (SaveState.NotSavable e) {
            // The game is running from its beginning, which is worse than
            // resuming and far better than not starting: say so and carry on.
            Map<String, Object> json = Json.readObject(started);
            json.put("resumed", Boolean.FALSE);
            json.put("warning", e.getMessage());
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public boolean hasSaveState(String suiteId) {
        return hasSaveState(suiteId, StorageLayout.SLOT_AUTO);
    }

    public boolean hasSaveState(String suiteId, int slot) {
        return library != null && library.hasSaveState(suiteId, slot);
    }

    // ---------------------------------------------------------------- rewind

    /**
     * Takes back the last second or so of play.
     *
     * <p>What it is for: a game that restarts a level on one mistake, which
     * was fair on a bus and is not fair now. Each step lands a second further
     * back, so holding the control walks backwards through the mistake.</p>
     */
    public String rewindStep() {
        if (session == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        boolean moved = session.rewind().stepBack(session);
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.valueOf(moved));
        json.put("seconds", Integer.valueOf(session.rewind().depth()));
        if (!moved) {
            json.put("error", session.rewind().isEnabled()
                    ? "Chưa có gì để tua lại"
                    : "Tua lại đang tắt");
        }
        return Json.write(json);
    }

    /** How much history there is, and whether it is being kept at all. */
    public String rewindJson() {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("enabled", Boolean.valueOf(session == null || session.rewind().isEnabled()));
        json.put("seconds", Integer.valueOf(session == null ? 0 : session.rewind().depth()));
        return Json.write(json);
    }

    /**
     * Keeps history, or stops and throws away what was kept.
     *
     * <p>Off means off: leaving several megabytes of heap captures around
     * after someone has said they do not want the feature is the opposite of
     * what they asked for.</p>
     */
    public String setRewindEnabled(boolean enabled) {
        if (session == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        session.rewind().setEnabled(enabled);
        return rewindJson();
    }

    // ----------------------------------------------------------------- speed

    /**
     * How fast the running game is playing, as a percentage of a handset's
     * pace.
     *
     * <p>Not a frame rate: a J2ME game paces itself off the clock, so this
     * changes what it is told the time is, and the game does the rest with
     * its own logic and animations intact.</p>
     */
    public String speedJson() {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        int speed = session == null ? SpeedClock.NORMAL : session.speed();
        json.put("speed", Integer.valueOf(speed));
        json.put("label", speedLabel(speed));
        return Json.write(json);
    }

    public String setSpeed(int percent) {
        if (session == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        session.setSpeed(percent);
        return speedJson();
    }

    /** Steps through the speeds a control offers: half, normal, double, triple. */
    public String cycleSpeed() {
        if (session == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        session.cycleSpeed();
        return speedJson();
    }

    private static String speedLabel(int speed) {
        if (speed % 100 == 0) {
            return (speed / 100) + "×";
        }
        return (speed / 100) + "," + ((speed % 100) / 10) + "×";
    }

    /**
     * Loads a slot into the game already running.
     *
     * <p>Separate from {@link #resumeGame}: that starts a game, this one is
     * what the in-game menu calls, and reloading a slot mid-play must not
     * throw away the machine the game is running on.</p>
     */
    public String loadState(int slot) {
        if (session == null || activeSuiteId == null) {
            return error("Không có trò chơi nào đang chạy");
        }
        try {
            byte[] state = library.readSaveState(activeSuiteId, slot);
            if (state == null) {
                return error("Ô này chưa có gì");
            }
            SaveState.restore(session, state);
            return ok("slot", String.valueOf(slot));
        } catch (SaveState.NotSavable e) {
            return error(e.getMessage());
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Every slot of one game: which are full, when they were written, and
     * whether they carry a picture.
     */
    public String saveStatesJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            List<Object> slots = new ArrayList<Object>();
            for (int slot = 0; slot <= StorageLayout.SLOTS; slot++) {
                Map<String, Object> entry = Json.object();
                boolean used = library.hasSaveState(suiteId, slot);
                entry.put("slot", Integer.valueOf(slot));
                entry.put("auto", Boolean.valueOf(slot == StorageLayout.SLOT_AUTO));
                entry.put("used", Boolean.valueOf(used));
                entry.put("savedAt", Long.valueOf(library.saveStateTime(suiteId, slot)));
                byte[] shot = library.saveStateThumbnail(suiteId, slot);
                entry.put("thumbnail", Boolean.valueOf(shot != null && shot.length > 0));
                slots.add(entry);
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("slots", slots);
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** The screen as it looked when the game was saved, as PNG bytes. */
    public byte[] saveStateThumbnail(String suiteId) {
        return saveStateThumbnail(suiteId, StorageLayout.SLOT_AUTO);
    }

    public byte[] saveStateThumbnail(String suiteId, int slot) {
        try {
            byte[] data = library == null ? null : library.saveStateThumbnail(suiteId, slot);
            return data == null ? new byte[0] : data;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public String deleteSaveState(String suiteId) {
        return deleteSaveState(suiteId, StorageLayout.SLOT_AUTO);
    }

    public String deleteSaveState(String suiteId, int slot) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            return ok("removed", String.valueOf(library.deleteSaveState(suiteId, slot)));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Saves the running game before it is put away, then stops it.
     *
     * <p>What "leaving a game" should mean on a phone: the player gets back
     * what they had. A game holding something that cannot be saved is stopped
     * anyway — refusing to close it would be worse than losing the position.</p>
     */
    public String stopGameSaving() {
        if (session == null) {
            return ok("saved", "false");
        }
        String saved = saveState();
        stopGame();
        return saved;
    }

    public void stopGame() {
        if (session != null) {
            recordPlayTime();
            session.destroy();
            session = null;
            activeSuiteId = null;
        }
    }

    /** When the running game was started, on the wall clock. */
    private long startedAt;

    /**
     * Adds this session's length to the game's total.
     *
     * <p>Measured on the wall clock rather than the game's own: at triple
     * speed the player still spent the minutes they spent, and a total that
     * shrank because someone used fast-forward would be measuring the wrong
     * thing.</p>
     */
    private void recordPlayTime() {
        if (library == null || activeSuiteId == null || startedAt == 0) {
            return;
        }
        long elapsed = now() - startedAt;
        startedAt = 0;
        if (elapsed <= 0) {
            return;
        }
        try {
            GameProfile profile = library.profile(activeSuiteId);
            if (profile != null) {
                profile.addPlayedMs(elapsed);
                library.saveProfile(profile);
            }
        } catch (IOException e) {
            // A total that missed one session is worth more than a crash on
            // the way out of a game.
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
