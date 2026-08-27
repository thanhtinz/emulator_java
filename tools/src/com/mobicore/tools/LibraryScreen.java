package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.library.CollectionStore;
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
 * Preview of the Home screen: a toolbar and the games, read from a real
 * {@link GameLibrary}.
 *
 * <p>Shaped after the emulators people already use — one flat, sorted list of
 * what is installed, with find, sort and everything else on the toolbar, and
 * the one floating button that adds a game. The tabs, sections and cards this
 * screen used to carry were the app talking about itself; a library screen's
 * job is to get out of the way of the game someone came to open.</p>
 */
public final class LibraryScreen {

    private final String fixtureDir;
    /** The shelves the sample library has, drawn as a row over the list. */
    private CollectionStore shelves;

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

        // Real shelves, through the same store the app writes to, so the
        // screenshot cannot show a feature that does not work.
        CollectionStore shelves = new CollectionStore(vfs, layout);
        shelves.add("Chơi trên xe buýt", "mobicore-samples.sky-runner.1-2-0");
        shelves.add("Chơi trên xe buýt", "mobicore-samples.tile-rush.1-0-4");
        shelves.add("Đua xe", "blue-fox-games.night-racer.2-1");
        this.shelves = shelves;

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

        int y = toolBar(ui);
        y = shelfRow(ui, y);
        List<LibraryEntry> games = library.sort(library.all(), GameLibrary.SORT_TITLE, profiles);
        for (int i = 0; i < games.size(); i++) {
            LibraryEntry entry = games.get(i);
            drawRow(ui, library, entry, profiles.get(entry.suiteId()), y);
            y += ROW_HEIGHT;
            if (i < games.size() - 1) {
                frame.setColor(Theme.BORDER);
                frame.fillRect(MARGIN + ICON + GAP, y, frame.width() - MARGIN - ICON - GAP, 1);
            }
        }

        // Importing is what a new install has to do first and what most later
        // visits come back for, so it gets the one floating button on the
        // screen — always the same corner, never over the list.
        ui.fab(Icons.ADD, 0);
        return frame;
    }

    /**
     * The shelves, as a row of chips over the list.
     *
     * <p>"Tất cả" is first and is a shelf like the others, because "no
     * filter" is what a player picks most often and reaching it should not
     * mean finding a cross to tap. The row appears only once there are
     * shelves: one chip saying "Tất cả" tells nobody anything.</p>
     */
    private int shelfRow(Ui ui, int y) {
        java.util.List<String> names = shelves == null
                ? new java.util.ArrayList<String>() : shelves.names();
        if (names.isEmpty()) {
            return y;
        }
        int top = y + 8;
        int x = 12;
        x += ui.chip("Tất cả", x, top, Theme.BG, Theme.ACCENT) + 8;
        for (int i = 0; i < names.size(); i++) {
            x += ui.chip(names.get(i), x, top, Theme.TEXT, Theme.SURFACE_ALT) + 8;
        }
        return top + ui.chipHeight() + 8;
    }

    /**
     * The toolbar, laid out the way a J2ME emulator's is: the app's name, and
     * on the right the three things that act on the list — find one, order
     * them, everything else.
     *
     * <p>The bottom tabs this screen used to carry are gone with it. There
     * was one list and two settings pages behind them, and settings reached
     * through a toolbar menu is where anyone looks for them anyway.</p>
     */
    private int toolBar(Ui ui) {
        Framebuffer frame = ui.frame();
        int height = 74;
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), height);
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, height - 1, frame.width(), 1);

        ui.text(ui.large(), "MobiCore", MARGIN, (height - ui.large().height()) / 2, Theme.TEXT);
        int glyph = 26;
        int step = 46;
        int x = frame.width() - MARGIN - glyph;
        String[] actions = {Icons.MORE, Icons.SORT, Icons.SEARCH};
        for (int i = 0; i < actions.length; i++) {
            Icons.draw(frame, actions[i], x, (height - glyph) / 2, glyph, Theme.TEXT_DIM);
            x -= step;
        }
        return height;
    }

    /** Margins and sizes: 36dp icon, 10dp padding, as the same list has. */
    private static final int MARGIN = 16;
    private static final int ICON = 52;
    private static final int GAP = 14;
    private static final int ROW_HEIGHT = 84;

    /**
     * One game: its icon, its name, and underneath the vendor and version.
     *
     * <p>A flat row rather than a card. A list of eighty games in eighty
     * cards is eighty rectangles to look past, and the icon already tells one
     * row from the next.</p>
     */
    private void drawRow(Ui ui, GameLibrary library, LibraryEntry entry, GameProfile profile,
                         int y) throws Exception {
        Framebuffer frame = ui.frame();
        int width = frame.width();
        drawArtwork(ui, library, entry, MARGIN, y + (ROW_HEIGHT - ICON) / 2, ICON);

        int left = MARGIN + ICON + GAP;
        int right = width - MARGIN;
        int textTop = y + (ROW_HEIGHT - ui.mediumBold().height() - ui.small().height() - 6) / 2;
        ui.text(ui.mediumBold(),
                ui.ellipsize(ui.mediumBold(), entry.title(), right - left - 30),
                left, textTop, Theme.TEXT);

        int metaY = textTop + ui.mediumBold().height() + 6;
        String version = entry.version();
        int versionWidth = ui.small().stringWidth(version);
        ui.text(ui.small(),
                ui.ellipsize(ui.small(), entry.vendor(), right - left - versionWidth - 16),
                left, metaY, Theme.TEXT_DIM);
        ui.textRight(ui.small(), version, right, metaY, Theme.TEXT_DIM);

        if (profile != null && profile.isFavourite()) {
            Icons.drawCentred(frame, Icons.STAR, right - 8, textTop + ui.mediumBold().height() / 2,
                    18, Theme.WARN);
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
