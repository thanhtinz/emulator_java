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
 * The library tab: a search box, the sort order, and what matched.
 *
 * <p>Photographed mid-search with the marks left off — "nguoi chay" against a
 * library holding "Người Chạy Trên Mây" — because that is the case worth
 * proving. A search that demanded the marks would find nothing here and look
 * broken to the person typing it.</p>
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

        int margin = 16;
        int width = frame.width() - margin * 2;
        ui.text(ui.title(), "Thư viện", margin, 14, Theme.TEXT);

        int y = 14 + ui.title().height() + 14;
        y += ui.searchField(margin, y, width, query, "Tìm theo tên hoặc nhà phát hành") + 12;

        String[] sorts = {"Tên", "Vừa chơi", "Nhà phát hành"};
        int chipX = margin;
        for (int i = 0; i < sorts.length; i++) {
            boolean selected = i == GameLibrary.SORT_TITLE;
            chipX += ui.chip(sorts[i], chipX, y,
                    selected ? Theme.ACCENT : Theme.TEXT_DIM,
                    selected ? Theme.ACCENT_DIM : Theme.SURFACE_ALT) + 8;
        }
        y += ui.chipHeight() + 16;

        ui.text(ui.small(), found.size() + " kết quả", margin, y, Theme.TEXT_DIM);
        y += ui.small().height() + 10;

        for (int i = 0; i < found.size(); i++) {
            LibraryEntry entry = found.get(i);
            y += row(ui, library, entry, profiles.get(entry.suiteId()), margin, y, width) + 12;
        }

        if (found.isEmpty()) {
            ui.textCenter(ui.medium(), "Không có kết quả", frame.width() / 2, y + 40,
                    Theme.TEXT_DIM);
        }

        ui.tabBar(new String[]{"Trang chủ", "Thư viện", "Công cụ", "Cài đặt"}, 1);
        return frame;
    }

    /** One result: cover, both names when they differ, and the device. */
    private int row(Ui ui, GameLibrary library, LibraryEntry entry, GameProfile profile,
                    int x, int y, int width) throws Exception {
        int height = 84;
        Framebuffer frame = ui.frame();
        ui.panel(x, y, width, height, Theme.SURFACE, Theme.BORDER);

        int cover = height - 24;
        LibraryScreen.drawCover(ui, library, entry, x + 12, y + 12, cover);

        int textLeft = x + 12 + cover + 14;
        String chip = profile != null ? profile.device().resolution() : entry.profile();
        int chipWidth = ui.small().stringWidth(chip) + 18;
        ui.text(ui.mediumBold(),
                ui.ellipsize(ui.mediumBold(), entry.title(), width - cover - chipWidth - 60),
                textLeft, y + 14, Theme.TEXT);
        String second = entry.isRenamed()
                ? "tên gốc: " + entry.originalTitle()
                : entry.vendor() + "  ·  " + entry.version();
        ui.text(ui.small(), ui.ellipsize(ui.small(), second, width - cover - chipWidth - 60),
                textLeft, y + 14 + ui.mediumBold().height() + 4, Theme.TEXT_DIM);
        ui.chip(chip, x + width - chipWidth - 14, y + 14, Theme.ACCENT, Theme.ACCENT_DIM);
        return height;
    }
}
