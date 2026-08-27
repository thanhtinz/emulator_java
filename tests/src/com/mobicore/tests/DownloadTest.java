package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.library.UrlInstaller;
import com.mobicore.core.net.LoopbackTransport;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.List;
import java.util.Map;

/**
 * Installing a game from a link.
 *
 * <p>These games arrive as a link before they arrive as a file — an archive
 * site, a forum post, a friend's folder. Everything here is served from a
 * loopback transport, so the suite tests the whole path without a network.</p>
 */
public final class DownloadTest extends Test {

    private final String fixtureDir;

    public DownloadTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Cài game từ liên kết";
    }

    @Override
    public void run() throws Exception {
        jarUrls();
        fromJar();
        fromDescriptor();
        refusals();
    }

    // ------------------------------------------------------------ resolution

    /**
     * A descriptor names its JAR however whoever wrote it felt like.
     *
     * <p>Most name a bare file, meaning "beside this descriptor"; getting that
     * wrong fetches a JAR from the site's root, which is usually a 404 and
     * occasionally somebody else's game.</p>
     */
    private void jarUrls() throws Exception {
        eq("http://site.example/games/run.jar",
                UrlInstaller.resolve("http://site.example/games/run.jad", "run.jar"),
                "a bare name means beside the descriptor");
        eq("http://site.example/files/run.jar",
                UrlInstaller.resolve("http://site.example/games/run.jad", "/files/run.jar"),
                "a leading slash means from the site's root");
        eq("https://cdn.example/run.jar",
                UrlInstaller.resolve("http://site.example/games/run.jad",
                        "https://cdn.example/run.jar"),
                "and a full address is taken as it is");
        eq("http://site.example/run.jar",
                UrlInstaller.resolve("http://site.example/run.jad", "run.jar"),
                "a descriptor at the root works too");

        check(UrlInstaller.isJar(new byte[]{'P', 'K', 3, 4, 0, 0}), "a JAR is a zip");
        check(!UrlInstaller.isJar("<html>".getBytes("UTF-8")), "a web page is not");
    }

    // ---------------------------------------------------------------- a JAR

    /** The simple case: the link is the game. */
    private void fromJar() throws Exception {
        MobiCoreFacade facade = open();
        facade.setInstallerTransport(new LoopbackTransport()
                .respondBytes("/run.jar", 200, SampleSuite.jar(fixtureDir),
                        "application/java-archive"));

        Map<String, Object> installed = Json.readObject(
                facade.installFromUrl("http://site.example/games/run.jar"));
        check(Json.bool(installed, "ok", false),
                "a link to a JAR installs: " + Json.string(installed, "error", ""));
        eq("Sky Runner", Json.string(Json.child(installed, "game"), "title", ""),
                "and the game is the one that was downloaded");
        eq(1, Json.array(Json.readObject(facade.libraryJson()), "games").size(),
                "and it is in the library");

        // What was fetched is recorded, so the player can see where their
        // game came from rather than having to trust the app.
        List<Object> downloads = Json.array(Json.readObject(facade.downloadsJson()), "downloads");
        eq(1, downloads.size(), "the download is recorded");
        check(Json.string((Map<String, Object>) downloads.get(0), "url", "")
                .indexOf("site.example") >= 0, "with the address it came from");
    }

    // --------------------------------------------------------- a descriptor

    /** The usual case: the link is a JAD, which names the JAR beside it. */
    private void fromDescriptor() throws Exception {
        MobiCoreFacade facade = open();
        facade.setInstallerTransport(new LoopbackTransport()
                .respond("/games/run.jad", 200, "OK",
                        "MIDlet-Name: Sky Runner\n"
                                + "MIDlet-Version: 1.2.0\n"
                                + "MIDlet-Vendor: MobiCore Samples\n"
                                + "MIDlet-Jar-URL: run.jar\n"
                                + "MIDlet-Jar-Size: 24576\n"
                                + "MIDlet-1: Sky Runner,,demo.SkyRunner\n",
                        "text/vnd.sun.j2me.app-descriptor")
                .respondBytes("/games/run.jar", 200, SampleSuite.jar(fixtureDir),
                        "application/java-archive"));

        Map<String, Object> installed = Json.readObject(
                facade.installFromUrl("http://site.example/games/run.jad"));
        check(Json.bool(installed, "ok", false),
                "a link to a descriptor installs: " + Json.string(installed, "error", ""));
        eq("http://site.example/games/run.jar", Json.string(installed, "jarUrl", ""),
                "the JAR was fetched from beside the descriptor");
        check(Json.array(installed, "notes").size() >= 3,
                "and it says in words what it did");

        // The descriptor is kept: its attributes beat the manifest's, which is
        // how a JAD tells the emulator things the JAR does not.
        Map<String, Object> game = (Map<String, Object>) Json.array(
                Json.readObject(facade.libraryJson()), "games").get(0);
        eq("1.2.0", Json.string(game, "version", ""), "with the descriptor's own version");

        eq(2, Json.array(Json.readObject(facade.downloadsJson()), "downloads").size(),
                "both fetches are recorded, not just the one that was typed");
    }

    // ------------------------------------------------------------- refusals

    /**
     * A link that is wrong should say so plainly.
     *
     * <p>A web page installed as a game is a game that fails later and less
     * clearly, so what comes back is looked at before it is installed.</p>
     */
    private void refusals() throws Exception {
        MobiCoreFacade facade = open();
        facade.setInstallerTransport(new LoopbackTransport()
                .respond("/missing.jar", 404, "Not Found", "", "text/plain")
                .respond("/login", 200, "OK",
                        "<!DOCTYPE html><html><body>Sign in</body></html>", "text/html")
                .respond("/empty.jar", 200, "OK", "", "text/plain")
                .respond("/broken.jad", 200, "OK",
                        "MIDlet-Name: Broken\nMIDlet-1: Broken,,demo.Broken\n",
                        "text/vnd.sun.j2me.app-descriptor")
                .respond("/nojar.jad", 200, "OK",
                        "MIDlet-Name: Sky Runner\nMIDlet-Jar-URL: run.jar\n"
                                + "MIDlet-1: Sky Runner,,demo.SkyRunner\n",
                        "text/vnd.sun.j2me.app-descriptor")
                .respond("/games/run.jar", 200, "OK",
                        "<!DOCTYPE html><html>gone</html>", "text/html"));

        eq("Chỉ tải được liên kết http hoặc https",
                errorOf(facade.installFromUrl("ftp://site.example/run.jar")),
                "a link that is not http is refused before anything is fetched");
        eq("Chưa có liên kết nào", errorOf(facade.installFromUrl("   ")),
                "and so is no link at all");
        eq("Không có gì ở liên kết này (404)",
                errorOf(facade.installFromUrl("http://site.example/missing.jar")),
                "a dead link says it is dead");
        check(errorOf(facade.installFromUrl("http://site.example/login"))
                        .indexOf("trang web") >= 0,
                "a login page is named for what it is, so the player knows to open it");
        eq("Liên kết trả về tệp rỗng",
                errorOf(facade.installFromUrl("http://site.example/empty.jar")),
                "an empty response is refused");
        check(errorOf(facade.installFromUrl("http://site.example/broken.jad"))
                        .indexOf("không phải tệp game") >= 0,
                "a descriptor with no JAR named in it cannot be followed");
        check(errorOf(facade.installFromUrl("http://site.example/games/nojar.jad"))
                        .indexOf("không phải .jar") >= 0,
                "and a descriptor pointing at a web page says which half failed");

        eq(0, Json.array(Json.readObject(facade.libraryJson()), "games").size(),
                "and after all that, nothing was installed");
    }

    // ---------------------------------------------------------------- tools

    private MobiCoreFacade open() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        return facade;
    }

    private String errorOf(String response) {
        return Json.string(Json.readObject(response), "error", "");
    }
}
