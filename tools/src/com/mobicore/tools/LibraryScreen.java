package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
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

    /** Distinct placeholder cover derived from the title, so tiles differ. */
    private byte[] coverFor(String title) throws Exception {
        int size = 48;
        Framebuffer icon = new Framebuffer(size, size);
        int seed = Math.abs(title.hashCode());
        int base = 0xFF000000 | ((40 + seed % 90) << 16) | ((60 + (seed / 7) % 120) << 8)
                | (90 + (seed / 13) % 140);
        icon.fill(base);
        icon.setColor(0x40FFFFFF);
        for (int i = 0; i < size; i += 6) {
            icon.drawLine(0, i, i, 0);
        }
        icon.setColor(0xFFFFFFFF);
        icon.fillRoundRect(10, 14, 28, 20, 8, 8);
        icon.setColor(base);
        icon.fillArc(18, 19, 10, 10, 0, 360);
        return com.mobicore.core.gfx.PngWriter.encode(icon);
    }

    private Framebuffer draw(GameLibrary library, Map<String, GameProfile> profiles) throws Exception {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        int margin = 16;
        int width = frame.width() - margin * 2;

        ui.text(ui.largeBold(), "MobiCore", margin, 16, Theme.TEXT);
        ui.textRight(ui.small(), "Import", frame.width() - margin, 24, Theme.ACCENT);

        int y = 54;
        ui.text(ui.small(), "RECENTLY PLAYED", margin, y, Theme.TEXT_DIM);
        y += 18;
        List<LibraryEntry> recent = library.sort(library.all(), GameLibrary.SORT_RECENT, profiles);
        int tileX = margin;
        for (LibraryEntry entry : recent) {
            GameProfile profile = profiles.get(entry.suiteId());
            if (profile == null || profile.lastPlayed() == 0) {
                continue;
            }
            drawTile(ui, library, entry, tileX, y);
            tileX += 92;
            if (tileX + 84 > margin + width) {
                break;
            }
        }
        y += 116;

        ui.text(ui.small(), "ALL GAMES", margin, y, Theme.TEXT_DIM);
        ui.textRight(ui.small(), library.size() + " installed", frame.width() - margin, y, Theme.TEXT_DIM);
        y += 18;

        for (LibraryEntry entry : library.sort(library.all(), GameLibrary.SORT_TITLE, profiles)) {
            drawRow(ui, library, entry, profiles.get(entry.suiteId()), margin, y, width);
            y += 72;
        }

        return frame;
    }

    private void drawTile(Ui ui, GameLibrary library, LibraryEntry entry, int x, int y)
            throws Exception {
        drawArtwork(ui, library, entry, x, y, 80);
        ui.textCenter(ui.small(), ui.ellipsize(ui.small(), entry.title(), 84), x + 40, y + 86,
                Theme.TEXT);
    }

    private void drawRow(Ui ui, GameLibrary library, LibraryEntry entry, GameProfile profile,
                         int x, int y, int width) throws Exception {
        ui.panel(x, y, width, 62, Theme.SURFACE, Theme.BORDER);
        drawArtwork(ui, library, entry, x + 10, y + 8, 46);
        int textLeft = x + 10 + 46 + 12;
        ui.text(ui.mediumBold(), ui.ellipsize(ui.mediumBold(), entry.title(), width - 170),
                textLeft, y + 12, Theme.TEXT);
        ui.text(ui.small(), entry.vendor() + "   -   " + entry.version(), textLeft, y + 34, Theme.TEXT_DIM);
        String chip = profile != null ? profile.device().resolution() : entry.profile();
        int chipWidth = ui.small().stringWidth(chip) + 12;
        ui.chip(chip, x + width - chipWidth - 12, y + 12, Theme.ACCENT, Theme.ACCENT_DIM);
        if (profile != null && profile.isFavourite()) {
            ui.textRight(ui.small(), "favourite", x + width - 12, y + 36, Theme.WARN);
        }
    }

    private void drawArtwork(Ui ui, GameLibrary library, LibraryEntry entry, int x, int y, int size)
            throws Exception {
        Framebuffer frame = ui.frame();
        byte[] artwork = library.artwork(entry.suiteId());
        frame.setColor(Theme.SURFACE_ALT);
        frame.fillRoundRect(x, y, size, size, 12, 12);
        if (artwork != null && PngReader.looksLikePng(artwork)) {
            PngReader.Image decoded = PngReader.decode(artwork);
            Framebuffer icon = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height)
                    .scaleNearest(size - 8, size - 8);
            frame.drawFramebuffer(icon, x + 4, y + 4);
        } else {
            ui.textCenter(ui.largeBold(), entry.title().substring(0, 1).toUpperCase(),
                    x + size / 2, y + size / 2 - 10, Theme.ACCENT);
        }
    }
}
