package com.mobicore.core.jar;

import com.mobicore.core.model.MidletSuiteInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Entry point used by the import flow: turns raw {@code .jar}/{@code .jad}
 * bytes into an archive plus a merged descriptor.
 */
public final class SuiteLoader {

    public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    private final JarArchive archive;
    private final MidletSuiteInfo info;
    private final AttributeSet manifest;
    private final AttributeSet jad;

    private SuiteLoader(JarArchive archive, AttributeSet manifest, AttributeSet jad, MidletSuiteInfo info) {
        this.archive = archive;
        this.manifest = manifest;
        this.jad = jad;
        this.info = info;
    }

    /** Loads a suite from a JAR, optionally refined by a companion JAD. */
    public static SuiteLoader load(byte[] jarBytes, byte[] jadBytes) throws IOException {
        if (jarBytes == null) {
            throw new IOException("A MIDlet suite needs a JAR archive");
        }
        JarArchive archive = JarArchive.read(new ByteArrayInputStream(jarBytes));
        byte[] manifestBytes = archive.read(MANIFEST_PATH);
        AttributeSet manifest = manifestBytes == null ? new AttributeSet() : AttributeSet.parse(manifestBytes);
        AttributeSet jad = jadBytes == null ? null : AttributeSet.parse(jadBytes);
        return new SuiteLoader(archive, manifest, jad, MidletSuiteInfo.merge(manifest, jad));
    }

    /**
     * Reads a stand-alone descriptor. The library shows the entry immediately
     * and downloads the JAR named by {@code MIDlet-Jar-URL} on demand.
     */
    public static MidletSuiteInfo describe(byte[] jadBytes) {
        return MidletSuiteInfo.merge(null, AttributeSet.parse(jadBytes));
    }

    public JarArchive archive() {
        return archive;
    }

    public MidletSuiteInfo info() {
        return info;
    }

    public AttributeSet manifest() {
        return manifest;
    }

    /** The descriptor as supplied by the user, or {@code null} for a bare JAR. */
    public AttributeSet jad() {
        return jad;
    }

    /** Icon bytes from inside the JAR, or {@code null} when absent. */
    public byte[] iconBytes() {
        String path = info.iconPath();
        return path == null ? null : archive.read(path);
    }
}
