package com.mobicore.tools;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.net.LoopbackTransport;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;
import java.util.Map;

/**
 * Preview of installing a game from a link.
 *
 * <p>The install really runs: a descriptor and a JAR are served from the
 * loopback transport and go through the same facade the phone calls, so what
 * is drawn is what a real install reports rather than a mock-up of it.</p>
 */
public final class LinkScreen {

    private final String fixtureDir;

    public LinkScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        String link = "http://games.example/j2me/skyrunner.jad";
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        facade.setInstallerTransport(new LoopbackTransport()
                .respond("skyrunner.jad", 200, "OK",
                        "MIDlet-Name: Sky Runner\n"
                                + "MIDlet-Version: 1.2.0\n"
                                + "MIDlet-Vendor: MobiCore Samples\n"
                                + "MIDlet-Jar-URL: skyrunner.jar\n"
                                + "MIDlet-1: Sky Runner,,demo.SkyRunner\n",
                        "text/vnd.sun.j2me.app-descriptor")
                .respondBytes("skyrunner.jar", 200, SampleSuite.jar(fixtureDir),
                        "application/java-archive")
                // What people actually hit: a link that has turned into a
                // sign-in page since it was posted.
                .respond("dang-nhap", 200, "OK",
                        "<!DOCTYPE html><html><body>Đăng nhập</body></html>", "text/html"));
        Map<String, Object> result = Json.readObject(facade.installFromUrl(link));

        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Nhập từ liên kết", "MobiCore");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 16;

        // The link, as typed.
        int linkHeight = 12 + ui.small().height() + 8 + ui.medium().height() + 18;
        int row = ui.section(margin, y, width, linkHeight, "LIÊN KẾT", null);
        ui.text(ui.medium(), ui.ellipsize(ui.medium(), link, fieldWidth), fieldX, row,
                Theme.TEXT);
        y += linkHeight + 14;

        // What the emulator did about it, in the words it reports.
        List<Object> notes = Json.array(result, "notes");
        int notesHeight = 12 + ui.small().height() + 8 + notes.size() * 24 + 8;
        row = ui.section(margin, y, width, notesHeight, "ĐÃ LÀM GÌ", null);
        int glyph = ui.small().height() + 2;
        for (int i = 0; i < notes.size(); i++) {
            Icons.draw(frame, Icons.CHECK, fieldX, row, glyph, Theme.GOOD);
            ui.text(ui.small(), ui.ellipsize(ui.small(), String.valueOf(notes.get(i)),
                            fieldWidth - glyph - 10), fieldX + glyph + 10, row + 1,
                    Theme.TEXT);
            row += 24;
        }
        y += notesHeight + 14;

        // And the game that came out of it.
        Map<String, Object> game = Json.child(result, "game");
        int gameHeight = ui.sectionHeight(3);
        row = ui.section(margin, y, width, gameHeight, "ĐÃ CÀI", "xong");
        ui.field("Tên", Json.string(game, "title", ""), fieldX, row, fieldWidth);
        ui.field("Nhà phát hành", Json.string(game, "vendor", ""), fieldX, row + Ui.ROW,
                fieldWidth);
        ui.field("Phiên bản", Json.string(game, "version", ""), fieldX, row + Ui.ROW * 2,
                fieldWidth);
        y += gameHeight + 16;

        // What a link that is not a game says, which is the case that matters:
        // a dead link and a login page are what people actually hit.
        String refused = Json.string(Json.readObject(
                facade.installFromUrl("http://games.example/dang-nhap")), "error", "");
        // Wrapped rather than cut: the half of the sentence that says what to
        // do about it is the half that gets lost to an ellipsis.
        List<String> reason = ui.wrap(ui.small(), refused, fieldWidth - glyph - 10, 3);
        int errorHeight = 12 + ui.small().height() + 8
                + reason.size() * (ui.small().height() + 4) + 12;
        row = ui.section(margin, y, width, errorHeight, "KHI LIÊN KẾT HỎNG", null);
        Icons.draw(frame, Icons.CLOSE, fieldX, row, glyph, Theme.BAD);
        for (int i = 0; i < reason.size(); i++) {
            ui.text(ui.small(), reason.get(i), fieldX + glyph + 10,
                    row + 1 + i * (ui.small().height() + 4), Theme.TEXT);
        }
        y += errorHeight + 16;

        int buttonWidth = (width - 12) / 2;
        ui.button(margin, y, buttonWidth, "Hủy", false);
        ui.button(margin + buttonWidth + 12, y, buttonWidth, "Tải về", true, Icons.IMPORT);
        return frame;
    }
}
