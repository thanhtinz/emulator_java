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

        // Bảng vật phẩm: ô tìm kiếm, số lượng, nút gửi.
        int searchHeight = 12 + ui.small().height() + 8 + 44 + 10;
        int row = ui.section(margin, y, width, searchHeight, "VẬT PHẨM ĐÃ TÌM ĐƯỢC",
                items.size() + " thứ");
        ui.searchField(fieldX, row, fieldWidth, "", "Tìm vật phẩm…");
        y += searchHeight + 12;

        int itemHeight = ui.mediumBold().height() + ui.small().height() + 12;
        int listHeight = 12 + ui.small().height() + 8 + items.size() * itemHeight + 8;
        row = ui.section(margin, y, width, listHeight, "TRONG GAME NÀY", null);
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = (Map<String, Object>) items.get(i);
            ui.text(ui.mediumBold(), Json.string(item, "name", ""), fieldX, row, Theme.TEXT);
            ui.textRight(ui.mediumBold(), String.valueOf(Json.longValue(item, "amount", 0L)),
                    fieldX + fieldWidth, row, Theme.ACCENT);
            ui.text(ui.small(), Json.integer(item, "places", 0) + " chỗ trong phần lưu  ·  "
                            + "nhiều nhất " + Json.longValue(item, "ceiling", 0L),
                    fieldX, row + ui.mediumBold().height() + 2, Theme.TEXT_DIM);
            row += itemHeight;
        }
        y += listHeight + 12;

        // Ô số lượng và nút gửi, cạnh nhau: gõ số rồi bấm là xong.
        int sendHeight = 12 + ui.small().height() + 8 + 46 + 10;
        row = ui.section(margin, y, width, sendHeight, "GỬI VÀO GAME", "Thuốc hồi máu");
        int amountWidth = fieldWidth - 150;
        ui.input(fieldX, row, amountWidth, "99", "Số lượng");
        ui.button(fieldX + amountWidth + 12, row - 2, 138, "Gửi", true, Icons.IMPORT);
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

        ui.text(ui.small(), "Phần lưu được sao lưu trước khi sửa.", fieldX, y, Theme.TEXT_DIM);
    }
}
