package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

/**
 * Preview of the import flow: the user picked a JAR and JAD pair and MobiCore
 * is showing what it parsed before the suite is installed.
 */
public final class ImportScreen {

    public Framebuffer render() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(), SampleSuite.jad());
        MidletSuiteInfo info = suite.info();

        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Nhập trò chơi", "MobiCore");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int y = Ui.APP_BAR + 18;

        // Chosen files ---------------------------------------------------
        int filesHeight = ui.sectionHeight(2);
        int row = ui.section(margin, y, width, filesHeight, "TỆP ĐÃ CHỌN", null);
        ui.field("SkyRunner.jar", kb(SampleSuite.jar().length), margin + Ui.PAD, row, width - Ui.PAD * 2);
        ui.field("SkyRunner.jad", kb(SampleSuite.jad().length), margin + Ui.PAD, row + Ui.ROW,
                width - Ui.PAD * 2);
        y += filesHeight + 14;

        // Parsed metadata ------------------------------------------------
        int iconSize = 72;
        int metaHeight = iconSize + 24 + 5 * Ui.ROW + 14;
        ui.panel(margin, y, width, metaHeight, Theme.SURFACE, Theme.BORDER);
        drawIcon(ui, margin + Ui.PAD, y + Ui.PAD, iconSize);
        int textLeft = margin + Ui.PAD + iconSize + 16;
        ui.text(ui.largeBold(), ui.ellipsize(ui.largeBold(), info.title(), width - iconSize - 60),
                textLeft, y + Ui.PAD, Theme.TEXT);
        ui.text(ui.small(), info.vendor(), textLeft, y + Ui.PAD + ui.largeBold().height() + 2,
                Theme.TEXT_DIM);
        int chipY = y + Ui.PAD + ui.largeBold().height() + ui.small().height() + 8;
        int chipX = textLeft;
        chipX += ui.chip(info.configuration(), chipX, chipY, Theme.ACCENT, Theme.ACCENT_DIM) + 8;
        ui.chip(info.profile(), chipX, chipY, Theme.ACCENT, Theme.ACCENT_DIM);

        int fieldY = y + iconSize + 24;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        ui.field("Phiên bản", info.version(), fieldX, fieldY, fieldWidth);
        ui.field("Mã bộ cài", info.suiteId(), fieldX, fieldY + Ui.ROW, fieldWidth);
        ui.field("Số tệp trong JAR", suite.archive().size() + " tệp", fieldX, fieldY + Ui.ROW * 2,
                fieldWidth);
        ui.field("Sau giải nén", kb(suite.archive().uncompressedSize()), fieldX,
                fieldY + Ui.ROW * 3, fieldWidth);
        ui.field("Mô tả", suite.jad() != null ? "JAD + manifest" : "chỉ manifest", fieldX,
                fieldY + Ui.ROW * 4, fieldWidth);
        y += metaHeight + 14;

        // MIDlets --------------------------------------------------------
        int entryHeight = ui.small().height() + ui.mediumBold().height() + 14;
        int midletHeight = 12 + ui.small().height() + 8 + info.midlets().size() * (entryHeight + 8);
        row = ui.section(margin, y, width, midletHeight, "CÁC MIDLET", null);
        for (MidletEntry entry : info.midlets()) {
            ui.panel(margin + Ui.PAD, row, width - Ui.PAD * 2, entryHeight, Theme.SURFACE_ALT,
                    Theme.SURFACE_ALT);
            ui.text(ui.mediumBold(), entry.name(), margin + Ui.PAD + 12, row + 6, Theme.TEXT);
            ui.text(ui.small(), entry.className(), margin + Ui.PAD + 12,
                    row + 6 + ui.mediumBold().height(), Theme.TEXT_DIM);
            if (entry.index() == 1) {
                int chipWidth = ui.small().stringWidth("MẶC ĐỊNH") + 18;
                ui.chip("MẶC ĐỊNH", margin + width - Ui.PAD - 12 - chipWidth,
                        row + (entryHeight - ui.chipHeight()) / 2, Theme.GOOD, Theme.GOOD_BG);
            }
            row += entryHeight + 8;
        }
        y += midletHeight + 14;

        // Sandbox --------------------------------------------------------
        StorageLayout layout = new StorageLayout("MobiCore");
        String[] paths = {
                layout.gameDir(info.suiteId()),
                layout.profilePath(info.suiteId()),
                layout.rmsDir(info.suiteId()),
                layout.backupDir(info.suiteId()),
        };
        int sandboxHeight = 12 + ui.small().height() + 8 + paths.length * (ui.small().height() + 6) + 8;
        row = ui.section(margin, y, width, sandboxHeight, "VÙNG DỮ LIỆU RIÊNG", null);
        for (String path : paths) {
            ui.text(ui.small(), ui.ellipsize(ui.small(), path, width - Ui.PAD * 2),
                    margin + Ui.PAD, row, Theme.TEXT);
            row += ui.small().height() + 6;
        }
        y += sandboxHeight + 14;

        // Device profile -------------------------------------------------
        DeviceProfile suggested = DeviceProfile.suggestFor(info);
        int chipRowHeight = ui.chipHeight() + 8;
        int deviceHeight = 12 + ui.small().height() + 8 + chipRowHeight * 2 + Ui.ROW + 6;
        row = ui.section(margin, y, width, deviceHeight, "MÁY GIẢ LẬP", suggested.keypadName());
        int deviceX = margin + Ui.PAD;
        int deviceY = row;
        for (DeviceProfile candidate : DeviceProfile.catalog()) {
            boolean selected = candidate.id().equals(suggested.id());
            int chipWidth = ui.small().stringWidth(candidate.resolution()) + 18;
            if (deviceX + chipWidth > margin + width - Ui.PAD) {
                deviceX = margin + Ui.PAD;
                deviceY += chipRowHeight;
            }
            ui.chip(candidate.resolution(), deviceX, deviceY,
                    selected ? Theme.ACCENT : Theme.TEXT_DIM,
                    selected ? Theme.ACCENT_DIM : Theme.SURFACE_ALT);
            deviceX += chipWidth + 8;
        }
        ui.field("Đề xuất cho bộ cài này", suggested.name(), margin + Ui.PAD,
                deviceY + chipRowHeight + 2, width - Ui.PAD * 2);
        y += deviceHeight + 16;

        // Actions --------------------------------------------------------
        int buttonWidth = (width - 12) / 2;
        ui.button(margin, y, buttonWidth, "Hủy", false);
        ui.button(margin + buttonWidth + 12, y, buttonWidth, "Cài đặt", true, Icons.IMPORT);

        return frame;
    }

    /** Placeholder cover for a suite that carries no icon of its own. */
    private void drawIcon(Ui ui, int x, int y, int size) {
        Framebuffer frame = ui.frame();
        frame.setColor(Theme.ACCENT_DIM);
        frame.fillRoundRect(x, y, size, size, 18, 18);
        frame.setColor(Theme.ACCENT);
        frame.drawRoundRect(x, y, size - 1, size - 1, 18, 18);
        Icons.drawCentred(frame, Icons.LIBRARY, x + size / 2, y + size / 2, size / 2, Theme.ACCENT);
    }

    private static String kb(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + "," + ((bytes % 1024) * 10 / 1024) + " KB";
    }
}
