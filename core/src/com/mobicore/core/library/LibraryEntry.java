package com.mobicore.core.library;

import com.mobicore.core.storage.Json;

import java.util.Map;

/** One installed suite, as the library list shows it. */
public final class LibraryEntry {

    private final String suiteId;
    private final String title;
    /** The title the suite itself declares, kept so a rename can be undone. */
    private final String originalTitle;
    private final String vendor;
    private final String version;
    private final String configuration;
    private final String profile;
    private final long installedAt;
    private final long jarSize;
    private final boolean hasArtwork;

    public LibraryEntry(String suiteId, String title, String vendor, String version,
                        String configuration, String profile, long installedAt,
                        long jarSize, boolean hasArtwork) {
        this(suiteId, title, title, vendor, version, configuration, profile, installedAt,
                jarSize, hasArtwork);
    }

    public LibraryEntry(String suiteId, String title, String originalTitle, String vendor,
                        String version, String configuration, String profile, long installedAt,
                        long jarSize, boolean hasArtwork) {
        this.suiteId = suiteId;
        this.title = title;
        this.originalTitle = originalTitle == null || originalTitle.length() == 0
                ? title : originalTitle;
        this.vendor = vendor;
        this.version = version;
        this.configuration = configuration;
        this.profile = profile;
        this.installedAt = installedAt;
        this.jarSize = jarSize;
        this.hasArtwork = hasArtwork;
    }

    public String suiteId() {
        return suiteId;
    }

    public String title() {
        return title;
    }

    /** The title from the suite's own manifest, whatever the user renamed it to. */
    public String originalTitle() {
        return originalTitle;
    }

    public boolean isRenamed() {
        return !title.equals(originalTitle);
    }

    /** A copy under a different display title; the manifest title is kept. */
    public LibraryEntry withTitle(String newTitle) {
        return new LibraryEntry(suiteId, newTitle, originalTitle, vendor, version, configuration,
                profile, installedAt, jarSize, hasArtwork);
    }

    /** A copy that does or does not have cover art of its own. */
    public LibraryEntry withArtwork(boolean present) {
        return new LibraryEntry(suiteId, title, originalTitle, vendor, version, configuration,
                profile, installedAt, jarSize, present);
    }

    public String vendor() {
        return vendor;
    }

    public String version() {
        return version;
    }

    public String configuration() {
        return configuration;
    }

    /** MIDP profile string, e.g. {@code MIDP-2.0}. */
    public String profile() {
        return profile;
    }

    public long installedAt() {
        return installedAt;
    }

    public long jarSize() {
        return jarSize;
    }

    public boolean hasArtwork() {
        return hasArtwork;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("suiteId", suiteId);
        json.put("title", title);
        json.put("originalTitle", originalTitle);
        // Stated rather than left to be worked out: the phone should not have
        // to compare two strings to know whether to offer the old name back.
        json.put("renamed", Boolean.valueOf(isRenamed()));
        json.put("vendor", vendor);
        json.put("version", version);
        json.put("configuration", configuration);
        json.put("profile", profile);
        json.put("installedAt", Long.valueOf(installedAt));
        json.put("jarSize", Long.valueOf(jarSize));
        json.put("hasArtwork", Boolean.valueOf(hasArtwork));
        return json;
    }

    public static LibraryEntry fromJson(Map<String, Object> json) {
        return new LibraryEntry(
                Json.string(json, "suiteId", ""),
                Json.string(json, "title", "Unknown"),
                // Libraries written before renaming existed carry no original
                // title; the one they have is the manifest's.
                Json.string(json, "originalTitle", Json.string(json, "title", "Unknown")),
                Json.string(json, "vendor", "Unknown"),
                Json.string(json, "version", "1.0"),
                Json.string(json, "configuration", "CLDC-1.1"),
                Json.string(json, "profile", "MIDP-2.0"),
                Json.longValue(json, "installedAt", 0L),
                Json.longValue(json, "jarSize", 0L),
                Json.bool(json, "hasArtwork", false));
    }

    @Override
    public String toString() {
        return title + " " + version + " — " + vendor;
    }
}
