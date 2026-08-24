package com.mobicore.core.library;

import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.AutoSetup;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The installed game collection: import, list, configure, back up and remove.
 *
 * <p>Installing copies the suite into its own sandbox directory rather than
 * referencing wherever the user picked the file from. A game must keep working
 * after the original download is deleted, and it must never be able to reach
 * outside its own folder.</p>
 */
public final class GameLibrary {

    /** Result of an import attempt. */
    public static final class InstallResult {

        private final LibraryEntry entry;
        private final GameProfile profile;
        private final boolean replaced;

        InstallResult(LibraryEntry entry, GameProfile profile, boolean replaced) {
            this.entry = entry;
            this.profile = profile;
            this.replaced = replaced;
        }

        public LibraryEntry entry() {
            return entry;
        }

        public GameProfile profile() {
            return profile;
        }

        /** True when an existing installation of the same suite was upgraded. */
        public boolean replaced() {
            return replaced;
        }
    }

    private static final int BACKUP_MAGIC = 0x4D43424B;
    private static final int BACKUP_VERSION = 1;

    private final Vfs vfs;
    private final StorageLayout layout;
    private final Map<String, LibraryEntry> entries = new LinkedHashMap<String, LibraryEntry>();
    private long clock;

    public GameLibrary(Vfs vfs, StorageLayout layout) {
        this.vfs = vfs;
        this.layout = layout;
    }

    /** Timestamp used for install and backup times; injectable for tests. */
    public void setClock(long millis) {
        this.clock = millis;
    }

    public StorageLayout layout() {
        return layout;
    }

    public Vfs storage() {
        return vfs;
    }

