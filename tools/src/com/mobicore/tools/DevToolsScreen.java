package com.mobicore.tools;

import com.mobicore.tools.ui.Icons;
import com.mobicore.core.storage.Json;
import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;
import java.util.Map;

/**
 * Preview of the tools screen: finding and editing what a game keeps for the
 * player — gold, potions, whatever it counts.
 *
 * <p>Một trang, không phải năm thẻ. Bốn thẻ kia — tệp trong gói, bảng theo dõi
 * mạng, mod, trình sửa JAD và RMS — là dụng cụ của người viết máy ảo, không
 * phải của người chơi, và một người chơi đi tìm số vàng của mình không việc gì
 * phải cuộn qua chúng.</p>
 *
 * <p>Con số trong ảnh là kết quả thật: một game mẫu giữ ví tiền trong RMS được
 * mở ra, tiêu bớt tiền, rồi tìm hai lần qua đúng cầu nối mà điện thoại gọi.</p>
 */
public final class DevToolsScreen {

    private final String fixtureDir;

    public DevToolsScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        Framebuffer frame = Preview.newPage();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        // Thanh quay lại, không phải thanh thẻ dưới đáy: hai ứng dụng thật đã
        // bỏ thanh thẻ từ lâu, thư viện là gốc và mọi thứ khác chồng lên nó.
        ui.appBar("Vật phẩm", "Sky Runner");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 18;

