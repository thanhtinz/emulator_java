package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.AttributeSet;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.GameProfile;
import com.mobicore.tests.net.LoopbackTransport;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.VmObject;
import com.mobicore.core.vm.VmThrow;
import com.mobicore.tools.SampleSuite;
import com.mobicore.core.midp.SystemChrome;
import com.mobicore.tools.ui.IconData;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;

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
        icons();
        themes();

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

    /**
     * The palette has to work in both themes, and the emulated handset's own
     * bars have to follow it: a dark strip on a light screen reads as a bug.
     */
    private void themes() {
        int darkText = 0;
        int lightText = 0;
        try {
            Theme.setMode(Theme.DARK);
            check(Theme.isDark(), "dark mode is what it says");
            check(SystemChrome.isDark(), "and the handset's bars go dark with it");
            darkText = Theme.TEXT;
            check(luminance(Theme.BG) < luminance(Theme.TEXT),
                    "dark: text is lighter than the page");

            Theme.setMode(Theme.LIGHT);
            check(!Theme.isDark(), "light mode is what it says");
            check(!SystemChrome.isDark(), "and the bars follow");
            lightText = Theme.TEXT;
            check(luminance(Theme.BG) > luminance(Theme.TEXT),
                    "light: text is darker than the page");
            check(luminance(Theme.BG) < 250,
                    "the page is not pure white, which glares beside a game");

            // Contrast is the point of the exercise: a pale accent on white
            // is decoration, not something anyone can read.
            check(contrast(Theme.ACCENT, Theme.SURFACE) > 3.0,
                    "light: the accent reads on a card, ratio "
                            + (int) contrast(Theme.ACCENT, Theme.SURFACE));
            check(contrast(Theme.TEXT, Theme.BG) > 7.0, "light: body text is well clear");
            check(contrast(Theme.TEXT_DIM, Theme.SURFACE) > 3.5,
                    "light: even the quiet text is readable");
        } finally {
            Theme.setMode(Theme.LIGHT);
        }
        check(darkText != lightText, "the two themes are actually different");
    }

    private double luminance(int argb) {
        return 0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF)
                + 0.0722 * (argb & 0xFF);
    }

    /** WCAG contrast ratio, which is what "readable" is measured in. */
    private double contrast(int foreground, int background) {
        double first = relative(foreground);
        double second = relative(background);
        double lighter = Math.max(first, second);
        double darker = Math.min(first, second);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double relative(int argb) {
        double red = channel((argb >> 16) & 0xFF);
        double green = channel((argb >> 8) & 0xFF);
        double blue = channel(argb & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
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
}
