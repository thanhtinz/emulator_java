package com.mobicore.tools;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.ui.Icons;
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
        // Renamed here so the screenshot shows what a renamed game looks
        // like, including the line offering the manifest name back.
        facade.renameGame(suiteId, "Sky Runner (bản Việt)");
        facade.startGame(suiteId);
        for (int i = 0; i < 25; i++) {
            facade.renderFrame();
        }
        // Left the way a player leaves a game, so the screen below is the
        // real saved state rather than a mock-up of one.
        facade.stopGameSaving();
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
        Framebuffer frame = Preview.newPage();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar(Json.string(game, "title", ""), "Chi tiết");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 18;

        // Header ---------------------------------------------------------
        // Tall enough for the title, the vendor and two rows of chips.
        int cover = 112;
        byte[] artwork = facade.artwork(suiteId);
        if (artwork.length > 0 && PngReader.looksLikePng(artwork)) {
            PngReader.Image decoded = PngReader.decode(artwork);
            Framebuffer icon = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height)
                    .scaleSmooth(cover, cover);
            frame.drawFramebuffer(icon, margin, y);
        }
        // A tap target on the corner of the cover, because that is where a
        // user looks to change a picture — not in a list of settings.
        int badge = 30;
        int badgeX = margin + cover - badge + 4;
        int badgeY = y + cover - badge + 4;
        frame.setColor(Theme.ACCENT);
        frame.fillArc(badgeX, badgeY, badge, badge, 0, 360);
        Icons.drawCentred(frame, Icons.PHOTO, badgeX + badge / 2, badgeY + badge / 2, 16, Theme.BG);

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
        // On its own line above the version chips: it is the one thing here
        // that decides whether pressing Chơi will do anything at all.
        compatibilityChip(ui, textLeft, chipY - ui.chipHeight() - 8,
                Json.integer(settings, "compatibility", 0));
        if (Json.bool(settings, "favourite", false)) {
            ui.iconChip(Icons.STAR, "YÊU THÍCH", chipX, chipY, Theme.WARN, Theme.WARN_BG);
        }
        y += cover + 18;

        // Actions --------------------------------------------------------
        int buttonWidth = (width - 12) / 2;
        int buttonHeight = ui.button(margin, y, buttonWidth, "Chơi", true, Icons.PLAY);
        ui.button(margin + buttonWidth + 12, y, buttonWidth, "Cài đặt", false, Icons.TUNE);
        y += buttonHeight + 16;

        // Where the player left off ---------------------------------------
        byte[] saved = facade.saveStateThumbnail(suiteId);
        if (saved.length > 0 && PngReader.looksLikePng(saved)) {
            y += resumeCard(ui, facade, saved, margin, y, width, buttonHeight) + 16;
        }

        // Name and cover -------------------------------------------------
        boolean renamed = Json.bool(game, "renamed", false);
        String originalTitle = Json.string(game, "originalTitle", "");
        int nameRows = renamed ? 2 : 1;
        int nameHeight = ui.sectionHeight(nameRows) + buttonHeight + 4;
        int nameRow = ui.section(margin, y, width, nameHeight, "TÊN VÀ ẢNH BÌA", null);
        ui.field("Tên hiển thị", Json.string(game, "title", ""), fieldX, nameRow, fieldWidth);
        if (renamed) {
            ui.field("Tên gốc", originalTitle, fieldX, nameRow + Ui.ROW, fieldWidth);
        }
        int actionY = nameRow + Ui.ROW * nameRows + 2;
        int half = (fieldWidth - 12) / 2;
        ui.button(fieldX, actionY, half, renamed ? "Đổi tên" : "Đặt tên", false, Icons.EDIT);
        ui.button(fieldX + half + 12, actionY, half, "Chọn ảnh", false, Icons.PHOTO);
        y += nameHeight + 16;

        // Details --------------------------------------------------------
        Map<String, Object> device = Json.child(settings, "device");
        int detailsHeight = ui.sectionHeight(6);
        int row = ui.section(margin, y, width, detailsHeight, "THÔNG TIN", null);
        ui.field("Phiên bản", Json.string(game, "version", ""), fieldX, row, fieldWidth);
        ui.field("Mã bộ cài", Json.string(game, "suiteId", ""), fieldX, row + Ui.ROW, fieldWidth);
        ui.field("Dung lượng", kb(Json.longValue(game, "jarSize", 0)), fieldX, row + Ui.ROW * 2,
                fieldWidth);
        ui.field("Màn hình", Json.string(device, "name", ""), fieldX, row + Ui.ROW * 3, fieldWidth);
        ui.field("Đã chơi", Json.string(settings, "playedName", "chưa chơi"), fieldX,
                row + Ui.ROW * 5, fieldWidth);
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

        // What else is in the JAR ----------------------------------------
        // Only drawn when the suite really holds more than one MIDlet: a
        // picker over a list of one is a question with a single answer.
        java.util.List<Object> midlets = Json.array(
                Json.readObject(facade.midletsJson(suiteId)), "midlets");
        if (midlets.size() > 1) {
            int midletHeight = ui.sectionHeight(midlets.size());
            row = ui.section(margin, y, width, midletHeight, "TRONG GÓI NÀY",
                    midlets.size() + " ứng dụng");
            for (int i = 0; i < midlets.size(); i++) {
                Map<String, Object> midlet = (Map<String, Object>) midlets.get(i);
                boolean chosen = Json.bool(midlet, "chosen", false);
                ui.text(chosen ? ui.mediumBold() : ui.medium(),
                        Json.string(midlet, "name", ""), fieldX, row,
                        chosen ? Theme.ACCENT : Theme.TEXT);
                row += Ui.ROW;
            }
            y += midletHeight + 14;
        }

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
        int trash = ui.medium().height() + 2;
        Icons.draw(ui.frame(), Icons.DELETE, fieldX, y + 12, trash, Theme.BAD);
        ui.text(ui.medium(), "Gỡ trò chơi", fieldX + trash + 6, y + 12, Theme.BAD);
        ui.text(ui.small(), "Dữ liệu lưu luôn được sao lưu trước khi xoá bất cứ thứ gì.",
                fieldX, y + 12 + ui.medium().height() + 4, Theme.TEXT_DIM);

        return Preview.fit(frame);
    }

    /**
     * The screen the player was looking at when they left, and a button back
     * into it.
     *
     * <p>A picture rather than a date: someone coming back to four games
     * recognises where they were from the screen long before they work it out
     * from a timestamp.</p>
     */
    private int resumeCard(Ui ui, MobiCoreFacade facade, byte[] thumbnail, int x, int y,
                           int width, int buttonHeight) throws Exception {
        Framebuffer frame = ui.frame();
        PngReader.Image decoded = PngReader.decode(thumbnail);
        int shotHeight = 96;
        int shotWidth = decoded.width * shotHeight / Math.max(1, decoded.height);
        int height = shotHeight + 24 + buttonHeight + 12;

        ui.panel(x, y, width, height, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "ĐANG CHƠI DỞ", x + Ui.PAD, y + 12, Theme.TEXT_DIM);

        int shotY = y + 12 + ui.small().height() + 8;
        Framebuffer shot = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height)
                .scaleSmooth(shotWidth, shotHeight);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(shot, x + Ui.PAD, shotY);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);
        frame.setColor(Theme.BORDER);
        frame.drawRect(x + Ui.PAD, shotY, shotWidth - 1, shotHeight - 1);

        int textLeft = x + Ui.PAD + shotWidth + 14;
        ui.text(ui.medium(), "Chơi tiếp từ chỗ đã dừng", textLeft, shotY + 2, Theme.TEXT);
        ui.text(ui.small(), "Tự lưu khi bạn thoát game", textLeft,
                shotY + ui.medium().height() + 6, Theme.TEXT_DIM);
        ui.button(textLeft, shotY + shotHeight - buttonHeight, width - (textLeft - x) - Ui.PAD,
                "Chơi tiếp", true, Icons.PLAY);
        return height;
    }

    /**
     * Says whether the game will run at all, before the user tries it.
     *
     * <p>A J2ME game that needs a package the emulator lacks does not run
     * badly — it fails to start, on a black screen, with nothing to explain
     * it. The scan at import knows; this is where it gets said.</p>
     */
    private int compatibilityChip(Ui ui, int x, int y, int level) {
        if (level == 1) {
            return ui.iconChip(Icons.CHECK, "THIẾU VÀI THỨ", x, y, Theme.WARN, Theme.WARN_BG);
        }
        if (level >= 2) {
            return ui.iconChip(Icons.CLOSE, "CHƯA CHẠY ĐƯỢC", x, y, Theme.BAD, Theme.BAD_BG);
        }
        return ui.iconChip(Icons.CHECK, "CHẠY TỐT", x, y, Theme.GOOD, Theme.GOOD_BG);
    }

    private static String kb(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + "," + ((bytes % 1024) * 10 / 1024) + " KB";
    }
}
