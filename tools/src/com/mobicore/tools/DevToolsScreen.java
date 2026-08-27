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
        Framebuffer frame = Preview.newPage();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Công cụ", entry.title());

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 18;

        // Network monitor ------------------------------------------------
        List<NetworkMonitor.Exchange> exchanges = session.network().monitor().exchanges();
        int entryHeight = ui.mediumBold().height() + ui.small().height() + 8;
        int netHeight = 12 + ui.small().height() + 8 + exchanges.size() * entryHeight + 6;
        int[] totals = session.network().monitor().totals();
        int row = ui.section(margin, y, width, netHeight, "THEO DÕI MẠNG",
                "gửi " + totals[0] + " B  ·  nhận " + totals[1] + " B");
        for (NetworkMonitor.Exchange exchange : exchanges) {
            boolean blocked = "blocked".equals(exchange.outcome());
            String label = exchange.method() + "  " + exchange.host();
            String status = blocked ? "ĐÃ CHẶN" : exchange.status() + "  ·  " + exchange.durationMs() + "ms";
            int statusWidth = ui.small().stringWidth(status) + 12;
            ui.text(ui.mediumBold(), ui.ellipsize(ui.mediumBold(), label, fieldWidth - statusWidth),
                    fieldX, row, blocked ? Theme.BAD : Theme.TEXT);
            ui.textRight(ui.small(), status, fieldX + fieldWidth, row + 3,
                    blocked ? Theme.BAD : (exchange.status() >= 400 ? Theme.WARN : Theme.GOOD));
            String detail = blocked ? "bị chính sách từ chối"
                    : (exchange.responsePreview() == null ? "" : exchange.responsePreview());
            ui.text(ui.small(), ui.ellipsize(ui.small(), detail, fieldWidth),
                    fieldX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += entryHeight;
        }
        y += netHeight + 14;

        // Mods -----------------------------------------------------------
        List<ModPackage> installed = mods.installed();
        int modHeight = 12 + ui.small().height() + 8 + installed.size() * entryHeight + 6;
        row = ui.section(margin, y, width, modHeight, "BẢN MOD", null);
        for (ModPackage mod : installed) {
            ui.text(ui.mediumBold(), mod.name() + "  " + mod.version(), fieldX, row, Theme.TEXT);
            String state = mod.isEnabled() ? "BẬT" : "TẮT";
            int chipWidth = ui.small().stringWidth(state) + 18;
            ui.chip(state, fieldX + fieldWidth - chipWidth, row + 2,
                    mod.isEnabled() ? Theme.GOOD : Theme.TEXT_DIM,
                    mod.isEnabled() ? Theme.GOOD_BG : Theme.SURFACE_ALT);
            ui.text(ui.small(), "thay " + mod.replacedResources().size() + " tài nguyên  ·  "
                            + (mod.touchesCode() ? "có chạm mã nguồn" : "chỉ tài nguyên"),
                    fieldX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += entryHeight;
        }
        y += modHeight + 14;

        // Descriptor -----------------------------------------------------
        JadEditor editor = new JadEditor(library.load(entry.suiteId()).info().attributes());
        List<JadEditor.Problem> problems = editor.validate();
        int jadRows = Math.max(1, problems.size());
        int jadHeight = 12 + ui.small().height() + 8 + jadRows * (ui.medium().height() + 6) + 6;
        row = ui.section(margin, y, width, jadHeight, "TRÌNH SỬA JAD",
                editor.isValid() ? "hợp lệ" : "có lỗi");
        if (problems.isEmpty()) {
            ui.text(ui.medium(), editor.keys().size() + " thuộc tính, không có lỗi nào",
                    fieldX, row, Theme.TEXT_DIM);
        } else {
            for (JadEditor.Problem problem : problems) {
                ui.text(ui.medium(), ui.ellipsize(ui.medium(), problem.toString(), fieldWidth),
                        fieldX, row, problem.isError() ? Theme.BAD : Theme.WARN);
                row += ui.medium().height() + 6;
            }
        }
        y += jadHeight + 14;

        // RMS editor -----------------------------------------------------
        List<RmsEditor.Record> records = rms.records("skyrunner-scores");
        int rmsHeight = 12 + ui.small().height() + 8 + records.size() * entryHeight + 6;
        row = ui.section(margin, y, width, rmsHeight, "TRÌNH SỬA RMS", "skyrunner-scores");
        for (RmsEditor.Record record : records) {
            ui.text(ui.mediumBold(), "#" + record.id() + "   " + record.asHex(), fieldX, row,
                    Theme.TEXT);
            ui.textRight(ui.small(), record.size() + " B", fieldX + fieldWidth, row + 3,
                    Theme.TEXT_DIM);
            ui.text(ui.small(), "số nguyên: " + record.asInt() + "     văn bản: \""
                            + record.asText() + "\"",
                    fieldX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += entryHeight;
        }

        // Thanh thẻ nằm đáy trang, nên nó được vẽ sau khi trang đã cắt về
        // đúng chiều cao thật — vẽ trước thì nó dính vào đáy tấm thừa.
        Framebuffer page = Preview.fit(frame, Ui.TAB_BAR);
        new Ui(page).tabBar(new String[]{"Trang chủ", "Công cụ", "Cài đặt"}, 1);
        return page;
    }
}
