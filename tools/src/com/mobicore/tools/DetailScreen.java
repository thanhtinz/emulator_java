package com.mobicore.tools;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;
import java.util.Map;

/**
 * Preview of the Game Detail screen, rendered from the JSON the mobile shells
 * actually receive: every value here crossed the bridge facade.
 */
public final class DetailScreen {

    private final String fixtureDir;

    public DetailScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        facade.toggleFavourite(suiteId);
        facade.startGame(suiteId);
        facade.renderFrame();
        facade.stopGame();
        facade.backup(suiteId);

        Map<String, Object> library = Json.readObject(facade.libraryJson());
        @SuppressWarnings("unchecked")
        Map<String, Object> game = (Map<String, Object>) Json.array(library, "games").get(0);
        Map<String, Object> settings = Json.child(game, "settings");
        Map<String, Object> saves = Json.readObject(facade.savesJson(suiteId));
        Map<String, Object> inspect = Json.readObject(facade.inspectJson(suiteId));

        return draw(facade, game, settings, saves, inspect, suiteId);
    }

    private Framebuffer draw(MobiCoreFacade facade, Map<String, Object> game,
                             Map<String, Object> settings, Map<String, Object> saves,
                             Map<String, Object> inspect, String suiteId) throws Exception {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar(Json.string(game, "title", ""), "Chi tiết");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 18;

        // Header ---------------------------------------------------------
        int cover = 92;
        byte[] artwork = facade.artwork(suiteId);
        if (artwork.length > 0 && PngReader.looksLikePng(artwork)) {
            PngReader.Image decoded = PngReader.decode(artwork);
            Framebuffer icon = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height)
                    .scaleSmooth(cover, cover);
            frame.drawFramebuffer(icon, margin, y);
        }
        int textLeft = margin + cover + 16;
        ui.text(ui.largeBold(), Json.string(game, "title", ""), textLeft, y + 2, Theme.TEXT);
        ui.text(ui.small(), Json.string(game, "vendor", ""), textLeft,
                y + 2 + ui.largeBold().height() + 4, Theme.TEXT_DIM);
        int chipY = y + cover - ui.chipHeight() - 2;
        int chipX = textLeft;
        chipX += ui.chip(Json.string(game, "configuration", ""), chipX, chipY,
                Theme.ACCENT, Theme.ACCENT_DIM) + 8;
        chipX += ui.chip(Json.string(game, "profile", ""), chipX, chipY,
                Theme.ACCENT, Theme.ACCENT_DIM) + 8;
        if (Json.bool(settings, "favourite", false)) {
            ui.chip("★ YÊU THÍCH", chipX, chipY, Theme.WARN, 0xFF3A2E10);
        }
        y += cover + 18;

        // Actions --------------------------------------------------------
        int buttonWidth = (width - 12) / 2;
        int buttonHeight = ui.button(margin, y, buttonWidth, "Chơi", true);
        ui.button(margin + buttonWidth + 12, y, buttonWidth, "Cài đặt", false);
        y += buttonHeight + 16;

        // Details --------------------------------------------------------
        Map<String, Object> device = Json.child(settings, "device");
        int detailsHeight = ui.sectionHeight(5);
        int row = ui.section(margin, y, width, detailsHeight, "THÔNG TIN", null);
        ui.field("Phiên bản", Json.string(game, "version", ""), fieldX, row, fieldWidth);
        ui.field("Mã bộ cài", Json.string(game, "suiteId", ""), fieldX, row + Ui.ROW, fieldWidth);
        ui.field("Dung lượng", kb(Json.longValue(game, "jarSize", 0)), fieldX, row + Ui.ROW * 2,
                fieldWidth);
        ui.field("Máy giả lập", Json.string(device, "name", ""), fieldX, row + Ui.ROW * 3, fieldWidth);
        ui.field("Số lần chơi", String.valueOf(Json.integer(settings, "playCount", 0)), fieldX,
                row + Ui.ROW * 4, fieldWidth);
        y += detailsHeight + 14;

        // Saves ----------------------------------------------------------
        List<Object> stores = Json.array(saves, "stores");
        List<Object> backups = Json.array(saves, "backups");
        int savesHeight = ui.sectionHeight(Math.max(1, stores.size()));
        row = ui.section(margin, y, width, savesHeight, "DỮ LIỆU LƯU",
                backups.size() + " bản sao lưu");
        if (stores.isEmpty()) {
            ui.text(ui.medium(), "Trò chơi này chưa lưu gì.", fieldX, row, Theme.TEXT_DIM);
        } else {
            for (Object item : stores) {
                @SuppressWarnings("unchecked")
                Map<String, Object> store = (Map<String, Object>) item;
                ui.field(Json.string(store, "name", ""),
                        Json.integer(store, "records", 0) + " bản ghi", fieldX, row, fieldWidth);
                row += Ui.ROW;
            }
        }
        y += savesHeight + 14;

        // Contents -------------------------------------------------------
        int contentsHeight = ui.sectionHeight(3);
        row = ui.section(margin, y, width, contentsHeight, "NỘI DUNG BỘ CÀI", null);
        ui.field("MIDlet", String.valueOf(Json.array(inspect, "midlets").size()), fieldX, row,
                fieldWidth);
        ui.field("Lớp Java", String.valueOf(Json.array(inspect, "classes").size()), fieldX,
                row + Ui.ROW, fieldWidth);
        ui.field("Tài nguyên", Json.array(inspect, "resources").size() + "  ("
                + kb(Json.longValue(inspect, "uncompressed", 0)) + ")", fieldX, row + Ui.ROW * 2,
                fieldWidth);
        y += contentsHeight + 14;

        // Danger zone ----------------------------------------------------
        int dangerHeight = 12 + ui.medium().height() + ui.small().height() + 26;
        ui.panel(margin, y, width, dangerHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.medium(), "Gỡ trò chơi", fieldX, y + 12, Theme.BAD);
        ui.text(ui.small(), "Dữ liệu lưu luôn được sao lưu trước khi xoá bất cứ thứ gì.",
                fieldX, y + 12 + ui.medium().height() + 4, Theme.TEXT_DIM);

        return frame;
    }

    private static String kb(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + "," + ((bytes % 1024) * 10 / 1024) + " KB";
    }
}
