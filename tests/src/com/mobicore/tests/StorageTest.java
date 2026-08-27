package com.mobicore.tests;

import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

public final class StorageTest extends Test {

    @Override
    public String name() {
        return "Storage layout + VFS";
    }

    @Override
    public void run() throws Exception {
        StorageLayout layout = new StorageLayout("/data/MobiCore/");
        eq("/data/MobiCore", layout.root(), "trailing separators are trimmed");
        eq("/data/MobiCore/games/demo", layout.gameDir("demo"), "game dir is namespaced");
        eq("/data/MobiCore/rms/demo", layout.rmsDir("demo"), "RMS is sandboxed per suite");
        eq("/data/MobiCore/profiles/demo.json", layout.profilePath("demo"), "profile path");
        eq("/data/MobiCore/library.json", layout.libraryIndexPath(), "library index at the root");
        eq("/data/MobiCore/screenshots/demo", layout.screenshotDir("demo"),
                "pictures of a game live under its own name");
        eq(11, StorageLayout.TOP_LEVEL.length, "all documented directories are declared");

        Vfs vfs = new MemoryVfs();
        for (String dir : StorageLayout.TOP_LEVEL) {
            vfs.mkdirs(layout.dir(dir));
        }
        eq(11, vfs.list(layout.root()).size(), "every top level directory is created");
        check(vfs.isDirectory(layout.dir(StorageLayout.GAMES)), "games is a directory");

        vfs.write(layout.jarPath("demo"), new byte[]{1, 2, 3});
        check(vfs.exists(layout.jarPath("demo")), "written file exists");
        eq(3, (int) vfs.size(layout.jarPath("demo")), "size is reported");
        eqBytes(new byte[]{1, 2, 3}, vfs.read(layout.jarPath("demo")), "payload round-trips");
        check(vfs.list(layout.gameDir("demo")).contains("suite.jar"), "listing shows the child");

        vfs.copy(layout.jarPath("demo"), layout.backupDir("demo") + "/suite.jar");
        eqBytes(new byte[]{1, 2, 3}, vfs.read(layout.backupDir("demo") + "/suite.jar"), "copy duplicates data");

        check(vfs.delete(layout.gameDir("demo")), "delete removes the tree");
        check(!vfs.exists(layout.jarPath("demo")), "children are gone after a tree delete");
        check(vfs.exists(layout.backupDir("demo") + "/suite.jar"), "backups survive a game delete");
        check(!vfs.delete("/nowhere"), "deleting a missing path reports false");
    }
}
