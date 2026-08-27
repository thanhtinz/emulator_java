package com.mobicore.core.bridge;

import com.mobicore.core.audio.AudioSink;
import com.mobicore.core.emu.ClipRecorder;
import com.mobicore.core.emu.EmulatorLog;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.emu.SaveState;
import com.mobicore.core.emu.SpeedClock;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.library.BatchImport;
import com.mobicore.core.library.CollectionStore;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryArchive;
import com.mobicore.core.library.PresetStore;
import com.mobicore.core.library.ShareExport;
import com.mobicore.core.library.UrlInstaller;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.AppSettings;
import com.mobicore.core.model.AutoSetup;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.GamepadProfile;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.midp.MidpFiles;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.model.KeypadArrangement;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.model.TiltProfile;
import com.mobicore.core.mod.ModManager;
import com.mobicore.core.mod.ModPackage;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.net.HttpTransport;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.net.NetworkTransport;
import com.mobicore.core.net.RealSockets;
import com.mobicore.core.net.SocketTransport;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.emu.CrashDiagnosis;
import com.mobicore.core.emu.SystemProperties;
import com.mobicore.core.tools.CrashReport;
import com.mobicore.core.tools.JadEditor;
import com.mobicore.core.tools.RmsEditor;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.VmCancelled;
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
    /** Where downloads go through; built the first time one is asked for. */
    private NetworkStack installerNetwork;
    /** What a running game reaches the network through; real unless replaced. */
    private NetworkTransport gameTransport = new HttpTransport();
    private SocketTransport gameSockets = new RealSockets();
    /** The player's shelves; read the first time they are asked for. */
    private CollectionStore collectionStore;
    private StorageLayout layout;
    private EmulatorSession session;
    private String activeSuiteId;
    /** Lần hỏng gần nhất, giữ lại sau khi game đã tắt để còn kể lại được. */
    private CrashDiagnosis crash;
    private String crashSuiteId = "";
    private String crashGame = "";
    private String crashStack = "";
    private long crashAt;
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

    /**
     * The one game to offer on the way in.
     *
     * <p>Opening the app to play the game you were just playing is the most
     * common thing anyone does with it, and today it costs three taps: find
     * the game, open it, press play. This is that in one.</p>
     *
     * <p>It says which of the two it would do, because they are not the same
     * thing: carrying on from where a game was left is not starting it again,
     * and a player who is offered "continue" and gets a fresh start has lost
     * the thing they came back for.</p>
     */
    public String continueJson() {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            Map<String, GameProfile> profiles = library.allProfiles();
            LibraryEntry latest = null;
            GameProfile latestProfile = null;
            for (LibraryEntry entry : library.all()) {
                GameProfile profile = profiles.get(entry.suiteId());
                if (profile == null || profile.lastPlayed() <= 0) {
                    continue;
                }
                if (latestProfile == null || profile.lastPlayed() > latestProfile.lastPlayed()) {
                    latest = entry;
                    latestProfile = profile;
                }
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            if (latest == null) {
                // Nothing has been played, so there is nothing to carry on
                // with: better an empty answer than a card offering to
                // continue a game nobody has started.
                json.put("has", Boolean.FALSE);
                return Json.write(json);
            }
            boolean saved = library.readSaveState(latest.suiteId(), 0) != null;
            json.put("has", Boolean.TRUE);
            json.put("game", latest.toJson());
            json.put("suiteId", latest.suiteId());
            json.put("resumes", Boolean.valueOf(saved));
            json.put("action", saved ? "Chơi tiếp" : "Chơi lại");
            json.put("lastPlayed", Long.valueOf(latestProfile.lastPlayed()));
            json.put("playedName", GameProfile.playedName(latestProfile.playedMs()));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Starts whatever {@link #continueJson} offered.
     *
     * <p>Resolved here rather than by the caller passing the id back: between
     * the card being drawn and the button being pressed a game can be
     * uninstalled, and starting the one that is actually most recent is
     * better than failing on the one that was.</p>
     */
    public String continueGame() {
        Map<String, Object> card = Json.readObject(continueJson());
        if (!Json.bool(card, "has", false)) {
            return error("Chưa chơi game nào");
        }
        return resumeGame(Json.string(card, "suiteId", ""));
    }

    /**
     * The shelves the player has put their games on.
     *
     * <p>Every shelf comes back with how many games are on it, and — when a
     * game is named — whether that game is on it: the screen that shows this
     * is the one where a game is put on a shelf, and it has to show both.</p>
     *
     * @param suiteId a game to report membership for, or empty for none
     */
    public String collectionsJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        CollectionStore shelves = collections();
        List<Object> out = new ArrayList<Object>();
        List<String> names = shelves.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            List<String> games = shelves.gamesOn(name);
            Map<String, Object> shelf = Json.object();
            shelf.put("name", name);
            shelf.put("games", Integer.valueOf(games.size()));
            shelf.put("holds", Boolean.valueOf(suiteId != null && games.contains(suiteId)));
            out.add(shelf);
        }
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("collections", out);
        return Json.write(json);
    }

    /** Makes a shelf, empty, for games to be put on. */
    public String createCollection(String name) {
        try {
            return collections().create(name)
                    ? ok("name", name.trim())
                    : error("Tên này đã có rồi, hoặc chưa đặt tên");
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Puts a game on a shelf, or takes it off if it is already there. */
    public String toggleCollection(String name, String suiteId) {
        try {
            boolean added = collections().toggle(name, suiteId);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("name", name.trim());
            json.put("holds", Boolean.valueOf(added));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String renameCollection(String from, String to) {
        try {
            return collections().rename(from, to)
                    ? ok("name", to.trim())
                    : error("Không đổi được tên: tên mới đã có hoặc chưa đặt");
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Throws away a shelf. The games on it stay installed. */
    public String deleteCollection(String name) {
        try {
            return collections().delete(name)
                    ? ok("name", name.trim())
                    : error("Không có bộ sưu tập này");
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * The games on one shelf, in the library's own listing shape.
     *
     * <p>The same shape the whole library comes back in, so the screen that
     * draws a list of games does not need a second way to draw one.</p>
     */
    public String collectionJson(String name) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            List<String> ids = collections().gamesOn(name);
            List<Object> games = new ArrayList<Object>();
            for (int i = 0; i < ids.size(); i++) {
                LibraryEntry entry = library.find(ids.get(i));
                if (entry != null) {
                    games.add(entry.toJson());
                }
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("name", name.trim());
            json.put("games", games);
            return Json.write(json);
        } catch (RuntimeException e) {
            return error(String.valueOf(e.getMessage()));
        }
    }

    private CollectionStore collections() {
        if (collectionStore == null) {
            collectionStore = new CollectionStore(library.storage(), library.layout());
        }
        return collectionStore;
    }

    /**
     * Installs a game from a link.
     *
     * <p>These games arrive as a link before they arrive as a file. Fetching
     * it in a browser, finding it in Downloads and picking it out of a file
     * chooser is three steps for something the emulator can do in one.</p>
     *
     * <p>The download goes through the same network stack a game's own
     * connections do, so it is recorded and can be looked at afterwards. It is
     * allowed rather than asked about — the player typed the address, which is
     * the permission — but what was fetched, and from where, is reported back
     * rather than hidden.</p>
     */
    public String installFromUrl(String url) {
        if (library == null) {
            return error("The library is not open");
        }
        final NetworkStack downloads = installerNetwork();
        try {
            UrlInstaller.Download download = UrlInstaller.fetch(new UrlInstaller.Fetcher() {
                public NetworkTransport.Response fetch(String target) throws IOException {
                    NetworkTransport.Request request = new NetworkTransport.Request(target);
                    // A handset asked for exactly this, and a few servers of
                    // the era still refuse anything that does not.
                    request.headers.put("Accept",
                            "text/vnd.sun.j2me.app-descriptor, application/java-archive, */*");
                    return downloads.perform(request);
                }
            }, url);

            library.setClock(now());
            GameLibrary.InstallResult result = library.install(download.jar(), download.jad());
            applyDefaultPreset(result.entry().suiteId());
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("replaced", Boolean.valueOf(result.replaced()));
            json.put("game", result.entry().toJson());
            json.put("jarUrl", download.jarUrl());
            json.put("notes", new ArrayList<Object>(download.notes()));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * The network the installer downloads over.
     *
     * <p>Separate from a game's: a game has to ask before it connects, and a
     * download the player asked for by typing an address does not. It still
     * goes through a policy and a monitor, so the same rules about what is
     * recorded apply.</p>
     */
    private NetworkStack installerNetwork() {
        if (installerNetwork == null) {
            NetworkPolicy policy = new NetworkPolicy();
            policy.setMode(GameProfile.NETWORK_ALLOWED);
            installerNetwork = new NetworkStack(policy);
            installerNetwork.setTransport(new HttpTransport());
        }
        return installerNetwork;
    }

    /**
     * Points the installer at a different transport.
     *
     * <p>For tests and for the local server bridge: a game whose site is gone
     * can be served from the device instead.</p>
     */
    public void setInstallerTransport(NetworkTransport transport) {
        installerNetwork().setTransport(transport);
    }

    /**
     * Points a running game at a different network.
     *
     * <p>The local server bridge, from the game's side: a multiplayer title
     * whose lobby shut down years ago can be answered from the device. Tests
     * use the same door to play a whole conversation without a network card.
     * Either argument may be null to keep the real one.</p>
     */
    public void setGameNetwork(NetworkTransport transport, SocketTransport sockets) {
        if (transport != null) {
            gameTransport = transport;
        }
        if (sockets != null) {
            gameSockets = sockets;
        }
        if (session != null) {
            session.network().setTransport(gameTransport);
            session.network().setSocketTransport(gameSockets);
        }
    }

    /** What the installer fetched, so the player can see where it came from. */
    public String downloadsJson() {
        Map<String, Object> root = Json.object();
        List<Object> requests = new ArrayList<Object>();
        if (installerNetwork != null) {
            for (NetworkMonitor.Exchange exchange : installerNetwork.monitor().exchanges()) {
                requests.add(exchange.toJson());
            }
        }
        root.put("ok", Boolean.TRUE);
        root.put("downloads", requests);
        return Json.write(root);
    }

    public String uninstall(String suiteId, boolean keepData) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            // A shelf holding the name of something that is gone shows a
            // count nobody can reach.
            collections().forget(suiteId);
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
            return Json.write(profile.toJson());
        } catch (IOException e) {
            return error(e.getMessage());
        }
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
     * Starts recording the screen as an animation.
     *
     * <p>A screenshot says where the player got to; it cannot say how. A few
     * seconds of the game can, and a GIF plays wherever a picture plays, so
     * nobody has to install anything to watch it.</p>
     */
    public String startRecording() {
        if (session == null) {
            return error("No game is running");
        }
        session.clip().start(session.vm().host().currentTimeMillis());
        return recordingJson();
    }

    /**
     * Ends the recording and saves it beside the screenshots.
     *
     * <p>Encoding happens here, not while playing: the colour table is chosen
     * from the whole clip at once, so there is nothing to encode until the
     * clip is whole.</p>
     */
    public String stopRecording() {
        if (session == null || library == null || activeSuiteId == null) {
            return error("No game is running");
        }
        try {
            byte[] gif = session.clip().stop();
            if (gif == null) {
                return error("Chưa quay được khung hình nào");
            }
            String path = library.writeClip(activeSuiteId, gif);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("path", path);
            json.put("bytes", Integer.valueOf(gif.length));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Throws a recording away without saving it. */
    public String cancelRecording() {
        if (session == null) {
            return error("No game is running");
        }
        session.clip().cancel();
        return recordingJson();
    }

    /**
     * Whether a clip is being recorded, and how long it has got.
     *
     * <p>Asked on every drawn frame while recording, because a red dot that
     * does not count up is a red dot nobody trusts.</p>
     */
    public String recordingJson() {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        ClipRecorder clip = session == null ? null : session.clip();
        json.put("recording", Boolean.valueOf(clip != null && clip.isRecording()));
        json.put("frames", Integer.valueOf(clip == null ? 0 : clip.frameCount()));
        json.put("tenths", Integer.valueOf(clip == null ? 0 : clip.tenths()));
        json.put("full", Boolean.valueOf(clip != null && clip.isFull()));
        json.put("maxSeconds", Integer.valueOf(ClipRecorder.MAX_SECONDS));
        return Json.write(json);
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

    /**
     * How the virtual keypad looks: how solid, what shape, when it fades.
     *
     * <p>One call rather than three, because the screen that shows them shows
     * all three together and the keypad it is drawing has to be redrawn for
     * any of them.</p>
     */
    public String keypadJson(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("layout", Integer.valueOf(profile.keypadLayout()));
            json.put("layoutName", profile.keypadLayoutName());
            json.put("opacity", Integer.valueOf(profile.keypadOpacity()));
            json.put("shape", Integer.valueOf(profile.keypadShape()));
            json.put("shapeName", profile.keypadShapeName());
            json.put("fadeDelay", Integer.valueOf(profile.keypadFadeDelay()));
            json.put("fadeDelayName", profile.keypadFadeDelayName());
            // What it should be drawn at this instant, which is the opacity
            // above until the fade has had time to happen.
            json.put("drawOpacity", Integer.valueOf(session != null
                    && suiteId.equals(activeSuiteId)
                    ? session.keypadOpacity() : profile.keypadOpacity()));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Whether tilting the phone steers this game, and how far it must lean.
     *
     * <p>No J2ME handset could do this, so it is not emulation but a way to
     * play: it suits a racing game steered left and right, and suits nothing
     * else, which is why it is off until it is asked for.</p>
     */
    public String tiltJson(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            TiltProfile tilt = profile.tilt();
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("enabled", Boolean.valueOf(tilt.isEnabled()));
            json.put("sensitivity", Integer.valueOf(tilt.sensitivity()));
            json.put("axes", Integer.valueOf(tilt.axes()));
            json.put("axesName", tilt.axesName());
            json.put("inverted", Boolean.valueOf(tilt.isInverted()));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Những gì game đọc được khi nó hỏi máy nó đang chạy trên đó là máy gì.
     *
     * <p>Chỉ để đọc: máy ảo là một cỗ máy duy nhất và bảng này là của chung,
     * không có bản riêng cho từng game. Bày ra vì khi một game chạy sai vì
     * tưởng mình đang ở trên máy khác, đây là thứ cần nhìn.</p>
     */
    public String systemPropertiesJson() {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("platform", SystemProperties.PLATFORM);
        json.put("properties", SystemProperties.toJson());
        return Json.write(json);
    }

    /** Turns tilting on or off for one game. */
    public String setTiltEnabled(String suiteId, boolean enabled) {
        return withTilt(suiteId, enabled, -1, -1, -1);
    }

    /** How far the phone has to lean, 50-200 percent of the standard angle. */
    public String setTiltSensitivity(String suiteId, int percent) {
        return withTilt(suiteId, null, percent, -1, -1);
    }

    /** Which directions tilting may press; see {@link TiltProfile}. */
    public String setTiltAxes(String suiteId, int axes) {
        return withTilt(suiteId, null, -1, axes, -1);
    }

    /** Leaning left presses right, which some games were drawn the other way. */
    public String setTiltInverted(String suiteId, boolean inverted) {
        return withTilt(suiteId, null, -1, -1, inverted ? 1 : 0);
    }

    /**
     * One way in for all four, because they change one object and save it.
     *
     * @param enabled null to leave it alone
     * @param sensitivity a negative number to leave it alone
     * @param axes a negative number to leave it alone
     * @param inverted a negative number to leave it alone
     */
    private String withTilt(String suiteId, Boolean enabled, int sensitivity, int axes,
                            int inverted) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            TiltProfile tilt = profile.tilt();
            if (enabled != null) {
                tilt.setEnabled(enabled.booleanValue());
            }
            if (sensitivity >= 0) {
                tilt.setSensitivity(sensitivity);
            }
            if (axes >= 0) {
                tilt.setAxes(axes);
            }
            if (inverted >= 0) {
                tilt.setInverted(inverted == 1);
            }
            library.saveProfile(profile);
            if (session != null && suiteId.equals(activeSuiteId)) {
                // The running game takes it now: someone switching tilting on
                // is switching it on for the race they are in.
                TiltProfile live = session.profile().tilt();
                live.setEnabled(tilt.isEnabled());
                live.setSensitivity(tilt.sensitivity());
                live.setAxes(tilt.axes());
                live.setInverted(tilt.isInverted());
                if (!tilt.isEnabled()) {
                    session.releaseTilt();
                }
            }
            return tiltJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * The phone was tilted.
     *
     * <p>Called from the sensor, many times a second, so it answers with the
     * one number a front end has any use for: how many directions are held.</p>
     */
    public String tilted(int xMilli, int yMilli) {
        if (session == null) {
            return error("No game is running");
        }
        int held = session.tilted(xMilli / 1000f, yMilli / 1000f);
        return ok("held", String.valueOf(held));
    }

    /**
     * What a real controller's buttons do for one game.
     *
     * <p>Every control comes back, bound or not: the screen that maps them
     * shows the whole pad, because a player looking for "where is fire" needs
     * to see the button that is not fire too.</p>
     */
    public String gamepadJson(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            GamepadProfile pad = profile.gamepad();
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("enabled", Boolean.valueOf(pad.isEnabled()));
            json.put("custom", Boolean.valueOf(pad.isCustom()));
            List<Object> pads = new ArrayList<Object>();
            for (int i = 0; i < GamepadProfile.PADS.length; i++) {
                String name = GamepadProfile.PADS[i];
                Map<String, Object> entry = Json.object();
                entry.put("pad", name);
                entry.put("padName", GamepadProfile.padName(name));
                // What it is mapped to, not what it would press right now:
                // switching the pad off should not read as every button
                // having been unbound one at a time.
                String button = pad.mapping(name);
                entry.put("button", button);
                entry.put("buttonName", buttonName(button));
                pads.add(entry);
            }
            json.put("pads", pads);
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** What the settings screen calls one of the emulator's own buttons. */
    private static String buttonName(String button) {
        if (button == null || button.length() == 0) {
            return "Không dùng";
        }
        if ("up".equals(button)) return "Lên";
        if ("down".equals(button)) return "Xuống";
        if ("left".equals(button)) return "Trái";
        if ("right".equals(button)) return "Phải";
        if ("fire".equals(button)) return "Bắn";
        if ("softLeft".equals(button)) return "Phím mềm trái";
        if ("softRight".equals(button)) return "Phím mềm phải";
        if ("star".equals(button)) return "Phím *";
        if ("hash".equals(button)) return "Phím #";
        if ("clear".equals(button)) return "Xoá";
        if (button.startsWith("num")) {
            return "Phím " + button.substring(3);
        }
        return button;
    }

    /** Points one control at an emulator button, or at nothing to unbind it. */
    public String setPadMapping(String suiteId, String pad, String button) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.gamepad().map(pad, button);
            library.saveProfile(profile);
            applyGamepad(suiteId, profile);
            return gamepadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Whether controller input reaches the game at all. */
    public String setGamepadEnabled(String suiteId, boolean enabled) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.gamepad().setEnabled(enabled);
            library.saveProfile(profile);
            applyGamepad(suiteId, profile);
            return gamepadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Puts the pad back to the arrangement a J2ME game expects. */
    public String resetGamepad(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            GamepadProfile fresh = GamepadProfile.defaults();
            fresh.setEnabled(profile.gamepad().isEnabled());
            profile.setGamepad(fresh);
            library.saveProfile(profile);
            applyGamepad(suiteId, profile);
            return gamepadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * A control on a real pad was pressed.
     *
     * <p>The front end names the control it saw — Android, iOS and a keyboard
     * each have their own numbers for the same button — and the profile says
     * what it does.</p>
     */
    public String pressPad(String pad) {
        if (session == null) {
            return error("No game is running");
        }
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("pad", pad);
        // Whether anything happened, rather than whether the call arrived: a
        // control bound to nothing is not an error, and a front end showing
        // "pressed" for it would be showing a press the game never saw.
        json.put("pressed", Boolean.valueOf(
                session.profile().gamepad().buttonFor(pad).length() > 0));
        session.pressPad(pad);
        return Json.write(json);
    }

    public String releasePad(String pad) {
        if (session == null) {
            return error("No game is running");
        }
        session.releasePad(pad);
        return ok("pad", pad);
    }

    /** Hands the running game a pad mapping that was just edited. */
    private void applyGamepad(String suiteId, GameProfile edited) {
        if (session == null || !suiteId.equals(activeSuiteId)) {
            return;
        }
        session.profile().setGamepad(GamepadProfile.fromJson(edited.gamepad().toJson()));
    }

    /**
     * Where the keys have been dragged to, and how big they are drawn.
     *
     * <p>Every moved key comes back with it: the screen that lets keys be
     * dragged has to draw the keypad as it stands, and asking per key would
     * be twenty calls to draw one keypad.</p>
     */
    public String keypadArrangementJson(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            KeypadArrangement arrangement = profile.keypadArrangement();
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("scale", Integer.valueOf(arrangement.scale()));
            json.put("custom", Boolean.valueOf(arrangement.isCustom()));
            List<Object> keys = new ArrayList<Object>();
            List<String> moved = arrangement.movedKeys();
            for (int i = 0; i < moved.size(); i++) {
                String button = moved.get(i);
                Map<String, Object> key = Json.object();
                key.put("button", button);
                key.put("x", Integer.valueOf(Math.round(arrangement.offsetX(button) * 1000)));
                key.put("y", Integer.valueOf(Math.round(arrangement.offsetY(button) * 1000)));
                keys.add(key);
            }
            json.put("keys", keys);
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Drags one key to an offset from where the standard layout puts it.
     *
     * <p>Offsets are in thousandths of a key rather than pixels, because a
     * key is a different number of pixels upright, sideways, and on every
     * different phone — and the same arrangement has to hold for all of
     * them.</p>
     */
    public String moveKey(String suiteId, String button, int xMilli, int yMilli) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.keypadArrangement().move(button, xMilli / 1000f, yMilli / 1000f);
            library.saveProfile(profile);
            applyArrangement(suiteId, profile);
            return keypadArrangementJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** How big the keys are drawn, 60-160 percent of the standard size. */
    public String setKeyScale(String suiteId, int percent) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.keypadArrangement().setScale(percent);
            library.saveProfile(profile);
            applyArrangement(suiteId, profile);
            return keypadArrangementJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Puts every key back where the standard layout has it. */
    public String resetKeypad(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.keypadArrangement().reset();
            library.saveProfile(profile);
            applyArrangement(suiteId, profile);
            return keypadArrangementJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * Hands the running game the arrangement that was just edited.
     *
     * <p>Keys are dragged while looking at the game they are for, so the
     * change has to be there when the editor is closed rather than at the
     * next start.</p>
     */
    private void applyArrangement(String suiteId, GameProfile edited) {
        if (session == null || !suiteId.equals(activeSuiteId)) {
            return;
        }
        KeypadArrangement live = session.profile().keypadArrangement();
        live.reset();
        live.setScale(edited.keypadArrangement().scale());
        List<String> moved = edited.keypadArrangement().movedKeys();
        for (int i = 0; i < moved.size(); i++) {
            String button = moved.get(i);
            live.move(button, edited.keypadArrangement().offsetX(button),
                    edited.keypadArrangement().offsetY(button));
        }
    }

    /** How solid the keypad is drawn, 20-100 percent. */
    public String setKeypadOpacity(String suiteId, int percent) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setKeypadOpacity(percent);
            library.saveProfile(profile);
            return keypadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Rounded, square or round; the next one along each time it is called. */
    public String cycleKeypadShape(String suiteId) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setKeypadShape((profile.keypadShape() + 1) % 3);
            library.saveProfile(profile);
            return keypadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    public String setKeypadShape(String suiteId, int shape) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setKeypadShape(shape);
            library.saveProfile(profile);
            return keypadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /** Seconds of not being touched before the keypad fades; 0 never fades. */
    public String setKeypadFadeDelay(String suiteId, int seconds) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setKeypadFadeDelay(seconds);
            library.saveProfile(profile);
            return keypadJson(suiteId);
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    /**
     * How solid the running game's keypad should be drawn right now.
     *
     * <p>A plain number rather than JSON: the front end asks this once per
     * drawn frame, and a frame is not the place to parse a document.</p>
     */
    public int keypadDrawOpacity() {
        return session == null ? 100 : session.keypadOpacity();
    }

    /**
     * Tells the emulator the keypad was touched, which brings it back to full.
     *
     * <p>The front end knows where the keypad is on screen; the session keeps
     * the clock. This is the one line between them.</p>
     */
    public String noteKeypadUse() {
        if (session == null) {
            return error("No game is running");
        }
        session.noteKeypadUse();
        return ok("opacity", String.valueOf(session.keypadOpacity()));
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
                // A clip is a screenshot that moves, and lives in the same
                // gallery; the viewer needs to know which it is holding.
                shot.put("clip", Boolean.valueOf(name.endsWith(".gif")));
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

    /**
     * Gets one picture or clip ready to leave the app.
     *
     * <p>A screenshot nobody can send is half a screenshot. Inside the app it
     * is called {@code 1700000000000.png}, which is the right name for a file
     * the app itself reads and says nothing at all in a chat — so a copy is
     * made under a readable name and it is the copy that goes.</p>
     *
     * @return where the copy is, what it should be called, and what it is
     */
    public String shareScreenshot(String suiteId, String name) {
        if (library == null) {
            return error("The library is not open");
        }
        try {
            byte[] bytes = library.readScreenshot(suiteId, name);
            if (bytes == null || bytes.length == 0) {
                return error("Không có ảnh này");
            }
            LibraryEntry entry = library.find(suiteId);
            String title = entry == null ? "MobiCore" : entry.title();
            ShareExport share = new ShareExport(library.storage(), library.layout());
            String path = share.prepare(title, name, bytes);
            Map<String, Object> json = Json.object();
            json.put("ok", Boolean.TRUE);
            json.put("path", path);
            json.put("name", share.fileNameFor(title, name));
            json.put("mime", ShareExport.mimeOf(name));
            json.put("clip", Boolean.valueOf(ShareExport.isClip(name)));
            return Json.write(json);
        } catch (IOException e) {
            return error(e.getMessage());
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
            // A game's own connections, kept apart from the installer's. The
            // policy in front still decides: handing the session a working
            // transport is what makes "allow this game online" mean something,
            // not a licence to connect.
            session.network().setTransport(gameTransport);
            session.network().setSocketTransport(gameSockets);
            if (audioSink != null) {
                session.setAudio(audioSink);
            }
            if (vibrationSink != null) {
                session.setVibration(vibrationSink);
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
            return error(noteCrash(suiteId, e));
        } catch (VmCancelled e) {
            // Người chơi dừng, không phải game hỏng: không có gì để báo.
            return error("Đã dừng mở game");
        } catch (VmError e) {
            return error(noteCrash(suiteId, e));
        }
    }

    /**
     * Phiên đang chạy, cho công cụ và bài kiểm tra.
     *
     * <p>Giao diện điện thoại không dùng chỗ này — nó nói chuyện qua JSON như
     * mọi thứ khác — nhưng công cụ trên máy tính thì cần chạm vào máy ảo
     * thật, chẳng hạn để rút ngắn hạn chờ treo xuống còn một phần tư giây
     * thay vì ngồi đợi tám giây.</p>
     */
    public EmulatorSession session() {
        return session;
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
        if (crash != null && activeSuiteId != null && activeSuiteId.equals(crashSuiteId)) {
            // Một game đã chết thì khung hình sau cũng chết y như vậy. Chạy
            // tiếp chỉ để ghi cùng một lỗi mỗi giây mấy chục lần.
            return false;
        }
        try {
            return session.renderFrame();
        } catch (VmThrow e) {
            session.log().error("Frame aborted: " + e);
            noteCrash(activeSuiteId, e);
            return false;
        } catch (VmCancelled e) {
            session.log().info("Frame aborted: người chơi dừng game");
            return false;
        } catch (VmError e) {
            session.log().error("Frame aborted: " + e.getMessage());
            noteCrash(activeSuiteId, e);
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

    // ------------------------------------------------------------ vibration

    /** Where a request to vibrate goes; the platform sets this once. */
    private com.mobicore.core.haptics.VibrationSink vibrationSink;

    public void setVibrationSink(com.mobicore.core.haptics.VibrationSink sink) {
        this.vibrationSink = sink;
        if (session != null) {
            session.setVibration(sink);
        }
    }

    /**
     * Turns buzzing on or off for one game.
     *
     * <p>On by default, because the buzz was part of the game. Off is a real
     * choice: a game that vibrates on every hit cannot be played quietly next
     * to someone.</p>
     */
    public String setVibration(String suiteId, boolean enabled) {
        try {
            GameProfile profile = library == null ? null : library.profile(suiteId);
            if (profile == null) {
                return error("No profile for " + suiteId);
            }
            profile.setVibration(enabled);
            library.saveProfile(profile);
            if (session != null && suiteId.equals(activeSuiteId)) {
                session.context().setVibrationAllowed(enabled);
            }
            return ok("vibration", String.valueOf(enabled));
        } catch (IOException e) {
            return error(e.getMessage());
        }
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

    /**
     * Bảo game dừng ngay, kể cả khi nó đang kẹt giữa một khung hình.
     *
     * <p>Gọi từ luồng giao diện trong lúc luồng chạy game còn đang ở trong
     * máy ảo: một game vòng lặp vô tận không bao giờ đọc tới cờ dừng thường,
     * nên lệnh này xuyên thẳng vào chỗ nó đang chạy.</p>
     */
    public String requestStop() {
        if (session != null) {
            session.requestStop();
        }
        return ok("stopping", "true");
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

    /**
     * The files a game has written for itself, oldest folder first.
     *
     * <p>A game that uses JSR-75 keeps things record stores cannot hold — a
     * saved level, a downloaded track. They are the player's, so they are
     * visible and removable rather than hidden inside the app.</p>
     */
    public String gameFilesJson(String suiteId) {
        if (library == null) {
            return error("The library is not open");
        }
        Map<String, Object> root = Json.object();
        List<Object> files = new ArrayList<Object>();
        String base = gameFilesDir(suiteId);
        collectFiles(library.storage(), base, "", files);
        root.put("ok", Boolean.TRUE);
        root.put("files", files);
        root.put("bytes", Integer.valueOf(totalBytes(files)));
        return Json.write(root);
    }

    /** Where one game's own files live, which nothing outside may reach. */
    private String gameFilesDir(String suiteId) {
        return StorageLayout.join(library.layout().gameDir(suiteId), "files");
    }

    private void collectFiles(Vfs vfs, String base, String prefix, List<Object> files) {
        String dir = prefix.length() == 0 ? base : StorageLayout.join(base, prefix);
        List<String> children = vfs.list(dir);
        for (int i = 0; i < children.size(); i++) {
            String name = children.get(i);
            String relative = prefix.length() == 0 ? name : prefix + "/" + name;
            String path = StorageLayout.join(dir, name);
            if (vfs.isDirectory(path)) {
                collectFiles(vfs, base, relative, files);
                continue;
            }
            Map<String, Object> file = Json.object();
            file.put("path", relative);
            file.put("bytes", Integer.valueOf((int) vfs.size(path)));
            file.put("modifiedAt", Long.valueOf(vfs.modifiedAt(path)));
            files.add(file);
        }
    }

    private static int totalBytes(List<Object> files) {
        int total = 0;
        for (int i = 0; i < files.size(); i++) {
            total += Json.integer((Map<String, Object>) files.get(i), "bytes", 0);
        }
        return total;
    }

    /**
     * Deletes one of a game's own files.
     *
     * <p>The path comes back through the bridge as a string, so it is resolved
     * against the game's folder the same way the game's own paths are: a name
     * from outside must never be able to name a file of its own.</p>
     */
    public String deleteGameFile(String suiteId, String path) {
        if (library == null) {
            return error("The library is not open");
        }
        String full = MidpFiles.resolveOrNull(gameFilesDir(suiteId), path);
        if (full == null) {
            return error("Đường dẫn không hợp lệ");
        }
        if (!library.storage().delete(full)) {
            return error("Không có tệp này");
        }
        return ok("path", path);
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

    // --------------------------------------------------------- game hỏng

    /**
     * Ghi lại một lần hỏng và trả về câu giải thích cho người chơi.
     *
     * <p>Ngăn xếp được chụp ngay lúc này: phiên chạy sẽ bị dọn, còn câu hỏi
     * "hỏng ở đâu" thì chỉ trả lời được khi phiên đó vẫn còn.</p>
     */
    private String noteCrash(String suiteId, Throwable failure) {
        crash = CrashDiagnosis.of(failure);
        crashSuiteId = suiteId == null ? "" : suiteId;
        crashAt = now();
        crashStack = session == null ? "" : session.vm().interpreter().crashTrace();
        crashGame = session == null ? "" : session.info().title();
        if (crashGame.length() == 0 && library != null && suiteId != null) {
            LibraryEntry entry = library.find(suiteId);
            crashGame = entry == null ? "" : entry.title();
        }
        return crash.reason();
    }

    /**
     * Có lần hỏng nào chưa đọc không.
     *
     * <p>Hỏi kiểu này rẻ hơn đọc {@link #crashJson()}: bên iOS xem lại mỗi
     * khung hình, và dựng một chuỗi JSON sáu chục lần một giây để phần lớn
     * thời gian nhận về "không có gì" là việc thừa.</p>
     */
    public boolean hasCrashed() {
        return crash != null;
    }

    /**
     * Lần hỏng gần nhất, nếu có.
     *
     * <p>Còn nguyên sau khi game đã tắt, vì màn hình báo lỗi chỉ hiện ra sau
     * lúc đó — và người chơi cần đọc được nó chứ không phải nhìn màn hình đen
     * rồi tự đoán.</p>
     */
    public String crashJson() {
        Map<String, Object> json = Json.object();
        json.put("ok", Boolean.TRUE);
        json.put("has", Boolean.valueOf(crash != null));
        if (crash == null) {
            return Json.write(json);
        }
        json.putAll(crash.toJson());
        json.put("suiteId", crashSuiteId);
        json.put("game", crashGame);
        json.put("when", Long.valueOf(crashAt));
        List<Object> frames = new ArrayList<Object>();
        String[] lines = crashStack.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() > 0) {
                frames.add(line);
            }
        }
        json.put("stack", frames);
        return Json.write(json);
    }

    /** Người chơi đã đọc xong: bỏ lời báo đi và tắt hẳn game đã chết. */
    public String dismissCrash() {
        crash = null;
        crashSuiteId = "";
        crashGame = "";
        crashStack = "";
        crashAt = 0;
        stopGame();
        return ok("has", "false");
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
