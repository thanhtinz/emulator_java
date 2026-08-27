package com.mobicore.tools;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;
import java.util.Map;

/**
 * Chọn máy mà game tưởng nó đang chạy trên đó.
 *
 * <p>Khung hình trên cùng là game thật: bản mẫu {@code demo.DeviceDemo} hỏi
 * đúng câu {@code microedition.platform} mà game đời ấy hỏi, rồi tự viết ra
 * nó nghe thấy gì. Đổi máy trong màn hình này thì chữ trong game đổi theo —
 * nên ảnh chụp chứng minh được điều mà một bảng cài đặt chỉ nói suông.</p>
 */
public final class DeviceScreen {

    private final String fixtureDir;

    public DeviceScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");
        Map<String, Object> handset = Json.readObject(facade.handsetJson(suiteId));

        Framebuffer frame = Preview.newPage();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Máy giả lập", "Sky Runner");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int y = Ui.APP_BAR + 14;

        // Game tự nói nó nghe thấy gì, bên cạnh là vì sao chỗ này đáng có.
        Framebuffer shot = gameFrame(handset);
        int shotWidth = 190;
        int shotHeight = shotWidth * shot.height() / shot.width();
        int cardHeight = shotHeight + Ui.PAD * 2;
        ui.panel(margin, y, width, cardHeight, Theme.SURFACE_ALT, Theme.BORDER);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(shot.scaleSmooth(shotWidth, shotHeight), fieldX, y + Ui.PAD);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);

        int noteX = fieldX + shotWidth + 14;
        int noteWidth = margin + width - Ui.PAD - noteX;
        int noteY = y + Ui.PAD;
        ui.text(ui.mediumBold(), "Game hỏi máy nào", noteX, noteY, Theme.TEXT);
        noteY += ui.mediumBold().height() + 8;
        List<String> why = ui.wrap(ui.small(),
                "Game đời ấy đọc microedition.platform rồi mới chọn bộ ảnh, nhánh vẽ "
                        + "và mã phím. Nghe thấy một cái tên lạ, nó rơi vào nhánh dành "
                        + "cho máy lạ.", noteWidth, 8);
        for (int i = 0; i < why.size(); i++) {
            ui.text(ui.small(), why.get(i), noteX, noteY, Theme.TEXT_DIM);
            noteY += ui.small().height() + 4;
        }
        y += cardHeight + 14;

        // Danh sách máy.
        List<Object> handsets = Json.array(handset, "handsets");
        int row = 46;
        int listHeight = 12 + ui.small().height() + 8 + handsets.size() * row + 8;
        int top = ui.section(margin, y, width, listHeight, "GIẢ LÀM MÁY", null);
        int glyph = ui.small().height() + 2;
        for (int i = 0; i < handsets.size(); i++) {
            Map<String, Object> entry = (Map<String, Object>) handsets.get(i);
            boolean chosen = Json.bool(entry, "chosen", false);
            if (chosen) {
                frame.setColor(Theme.ACCENT_DIM);
                frame.fillRoundRect(margin + Ui.PAD - 8, top - 6, width - Ui.PAD * 2 + 16,
                        row - 4, 10, 10);
            }
            ui.text(ui.medium(), Json.string(entry, "name", ""), fieldX, top,
                    chosen ? Theme.ACCENT : Theme.TEXT);
            ui.text(ui.small(), ui.ellipsize(ui.small(), Json.string(entry, "note", ""),
                            width - Ui.PAD * 2 - glyph - 12),
                    fieldX, top + ui.medium().height() + 2, Theme.TEXT_DIM);
            if (chosen) {
                Icons.draw(frame, Icons.CHECK, margin + width - Ui.PAD - glyph,
                        top + 2, glyph, Theme.ACCENT);
            }
            top += row;
        }
        y += listHeight + 14;

        // Và đúng những chuỗi game đọc được, vì đó là thứ cần nhìn khi một
        // game chạy sai vì tưởng mình đang ở trên máy khác.
        List<Object> properties = Json.array(handset, "properties");
        int line = ui.small().height() + 6;
        int tableHeight = 12 + ui.small().height() + 8 + properties.size() * line + 10;
        top = ui.section(margin, y, width, tableHeight, "GAME ĐỌC ĐƯỢC GÌ", "sửa");
        int valueRight = margin + width - Ui.PAD;
        for (int i = 0; i < properties.size(); i++) {
            Map<String, Object> entry = (Map<String, Object>) properties.get(i);
            String name = Json.string(entry, "name", "");
            String value = Json.string(entry, "value", "");
            ui.text(ui.small(), ui.ellipsize(ui.small(), name, width / 2), fieldX, top,
                    Theme.TEXT_DIM);
            ui.textRight(ui.small(), ui.ellipsize(ui.small(), value, width / 2 - 20),
                    valueRight, top, Theme.TEXT);
            top += line;
        }
        return Preview.fit(frame);
    }

    /**
     * Một khung hình thật của bản mẫu, chạy dưới chiếc máy đang chọn.
     *
     * <p>Chạy riêng chứ không dùng phiên của facade: cái đáng chụp là màn
     * hình game vẽ ra sau khi đã đọc xong máy nó đang chạy trên đó.</p>
     */
    private Framebuffer gameFrame(Map<String, Object> handset) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        profile.identity().setHandset(Json.string(handset, "handset", ""));
        EmulatorSession session = EmulatorSession.create(suite, profile, null, null, null);
        session.start("demo.DeviceDemo");
        session.renderFrame();
        Framebuffer copy = session.screen().copy();
        session.destroy();
        return copy;
    }
}
