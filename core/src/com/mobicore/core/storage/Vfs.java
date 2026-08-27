package com.mobicore.core.storage;

import java.io.IOException;
import java.util.List;

/**
 * Minimal filesystem abstraction.
 *
 * <p>The core never touches platform storage APIs directly. Android supplies an
 * implementation rooted at the app's private directory, iOS one rooted at the
 * application support directory, and tests an in-memory one.</p>
 */
public interface Vfs {

    boolean exists(String path);

    boolean isDirectory(String path);

    long size(String path);

    /**
     * When a file was last written, in epoch milliseconds, or zero when it
     * does not exist.
     *
     * <p>Needed because a save slot is worth nothing without "when": four
     * identical-looking slots and no dates is a guess about which one to
     * load.</p>
     */
    long modifiedAt(String path);

    byte[] read(String path) throws IOException;

    void write(String path, byte[] data) throws IOException;

    void mkdirs(String path) throws IOException;

    /** Child names (not full paths), sorted; empty when the path is not a directory. */
    List<String> list(String path);

    /** Deletes a file or a directory tree. Returns false when nothing was removed. */
    boolean delete(String path);

    void copy(String from, String to) throws IOException;
}
