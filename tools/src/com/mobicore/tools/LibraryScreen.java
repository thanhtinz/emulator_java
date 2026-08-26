package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview of the Home screen the mobile shells present: recently played,
 * favourites and the full library, all read from a real {@link GameLibrary}.
 */
public final class LibraryScreen {

    private final String fixtureDir;

    public LibraryScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();

        library.install(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        installExtra(library, "Tile Rush", "MobiCore Samples", "1.0.4", "puzzle.TileRush");
        installExtra(library, "Night Racer", "Blue Fox Games", "2.1", "racer.NightRacer");
        installExtra(library, "Dungeon Bell", "Iron Lantern", "0.9", "rpg.DungeonBell");

        Map<String, GameProfile> profiles = library.allProfiles();
        long now = 1_700_000_000_000L;
        markPlayed(library, profiles, "mobicore-samples.sky-runner.1-2-0", now, true);
        markPlayed(library, profiles, "blue-fox-games.night-racer.2-1", now - 3_600_000L, false);
        markPlayed(library, profiles, "iron-lantern.dungeon-bell.0-9", now - 86_400_000L, true);
        profiles = library.allProfiles();

        return draw(library, profiles);
    }

    private void markPlayed(GameLibrary library, Map<String, GameProfile> profiles, String suiteId,
                            long when, boolean favourite) throws Exception {
        GameProfile profile = profiles.get(suiteId);
        if (profile == null) {
            return;
        }
        profile.markPlayed(when);
        profile.setFavourite(favourite);
        library.saveProfile(profile);
    }

    /** The two extra sample games, for any screen that wants a real library. */
    static void installExtras(GameLibrary library) throws Exception {
        new LibraryScreen(null).installExtra(library, "Night Racer", "Blue Fox Games", "2.1",
                "racer.NightRacer");
        new LibraryScreen(null).installExtra(library, "Dungeon Bell", "Iron Lantern", "0.9",
                "rpg.DungeonBell");
    }

    /** The cover for one game, drawn the same way on every screen. */
    static void drawCover(Ui ui, GameLibrary library, LibraryEntry entry, int x, int y, int size)
            throws Exception {
        new LibraryScreen(null).drawArtwork(ui, library, entry, x, y, size);
    }

    private void installExtra(GameLibrary library, String title, String vendor, String version,
                              String midletClass) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        String manifest = "Manifest-Version: 1.0\n"
                + "MIDlet-Name: " + title + "\n"
                + "MIDlet-Version: " + version + "\n"
                + "MIDlet-Vendor: " + vendor + "\n"
                + "MIDlet-1: " + title + ",/icon.png," + midletClass + "\n"
                + "MicroEdition-Configuration: CLDC-1.1\n"
                + "MicroEdition-Profile: MIDP-2.0\n";
        entries.put("META-INF/MANIFEST.MF", SampleSuite.utf8(manifest));
        entries.put("icon.png", coverFor(title));
        entries.put(midletClass.replace('.', '/') + ".class",
                new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        library.install(SampleSuite.zip(entries), null);
    }

    /**
     * Distinct placeholder cover derived from the title, so tiles differ.
     *
     * <p>The mark on it is the Material controller icon, the same one the
     * library tab and the empty states use — a cover with no artwork should
     * look like the rest of the product, not like something drawn for the
     * occasion.</p>
     *
     * <p>Painted at 192 square: a tile is about ninety pixels across, so a
     * cover the size of a real {@code icon.png} would have to be blown up,
     * and every edge in it would come out as a staircase.</p>
     */
    private byte[] coverFor(String title) throws Exception {
        int size = 192;
        Framebuffer icon = new Framebuffer(size, size);
        icon.setAntialias(true);
        int seed = Math.abs(title.hashCode());
        int base = 0xFF000000 | ((40 + seed % 90) << 16) | ((60 + (seed / 7) % 120) << 8)
                | (90 + (seed / 13) % 140);
        icon.fill(base);
        Icons.drawCentred(icon, Icons.LIBRARY, size / 2, size / 2, 104, 0xCCFFFFFF);
        return com.mobicore.core.gfx.PngWriter.encode(icon);
    }

