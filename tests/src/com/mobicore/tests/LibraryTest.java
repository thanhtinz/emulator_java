package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Covers install, per-game configuration, record store persistence and the
 * backup/restore round trip — including a save written by real bytecode.
 */
public final class LibraryTest extends Test {

    private final String fixtureDir;

    public LibraryTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Library, RMS and backups";
    }

    @Override
    public void run() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();
        eq(0, library.size(), "a fresh library is empty");
        check(vfs.isDirectory(layout.dir(StorageLayout.RMS)), "open creates the directory tree");

        byte[] jar = SampleSuite.jar(fixtureDir);
        GameLibrary.InstallResult result = library.install(jar, SampleSuite.jad());
        LibraryEntry entry = result.entry();
        eq("Sky Runner", entry.title(), "the installed entry carries the title");
        eq("mobicore-samples.sky-runner.1-2-0", entry.suiteId(), "the sandbox id is stable");
        check(!result.replaced(), "a first install is not a replacement");
        check(entry.hasArtwork(), "the icon was extracted from the JAR");
        check(vfs.exists(layout.jarPath(entry.suiteId())), "the JAR is copied into the sandbox");
        check(vfs.exists(layout.profilePath(entry.suiteId())), "a profile is written");

        // Reopening must find the game again: the index has to be durable.
        GameLibrary reopened = new GameLibrary(vfs, layout);
        reopened.open();
        eq(1, reopened.size(), "the index survives a reopen");
        eq("Sky Runner", reopened.find(entry.suiteId()).title(), "the entry is found by id");

        eq(1, reopened.search("sky").size(), "search matches the title");
        eq(1, reopened.search("MobiCore").size(), "search matches the vendor");
        eq(0, reopened.search("tetris").size(), "search rejects non-matches");
        eq(1, reopened.search("").size(), "an empty query lists everything");

        // Renaming and cover art -----------------------------------------
        LibraryEntry renamed = reopened.rename(entry.suiteId(), "  Người Chạy Trên Mây  ");
        eq("Người Chạy Trên Mây", renamed.title(), "a new name is trimmed and kept");
        eq("Sky Runner", renamed.originalTitle(), "the manifest title is not overwritten");
        check(renamed.isRenamed(), "the entry knows it was renamed");
        eq(1, reopened.search("Người").size(), "search finds the game under its new name");

        GameLibrary afterRename = new GameLibrary(vfs, layout);
        afterRename.open();
        eq("Người Chạy Trên Mây", afterRename.find(entry.suiteId()).title(),
                "the new name survives a reopen");
        eq("Sky Runner", afterRename.find(entry.suiteId()).originalTitle(),
                "so does the manifest title");
        eq("Sky Runner", afterRename.resetTitle(entry.suiteId()).title(),
                "resetting puts the manifest title back");

        boolean blankRejected = false;
        try {
            afterRename.rename(entry.suiteId(), "   ");
        } catch (IOException expected) {
            blankRejected = true;
        }
        check(blankRejected, "a blank name is refused, not stored");

        Framebuffer cover = new Framebuffer(24, 24);
        cover.fill(0xFF4488CC);
        byte[] chosen = PngWriter.encode(cover);
        check(afterRename.setArtwork(entry.suiteId(), chosen).hasArtwork(),
                "a chosen cover is accepted");
        eq(chosen.length, afterRename.artwork(entry.suiteId()).length,
                "the cover is stored exactly as given");

        boolean notAPng = false;
        try {
            afterRename.setArtwork(entry.suiteId(), new byte[]{1, 2, 3});
        } catch (IOException expected) {
            notAPng = true;
        }
        check(notAPng, "a file that is not a PNG is refused");
        eq(chosen.length, afterRename.artwork(entry.suiteId()).length,
                "and the cover that was there is left alone");

        afterRename.resetArtwork(entry.suiteId());
        check(afterRename.artwork(entry.suiteId()).length != chosen.length,
                "resetting puts the suite's own icon back");

        // Searching the way a name gets typed on a phone ------------------
        afterRename.rename(entry.suiteId(), "Người Chạy Trên Mây");
        eq(1, afterRename.search("nguoi chay").size(),
                "a search without marks finds a name that has them");
        eq(1, afterRename.search("NGƯỜI").size(), "and case does not matter either");
        eq(1, afterRename.search("Sky").size(),
                "a renamed game is still found under the name the suite gave it");
        eq(1, afterRename.search("mobicore").size(), "the publisher is searched too");
        eq(0, afterRename.search("tetris").size(), "and a word that is in none of them finds none");
        eq(1, afterRename.search("   ").size(), "a search of only spaces lists everything");
        afterRename.resetTitle(entry.suiteId());

        GameProfile profile = reopened.profile(entry.suiteId());
        check(profile != null, "the stored profile loads");
        profile.setVolume(40);
        profile.input().remap("fire", '5');
        reopened.saveProfile(profile);
        GameProfile again = reopened.profile(entry.suiteId());
        eq(40, again.volume(), "profile edits persist: the volume");
        eq('5', again.input().keyCodeFor("fire"), "the remap persists");

        // A save written by the game must land in the suite's own sandbox.
        if (new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            SuiteLoader suite = reopened.load(entry.suiteId());
            EmulatorSession session = EmulatorSession.create(suite, again, vfs, layout, null);
            session.start();
            eq(0, ((Integer) session.vm().callVirtual(session.context().midlet(),
                    "bestScore", "()I")).intValue(), "no score is saved yet");
            session.vm().callVirtual(session.context().midlet(), "saveScore", "(I)I",
                    Integer.valueOf(120));
            session.vm().callVirtual(session.context().midlet(), "saveScore", "(I)I",
                    Integer.valueOf(4500));
            session.vm().callVirtual(session.context().midlet(), "saveScore", "(I)I",
                    Integer.valueOf(300));
            eq(4500, ((Integer) session.vm().callVirtual(session.context().midlet(),
                    "bestScore", "()I")).intValue(),
                    "the RecordComparator sorted the highest score first");
            eq(3, ((Integer) session.vm().callVirtual(session.context().midlet(),
                    "savedScoreCount", "()I")).intValue(), "every score is a record");
            session.destroy();

            // A second session must see the same save on disk.
            EmulatorSession reloaded = EmulatorSession.create(reopened.load(entry.suiteId()),
                    again, vfs, layout, null);
            reloaded.start();
            eq(4500, ((Integer) reloaded.vm().callVirtual(reloaded.context().midlet(),
                    "bestScore", "()I")).intValue(), "saves survive a restart");
            reloaded.destroy();
        } else {
            fail("fixtures are not compiled; run ./build.sh fixtures");
        }

        RecordStoreManager records = reopened.records(entry.suiteId());
        List<String> stores = records.listStoreNames();
        eq(1, stores.size(), "the suite has one record store");
        eq("skyrunner-scores", stores.get(0), "the store keeps its declared name");
        RecordStoreManager.Store store = records.openStore("skyrunner-scores", false);
        eq(3, store.size(), "the store holds three records");
        eq(12, store.byteSize(), "each score is four bytes");

        // Backup, wipe, restore.
        String backupPath = reopened.backup(entry.suiteId());
        check(vfs.exists(backupPath), "the backup file was written");
        eq(1, reopened.backupsFor(entry.suiteId()).size(), "the backup is listed");

        reopened.records(entry.suiteId()).deleteAll();
        eq(0, reopened.records(entry.suiteId()).listStoreNames().size(), "the wipe removed the stores");

        reopened.restore(vfs.read(backupPath));
        RecordStoreManager restored = reopened.records(entry.suiteId());
        eq(1, restored.listStoreNames().size(), "restore brought the store back");
        eq(3, restored.openStore("skyrunner-scores", false).size(), "restore brought the records back");

        // resetGameData must snapshot before it destroys anything.
        String safety = reopened.resetGameData(entry.suiteId());
        check(vfs.exists(safety), "reset takes a backup first");
        eq(0, reopened.records(entry.suiteId()).listStoreNames().size(), "reset clears the stores");

        // Reinstalling the same suite is an upgrade, not a duplicate.
        GameLibrary.InstallResult upgrade = reopened.install(jar, SampleSuite.jad());
        check(upgrade.replaced(), "installing again replaces the existing entry");
        eq(1, reopened.size(), "an upgrade does not duplicate the library entry");

        check(reopened.uninstall(entry.suiteId(), false), "uninstall reports success");
        eq(0, reopened.size(), "the entry is gone");
        check(!vfs.exists(layout.gameDir(entry.suiteId())), "the sandbox directory is removed");
        check(vfs.exists(backupPath), "backups outlive the game they came from");
        check(!reopened.uninstall("nonexistent", false), "uninstalling an unknown id fails cleanly");
    }
}
