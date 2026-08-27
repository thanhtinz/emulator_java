package com.mobicore.tools;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;
import java.util.Map;

/**
 * Cái người chơi thấy khi game chết.
 *
 * <p>Game trong ảnh này hỏng thật: bản mẫu {@code demo.CrashDemo} chạy bằng
 * bytecode thật và ngã ở khung hình đầu tiên, rồi lời giải thích trên màn hình
 * được đọc thẳng từ facade. Không có chữ nào ở đây được viết sẵn cho đẹp
 * ảnh.</p>
 */
public final class CrashScreen {

    private final String fixtureDir;
    private final String midletClass;

    public CrashScreen(String fixtureDir) {
        this(fixtureDir, "demo.CrashDemo");
    }

    /**
     * @param midletClass bản mẫu hỏng theo kiểu nào — mỗi kiểu một lời giải
     *                    thích khác, và màn hình này vẽ lời giải thích ấy
     */
    public CrashScreen(String fixtureDir, String midletClass) {
        this.fixtureDir = fixtureDir;
        this.midletClass = midletClass;
    }

    public Framebuffer render() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");
        facade.startGame(suiteId, midletClass);
        // Hạn chờ treo rút xuống một phần tư giây: ảnh chụp cần cái màn hình
        // hiện ra sau đó, không cần tám giây chờ thật.
        facade.session().vm().setStuckAfterMs(250);
        facade.renderFrame();
        Map<String, Object> crash = Json.readObject(facade.crashJson());

        Framebuffer frame = Preview.newPage();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar(Json.string(crash, "game", "Game"), "MobiCore");

        // Màn hình game vẫn ở đó, mờ đi: người chơi cần thấy mình vừa ở đâu,
        // chứ không phải bị ném thẳng về danh sách game.
        int gameTop = Ui.APP_BAR;
        int gameHeight = 430;
        frame.setColor(0xFF000000);
        frame.fillRect(0, gameTop, frame.width(), gameHeight);
        frame.setColor(0xB0000000);
        frame.fillRect(0, gameTop, frame.width(), gameHeight);
        ui.textCenter(ui.small(), "Màn hình lúc game dừng", frame.width() / 2,
                gameTop + gameHeight / 2 - ui.small().height() / 2, 0xFF6A6A6A);

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int inset = 18;
        int textWidth = width - inset * 2;
        int y = gameTop + gameHeight + 18;

        // Ba câu, đúng thứ tự người ta hỏi: hỏng cái gì, vì sao, làm gì tiếp.
        List<String> reason = ui.wrap(ui.medium(), Json.string(crash, "reason", ""),
                textWidth, 4);
        List<String> advice = ui.wrap(ui.small(), Json.string(crash, "advice", ""),
                textWidth - 30, 4);

        int badge = 30;
        int lineGap = 6;
        int reasonBlock = reason.size() * (ui.medium().height() + lineGap);
        int adviceBlock = advice.size() * (ui.small().height() + lineGap);
        int cardHeight = inset + badge + 14 + ui.largeBold().height() + 12
                + reasonBlock + 10 + adviceBlock + 14 + inset;
        ui.panel(margin, y, width, cardHeight, Theme.SURFACE, Theme.BORDER);

        int row = y + inset;
        frame.setColor(Theme.BAD_BG);
        frame.fillRoundRect(margin + inset, row, badge, badge, badge, badge);
        Icons.drawCentred(frame, Icons.CLOSE, margin + inset + badge / 2, row + badge / 2,
                18, Theme.BAD);
        row += badge + 14;

        ui.text(ui.largeBold(), Json.string(crash, "title", ""), margin + inset, row,
                Theme.TEXT);
        row += ui.largeBold().height() + 12;

        for (int i = 0; i < reason.size(); i++) {
            ui.text(ui.medium(), reason.get(i), margin + inset, row, Theme.TEXT);
            row += ui.medium().height() + lineGap;
        }
        row += 10;

        // Lời khuyên nằm sau một vạch màu: nó là phần duy nhất người chơi làm
        // được, nên nó không được lẫn vào phần kể lể.
        int adviceTop = row - 4;
        frame.setColor(Theme.ACCENT);
        frame.fillRect(margin + inset, adviceTop, 3, adviceBlock + 2);
        for (int i = 0; i < advice.size(); i++) {
            ui.text(ui.small(), advice.get(i), margin + inset + 16, row, Theme.TEXT_DIM);
            row += ui.small().height() + lineGap;
        }
        y += cardHeight + 14;

        // Dòng kỹ thuật: người chơi không cần, người sửa game thì cần, nên nó
        // ở đây nhưng nhỏ và xám.
        int techHeight = 12 + ui.small().height() + 8
                + ui.small().height() + 4 + ui.small().height() + 14;
        int techRow = ui.section(margin, y, width, techHeight, "CHI TIẾT KỸ THUẬT",
                "sao chép");
        ui.text(ui.small(), ui.ellipsize(ui.small(), Json.string(crash, "technical", ""),
                textWidth), margin + inset, techRow, Theme.TEXT_DIM);
        List<Object> stack = Json.array(crash, "stack");
        if (!stack.isEmpty()) {
            ui.text(ui.small(), ui.ellipsize(ui.small(), String.valueOf(stack.get(0)),
                    textWidth), margin + inset, techRow + ui.small().height() + 4,
                    Theme.TEXT_DIM);
        }
        y += techHeight + 16;

        int buttonWidth = (width - 12) / 2;
        ui.button(margin, y, buttonWidth, "Đóng", false);
        ui.button(margin + buttonWidth + 12, y, buttonWidth, "Chơi lại", true, Icons.PLAY);

        facade.dismissCrash();
        return Preview.fit(frame);
    }
}
