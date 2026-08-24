package com.mobicore.core.storage;

/**
 * Canonical on-device directory layout.
 *
 * <pre>
 * MobiCore/
 *   games/    installed suites (jar, jad, artwork)
 *   profiles/ per-game emulator, display and input configuration
 *   rms/      record stores, one sandbox per suite
 *   saves/    save states
 *   skins/    virtual phone skins
 *   mods/     mod packages
 *   backups/  snapshots taken before a reset or a mod is applied
 *   logs/     console and crash logs
 *   cache/    derived data, safe to delete
 * </pre>
 *
 * <p>Every per-game path is namespaced by suite id so that one game can never
 * read or clobber another game's data.</p>
 */
public final class StorageLayout {

    public static final String GAMES = "games";
    public static final String PROFILES = "profiles";
    public static final String RMS = "rms";
    public static final String SAVES = "saves";
    public static final String SKINS = "skins";
    public static final String MODS = "mods";
    public static final String BACKUPS = "backups";
    public static final String LOGS = "logs";
    public static final String CACHE = "cache";

    public static final String[] TOP_LEVEL = {
            GAMES, PROFILES, RMS, SAVES, SKINS, MODS, BACKUPS, LOGS, CACHE
    };

    private final String root;

    public StorageLayout(String root) {
        this.root = trimTrailingSeparator(root);
    }

    private static String trimTrailingSeparator(String path) {
        String value = path == null ? "." : path;
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String join(String base, String child) {
        if (base == null || base.length() == 0) {
            return child;
        }
        if (base.endsWith("/")) {
            return base + child;
        }
        return base + "/" + child;
    }

    public String root() {
        return root;
    }

    public String dir(String name) {
        return join(root, name);
    }

    public String gameDir(String suiteId) {
        return join(dir(GAMES), suiteId);
    }

    public String jarPath(String suiteId) {
        return join(gameDir(suiteId), "suite.jar");
    }

    public String jadPath(String suiteId) {
        return join(gameDir(suiteId), "suite.jad");
    }

    public String artworkPath(String suiteId) {
        return join(gameDir(suiteId), "artwork.png");
    }

    public String profilePath(String suiteId) {
        return join(dir(PROFILES), suiteId + ".json");
    }

    public String rmsDir(String suiteId) {
        return join(dir(RMS), suiteId);
    }

    public String saveDir(String suiteId) {
        return join(dir(SAVES), suiteId);
    }

    /**
     * Where a game's saved state lives.
     *
     * <p>Under {@code saves/}, beside whatever the game itself writes: both
     * are the player's progress, and a backup that took one without the other
     * would restore a game that has forgotten half of where it was.</p>
     */
    public String saveStatePath(String suiteId) {
        return join(saveDir(suiteId), "state.mcs");
    }

    /** The screen as it looked when the state was saved. */
    public String saveStateThumbnailPath(String suiteId) {
        return join(saveDir(suiteId), "state.png");
    }

    public String modDir(String suiteId) {
        return join(dir(MODS), suiteId);
    }

    public String backupDir(String suiteId) {
        return join(dir(BACKUPS), suiteId);
    }

    public String logPath(String name) {
        return join(dir(LOGS), name);
    }

    /** Index of the installed library. */
    public String libraryIndexPath() {
        return join(root, "library.json");
    }

    /** Application-wide settings. */
    public String settingsPath() {
        return join(root, "settings.json");
    }
}