    private Framebuffer draw(GameLibrary library, Map<String, GameProfile> profiles) throws Exception {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        int margin = 16;
        int width = frame.width() - margin * 2;

        ui.text(ui.title(), "MobiCore", margin, 14, Theme.TEXT);
        // One tap, always in the same corner: the light and dark switch is
        // the one setting people change often enough to want it on the way.
        int toggle = 40;
        int toggleX = frame.width() - margin - toggle;
        int toggleY = 14 + (ui.title().height() - toggle) / 2;
        ui.panel(toggleX, toggleY, toggle, toggle, Theme.SURFACE, Theme.BORDER);
        Icons.drawCentred(frame, Theme.isDark() ? Icons.LIGHT_MODE : Icons.DARK_MODE,
                toggleX + toggle / 2, toggleY + toggle / 2, 22, Theme.ACCENT);

        int y = 14 + ui.title().height() + 14;
        // The search box lives here rather than in a tab of its own: a tab
        // holding a search field over the games this screen already lists
        // makes people look in two places for one thing.
        y += ui.searchField(margin, y, width, "", "Tìm theo tên hoặc nhà phát hành") + 18;

        ui.text(ui.small(), "VỪA CHƠI", margin, y, Theme.TEXT_DIM);
        y += ui.small().height() + 10;
        List<LibraryEntry> recent = library.sort(library.all(), GameLibrary.SORT_RECENT, profiles);
        int tileX = margin;
        for (LibraryEntry entry : recent) {
            GameProfile profile = profiles.get(entry.suiteId());
            if (profile == null || profile.lastPlayed() == 0) {
                continue;
            }
            drawTile(ui, library, entry, tileX, y);
            tileX += TILE + 14;
            if (tileX + TILE > margin + width) {
                break;
            }
        }
        y += TILE + ui.small().height() + 22;

        ui.text(ui.small(), "TẤT CẢ TRÒ CHƠI", margin, y, Theme.TEXT_DIM);
        ui.textRight(ui.small(), "đã cài " + library.size(), frame.width() - margin, y, Theme.TEXT_DIM);
        y += ui.small().height() + 10;

        for (LibraryEntry entry : library.sort(library.all(), GameLibrary.SORT_TITLE, profiles)) {
            drawRow(ui, library, entry, profiles.get(entry.suiteId()), margin, y, width);
            y += ROW_HEIGHT + 12;
        }

        List<LibraryEntry> favourites = library.favourites(profiles);
        if (!favourites.isEmpty()) {
            y += 6;
            ui.text(ui.small(), "YÊU THÍCH", margin, y, Theme.TEXT_DIM);
            y += ui.small().height() + 10;
            for (LibraryEntry entry : favourites) {
                drawRow(ui, library, entry, profiles.get(entry.suiteId()), margin, y, width);
                y += ROW_HEIGHT + 12;
            }
        }

        ui.tabBar(new String[]{"Trang chủ", "Công cụ", "Cài đặt"}, 0);
        // Importing is what a new install has to do first and what most later
        // visits come back for, so it gets the one floating button on the
        // screen — small, always in the same corner, never in the way of the
        // list.
        ui.fab(Icons.ADD);
        return frame;
    }

    /** Cover size in the "vừa chơi" row, and the height of a list row. */
    private static final int TILE = 108;
    private static final int ROW_HEIGHT = 84;

    private void drawTile(Ui ui, GameLibrary library, LibraryEntry entry, int x, int y)
            throws Exception {
        drawArtwork(ui, library, entry, x, y, TILE);
        ui.textCenter(ui.small(), ui.ellipsize(ui.small(), entry.title(), TILE + 8),
                x + TILE / 2, y + TILE + 8, Theme.TEXT);
    }

    private void drawRow(Ui ui, GameLibrary library, LibraryEntry entry, GameProfile profile,
                         int x, int y, int width) throws Exception {
        ui.panel(x, y, width, ROW_HEIGHT, Theme.SURFACE, Theme.BORDER);
        int cover = ROW_HEIGHT - 24;
        drawArtwork(ui, library, entry, x + 12, y + 12, cover);
        int textLeft = x + 12 + cover + 14;
        String chip = profile != null ? profile.device().resolution() : entry.profile();
        int chipWidth = ui.small().stringWidth(chip) + 18;
        ui.text(ui.mediumBold(),
                ui.ellipsize(ui.mediumBold(), entry.title(), width - cover - chipWidth - 60),
                textLeft, y + 14, Theme.TEXT);
        ui.text(ui.small(), entry.vendor() + "  ·  " + entry.version(), textLeft,
                y + 14 + ui.mediumBold().height() + 4, Theme.TEXT_DIM);
        ui.chip(chip, x + width - chipWidth - 14, y + 14, Theme.ACCENT, Theme.ACCENT_DIM);
        if (profile != null && profile.isFavourite()) {
            int favouriteWidth = ui.iconChipWidth("yêu thích");
            ui.iconChip(Icons.STAR, "yêu thích", x + width - favouriteWidth - 14,
                    y + 14 + ui.chipHeight() + 8, Theme.WARN, Theme.WARN_BG);
        }
    }

    private void drawArtwork(Ui ui, GameLibrary library, LibraryEntry entry, int x, int y, int size)
            throws Exception {
        Framebuffer frame = ui.frame();
        byte[] artwork = library.artwork(entry.suiteId());
        frame.setColor(Theme.SURFACE_ALT);
        frame.fillRoundRect(x, y, size, size, 18, 18);
        if (artwork != null && PngReader.looksLikePng(artwork)) {
            PngReader.Image decoded = PngReader.decode(artwork);
            Framebuffer icon = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height)
                    .scaleSmooth(size - 10, size - 10);
            frame.drawFramebuffer(icon, x + 5, y + 5);
        } else {
            ui.textCenter(ui.title(), entry.title().substring(0, 1).toUpperCase(),
                    x + size / 2, y + (size - ui.title().height()) / 2, Theme.ACCENT);
        }
    }
}
