package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.mod.ModManager;
import com.mobicore.core.mod.ModPackage;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.net.LoopbackTransport;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.tools.JadEditor;
import com.mobicore.core.tools.RmsEditor;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview of the developer tools: network monitor, mods, descriptor validation
 * and the RMS editor. Every figure comes from a real session.
 */
public final class DevToolsScreen {

    private final String fixtureDir;

    public DevToolsScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();
        LibraryEntry entry = library.install(SampleSuite.jar(fixtureDir), SampleSuite.jad()).entry();
        GameProfile profile = library.profile(entry.suiteId());

        // A mod that replaces one resource, enabled.
        ModManager mods = new ModManager(library, entry.suiteId());
        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();
        files.put("mod.json", SampleSuite.utf8("{\"id\":\"hd-tiles\",\"name\":\"HD Tiles\","
                + "\"version\":\"1.1\",\"author\":\"Community\",\"target\":\""
                + entry.suiteId() + "\"}"));
        files.put("res/tiles.png", new byte[4096]);
        mods.install("hd-tiles", SampleSuite.zip(files));
        mods.setEnabled("hd-tiles", true);

        // A session that actually makes a request, answered locally.
        EmulatorSession session = EmulatorSession.create(library.load(entry.suiteId()), profile,
                vfs, layout, null);
        NetworkStack network = session.network();
        network.setClock(new NetworkStack.Clock() {
            private long clock = 1_700_000_000_000L;

            public long now() {
                clock += 37;
                return clock;
            }
        });
        network.setTransport(new LoopbackTransport()
                .respond("/submit", 200, "OK", "rank=7&best=8630", "text/plain")
                .respond("/news", 404, "Not Found", "gone", "text/plain"));
        network.setPrompt(new NetworkStack.PermissionPrompt() {
            public boolean allowHost(String host, String url) {
                return "scores.example.com".equals(host);
            }
        });
        mods.applyTo(session);
        session.start();

        call(session, "http://scores.example.com/submit", 8630);
        call(session, "http://scores.example.com/news", 0);
        call(session, "http://ads.tracker.example/news", 0);

        RmsEditor rms = new RmsEditor(library.records(entry.suiteId()), 1_700_000_000_000L);
        rms.addRecord("skyrunner-scores", new byte[]{0, 0, 0x21, (byte) 0xB6});
        rms.addRecord("skyrunner-scores", new byte[]{0, 0, 0x10, 0x1A});

