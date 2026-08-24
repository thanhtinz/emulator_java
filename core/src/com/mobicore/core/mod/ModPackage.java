package com.mobicore.core.mod;

import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.storage.Json;
import com.mobicore.core.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A mod: a ZIP of replacement resources plus a {@code mod.json} manifest.
 *
 * <p>Mods replace resources; they never rewrite the game's bytecode. Patching
 * classes automatically is how emulators break games silently, so the
 * specification rules it out and so does this implementation: a mod that ships
 * {@code .class} files is reported, and applying it is a Developer-mode
 * decision, not something that happens on install.</p>
 */
public final class ModPackage {

    public static final String MANIFEST = "mod.json";

    private final String modId;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String targetSuiteId;
    private final JarArchive archive;
    private boolean enabled;

    private ModPackage(String modId, String name, String version, String author, String description,
                       String targetSuiteId, JarArchive archive) {
        this.modId = modId;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.targetSuiteId = targetSuiteId;
        this.archive = archive;
    }

    /** Reads a mod archive, deriving a manifest when the package omits one. */
    public static ModPackage read(String fallbackId, JarArchive archive) {
        Map<String, Object> manifest = Json.object();
        byte[] raw = archive.read(MANIFEST);
        if (raw != null) {
            try {
                manifest = Json.readObject(new String(raw, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                manifest = Json.object();
            }
        }
        return new ModPackage(
                Json.string(manifest, "id", Text.slug(fallbackId)),
                Json.string(manifest, "name", fallbackId),
                Json.string(manifest, "version", "1.0"),
                Json.string(manifest, "author", "Unknown"),
                Json.string(manifest, "description", ""),
                Json.string(manifest, "target", ""),
                archive);
    }

    public String modId() {
        return modId;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    /** Suite id the mod declares it is for, or empty when unrestricted. */
    public String targetSuiteId() {
        return targetSuiteId;
    }

    public JarArchive archive() {
        return archive;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** True when the mod is meant for this suite. */
    public boolean appliesTo(String suiteId) {
        return targetSuiteId.length() == 0 || targetSuiteId.equals(suiteId);
    }

    /** Resources the mod replaces, excluding its own manifest. */
    public List<String> replacedResources() {
        List<String> names = new ArrayList<String>();
        for (String name : archive.names()) {
            if (!MANIFEST.equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Class files the mod carries; empty for a well-behaved resource mod. */
    public List<String> replacedClasses() {
        List<String> names = new ArrayList<String>();
        for (String name : archive.names()) {
            if (name.endsWith(".class")) {
                names.add(name);
            }
        }
        return names;
    }

    public boolean touchesCode() {
        return !replacedClasses().isEmpty();
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("id", modId);
        json.put("name", name);
        json.put("version", version);
        json.put("author", author);
        json.put("description", description);
        json.put("target", targetSuiteId);
        json.put("enabled", Boolean.valueOf(enabled));
        json.put("resources", new ArrayList<Object>(replacedResources()));
        json.put("touchesCode", Boolean.valueOf(touchesCode()));
        return json;
    }
}