    /** Creates the directory tree and reads the index. */
    public void open() throws IOException {
        for (String directory : StorageLayout.TOP_LEVEL) {
            vfs.mkdirs(layout.dir(directory));
        }
        entries.clear();
        if (!vfs.exists(layout.libraryIndexPath())) {
            return;
        }
        Map<String, Object> index = Json.readObject(new String(vfs.read(layout.libraryIndexPath()), "UTF-8"));
        for (Object item : Json.array(index, "games")) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                LibraryEntry entry = LibraryEntry.fromJson((Map<String, Object>) item);
                entries.put(entry.suiteId(), entry);
            }
        }
    }

    private void writeIndex() throws IOException {
        Map<String, Object> index = Json.object();
        List<Object> games = new ArrayList<Object>();
        for (LibraryEntry entry : entries.values()) {
            games.add(entry.toJson());
        }
        index.put("version", Integer.valueOf(1));
        index.put("games", games);
        vfs.write(layout.libraryIndexPath(), Json.write(index).getBytes("UTF-8"));
    }

    // ------------------------------------------------------------- install

    public InstallResult install(byte[] jarBytes, byte[] jadBytes) throws IOException {
        return install(jarBytes, jadBytes, null);
    }

    /**
     * Installs a suite into its sandbox.
     *
     * @param profile the configuration to store, or {@code null} to derive
     *                sensible defaults from the descriptor
     */
    public InstallResult install(byte[] jarBytes, byte[] jadBytes, GameProfile profile)
            throws IOException {
        SuiteLoader suite = SuiteLoader.load(jarBytes, jadBytes);
        MidletSuiteInfo info = suite.info();
        if (!info.isValid()) {
            throw new IOException("The archive declares no MIDlet, so there is nothing to install");
        }
        String suiteId = info.suiteId();
        boolean replaced = entries.containsKey(suiteId);
        if (replaced) {
            // Upgrading keeps saves: the sandbox directories under rms/ and
            // saves/ are deliberately left alone.
            vfs.delete(layout.gameDir(suiteId));
        }

        vfs.write(layout.jarPath(suiteId), jarBytes);
        if (jadBytes != null) {
            vfs.write(layout.jadPath(suiteId), jadBytes);
        }
        byte[] icon = suite.iconBytes();
        if (icon != null) {
            vfs.write(layout.artworkPath(suiteId), icon);
        }

        // Nothing to set up by hand: the suite is inspected and configured on
        // the way in, so importing a game is the whole of getting it running.
        GameProfile stored = profile != null ? profile : AutoSetup.configure(suite).profile();
        saveProfile(stored);

        LibraryEntry entry = new LibraryEntry(suiteId, info.title(), info.vendor(), info.version(),
                info.configuration(), info.profile(), clock, jarBytes.length, icon != null);
        entries.put(suiteId, entry);
        writeIndex();
        return new InstallResult(entry, stored, replaced);
    }

    /** Removes a suite; {@code keepData} preserves its saves and record stores. */
    public boolean uninstall(String suiteId, boolean keepData) throws IOException {
        if (entries.remove(suiteId) == null) {
            return false;
        }
        vfs.delete(layout.gameDir(suiteId));
        vfs.delete(layout.profilePath(suiteId));
        if (!keepData) {
            vfs.delete(layout.rmsDir(suiteId));
            vfs.delete(layout.saveDir(suiteId));
            vfs.delete(layout.modDir(suiteId));
        }
        writeIndex();
        return true;
    }

    // ---------------------------------------------------------------- read

    public List<LibraryEntry> all() {
        return new ArrayList<LibraryEntry>(entries.values());
    }

    public LibraryEntry find(String suiteId) {
        return entries.get(suiteId);
    }

    public int size() {
        return entries.size();
    }

    /** Loads the installed suite so it can be run. */
    public SuiteLoader load(String suiteId) throws IOException {
        if (!entries.containsKey(suiteId)) {
            throw new IOException("No installed suite with id " + suiteId);
        }
        byte[] jar = vfs.read(layout.jarPath(suiteId));
        byte[] jad = vfs.exists(layout.jadPath(suiteId)) ? vfs.read(layout.jadPath(suiteId)) : null;
        return SuiteLoader.load(jar, jad);
    }

    public byte[] artwork(String suiteId) throws IOException {
        String path = layout.artworkPath(suiteId);
        return vfs.exists(path) ? vfs.read(path) : null;
    }

    /**
     * Renames a game as the library lists it.
     *
     * <p>Only the display title changes: the suite's own manifest title is
     * kept, so the change can be undone and so a reinstall of the same suite
     * is still recognised. The manifest inside the JAR is never rewritten —
     * a game that reads its own name would then disagree with the library.</p>
     *
     * @throws IOException if there is no such suite, or the name is blank
     */
    public LibraryEntry rename(String suiteId, String title) throws IOException {
        LibraryEntry entry = entries.get(suiteId);
        if (entry == null) {
            throw new IOException("No installed suite with id " + suiteId);
        }
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.length() == 0) {
            throw new IOException("A game needs a name");
        }
        if (trimmed.length() > MAX_TITLE) {
            trimmed = trimmed.substring(0, MAX_TITLE).trim();
        }
        LibraryEntry renamed = entry.withTitle(trimmed);
        entries.put(suiteId, renamed);
        writeIndex();
        return renamed;
    }

    /** Puts the manifest's own title back. */
    public LibraryEntry resetTitle(String suiteId) throws IOException {
        LibraryEntry entry = entries.get(suiteId);
        if (entry == null) {
            throw new IOException("No installed suite with id " + suiteId);
        }
        return rename(suiteId, entry.originalTitle());
    }

    /** Longest display title kept; past this a name is a paragraph. */
    public static final int MAX_TITLE = 120;

    /**
     * Replaces the cover art with a picture of the user's choosing.
     *
     * <p>Stored as given, so whatever the phone handed over is what later
     * screens scale down. Only a PNG is accepted: it is the one format the
     * emulator can decode everywhere, MIDP included, and a file that cannot
     * be decoded would leave a game with no cover at all.</p>
     *
     * @throws IOException if there is no such suite, or the bytes are not a PNG
     */
    public LibraryEntry setArtwork(String suiteId, byte[] png) throws IOException {
        LibraryEntry entry = entries.get(suiteId);
        if (entry == null) {
            throw new IOException("No installed suite with id " + suiteId);
        }
        if (png == null || !PngReader.looksLikePng(png)) {
            throw new IOException("Cover art must be a PNG");
        }
        vfs.write(layout.artworkPath(suiteId), png);
        LibraryEntry updated = entry.withArtwork(true);
        entries.put(suiteId, updated);
        writeIndex();
        return updated;
    }

    /**
     * Puts back the icon the suite ships, or leaves it with none if it ships
     * no icon. The JAR is the source of truth, so nothing is lost by
     * replacing the cover.
     */
    public LibraryEntry resetArtwork(String suiteId) throws IOException {
        LibraryEntry entry = entries.get(suiteId);
        if (entry == null) {
            throw new IOException("No installed suite with id " + suiteId);
        }
        byte[] icon = load(suiteId).iconBytes();
        if (icon != null) {
            vfs.write(layout.artworkPath(suiteId), icon);
        } else if (vfs.exists(layout.artworkPath(suiteId))) {
            vfs.delete(layout.artworkPath(suiteId));
        }
        LibraryEntry updated = entry.withArtwork(icon != null);
        entries.put(suiteId, updated);
        writeIndex();
        return updated;
    }

    public RecordStoreManager records(String suiteId) {
        return new RecordStoreManager(vfs, layout, suiteId);
    }

    // ------------------------------------------------------------ profiles

    public void saveProfile(GameProfile profile) throws IOException {
        vfs.write(layout.profilePath(profile.suiteId()),
                Json.write(profile.toJson()).getBytes("UTF-8"));
    }

    public GameProfile profile(String suiteId) throws IOException {
        String path = layout.profilePath(suiteId);
        if (!vfs.exists(path)) {
            LibraryEntry entry = entries.get(suiteId);
            if (entry == null) {
                return null;
            }
            return GameProfile.defaultsFor(load(suiteId).info());
        }
        return GameProfile.fromJson(Json.readObject(new String(vfs.read(path), "UTF-8")));
    }

    // -------------------------------------------------------------- search

    public List<LibraryEntry> search(String query) {
        List<LibraryEntry> matches = new ArrayList<LibraryEntry>();
        String needle = query == null ? "" : query.trim().toLowerCase();
        for (LibraryEntry entry : entries.values()) {
            if (needle.length() == 0
                    || entry.title().toLowerCase().indexOf(needle) >= 0
                    || entry.vendor().toLowerCase().indexOf(needle) >= 0) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public static final int SORT_TITLE = 0;
    public static final int SORT_RECENT = 1;
    public static final int SORT_VENDOR = 2;

    /** Sorts a result list; {@code profiles} supplies play times for SORT_RECENT. */
    public List<LibraryEntry> sort(List<LibraryEntry> input, final int mode,
                                   final Map<String, GameProfile> profiles) {
        List<LibraryEntry> sorted = new ArrayList<LibraryEntry>(input);
        Collections.sort(sorted, new Comparator<LibraryEntry>() {
            public int compare(LibraryEntry left, LibraryEntry right) {
                if (mode == SORT_VENDOR) {
                    int byVendor = left.vendor().compareToIgnoreCase(right.vendor());
                    if (byVendor != 0) {
                        return byVendor;
                    }
                } else if (mode == SORT_RECENT) {
                    long a = playedAt(profiles, left);
                    long b = playedAt(profiles, right);
                    if (a != b) {
                        return a > b ? -1 : 1;
                    }
                }
                return left.title().compareToIgnoreCase(right.title());
            }
        });
        return sorted;
    }

    private static long playedAt(Map<String, GameProfile> profiles, LibraryEntry entry) {
        GameProfile profile = profiles == null ? null : profiles.get(entry.suiteId());
        return profile == null ? 0 : profile.lastPlayed();
    }

    /** Games marked favourite, in title order. */
    public List<LibraryEntry> favourites(Map<String, GameProfile> profiles) {
        List<LibraryEntry> matches = new ArrayList<LibraryEntry>();
        for (LibraryEntry entry : entries.values()) {
            GameProfile profile = profiles.get(entry.suiteId());
            if (profile != null && profile.isFavourite()) {
                matches.add(entry);
            }
        }
        return sort(matches, SORT_TITLE, profiles);
    }

    /** Loads every profile once; the library screens need them together. */
    public Map<String, GameProfile> allProfiles() throws IOException {
        Map<String, GameProfile> profiles = new LinkedHashMap<String, GameProfile>();
        for (String suiteId : entries.keySet()) {
            GameProfile profile = profile(suiteId);
            if (profile != null) {
                profiles.put(suiteId, profile);
            }
        }
        return profiles;
    }

    // ------------------------------------------------------ backup/restore

    /**
     * Writes a self-contained backup: the suite, its descriptor, artwork,
     * profile and every record store.
     *
     * @return the path the backup was written to
     */
    public String backup(String suiteId) throws IOException {
        if (!entries.containsKey(suiteId)) {
            throw new IOException("No installed suite with id " + suiteId);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(BACKUP_MAGIC);
        out.writeInt(BACKUP_VERSION);
        out.writeUTF(suiteId);
        out.writeLong(clock);
        out.writeUTF(Json.write(entries.get(suiteId).toJson()));

        writeMember(out, "suite.jar", vfs.read(layout.jarPath(suiteId)));
        writeOptional(out, "suite.jad", layout.jadPath(suiteId));
        writeOptional(out, "artwork.png", layout.artworkPath(suiteId));
        writeOptional(out, "profile.json", layout.profilePath(suiteId));
        writeMember(out, "records.bin", records(suiteId).exportAll());
        out.writeUTF("");
        out.flush();

        String path = StorageLayout.join(layout.backupDir(suiteId), clock + ".mcb");
        vfs.write(path, bytes.toByteArray());
        return path;
    }

    private void writeOptional(DataOutputStream out, String name, String path) throws IOException {
        if (vfs.exists(path)) {
            writeMember(out, name, vfs.read(path));
        }
    }

    private void writeMember(DataOutputStream out, String name, byte[] data) throws IOException {
        out.writeUTF(name);
        out.writeInt(data.length);
        out.write(data);
    }

    /** Restores a backup produced by {@link #backup(String)}. */
    public LibraryEntry restore(byte[] backup) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(backup));
        try {
            if (in.readInt() != BACKUP_MAGIC || in.readInt() != BACKUP_VERSION) {
                throw new IOException("Not a MobiCore backup");
            }
            String suiteId = in.readUTF();
            in.readLong();
            LibraryEntry entry = LibraryEntry.fromJson(Json.readObject(in.readUTF()));

            while (true) {
                String name = in.readUTF();
                if (name.length() == 0) {
                    break;
                }
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                if ("suite.jar".equals(name)) {
                    vfs.write(layout.jarPath(suiteId), data);
                } else if ("suite.jad".equals(name)) {
                    vfs.write(layout.jadPath(suiteId), data);
                } else if ("artwork.png".equals(name)) {
                    vfs.write(layout.artworkPath(suiteId), data);
                } else if ("profile.json".equals(name)) {
                    vfs.write(layout.profilePath(suiteId), data);
                } else if ("records.bin".equals(name)) {
                    records(suiteId).importAll(data, true);
                }
            }
            entries.put(suiteId, entry);
            writeIndex();
            return entry;
        } finally {
            in.close();
        }
    }

    public List<String> backupsFor(String suiteId) {
        List<String> names = new ArrayList<String>();
        for (String name : vfs.list(layout.backupDir(suiteId))) {
            if (name.endsWith(".mcb")) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Snapshots a game and then wipes its record stores. Every destructive
     * action in MobiCore takes a backup first, as the specification requires.
     */
    public String resetGameData(String suiteId) throws IOException {
        String backupPath = backup(suiteId);
        records(suiteId).deleteAll();
        return backupPath;
    }

    /** Installs a mod archive alongside a game, leaving the original intact. */
    public void installMod(String suiteId, String modId, byte[] archive) throws IOException {
        JarArchive.read(new ByteArrayInputStream(archive));
        vfs.write(StorageLayout.join(layout.modDir(suiteId), modId + ".mod"), archive);
    }

    public List<String> mods(String suiteId) {
        List<String> names = new ArrayList<String>();
        for (String name : vfs.list(layout.modDir(suiteId))) {
            if (name.endsWith(".mod")) {
                names.add(name.substring(0, name.length() - 4));
            }
        }
        Collections.sort(names);
        return names;
    }

    public JarArchive loadMod(String suiteId, String modId) throws IOException {
        String path = StorageLayout.join(layout.modDir(suiteId), modId + ".mod");
        return JarArchive.read(new ByteArrayInputStream(vfs.read(path)));
    }
}
