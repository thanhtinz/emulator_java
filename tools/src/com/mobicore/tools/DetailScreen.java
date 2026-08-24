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
        ui.appBar(Json.string(game, "title", ""), "Game detail");

        int margin = 16;
        int width = frame.width() - margin * 2;
        int y = 62;

        // Header ---------------------------------------------------------
        byte[] artwork = facade.artwork(suiteId);
        if (artwork.length > 0 && PngReader.looksLikePng(artwork)) {
            PngReader.Image decoded = PngReader.decode(artwork);
            Framebuffer icon = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height)
                    .scaleNearest(72, 72);
            frame.drawFramebuffer(icon, margin, y);
        }
        int textLeft = margin + 86;
        ui.text(ui.largeBold(), Json.string(game, "title", ""), textLeft, y + 2, Theme.TEXT);
        ui.text(ui.small(), Json.string(game, "vendor", ""), textLeft, y + 26, Theme.TEXT_DIM);
        int chipX = textLeft;
        chipX += ui.chip(Json.string(game, "configuration", ""), chipX, y + 44,
                Theme.ACCENT, Theme.ACCENT_DIM) + 6;
        chipX += ui.chip(Json.string(game, "profile", ""), chipX, y + 44,
                Theme.ACCENT, Theme.ACCENT_DIM) + 6;
        if (Json.bool(settings, "favourite", false)) {
            ui.chip("FAVOURITE", chipX, y + 44, Theme.WARN, 0xFF3A2E10);
        }
        y += 88;

        // Actions --------------------------------------------------------
        int buttonWidth = (width - 10) / 2;
        ui.panel(margin, y, buttonWidth, 42, Theme.ACCENT_DIM, Theme.ACCENT);
        ui.textCenter(ui.mediumBold(), "Play", margin + buttonWidth / 2, y + 12, Theme.ACCENT);
        ui.panel(margin + buttonWidth + 10, y, buttonWidth, 42, Theme.SURFACE_ALT, Theme.BORDER);
        ui.textCenter(ui.mediumBold(), "Settings", margin + buttonWidth + 10 + buttonWidth / 2,
                y + 12, Theme.TEXT);
        y += 54;

        // Details --------------------------------------------------------
        Map<String, Object> device = Json.child(settings, "device");
        ui.panel(margin, y, width, 136, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "DETAILS", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.field("Version", Json.string(game, "version", ""), margin + 14, y + 30, width - 28);
        ui.field("Suite id", Json.string(game, "suiteId", ""), margin + 14, y + 50, width - 28);
        ui.field("Size", kb(Json.longValue(game, "jarSize", 0)), margin + 14, y + 70, width - 28);
        ui.field("Device", Json.string(device, "name", ""), margin + 14, y + 90, width - 28);
        ui.field("Times played", String.valueOf(Json.integer(settings, "playCount", 0)),
                margin + 14, y + 110, width - 28);
        y += 148;

        // Saves ----------------------------------------------------------
        List<Object> stores = Json.array(saves, "stores");
        List<Object> backups = Json.array(saves, "backups");
        int savesHeight = 52 + Math.max(1, stores.size()) * 20;
        ui.panel(margin, y, width, savesHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "SAVES", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.textRight(ui.small(), backups.size() + " backup", margin + width - 14, y + 10, Theme.ACCENT);
        int rowY = y + 30;
        if (stores.isEmpty()) {
            ui.text(ui.small(), "This game has not saved anything yet.", margin + 14, rowY,
                    Theme.TEXT_DIM);
        } else {
            for (Object item : stores) {
                @SuppressWarnings("unchecked")
                Map<String, Object> store = (Map<String, Object>) item;
                ui.field(Json.string(store, "name", ""),
                        Json.integer(store, "records", 0) + " records",
                        margin + 14, rowY, width - 28);
                rowY += 20;
            }
        }
        y += savesHeight + 12;

        // Contents -------------------------------------------------------
        ui.panel(margin, y, width, 96, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "CONTENTS", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.field("MIDlets", String.valueOf(Json.array(inspect, "midlets").size()),
                margin + 14, y + 30, width - 28);
        ui.field("Classes", String.valueOf(Json.array(inspect, "classes").size()),
                margin + 14, y + 50, width - 28);
        ui.field("Resources", Json.array(inspect, "resources").size() + "  ("
                + kb(Json.longValue(inspect, "uncompressed", 0)) + ")",
                margin + 14, y + 70, width - 28);
        y += 108;

        ui.panel(margin, y, width, 46, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "Uninstall game", margin + 14, y + 8, Theme.BAD);
        ui.text(ui.small(), "Saves are backed up before anything is removed.",
                margin + 14, y + 26, Theme.TEXT_DIM);

        return frame;
    }

    private static String kb(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + "." + ((bytes % 1024) * 10 / 1024) + " KB";
    }
}