        drawTreasure(ui, margin, y, width, fieldX, fieldWidth);
        return Preview.fit(frame);
    }

    /**
     * Tìm và sửa số vàng, số ngọc trong phần lưu.
     *
     * <p>Phần lưu của game là một dãy byte không nhãn: không chỗ nào ghi "đây
     * là số vàng". Nhưng người chơi thì biết mình đang có bao nhiêu, nên cách
     * làm là đi ngược: gõ con số đang thấy, chơi cho nó đổi, gõ lại — chỗ nào
     * đổi theo đúng như vậy mới là chỗ thật.</p>
     */
    @SuppressWarnings("unchecked")
    private void drawTreasure(Ui ui, int margin, int y, int width, int fieldX, int fieldWidth)
            throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        String suiteId = Json.string(Json.child(Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad())), "game"),
                "suiteId", "");
        facade.startGame(suiteId, "demo.PiggyBank");
        facade.session().vm().callVirtual(facade.session().context().midlet(),
                "newGame", "(I)V", Integer.valueOf(8630));
        facade.stopGame();
        Map<String, Object> first = Json.readObject(facade.scanSave(suiteId, 8630));

        facade.startGame(suiteId, "demo.PiggyBank");
        facade.session().vm().callVirtual(facade.session().context().midlet(),
                "spend", "(I)V", Integer.valueOf(130));
        facade.stopGame();
        Map<String, Object> second = Json.readObject(facade.narrowSave(suiteId, 8500));

        // Cất hai chỗ tìm được dưới tên của chúng, rồi bày ra bảng vật phẩm —
        // đúng thứ người chơi thấy sau khi đã tìm xong một lần.
        facade.keepItem(suiteId, "Vàng");
        facade.scanSave(suiteId, 12);
        facade.startGame(suiteId, "demo.PiggyBank");
        facade.session().vm().callVirtual(facade.session().context().midlet(),
                "drink", "()V");
        facade.stopGame();
        facade.narrowSave(suiteId, 11);
        facade.keepItem(suiteId, "Thuốc hồi máu");
        List<Object> items = Json.array(
                Json.readObject(facade.itemsJson(suiteId, "")), "items");

        // Vật phẩm đang được chọn. Ba bước, và bước một là chọn: gõ số lượng
        // trước khi biết gõ cho cái gì thì con số ấy chẳng thuộc về đâu cả.
        Map<String, Object> chosen = items.isEmpty()
                ? null : (Map<String, Object>) items.get(items.size() - 1);

        // Bảng vật phẩm: ô tìm kiếm, chọn một thứ, gõ số lượng, gửi.
        int searchHeight = 12 + ui.small().height() + 8 + 44 + 10;
        int row = ui.section(margin, y, width, searchHeight, "VẬT PHẨM ĐÃ TÌM ĐƯỢC",
                items.size() + " thứ");
        ui.searchField(fieldX, row, fieldWidth, "", "Tìm vật phẩm…");
        y += searchHeight + 12;

        int itemHeight = ui.mediumBold().height() + ui.small().height() + 16;
        int listHeight = 12 + ui.small().height() + 8 + items.size() * itemHeight + 8;
        row = ui.section(margin, y, width, listHeight, "TRONG GAME NÀY", "chạm để chọn");
        int glyph = ui.mediumBold().height();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = (Map<String, Object>) items.get(i);
            boolean picked = item == chosen;
            // Hàng đang chọn tự nói ra là nó đang được chọn: nền đổi, tên đổi
            // màu, và một dấu tích. Chỉ đổi màu chữ thôi thì trên màn hình
            // nhỏ không ai thấy.
            if (picked) {
                ui.panel(fieldX - 10, row - 6, fieldWidth + 20, itemHeight - 2,
                        Theme.ACCENT_DIM, Theme.ACCENT_DIM);
                Icons.draw(ui.frame(), Icons.CHECK, fieldX, row + 1, glyph, Theme.ACCENT);
            }
            int textX = picked ? fieldX + glyph + 8 : fieldX;
            ui.text(ui.mediumBold(), Json.string(item, "name", ""), textX, row,
                    picked ? Theme.ACCENT : Theme.TEXT);
            ui.textRight(ui.mediumBold(), String.valueOf(Json.longValue(item, "amount", 0L)),
                    fieldX + fieldWidth, row, Theme.ACCENT);
            ui.text(ui.small(), Json.integer(item, "places", 0) + " chỗ trong phần lưu  ·  "
                            + "nhiều nhất " + Json.longValue(item, "ceiling", 0L),
                    textX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += itemHeight;
        }
        y += listHeight + 12;

        // Bước hai và ba, và chỉ mở ra sau khi đã chọn: ô số lượng để trống,
        // rồi nút gửi. Ô để trống chứ không điền sẵn số đang có — điền sẵn thì
        // một cú bấm nhỡ tay ghi đè chính con số vừa tìm được.
        int sendHeight = 12 + ui.small().height() + 8 + 46 + ui.small().height() + 14;
        if (chosen == null) {
            sendHeight = 12 + ui.small().height() + 8 + ui.medium().height() + 10;
            row = ui.section(margin, y, width, sendHeight, "GỬI VÀO GAME", null);
            ui.text(ui.medium(), "Chọn một vật phẩm ở trên đã.", fieldX, row, Theme.TEXT_DIM);
        } else {
            row = ui.section(margin, y, width, sendHeight, "GỬI VÀO GAME",
                    Json.string(chosen, "name", ""));
            int amountWidth = fieldWidth - 150;
            ui.input(fieldX, row, amountWidth, "", "Số lượng");
            ui.button(fieldX + amountWidth + 12, row - 2, 138, "Gửi", true, Icons.IMPORT);
            ui.text(ui.small(), "nhiều nhất " + Json.longValue(chosen, "ceiling", 0L),
                    fieldX, row + 50, Theme.TEXT_DIM);
        }
        y += sendHeight + 14;

        // Hai bước, và bước nào cũng nói ra nó vừa làm gì.
        int stepHeight = 12 + ui.small().height() + 8 + Ui.ROW * 2 + 6;
        row = ui.section(margin, y, width, stepHeight, "TÌM THÊM VẬT PHẨM MỚI",
                Json.integer(second, "count", 0) + " chỗ còn lại");
        ui.field("Lần 1 — số đang thấy", "8630  ·  "
                + Json.integer(first, "count", 0) + " chỗ trùng", fieldX, row, fieldWidth);
        ui.field("Lần 2 — sau khi chơi", "8500  ·  "
                + Json.integer(second, "count", 0) + " chỗ trùng", fieldX, row + Ui.ROW,
                fieldWidth);
        y += stepHeight + 14;

        // Hai câu này là hai lời hứa, nên chúng được viết ra chứ không để
        // người chơi tự đoán: không mất gì, và không ghi vào chỗ sắp bị xoá.
        java.util.List<String> promise = ui.wrap(ui.small(),
                "Phần lưu được sao lưu trước khi sửa. Game đang chạy sẽ được đóng lại "
                        + "để ghi, có lưu trạng thái.", fieldWidth, 2);
        for (int i = 0; i < promise.size(); i++) {
            ui.text(ui.small(), promise.get(i), fieldX, y + i * (ui.small().height() + 3),
                    Theme.TEXT_DIM);
        }
    }
}
