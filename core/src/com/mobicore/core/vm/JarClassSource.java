package com.mobicore.core.vm;

import com.mobicore.core.jar.JarArchive;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ClassSource} over an installed suite.
 *
 * <p>Mods are layered in front of the original archive so a replaced resource
 * wins without the base JAR ever being modified — the specification requires
 * that the original file stays intact.</p>
 */
public final class JarClassSource implements ClassSource {

    private final JarArchive base;
    private final List<JarArchive> overlays = new ArrayList<JarArchive>();

    public JarClassSource(JarArchive base) {
        this.base = base;
    }

    /** Adds a mod overlay; later overlays take precedence. */
    public void addOverlay(JarArchive overlay) {
        overlays.add(overlay);
    }

    public void clearOverlays() {
        overlays.clear();
    }

    @Override
    public byte[] classBytes(String internalName) {
        return resourceBytes(internalName + ".class");
    }

    @Override
    public byte[] resourceBytes(String path) {
        String name = path.startsWith("/") ? path.substring(1) : path;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            byte[] data = overlays.get(i).read(name);
            if (data != null) {
                return data;
            }
        }
        return base.read(name);
    }

    public JarArchive archive() {
        return base;
    }
}
