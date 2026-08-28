package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.tools.ui.Icons;
import com.mobicore.core.storage.Json;
import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.mod.ModManager;
import com.mobicore.core.mod.ModPackage;
import com.mobicore.core.mod.ResourceCatalog;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.net.LoopbackSockets;
import com.mobicore.core.net.LoopbackTransport;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.net.SocketTransport;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.tools.JadEditor;
import com.mobicore.core.tools.RmsEditor;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview of the developer tools: network monitor, mods, descriptor validation
 * and the RMS editor. Every figure comes from a real session.
 */
public final class DevToolsScreen {

    /** Thẻ nào đang mở. */
    public static final int TAB_TREASURE = 0;
    public static final int TAB_RESOURCES = 1;
    public static final int TAB_NETWORK = 2;
    public static final int TAB_MODS = 3;
    public static final int TAB_DATA = 4;

    private final String fixtureDir;
    private final int tab;

    public DevToolsScreen(String fixtureDir) {
        this(fixtureDir, TAB_TREASURE);
    }

    public DevToolsScreen(String fixtureDir, int tab) {
        this.fixtureDir = fixtureDir;
        this.tab = tab;
    }

    /**
     * Hàng thẻ ở đầu trang.
     *
     * @return đáy của hàng thẻ
     */
    private int tabStrip(Ui ui, String[] labels, int chosen, int width) {
        Framebuffer frame = ui.frame();
        int top = Ui.APP_BAR + 1;
        int height = ui.medium().height() + 22;
        int each = width / labels.length;
        // Nhãn phải vừa ô của nó: năm thẻ trên một màn hình hẹp thì cỡ chữ
        // thường làm hai nhãn cạnh nhau dính vào nhau.
        com.mobicore.tools.ui.UiFont font = ui.medium();
        for (int i = 0; i < labels.length; i++) {
            if (font.stringWidth(labels[i]) + 10 > each) {
                font = ui.small();
                break;
            }
        }
        for (int i = 0; i < labels.length; i++) {
            int centre = Ui.PAD + each * i + each / 2;
            boolean active = i == chosen;
            String label = ui.ellipsize(font, labels[i], each - 6);
            ui.textCenter(font, label, centre, top + (height - font.height()) / 2,
                    active ? Theme.ACCENT : Theme.TEXT_DIM);
            if (active) {
                frame.setColor(Theme.ACCENT);
                int barWidth = font.stringWidth(label) + 12;
                frame.fillRect(centre - barWidth / 2, top + height - 3, barWidth, 3);
            }
        }
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, top + height, frame.width(), 1);
        return top + height;
    }

    /**
     * Tìm số vàng trong phần lưu — hai lần tìm, như người ta vẫn làm.
     *
     * <p>Con số trong ảnh là kết quả thật: một game mẫu giữ ví tiền trong RMS
     * được mở ra, tiêu bớt tiền, rồi tìm hai lần qua đúng cầu nối mà điện
     * thoại gọi.</p>
     */
    private void drawTreasure(Ui ui, int margin, int y, int width, int fieldX, int fieldWidth)
            throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        String suiteId = Json.string(Json.child(Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad())), "game"),
                "suiteId", "");
        facade.startGame(suiteId, "demo.PiggyBank");
        facade.session().vm().callVirtual(facade.session().context().midlet(),
                "newGame", "(I)V", Integer.valueOf(8630));
        facade.stopGame();
        Map<String, Object> first = Json.readObject(facade.scanSave(suiteId, 8630));

        facade.startGame(suiteId, "demo.PiggyBank");
        facade.session().vm().callVirtual(facade.session().context().midlet(),
                "spend", "(I)V", Integer.valueOf(130));
        facade.stopGame();
        Map<String, Object> second = Json.readObject(facade.narrowSave(suiteId, 8500));

        // Hai bước, và bước nào cũng nói ra nó vừa làm gì.
        int stepHeight = 12 + ui.small().height() + 8 + Ui.ROW * 2 + 6;
        int row = ui.section(margin, y, width, stepHeight, "TÌM SỐ VÀNG",
                Json.integer(second, "count", 0) + " chỗ còn lại");
        ui.field("Lần 1 — số đang thấy", "8630  ·  "
                + Json.integer(first, "count", 0) + " chỗ trùng", fieldX, row, fieldWidth);
        ui.field("Lần 2 — sau khi tiêu", "8500  ·  "
                + Json.integer(second, "count", 0) + " chỗ trùng", fieldX, row + Ui.ROW,
                fieldWidth);
        y += stepHeight + 14;

        List<Object> hits = Json.array(second, "hits");
        int rowHeight = ui.mediumBold().height() + ui.small().height() + 10;
        int height = 12 + ui.small().height() + 8 + Math.max(1, hits.size()) * rowHeight + 6;
        row = ui.section(margin, y, width, height, "CHỖ GIỮ SỐ VÀNG", "sửa hết");
        for (int i = 0; i < hits.size(); i++) {
            Map<String, Object> hit = (Map<String, Object>) hits.get(i);
            String where = Json.string(hit, "store", "") + "  ·  bản ghi "
                    + Json.integer(hit, "recordId", 0);
            ui.text(ui.mediumBold(), where, fieldX, row, Theme.TEXT);
            ui.textRight(ui.mediumBold(), String.valueOf(Json.longValue(hit, "value", 0L)),
                    fieldX + fieldWidth, row, Theme.ACCENT);
            ui.text(ui.small(), "byte thứ " + Json.integer(hit, "offset", 0) + "  ·  "
                            + Json.string(hit, "encodingName", ""),
                    fieldX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += rowHeight;
        }
        y += height + 16;

        int buttonWidth = (width - 12) / 2;
        ui.button(margin, y, buttonWidth, "Tìm lại", false);
        ui.button(margin + buttonWidth + 12, y, buttonWidth, "Đặt số mới", true, Icons.EDIT);
        y += 60;

        ui.text(ui.small(), "Phần lưu được sao lưu trước khi sửa.", fieldX, y, Theme.TEXT_DIM);
    }

    /**
     * Kho tài nguyên: mọi thứ trong tệp game, và thứ nào đã bị thay.
     *
     * <p>Loại của từng tệp đọc từ chính mấy byte đầu chứ không đoán theo tên:
     * game đời ấy để một tấm PNG trong {@code data/12.dat} là chuyện
     * thường.</p>
     */
    private void drawResources(Ui ui, GameLibrary library, LibraryEntry entry, ModManager mods,
                               int margin, int y, int width, int fieldX, int fieldWidth)
            throws Exception {
        List<ResourceCatalog.Entry> entries =
                ResourceCatalog.scan(library.load(entry.suiteId()), mods.installed());
        int rowHeight = ui.mediumBold().height() + ui.small().height() + 10;
        int height = 12 + ui.small().height() + 8 + entries.size() * rowHeight + 6;
        long total = 0;
        for (int i = 0; i < entries.size(); i++) {
            total += entries.get(i).bytes();
        }
        int row = ui.section(margin, y, width, height, "TRONG TỆP GAME",
                entries.size() + " tệp  ·  " + (total / 1024) + " KB");
        for (int i = 0; i < entries.size(); i++) {
            ResourceCatalog.Entry item = entries.get(i);
            String trailing = item.isReplaced() ? "ĐÃ THAY" : item.format();
            int trailingWidth = ui.small().stringWidth(trailing) + 14;
            ui.text(ui.mediumBold(),
                    ui.ellipsize(ui.mediumBold(), item.path(), fieldWidth - trailingWidth),
                    fieldX, row, item.isReplaced() ? Theme.ACCENT : Theme.TEXT);
            ui.textRight(ui.small(), trailing, fieldX + fieldWidth, row + 3,
                    item.isReplaced() ? Theme.ACCENT : Theme.TEXT_DIM);
            StringBuilder detail = new StringBuilder();
            detail.append(item.kindName()).append("  ·  ").append(item.bytes()).append(" B");
            if (item.width() > 0) {
                detail.append("  ·  ").append(item.width()).append('×').append(item.height());
            }
            if (item.isReplaced()) {
                detail.append("  ·  bản của ").append(item.replacedBy());
            }
            ui.text(ui.small(), ui.ellipsize(ui.small(), detail.toString(), fieldWidth),
                    fieldX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += rowHeight;
        }
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
        // Thay đúng một tệp game thật sự có, để bảng tài nguyên đánh dấu
        // được nó: một bản mod thay tệp không tồn tại thì chẳng thay gì.
        files.put("res/level1.dat", new byte[4096]);
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
                // Người chơi đã đồng ý cho game nói chuyện với máy chủ điểm và
                // với phòng chờ, và đã đồng ý cho nó mở cổng trên máy mình;
                // cái mạng quảng cáo thì không.
                return "scores.example.com".equals(host)
                        || "lobby.test".equals(host)
                        || NetworkPolicy.THIS_DEVICE.equals(host);
            }
        });
        mods.applyTo(session);
        session.start();

        call(session, "http://scores.example.com/submit", 8630);
        call(session, "http://scores.example.com/news", 0);
        call(session, "http://ads.tracker.example/news", 0);

        // Và một ván nhiều người chơi, để bảng theo dõi bày ra cả những thứ
        // không có hình dạng "hỏi một câu, nghe một câu": một đường dây giữ
        // mở, một cổng chờ người khác gọi vào, một gói bắn đi.
        multiplayer(session.network());

        RmsEditor rms = new RmsEditor(library.records(entry.suiteId()), 1_700_000_000_000L);
        rms.addRecord("skyrunner-scores", new byte[]{0, 0, 0x21, (byte) 0xB6});
        rms.addRecord("skyrunner-scores", new byte[]{0, 0, 0x10, 0x1A});

        Framebuffer frame = draw(library, entry, session, mods, rms);
        session.destroy();
        return frame;
    }

    /**
     * Một ván nhiều người chơi, chạy trong bộ nhớ.
     *
     * <p>Không có máy chủ thật nào ở đây: đường truyền vòng lại chính nó, nên
     * ảnh chụp vẫn dựng được ở bất cứ đâu — nhưng những dòng hiện ra trong
     * bảng theo dõi là do chính lớp mạng ghi lại, không phải viết sẵn.</p>
     */
    private void multiplayer(NetworkStack network) {
        try {
            network.setSocketTransport(new LoopbackSockets()
                    .serve(7000, LoopbackSockets.echo()));
            SocketTransport.Stream lobby = network.openSocket("lobby.test", 7000, 2000);
            lobby.output().write("PING".getBytes("UTF-8"));
            lobby.output().flush();
            byte[] answer = new byte[4];
            lobby.input().read(answer, 0, answer.length);
            lobby.close();

            SocketTransport.Server waiting = network.openServer(7100);
            waiting.close();

            SocketTransport.Datagrams packets = network.openDatagrams(7200);
            byte[] shot = "BAN 12,40".getBytes("UTF-8");
            packets.send("127.0.0.1", 7200, shot, 0, shot.length);
            packets.receive(new byte[64], 0, 64);
            packets.close();
        } catch (IOException e) {
            // Ảnh chụp vẫn dựng được: bảng theo dõi sẽ ghi đúng chỗ hỏng.
        }
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

        // Chia thẻ chứ không xếp thành một trang dài: mấy phần này trả lời
        // những câu hỏi khác nhau, và người đang tìm một tấm ảnh để thay
        // không việc gì phải cuộn qua bảng theo dõi mạng.
        String[] labels = {"Vật phẩm", "Tệp game", "Mạng", "Mod", "Dữ liệu"};
        int y = tabStrip(ui, labels, tab, width) + 14;

        if (tab == TAB_TREASURE) {
            drawTreasure(ui, margin, y, width, fieldX, fieldWidth);
            Framebuffer page = Preview.fit(frame, Ui.TAB_BAR);
            new Ui(page).tabBar(new String[]{"Trang chủ", "Công cụ", "Cài đặt"}, 1);
            return page;
        }
        if (tab == TAB_RESOURCES) {
            drawResources(ui, library, entry, mods, margin, y, width, fieldX, fieldWidth);
            Framebuffer only = Preview.fit(frame, Ui.TAB_BAR);
            new Ui(only).tabBar(new String[]{"Trang chủ", "Công cụ", "Cài đặt"}, 1);
            return only;
        }

        // Network monitor ------------------------------------------------
        List<NetworkMonitor.Exchange> exchanges = session.network().monitor().exchanges();
        int entryHeight = ui.mediumBold().height() + ui.small().height() + 8;
        int netHeight = 12 + ui.small().height() + 8 + exchanges.size() * entryHeight + 6;
        int[] totals = session.network().monitor().totals();
        int row = ui.section(margin, y, width, netHeight, "THEO DÕI MẠNG",
                "gửi " + totals[0] + " B  ·  nhận " + totals[1] + " B");
        for (NetworkMonitor.Exchange exchange : exchanges) {
            boolean blocked = "blocked".equals(exchange.outcome());
            String label = exchange.method() + "  " + exchange.hostLabel();
            // Một socket không có mã trạng thái như http: cái đáng nói về nó
            // là bao nhiêu byte đã đi qua.
            String status = blocked
                    ? "ĐÃ CHẶN"
                    : (exchange.status() > 0
                            ? exchange.status() + "  ·  " + exchange.durationMs() + "ms"
                            : exchange.requestBytes() + " B gửi  ·  "
                                    + exchange.responseBytes() + " B nhận");
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
        if (tab == TAB_NETWORK) {
            Framebuffer only = Preview.fit(ui.frame(), Ui.TAB_BAR);
            new Ui(only).tabBar(new String[]{"Trang chủ", "Công cụ", "Cài đặt"}, 1);
            return only;
        }

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
        if (tab == TAB_MODS) {
            Framebuffer only = Preview.fit(ui.frame(), Ui.TAB_BAR);
            new Ui(only).tabBar(new String[]{"Trang chủ", "Công cụ", "Cài đặt"}, 1);
            return only;
        }

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
