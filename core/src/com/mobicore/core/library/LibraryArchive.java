package com.mobicore.core.library;

import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole library in one file.
 *
 * <p>Per-game backups already exist, and they are the wrong shape for the
 * thing people actually do: changing phones. Eighty games means eighty
 * backups, eighty transfers and eighty restores, and whoever is doing that at
 * eleven at night will get to game sixty and give up.</p>
 *
 * <p>What goes in is everything that is theirs — the suites, what they named
 * them, the covers they chose, every setting, the record stores the games
 * wrote, save states, screenshots, presets and the app's own settings. What
 * stays out is what can be made again: the cache, and the per-game backups,
 * because a backup of the backups doubles the file for nothing.</p>
 *
 * <p>The container is the plain one the per-game backup uses rather than a
 * zip: it has to be read by the same code on two platforms, and a format with
 * one implementation cannot disagree with itself.</p>
 */
public final class LibraryArchive {

    private static final int MAGIC = 0x4D434C42;
    private static final int VERSION = 1;

    /** What was in an archive, once it has been put back. */
    public static final class Report {

        private final int files;
        private final int games;
        private final long bytes;

        Report(int files, int games, long bytes) {
            this.files = files;
            this.games = games;
            this.bytes = bytes;
        }

        public int files() {
            return files;
        }

        public int games() {
            return games;
        }

        public long bytes() {
            return bytes;
        }

        /** One line for the user. */
        public String summary() {
            return "Đã khôi phục " + games + " trò chơi (" + files + " tệp)";
        }
    }

    private LibraryArchive() {
    }

    /** Everything worth keeping, as one file to carry to the next phone. */
    public static byte[] export(Vfs vfs, StorageLayout layout) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);

        List<String> paths = new ArrayList<String>();
        collect(vfs, layout.root(), "", paths);
        Collections.sort(paths);
        for (int i = 0; i < paths.size(); i++) {
            String relative = paths.get(i);
            byte[] data = vfs.read(StorageLayout.join(layout.root(), relative));
            out.writeUTF(relative);
            out.writeInt(data.length);
            out.write(data);
        }
        // An empty name ends the list, as the per-game backup does.
        out.writeUTF("");
        out.flush();
        return bytes.toByteArray();
    }

    /**
     * Puts an archive back.
     *
     * <p>Files are written over whatever is there, and nothing is deleted
     * first: someone restoring onto a phone that already has games is asking
     * for their old ones back, not for the new ones to disappear. Where both
     * hold the same game, the archive wins — it is the one being restored.</p>
     */
    public static Report restore(Vfs vfs, StorageLayout layout, byte[] archive)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(archive));
        if (in.readInt() != MAGIC) {
            throw new IOException("Không phải bản sao lưu MobiCore");
        }
        if (in.readInt() != VERSION) {
            throw new IOException("Bản sao lưu từ một phiên bản khác");
        }
        int files = 0;
        int games = 0;
        long bytes = 0;
        while (true) {
            String relative = in.readUTF();
            if (relative.length() == 0) {
                break;
            }
            int length = in.readInt();
            if (length < 0) {
                throw new IOException("Bản sao lưu hỏng");
            }
            byte[] data = new byte[length];
            in.readFully(data);
            // A path out of an archive is a path from outside: it must not be
            // able to name somewhere other than this app's own storage.
            if (!safe(relative)) {
                continue;
            }
            String path = StorageLayout.join(layout.root(), relative);
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                vfs.mkdirs(path.substring(0, slash));
            }
            vfs.write(path, data);
            files++;
            bytes += length;
            if (relative.endsWith(".jar") && relative.startsWith(StorageLayout.GAMES + "/")) {
                games++;
            }
        }
        return new Report(files, games, bytes);
    }

    /** True when a name stays inside the folder it is being written into. */
    private static boolean safe(String relative) {
        if (relative.startsWith("/") || relative.indexOf(':') >= 0) {
            return false;
        }
        String normalised = relative.replace('\\', '/');
        return normalised.indexOf("../") < 0 && !normalised.endsWith("/..")
                && !normalised.equals("..");
    }

    /** Every file under {@code dir}, named relative to the storage root. */
    private static void collect(Vfs vfs, String dir, String prefix, List<String> into) {
        List<String> names = vfs.list(dir);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String relative = prefix.length() == 0 ? name : prefix + "/" + name;
            if (relative.startsWith(StorageLayout.BACKUPS) || relative.startsWith("cache")) {
                // Made again from what is already in here, or made again from
                // the games themselves.
                continue;
            }
            String path = StorageLayout.join(dir, name);
            if (vfs.isDirectory(path)) {
                collect(vfs, path, relative, into);
            } else {
                into.add(relative);
            }
        }
    }
}