        Framebuffer frame = draw(library, entry, session, mods, rms);
        session.destroy();
        return frame;
    }

    private void call(EmulatorSession session, String url, int score) {
        try {
            session.vm().callVirtual(session.context().midlet(), "submitScore",
                    "(Ljava/lang/String;I)Ljava/lang/String;", session.vm().newString(url),
                    Integer.valueOf(score));
        } catch (RuntimeException e) {
            // Refused and failed calls are exactly what the monitor is for.
        }
    }

    private Framebuffer draw(GameLibrary library, LibraryEntry entry, EmulatorSession session,
                             ModManager mods, RmsEditor rms) throws Exception {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Developer Tools", entry.title());

        int margin = 16;
        int width = frame.width() - margin * 2;
        int y = 60;

        // Network monitor ------------------------------------------------
        List<NetworkMonitor.Exchange> exchanges = session.network().monitor().exchanges();
        int netHeight = 46 + exchanges.size() * 30;
        ui.panel(margin, y, width, netHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "NETWORK MONITOR", margin + 14, y + 10, Theme.TEXT_DIM);
        int[] totals = session.network().monitor().totals();
        ui.textRight(ui.small(), totals[0] + " B up / " + totals[1] + " B down",
                margin + width - 14, y + 10, Theme.ACCENT);
        int rowY = y + 28;
        for (NetworkMonitor.Exchange exchange : exchanges) {
            boolean blocked = "blocked".equals(exchange.outcome());
            String label = exchange.method() + "  " + exchange.host();
            ui.text(ui.smallBold(), ui.ellipsize(ui.smallBold(), label, width - 110),
                    margin + 14, rowY, blocked ? Theme.BAD : Theme.TEXT);
            String status = blocked ? "BLOCKED"
                    : exchange.status() + " " + exchange.durationMs() + "ms";
            ui.textRight(ui.small(), status, margin + width - 14, rowY,
                    blocked ? Theme.BAD : (exchange.status() >= 400 ? Theme.WARN : Theme.GOOD));
            String detail = blocked ? "refused by policy"
                    : (exchange.responsePreview() == null ? "" : exchange.responsePreview());
            ui.text(ui.small(), ui.ellipsize(ui.small(), detail, width - 28),
                    margin + 14, rowY + 13, Theme.TEXT_DIM);
            rowY += 30;
        }
        y += netHeight + 12;

        // Mods -----------------------------------------------------------
        List<ModPackage> installed = mods.installed();
        int modHeight = 40 + installed.size() * 30;
        ui.panel(margin, y, width, modHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "MODS", margin + 14, y + 10, Theme.TEXT_DIM);
        rowY = y + 28;
        for (ModPackage mod : installed) {
            ui.text(ui.smallBold(), mod.name() + "  " + mod.version(), margin + 14, rowY, Theme.TEXT);
            ui.chip(mod.isEnabled() ? "ON" : "OFF", margin + width - 46, rowY - 1,
                    mod.isEnabled() ? Theme.GOOD : Theme.TEXT_DIM,
                    mod.isEnabled() ? 0xFF14361B : Theme.SURFACE_ALT);
            ui.text(ui.small(), mod.replacedResources().size() + " resource replaced  -  "
                            + (mod.touchesCode() ? "touches code" : "resources only"),
                    margin + 14, rowY + 13, Theme.TEXT_DIM);
            rowY += 30;
        }
        y += modHeight + 12;

        // Descriptor -----------------------------------------------------
        JadEditor editor = new JadEditor(library.load(entry.suiteId()).info().attributes());
        List<JadEditor.Problem> problems = editor.validate();
        int jadHeight = 46 + Math.max(1, problems.size()) * 16;
        ui.panel(margin, y, width, jadHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "JAD EDITOR", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.textRight(ui.small(), editor.isValid() ? "valid" : "has errors",
                margin + width - 14, y + 10, editor.isValid() ? Theme.GOOD : Theme.BAD);
        rowY = y + 28;
        if (problems.isEmpty()) {
            ui.text(ui.small(), editor.keys().size() + " attributes, no problems found",
                    margin + 14, rowY, Theme.TEXT_DIM);
        } else {
            for (JadEditor.Problem problem : problems) {
                ui.text(ui.small(), ui.ellipsize(ui.small(), problem.toString(), width - 28),
                        margin + 14, rowY, problem.isError() ? Theme.BAD : Theme.WARN);
                rowY += 16;
            }
        }
        y += jadHeight + 12;

        // RMS editor -----------------------------------------------------
        List<RmsEditor.Record> records = rms.records("skyrunner-scores");
        int rmsHeight = 46 + records.size() * 30;
        ui.panel(margin, y, width, rmsHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "RMS EDITOR", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.textRight(ui.small(), "skyrunner-scores", margin + width - 14, y + 10, Theme.ACCENT);
        rowY = y + 28;
        for (RmsEditor.Record record : records) {
            ui.text(ui.smallBold(), "#" + record.id() + "  " + record.asHex(),
                    margin + 14, rowY, Theme.TEXT);
            ui.textRight(ui.small(), record.size() + " B", margin + width - 14, rowY, Theme.TEXT_DIM);
            ui.text(ui.small(), "as int: " + record.asInt() + "    as text: \"" + record.asText() + "\"",
                    margin + 14, rowY + 13, Theme.TEXT_DIM);
            rowY += 30;
        }

        return frame;
    }
}
