package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

/**
 * Preview of the import flow: the user picked a JAR + JAD pair and MobiCore is
 * showing what it parsed before the suite is installed into the library.
 */
public final class ImportScreen {

    public Framebuffer render() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(), SampleSuite.jad());
        MidletSuiteInfo info = suite.info();

        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Import Game", "MobiCore");

        int margin = 16;
        int width = frame.width() - margin * 2;
        int y = 62;

        // Source files ---------------------------------------------------
        ui.panel(margin, y, width, 86, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.mediumBold(), "Selected files", margin + 14, y + 10, Theme.TEXT);
        ui.text(ui.small(), "SkyRunner.jar", margin + 14, y + 34, Theme.TEXT);
        ui.textRight(ui.small(), kb(SampleSuite.jar().length), margin + width - 14, y + 34, Theme.TEXT_DIM);
        ui.text(ui.small(), "SkyRunner.jad", margin + 14, y + 54, Theme.TEXT);
        ui.textRight(ui.small(), kb(SampleSuite.jad().length), margin + width - 14, y + 54, Theme.TEXT_DIM);
        y += 98;

        // Parsed metadata ------------------------------------------------
        ui.panel(margin, y, width, 196, Theme.SURFACE, Theme.BORDER);
        drawIcon(ui, margin + 14, y + 14);
        int textLeft = margin + 14 + 56 + 14;
        ui.text(ui.mediumBold(), ui.ellipsize(ui.mediumBold(), info.title(), width - 100),
                textLeft, y + 16, Theme.TEXT);
        ui.text(ui.small(), info.vendor(), textLeft, y + 38, Theme.TEXT_DIM);
        int chipX = textLeft;
        chipX += ui.chip(info.configuration(), chipX, y + 56, Theme.ACCENT, Theme.ACCENT_DIM) + 6;
        ui.chip(info.profile(), chipX, y + 56, Theme.ACCENT, Theme.ACCENT_DIM);

        int fieldY = y + 92;
        ui.field("Version", info.version(), margin + 14, fieldY, width - 28);
        ui.field("Suite id", info.suiteId(), margin + 14, fieldY + 22, width - 28);
        ui.field("Entries in JAR", suite.archive().size() + " files", margin + 14, fieldY + 44, width - 28);
        ui.field("Uncompressed", kb(suite.archive().uncompressedSize()), margin + 14, fieldY + 66, width - 28);
        ui.field("Descriptor", suite.jad() != null ? "JAD + manifest" : "manifest only",
                margin + 14, fieldY + 88, width - 28);
        y += 208;

        // MIDlets --------------------------------------------------------
        int midletPanelHeight = 34 + info.midlets().size() * 34;
        ui.panel(margin, y, width, midletPanelHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "MIDLETS", margin + 14, y + 10, Theme.TEXT_DIM);
        int rowY = y + 30;
        for (MidletEntry entry : info.midlets()) {
            ui.bar(margin + 14, rowY, width - 28, 30, Theme.SURFACE_ALT);
            ui.text(ui.smallBold(), entry.name(), margin + 22, rowY + 2, Theme.TEXT);
            ui.text(ui.small(), entry.className(), margin + 22, rowY + 15, Theme.TEXT_DIM);
            if (entry.index() == 1) {
                ui.chip("DEFAULT", margin + width - 90, rowY + 7, Theme.GOOD, 0xFF14361B);
            }
            rowY += 34;
        }
        y += midletPanelHeight + 12;

        // Sandbox preview ------------------------------------------------
        StorageLayout layout = new StorageLayout("MobiCore");
        ui.panel(margin, y, width, 108, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "SANDBOX", margin + 14, y + 10, Theme.TEXT_DIM);
        String[] paths = {
                layout.gameDir(info.suiteId()),
                layout.profilePath(info.suiteId()),
                layout.rmsDir(info.suiteId()),
                layout.backupDir(info.suiteId()),
        };
        int pathY = y + 30;
        for (String path : paths) {
            ui.text(ui.small(), ui.ellipsize(ui.small(), path, width - 28), margin + 14, pathY, Theme.TEXT);
            pathY += 18;
        }
        y += 120;

        // Actions --------------------------------------------------------
        int buttonWidth = (width - 12) / 2;
        ui.panel(margin, y, buttonWidth, 44, Theme.SURFACE_ALT, Theme.BORDER);
        ui.textCenter(ui.mediumBold(), "Cancel", margin + buttonWidth / 2, y + 13, Theme.TEXT_DIM);
        ui.panel(margin + buttonWidth + 12, y, buttonWidth, 44, Theme.ACCENT_DIM, Theme.ACCENT);
        ui.textCenter(ui.mediumBold(), "Install", margin + buttonWidth + 12 + buttonWidth / 2, y + 13, Theme.ACCENT);

        return frame;
    }

    private void drawIcon(Ui ui, int x, int y) {
        Framebuffer frame = ui.frame();
        frame.setColor(0xFF1D4E63);
        frame.fillRoundRect(x, y, 56, 56, 14, 14);
        frame.setColor(Theme.ACCENT);
        frame.drawRoundRect(x, y, 55, 55, 14, 14);
        frame.fillArc(x + 16, y + 14, 24, 24, 0, 360);
        frame.setColor(0xFF1D4E63);
        frame.fillArc(x + 22, y + 20, 12, 12, 0, 360);
        frame.setColor(Theme.ACCENT);
        frame.fillRect(x + 12, y + 42, 32, 3);
    }

    private static String kb(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + "." + ((bytes % 1024) * 10 / 1024) + " KB";
    }
}
