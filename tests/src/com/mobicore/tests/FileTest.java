package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpFiles;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * JSR-75 files, and the sandbox they live in.
 *
 * <p>The fixture is a real MIDlet, compiled to real bytecode and run by the
 * interpreter: it makes a directory, writes a level, reads it back, appends to
 * it, lists the directory, and then tries to write outside the game's own
 * folder. What it leaves in its own fields is what this checks — a call that
 * returned without doing anything would pass a test that only checked for the
 * absence of an exception.</p>
 */
public final class FileTest extends Test {

    private final String fixtureDir;

    public FileTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Tệp riêng của game (JSR-75)";
    }

    @Override
    public void run() throws Exception {
        paths();
        filters();
        if (!new File(fixtureDir, "demo/FileDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        runsAsBytecode();
        throughTheBridge();
    }

    // ------------------------------------------------------------- bytecode

    private void runsAsBytecode() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("MobiCore");
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        EmulatorSession session = EmulatorSession.create(suite, profile, vfs, layout, null);
        session.start("demo.FileDemo");

        VmObject midlet = session.midlet();
        eq("xin chào:1234", session.vm().stringOf(midlet.get("readBack")),
                "what the game wrote is what the game reads back");
        eq(6, ((Integer) midlet.get("steps")).intValue(),
                "every step the fixture takes succeeds");
        // Two bytes on the end of a file that held a UTF string and an int:
        // appending writes past what is there rather than over it.
        eq(17, ((Integer) midlet.get("size")).intValue(),
                "and appending made the file longer, not shorter");
        eq(1, ((Integer) midlet.get("listed")).intValue(),
                "and the directory lists what is in it");
        eq(MidpFiles.ROOT, session.vm().stringOf(midlet.get("root")),
                "the game is shown one root, named for the emulator");
        // Booleans live as ints inside the interpreter, the way they do in
        // real bytecode.
        check(((Integer) midlet.get("escapeRefused")).intValue() != 0,
                "and a path that climbs out of the game's folder is refused");

        // The bytes really are on disk, under this game's own folder and
        // nowhere else — which is the whole claim the sandbox makes.
        String home = StorageLayout.join(
                StorageLayout.join(layout.gameDir(profile.suiteId()), "files"), "levels");
        String path = StorageLayout.join(home, "level1.dat");
        check(vfs.exists(path), "the file is in storage: " + path);
        eq(17L, vfs.size(path), "with the appended bytes on the end");
        check(!vfs.exists("MobiCore/library.json"),
                "and nothing was written where the game tried to escape to");

        session.destroy();
    }

    /**
     * The same files, seen from outside the emulator.
     *
     * <p>They are the player's — a saved level, a downloaded track — so they
     * are visible and removable rather than hidden inside the app.</p>
     */
    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        eq(0, Json.array(Json.readObject(facade.gameFilesJson(suiteId)), "files").size(),
                "a game that has written nothing has no files");

        facade.startGame(suiteId, "demo.FileDemo");
        Map<String, Object> listing = Json.readObject(facade.gameFilesJson(suiteId));
        List<Object> files = Json.array(listing, "files");
        eq(1, files.size(), "what the game wrote shows up");
        Map<String, Object> file = (Map<String, Object>) files.get(0);
        eq("levels/level1.dat", Json.string(file, "path", ""),
                "under the name the game gave it, folder and all");
        eq(17, Json.integer(file, "bytes", 0), "with its size");
        eq(17, Json.integer(listing, "bytes", 0), "and a total to show the player");

        // A path from outside is confined exactly as a game's own path is.
        check(!Json.bool(Json.readObject(
                        facade.deleteGameFile(suiteId, "../../library.json")), "ok", true),
                "a name that tries to leave the folder deletes nothing");
        check(Json.bool(Json.readObject(
                        facade.deleteGameFile(suiteId, "levels/level1.dat")), "ok", false),
                "and a real one can be thrown away");
        eq(0, Json.array(Json.readObject(facade.gameFilesJson(suiteId)), "files").size(),
                "after which the game has no files again");

        facade.stopGame();
    }

    // ---------------------------------------------------------------- paths

    /**
     * Where a URL a game hands over actually lands.
     *
     * <p>Games hard-code whichever root their target handset had, so the root
     * is thrown away; what follows it is the file's name and must survive.</p>
     */
    private void paths() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("MobiCore");
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        EmulatorSession session = EmulatorSession.create(suite, profile, vfs, layout, null);
        String base = StorageLayout.join(layout.gameDir(profile.suiteId()), "files");

        eq(base + "/save.dat", opened(session, "file:///c:/save.dat"),
                "a Series 40 game's c:/ lands in the game's own folder");
        eq(base + "/save.dat", opened(session, "file:///root1/save.dat"),
                "and so does a Series 60 game's root1/");
        eq(base + "/save.dat", opened(session, "file:///Memory card/save.dat"),
                "and a memory card nobody has any more");
        // A first segment that is not a handset's storage is part of the name:
        // eating it would put the file where the game cannot find it again.
        eq(base + "/levels/1.dat", opened(session, "file:///levels/1.dat"),
                "a first segment that is not a root stays part of the path");
        eq(base + "/a/b.dat", opened(session, "file:///c:/x/../a/b.dat"),
                "a path that climbs and comes back is fine");

        boolean refused = false;
        try {
            opened(session, "file:///c:/../../library.json");
        } catch (RuntimeException e) {
            refused = true;
        }
        check(refused, "a path that climbs out is refused, not clipped");

        refused = false;
        try {
            opened(session, "file:///../profiles");
        } catch (RuntimeException e) {
            refused = true;
        }
        check(refused, "with or without a root in front of it");

        session.destroy();
    }

    /** Where one URL resolves to, through the same calls a game goes through. */
    private String opened(EmulatorSession session, String url) {
        String base = StorageLayout.join(new StorageLayout("MobiCore")
                .gameDir(session.profile().suiteId()), "files");
        VmObject connection = MidpFiles.open(session.vm(), url, 3);
        return MidpFiles.resolve(session.vm(), base,
                MidpFiles.pathOf(session.vm(), connection));
    }

    // --------------------------------------------------------------- filters

    /** JSR-75's listing filter: a name with at most one {@code *} in it. */
    private void filters() {
        check(MidpFiles.matches("level1.dat", "*"), "everything matches a bare star");
        check(MidpFiles.matches("level1.dat", "*.dat"), "a suffix filter matches");
        check(!MidpFiles.matches("level1.png", "*.dat"), "and rejects the rest");
        check(MidpFiles.matches("level1.dat", "level*"), "a prefix filter matches");
        check(MidpFiles.matches("level1.dat", "level1.dat"), "a plain name matches itself");
        check(!MidpFiles.matches("level1.dat", "save.dat"), "and nothing else");
        // The head and tail must not overlap: "ab" is not "a*b" twice over.
        check(!MidpFiles.matches("ab", "abc*abc"), "a filter longer than the name matches nothing");
    }
}
