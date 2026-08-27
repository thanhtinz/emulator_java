package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;
import java.util.Map;

/**
 * The home screen mid-search: the box, the sort order, and what matched.
 *
 * <p>Typing replaces the browsing sections with the matches: someone
 * searching has stopped browsing.</p>
 *
 * <p>Photographed with the marks left off — "nguoi chay" against a library
 * holding "Người Chạy Trên Mây" — because that is the case worth proving. A
 * search that demanded the marks would find nothing here and look broken to
 * the person typing it.</p>
 */
public final class SearchScreen {

    private final String fixtureDir;

    public SearchScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();

        LibraryEntry sky = library.install(SampleSuite.jar(fixtureDir), SampleSuite.jad()).entry();
        library.rename(sky.suiteId(), "Người Chạy Trên Mây");
        LibraryScreen.installExtras(library);

        Map<String, GameProfile> profiles = library.allProfiles();
        String query = "nguoi chay";
        List<LibraryEntry> found =
                library.sort(library.search(query), GameLibrary.SORT_TITLE, profiles);

        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        // Searching takes the toolbar over rather than living in a box below
        // it: a field that is always on screen costs a row of games on every
        // visit, and most visits are not searches.
        int height = 74;
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), height);
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, height - 1, frame.width(), 1);
        int glyph = 26;
        Icons.draw(frame, Icons.SEARCH, 16, (height - glyph) / 2, glyph, Theme.TEXT_DIM);
        ui.text(ui.large(), query, 16 + glyph + 14, (height - ui.large().height()) / 2,
                Theme.TEXT);
        Icons.draw(frame, Icons.CLOSE, frame.width() - 16 - glyph, (height - glyph) / 2, glyph,
                Theme.TEXT_DIM);

        int y = height;
        for (int i = 0; i < found.size(); i++) {
            LibraryEntry entry = found.get(i);
            row(ui, library, entry, profiles.get(entry.suiteId()), y);
            y += ROW_HEIGHT;
            if (i < found.size() - 1) {
                frame.setColor(Theme.BORDER);
                frame.fillRect(82, y, frame.width() - 82, 1);
            }
        }
        if (found.isEmpty()) {
            ui.textCenter(ui.medium(), "Không tìm thấy. Thử một từ khoá khác.",
                    frame.width() / 2, y + 40, Theme.TEXT_DIM);
        }
        return frame;
    }

    private static final int ROW_HEIGHT = 84;

    /** One result: its icon, its name, and the name it was installed under. */
    private void row(Ui ui, GameLibrary library, LibraryEntry entry, GameProfile profile, int y)
            throws Exception {
        Framebuffer frame = ui.frame();
        int icon = 52;
        LibraryScreen.drawCover(ui, library, entry, 16, y + (ROW_HEIGHT - icon) / 2, icon);

        int left = 16 + icon + 14;
        int right = frame.width() - 16;
        int top = y + (ROW_HEIGHT - ui.mediumBold().height() - ui.small().height() - 6) / 2;
        ui.text(ui.mediumBold(), ui.ellipsize(ui.mediumBold(), entry.title(), right - left),
                left, top, Theme.TEXT);
        String second = entry.isRenamed()
                ? "tên gốc: " + entry.originalTitle()
                : entry.vendor();
        int metaY = top + ui.mediumBold().height() + 6;
        String version = entry.version();
        ui.text(ui.small(), ui.ellipsize(ui.small(), second,
                right - left - ui.small().stringWidth(version) - 16), left, metaY, Theme.TEXT_DIM);
        ui.textRight(ui.small(), version, right, metaY, Theme.TEXT_DIM);
    }
}
