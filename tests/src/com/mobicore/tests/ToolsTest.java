package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.AttributeSet;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.mod.ModManager;
import com.mobicore.core.mod.ModPackage;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.net.LoopbackTransport;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.tools.CrashReport;
import com.mobicore.core.tools.JadEditor;
import com.mobicore.core.tools.RmsEditor;
import com.mobicore.core.vm.VmObject;
import com.mobicore.core.vm.VmThrow;
import com.mobicore.tools.SampleSuite;
import com.mobicore.tools.ui.IconData;
import com.mobicore.tools.ui.Icons;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Covers the network layer, the modding system and the developer tools. */
public final class ToolsTest extends Test {

    private final String fixtureDir;

    public ToolsTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Network, mods and tools";
    }

    @Override
    public void run() throws Exception {
        policy();
        jadEditor();
        icons();

        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();
        LibraryEntry entry = library.install(SampleSuite.jar(fixtureDir), SampleSuite.jad()).entry();

        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        network(library, entry);
        mods(library, entry);
        rmsEditor(library, entry);
        crash(library, entry);
    }

    /**
     * The interface draws no icon of its own: every glyph comes from the
     * Material set generated into {@link IconData}. These checks are what
     * catches a bad path parse — an icon that came out blank, solid, or
     * spilling past the box it was asked for.
     */
    private void icons() {
        check(IconData.NAMES.length >= 20, "the generated icon set is present");
        check(Icons.has(Icons.HOME) && Icons.has(Icons.IMPORT) && Icons.has(Icons.STAR),
                "the icons the screens name are in the set");

        Framebuffer frame = new Framebuffer(40, 40);
        frame.fill(0xFF000000);
        Icons.draw(frame, Icons.HOME, 4, 4, 32, 0xFFFFFFFF);

        int lit = 0;
        int outside = 0;
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) {
                boolean drawn = frame.pixel(x, y) != 0xFF000000;
                if (drawn) {
                    lit++;
                    if (x < 4 || y < 4 || x >= 36 || y >= 36) {
                        outside++;
                    }
                }
            }
        }
        check(lit > 100, "the icon paints something");
        check(lit < 32 * 32, "the icon is a shape, not a filled square");
        eq(0, outside, "the icon stays inside the box it was given");

        boolean partial = false;
        for (int x = 4; x < 36; x++) {
            int alpha = frame.pixel(x, 20) & 0xFF;
            if (alpha > 0 && alpha < 255) {
                partial = true;
            }
        }
        check(partial, "edges are anti-aliased, not hard");

        boolean rejected = false;
        try {
            Icons.draw(frame, "no_such_icon", 0, 0, 16, 0xFFFFFFFF);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "an icon that was never generated is refused, not blank");
    }

    private void policy() {
        NetworkPolicy policy = new NetworkPolicy();
        eq("scores.example.com", NetworkPolicy.hostOf("http://scores.example.com:8080/submit?a=1"),
                "the host is extracted without scheme or port");
        eq("example.com", NetworkPolicy.hostOf("socket://user@example.com:1234"),
                "userinfo and port are stripped");
        eq(null, NetworkPolicy.hostOf(""), "an empty URL has no host");
        eq("http", NetworkPolicy.schemeOf("http://example.com"), "the scheme is extracted");

        eq(NetworkPolicy.ASK, policy.decide("http://a.example.com/x"), "the default is to ask");
        policy.setMode(com.mobicore.core.model.GameProfile.NETWORK_BLOCKED);
        eq(NetworkPolicy.DENY, policy.decide("http://a.example.com/x"), "blocked mode denies");
        policy.allowHost("a.example.com");
        eq(NetworkPolicy.ALLOW, policy.decide("http://a.example.com/x"),
                "an allowed host overrides blocked mode");
        eq(NetworkPolicy.DENY, policy.decide("http://b.example.com/x"),
                "other hosts stay blocked");
        policy.denyHost("a.example.com");
        eq(NetworkPolicy.DENY, policy.decide("http://a.example.com/x"), "denying wins over allowing");
        eq(0, policy.allowedHosts().size(), "denying a host removes it from the allow list");

        NetworkPolicy restored = NetworkPolicy.fromJson(policy.toJson());
        eq(1, restored.deniedHosts().size(), "the policy survives JSON");
    }

    private void jadEditor() {
        JadEditor editor = JadEditor.parse(SampleSuite.jad());
        check(editor.isValid(), "the sample descriptor is valid");
        eq("Sky Runner", editor.get("MIDlet-Name"), "values read back");

        editor.set("MIDlet-Name", "Sky Runner HD");
        eq("Sky Runner HD", editor.get("MIDlet-Name"), "edits take effect");
        check(editor.toDescriptor().indexOf("MIDlet-Name: Sky Runner HD") >= 0,
                "the edit appears in the serialised descriptor");

        editor.syncWithJar("SkyRunnerHD.jar", 40960);
        eq("40960", editor.get("MIDlet-Jar-Size"), "the JAR size is synced");

        editor.remove("MIDlet-Vendor");
        List<JadEditor.Problem> problems = editor.validate();
        check(!editor.isValid(), "removing the vendor makes the descriptor invalid");
        boolean vendorReported = false;
        for (JadEditor.Problem problem : problems) {
            if (problem.attribute().equals("MIDlet-Vendor") && problem.isError()) {
                vendorReported = true;
            }
        }
        check(vendorReported, "the missing vendor is reported as an error");

        JadEditor broken = new JadEditor(AttributeSet.parse(
                "MIDlet-Name: X\nMIDlet-Vendor: Y\nMIDlet-Version: banana\nMIDlet-1: X,,\n"));
        check(!broken.isValid(), "a MIDlet entry without a class is invalid");
        boolean versionWarned = false;
        for (JadEditor.Problem problem : broken.validate()) {
            if (problem.attribute().equals("MIDlet-Version") && !problem.isError()) {
                versionWarned = true;
            }
        }
        check(versionWarned, "a nonsense version is a warning, not an error");
    }

    private void network(GameLibrary library, LibraryEntry entry) throws Exception {
        GameProfile profile = library.profile(entry.suiteId());
        SuiteLoader suite = library.load(entry.suiteId());
        EmulatorSession session = EmulatorSession.create(suite, profile,
                library.storage(), library.layout(), null);
        NetworkStack network = session.network();

        LoopbackTransport loopback = new LoopbackTransport()
                .respond("/submit", 200, "OK", "rank=7", "text/plain");
        network.setTransport(loopback);
        session.start();

        // Default policy is "ask", and nothing answers, so the call is refused.
        String url = "http://scores.example.com/submit";
        boolean refused = false;
        try {
            session.vm().callVirtual(session.context().midlet(), "submitScore",
                    "(Ljava/lang/String;I)Ljava/lang/String;", session.vm().newString(url),
                    Integer.valueOf(4500));
        } catch (VmThrow e) {
            refused = true;
        }
        check(refused, "an unanswered prompt refuses the connection");
        eq(1, network.monitor().size(), "the refused attempt is still recorded");
        eq("blocked", network.monitor().exchanges().get(0).outcome(), "it is recorded as blocked");
        eq(NetworkPolicy.ASK, network.policy().decide(url),
                "an unanswered prompt does not blacklist the host");

        // Now the user says yes for this host.
        network.setPrompt(new NetworkStack.PermissionPrompt() {
            public boolean allowHost(String host, String url) {
                return "scores.example.com".equals(host);
            }
        });
        Object result = session.vm().callVirtual(session.context().midlet(), "submitScore",
                "(Ljava/lang/String;I)Ljava/lang/String;", session.vm().newString(url),
                Integer.valueOf(4500));
        eq("200 rank=7", session.vm().stringOf((VmObject) result),
                "the game read the response the loopback served");

        eq(1, loopback.received().size(), "exactly one request left the emulator");
        eq("POST", loopback.received().get(0).method,
                "writing a body turned the GET into a POST");
        eq("score=4500", new String(loopback.received().get(0).body, "UTF-8"),
                "the body the game wrote arrived intact");
        eq("application/x-www-form-urlencoded",
                loopback.received().get(0).headers.get("Content-Type"),
                "request headers arrived");

        List<NetworkMonitor.Exchange> exchanges = network.monitor().exchanges();
        eq(2, exchanges.size(), "both attempts are in the monitor");
        NetworkMonitor.Exchange allowed = exchanges.get(1);
        eq(200, allowed.status(), "the status is recorded");
        eq("scores.example.com", allowed.host(), "the host is recorded");
        eq("score=4500", allowed.requestPreview(), "the request body preview is captured");
        eq("rank=7", allowed.responsePreview(), "the response body preview is captured");
        eq(20, network.monitor().totals()[0],
                "sent bytes are totalled across both attempts");

        // The remembered decision means the second call does not prompt again.
        eq(NetworkPolicy.ALLOW, network.policy().decide(url), "the allowance was remembered");

        // A scheme the emulator does not support fails loudly, not silently.
        boolean rejectedScheme = false;
        try {
            session.vm().callVirtual(session.context().midlet(), "submitScore",
                    "(Ljava/lang/String;I)Ljava/lang/String;",
                    session.vm().newString("socket://scores.example.com:9000"), Integer.valueOf(1));
        } catch (VmThrow e) {
            rejectedScheme = true;
        }
        check(rejectedScheme, "an unsupported scheme raises ConnectionNotFoundException");

        session.destroy();
    }

    private void mods(GameLibrary library, LibraryEntry entry) throws Exception {
        ModManager mods = new ModManager(library, entry.suiteId());
        eq(0, mods.installed().size(), "no mods are installed to begin with");

        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();
        files.put("mod.json", SampleSuite.utf8("{\"id\":\"hd-tiles\",\"name\":\"HD Tiles\","
                + "\"version\":\"1.1\",\"author\":\"Community\",\"target\":\"" + entry.suiteId()
                + "\",\"description\":\"Higher resolution ground tiles\"}"));
        files.put("res/level1.dat", new byte[]{7, 7, 7});
        ModPackage installed = mods.install("hd-tiles", SampleSuite.zip(files));
        eq("hd-tiles", installed.modId(), "the manifest id is used");
        eq("HD Tiles", installed.name(), "the manifest name is read");
        check(!installed.touchesCode(), "a resource-only mod does not touch code");
        eq(1, installed.replacedResources().size(), "the replaced resource is listed");
        eq(1, library.backupsFor(entry.suiteId()).size(),
                "installing a mod backs the game up first");

        eq(1, mods.installed().size(), "the mod is listed");
        check(!mods.installed().get(0).isEnabled(), "a new mod starts disabled");
        mods.setEnabled("hd-tiles", true);
        check(mods.installed().get(0).isEnabled(), "enabling persists");

        // With the mod enabled, the overlay wins over the original resource.
        GameProfile profile = library.profile(entry.suiteId());
        EmulatorSession session = EmulatorSession.create(library.load(entry.suiteId()), profile,
                library.storage(), library.layout(), null);
        eq(1, mods.applyTo(session), "the enabled mod is applied");
        eq(3, session.source().resourceBytes("res/level1.dat").length,
                "the mod's resource replaces the original");
        eq(1024, com.mobicore.core.jar.SuiteLoader
                        .load(library.storage().read(library.layout().jarPath(entry.suiteId())), null)
                        .archive().read("res/level1.dat").length,
                "the original JAR on disk is untouched");
        session.destroy();

        mods.setEnabled("hd-tiles", false);
        EmulatorSession plain = EmulatorSession.create(library.load(entry.suiteId()), profile,
                library.storage(), library.layout(), null);
        eq(0, mods.applyTo(plain), "a disabled mod is not applied");
        eq(1024, plain.source().resourceBytes("res/level1.dat").length,
                "disabling restores the stock resource exactly");
        plain.destroy();

        Map<String, byte[]> codeMod = new LinkedHashMap<String, byte[]>();
        codeMod.put("mod.json", SampleSuite.utf8("{\"id\":\"patched\",\"name\":\"Patched\"}"));
        codeMod.put("demo/SkyRunner.class", new byte[]{(byte) 0xCA, (byte) 0xFE, 0, 0});
        ModPackage patch = mods.install("patched", SampleSuite.zip(codeMod));
        check(patch.touchesCode(), "a mod carrying class files is flagged");

        check(mods.uninstall("patched"), "a mod can be removed");
        eq(1, mods.installed().size(), "removal leaves the other mod alone");

        // A mod for a different game is refused rather than silently applied.
        Map<String, byte[]> wrong = new LinkedHashMap<String, byte[]>();
        wrong.put("mod.json", SampleSuite.utf8("{\"id\":\"other\",\"target\":\"someone.else.1-0\"}"));
        boolean refused = false;
        try {
            mods.install("other", SampleSuite.zip(wrong));
        } catch (java.io.IOException e) {
            refused = true;
        }
        check(refused, "a mod aimed at another suite is refused");
    }

    private void rmsEditor(GameLibrary library, LibraryEntry entry) throws Exception {
        RecordStoreManager records = library.records(entry.suiteId());
        RmsEditor editor = new RmsEditor(records, 1_700_000_000_000L);

        int id = editor.addRecord("scores", new byte[]{0, 0, 0x11, 0x22});
        eq(1, id, "the first record gets id 1");
        eq(1, editor.stores().size(), "the store now exists");
        List<RmsEditor.Record> all = editor.records("scores");
        eq(1, all.size(), "the record is listed");
        eq(0x1122, all.get(0).asInt(), "records decode as big-endian ints, as games write them");
        eq("00 00 11 22", all.get(0).asHex(), "hex rendering is grouped");
        eq("...\"", all.get(0).asText(),
                "unprintable bytes render as dots and printable ones as themselves");

        check(editor.setRecord("scores", 1, RmsEditor.parseHex("00 00 30 39")),
                "a record can be rewritten from hex");
        eq(12345, editor.records("scores").get(0).asInt(), "the edit round-trips");
        check(!editor.setRecord("scores", 99, new byte[0]), "editing a missing record fails cleanly");

        // The edit must be on disk, not only in memory.
        RecordStoreManager reopened = library.records(entry.suiteId());
        eq(12345, new RmsEditor(reopened, 0).records("scores").get(0).asInt(),
                "the edit was flushed to storage");

        check(editor.deleteRecord("scores", 1), "a record can be deleted");
        eq(0, editor.records("scores").size(), "the store is empty afterwards");
        eqBytes(new byte[]{(byte) 0xDE, (byte) 0xAD}, RmsEditor.parseHex("de:ad"),
                "the hex parser ignores separators");
    }

    private void crash(GameLibrary library, LibraryEntry entry) throws Exception {
        GameProfile profile = library.profile(entry.suiteId());
        EmulatorSession session = EmulatorSession.create(library.load(entry.suiteId()), profile,
                library.storage(), library.layout(), null);
        session.start();
        VmThrow failure;
        try {
            throw session.vm().raise("java/lang/IllegalStateException", "the level index is out of range");
        } catch (VmThrow e) {
            failure = e;
        }
        CrashReport report = CrashReport.from(session, failure);
        eq("java.lang.IllegalStateException", report.title(), "the exception type is reported");
        eq("the level index is out of range", report.detail(), "the message is reported");
        String text = report.render();
        check(text.indexOf("Sky Runner") >= 0, "the report names the suite");
        check(text.indexOf("CLDC-1.1") >= 0, "the report records the runtime");
        check(text.indexOf("--- log ---") >= 0, "the report carries the log");
        check(report.toJson().indexOf("\"title\"") >= 0, "the report also serialises to JSON");
        session.destroy();
    }
}
