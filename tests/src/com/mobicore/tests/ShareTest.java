package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.library.ShareExport;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.SampleSuite;

import java.util.Map;

/**
 * Getting a picture or a clip out of the app.
 *
 * <p>A screenshot nobody can send is half a screenshot. Inside the app it is
 * called {@code 1700000000000.png} — the right name for a file the app itself
 * reads, and one that says nothing when it lands in a chat.</p>
 */
public final class ShareTest extends Test {

    private final String fixtureDir;

    public ShareTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Chia sẻ ảnh và đoạn quay";
    }

    @Override
    public void run() throws Exception {
        stamps();
        names();
        files();
        throughTheBridge();
    }

    // --------------------------------------------------------------- the time

    /**
     * The moment out of a file's own name.
     *
     * <p>Worked out with arithmetic rather than a date formatter, because the
     * core carries no dependencies — so it is worth checking against dates a
     * calendar can be held up to.</p>
     */
    private void stamps() {
        eq("1970-01-01 00-00", ShareExport.stampOf("1000.png"), "the epoch itself");
        eq("2023-11-14 22-13", ShareExport.stampOf("1699999999999.png"),
                "a moment in an ordinary year");
        // A leap year, the day after the leap day, and the last day of one:
        // the three dates a calendar written by hand gets wrong.
        eq("2024-02-29 12-00", ShareExport.stampOf("1709208000000.png"), "a leap day");
        eq("2024-03-01 00-00", ShareExport.stampOf("1709251200000.png"),
                "the day after a leap day");
        eq("2024-12-31 23-59", ShareExport.stampOf("1735689599000.png"),
                "the last minute of a leap year");
        eq("2100-03-01 00-00", ShareExport.stampOf("4107542400000.png"),
                "and a century that is not a leap year");

        eq("", ShareExport.stampOf("anh-chup.png"), "a name with no time in it has no stamp");
        eq("", ShareExport.stampOf("0.png"), "and neither does a time of zero");
    }

    // -------------------------------------------------------------- the name

    private void names() {
        ShareExport share = new ShareExport(new MemoryVfs(), new StorageLayout("MobiCore"));
        eq("Sky Runner 2023-11-14 22-13.png",
                share.fileNameFor("Sky Runner", "1699999999999.png"),
                "the game, the moment, and what kind of file it is");
        eq("Sky Runner 2023-11-14 22-13.gif",
                share.fileNameFor("Sky Runner", "1699999999999.gif"),
                "a clip keeps its own kind");

        // The title is the player's: they can rename a game to anything,
        // including something that is a path rather than a name.
        eq("etcpasswd 2023-11-14 22-13.png",
                share.fileNameFor("../../etc/passwd", "1699999999999.png"),
                "a title that is a path cannot become one");
        eq("MobiCore 2023-11-14 22-13.png",
                share.fileNameFor("///", "1699999999999.png"),
                "and a title with nothing usable in it still gets a name");
        eq(60, ShareExport.safeTitle("A".repeat(200)).length(),
                "a title nobody could have meant is cut to a length a file system takes");

        check(ShareExport.isClip("1700000000000.gif"), "a .gif is a clip");
        check(!ShareExport.isClip("1700000000000.png"), "a .png is not");
        eq("image/gif", ShareExport.mimeOf("a.gif"), "and each is sent as what it is");
        eq("image/png", ShareExport.mimeOf("a.png"), "so the other app knows what it got");
    }

    // ------------------------------------------------------------- the copy

    private void files() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("MobiCore");
        ShareExport share = new ShareExport(vfs, layout);

        String path = share.prepare("Sky Runner", "1699999999999.png", new byte[]{1, 2, 3});
        check(vfs.exists(path), "the copy is written: " + path);
        check(path.indexOf("cache") >= 0,
                "into the cache, because it is a copy nobody asked to keep");
        eq(3L, vfs.size(path), "with the picture in it");

        boolean refused = false;
        try {
            share.prepare("Sky Runner", "1.png", new byte[0]);
        } catch (java.io.IOException e) {
            refused = true;
        }
        check(refused, "and an empty picture is refused rather than written");

        // A folder that only ever grows is a folder that will one day be the
        // reason a phone is out of space.
        for (int i = 0; i < ShareExport.KEEP + 5; i++) {
            share.prepare("Game " + i, (1_700_000_000_000L + i * 60_000L) + ".png",
                    new byte[]{9});
        }
        eq(ShareExport.KEEP, vfs.list(share.directory()).size(),
                "only the newest copies are kept");
    }

    // ------------------------------------------------------------- the bridge

    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        facade.startGame(suiteId);
        facade.renderFrame();
        facade.takeScreenshot();
        String name = Json.string((Map<String, Object>) Json.array(
                Json.readObject(facade.screenshotsJson(suiteId)), "screenshots").get(0),
                "name", "");

        Map<String, Object> shared = Json.readObject(facade.shareScreenshot(suiteId, name));
        check(Json.bool(shared, "ok", false),
                "a picture can be got ready to send: " + Json.string(shared, "error", ""));
        check(Json.string(shared, "name", "").startsWith("Sky Runner"),
                "under the game's name: " + Json.string(shared, "name", ""));
        eq("image/png", Json.string(shared, "mime", ""), "and said to be what it is");
        check(!Json.bool(shared, "clip", true), "a still is not a clip");

        // A name from outside must never be able to name a file of its own.
        check(!Json.bool(Json.readObject(
                        facade.shareScreenshot(suiteId, "../library.json")), "ok", true),
                "a name that tries to leave the folder shares nothing");
        check(!Json.bool(Json.readObject(
                        facade.shareScreenshot(suiteId, "khong-co.png")), "ok", true),
                "and a picture that is not there cannot be sent");

        facade.stopGame();
    }
}
