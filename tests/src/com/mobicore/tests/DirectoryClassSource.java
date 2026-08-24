package com.mobicore.tests;

import com.mobicore.core.storage.LocalVfs;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.ClassSource;

/** Loads fixture classes from a directory of compiled output. */
public final class DirectoryClassSource implements ClassSource {

    private final String root;
    private final Vfs vfs = new LocalVfs();

    public DirectoryClassSource(String root) {
        this.root = root;
    }

    @Override
    public byte[] classBytes(String internalName) {
        return resourceBytes(internalName + ".class");
    }

    @Override
    public byte[] resourceBytes(String path) {
        String name = path.startsWith("/") ? path.substring(1) : path;
        String full = root + "/" + name;
        if (!vfs.exists(full)) {
            return null;
        }
        try {
            return vfs.read(full);
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
