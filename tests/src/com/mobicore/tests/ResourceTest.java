package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kho tài nguyên: xem trong game có gì, và tự thay.
 *
 * <p>Một game J2ME là một cái hộp .jar, và đổi một tấm ảnh bên trong là việc
 * người ta vẫn làm: Việt hoá chữ nằm trong ảnh, thay bộ hình nhân vật, đổi ảnh
 * nền. Cho tới giờ muốn làm thì phải mang tệp sang máy tính, giải nén, sửa,
 * đóng gói lại.</p>
 *
 * <p>Chỗ khó không phải là thay tệp, mà là <b>biết trong hộp có gì</b>: game
 * đời ấy đặt tên rất tuỳ hứng, một tấm PNG nằm trong {@code data/12.dat} là
 * chuyện thường. Nên bảng này đọc mấy byte đầu của từng tệp chứ không đoán
 * theo đuôi tên.</p>
 */
public final class ResourceTest extends Test {

    private final String fixtureDir;

    public ResourceTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Kho tài nguyên của game";
    }

    @Override
    public void run() throws Exception {
        readingTheBox();
        namesThatLie();
        swappingAFile();
    }

    // ------------------------------------------------------- đọc trong hộp

    private void readingTheBox() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        String suiteId = install(facade);

        Map<String, Object> box = Json.readObject(facade.resourcesJson(suiteId));
        check(Json.bool(box, "ok", false), "đọc được tệp game");
        List<Object> rows = Json.array(box, "resources");
        check(rows.size() >= 4, "bày ra mọi thứ trong hộp, được " + rows.size() + " tệp");
        check(Json.integer(box, "images", 0) >= 2, "đếm được ảnh");
        check(Json.longValue(box, "bytes", 0L) > 0, "và tổng dung lượng");

        Map<String, Object> icon = find(rows, "icon.png");
        eq("PNG", Json.string(icon, "format", ""), "ảnh biểu tượng đọc ra là PNG");
        eq("Ảnh", Json.string(icon, "kindName", ""), "và được xếp vào loại ảnh");
        eq(192, Json.integer(icon, "width", 0), "kèm chiều ngang thật của nó");
        eq(192, Json.integer(icon, "height", 0), "và chiều dọc");

        Map<String, Object> photo = find(rows, "res/photo.jpg");
        eq("JPEG", Json.string(photo, "format", ""), "ảnh JPEG cũng đọc được");
        eq(120, Json.integer(photo, "width", 0),
                "và kích thước đọc từ phần khai báo, không phải giải cả tấm ảnh");

        // Lớp Java không nằm trong bảng: đó là mã của game, không phải thứ
        // thay bằng một tấm ảnh.
        for (int i = 0; i < rows.size(); i++) {
            String path = Json.string((Map<String, Object>) rows.get(i), "path", "");
            check(!path.endsWith(".class"), "không liệt kê mã game: " + path);
            check(!path.startsWith("META-INF/"), "cũng không liệt kê phần khai báo: " + path);
        }
    }

    // -------------------------------------------------- cái tên nói dối

    /**
     * Tên tệp trong game hay nói dối, và bảng này không tin nó.
     *
     * <p>Một bộ cài dựng riêng cho phép thử: một tấm PNG mang tên
     * {@code data/12.dat}, một đoạn MIDI tên {@code r/07}, và một tệp chữ
     * không có đuôi. Đoán theo tên thì cả ba thành "không rõ".</p>
     */
    private void namesThatLie() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", SampleSuite.utf8(SampleSuite.MANIFEST));
        entries.put("demo/SkyRunner.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        entries.put("data/12.dat", PngWriter.encode(new int[]{0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFFFF}, 2, 2));
        entries.put("r/07", new byte[]{'M', 'T', 'h', 'd', 0, 0, 0, 6});
        entries.put("levels", SampleSuite.utf8("màn 1: rừng\nmàn 2: biển\n"));
        entries.put("save.bin", new byte[]{0, 1, 2, 3, (byte) 0x99, (byte) 0xAB, 4, 5});
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.zip(entries), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        List<Object> rows = Json.array(Json.readObject(facade.resourcesJson(suiteId)), "resources");
        Map<String, Object> hidden = find(rows, "data/12.dat");
        eq("PNG", Json.string(hidden, "format", ""),
                "một tấm ảnh mang tên .dat vẫn được nhận ra là ảnh");
        eq(2, Json.integer(hidden, "width", 0), "kèm kích thước thật");

        Map<String, Object> tune = find(rows, "r/07");
        eq("MIDI", Json.string(tune, "format", ""), "một đoạn nhạc không có đuôi tên cũng vậy");
        eq("Âm thanh", Json.string(tune, "kindName", ""), "và được xếp đúng loại");

        Map<String, Object> text = find(rows, "levels");
        eq("Chữ", Json.string(text, "kindName", ""),
                "tệp toàn chữ đọc được thì là chữ — đó là thứ hay bị sửa nhất khi Việt hoá");

        Map<String, Object> blob = find(rows, "save.bin");
        eq("Dữ liệu", Json.string(blob, "kindName", ""), "còn lại thì nhận là dữ liệu");
    }

    // ------------------------------------------------------------- tự thay

    /**
     * Thay một tấm ảnh, và game nhìn thấy tấm mới.
     *
     * <p>Cái đáng kiểm không phải là "có lưu được tệp không", mà là <b>game
     * đọc được gì</b> khi nó chạy: một bản mod lưu đúng chỗ nhưng không phủ
     * lên game thì cũng vô dụng.</p>
     */
    private void swappingAFile() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        String suiteId = install(facade);

        byte[] original = facade.resource(suiteId, "icon.png");
        check(original.length > 0, "ảnh gốc có sẵn trong game");

        byte[] mine = PngWriter.encode(new int[]{0xFF102030, 0xFF102030,
                0xFF102030, 0xFF102030}, 2, 2);
        Map<String, Object> swapped = Json.readObject(
                facade.replaceResource(suiteId, "icon.png", mine));
        check(Json.bool(swapped, "ok", false),
                "thay được: " + Json.string(swapped, "error", ""));

        byte[] played = facade.resourceAsPlayed(suiteId, "icon.png");
        eq(mine.length, played.length, "game sẽ đọc thấy tấm mới");
        check(played[0] == mine[0] && played[played.length - 1] == mine[mine.length - 1],
                "đúng từng byte một");
        eq(original.length, facade.resource(suiteId, "icon.png").length,
                "còn bản gốc trong tệp game thì không bị đụng tới");

        Map<String, Object> box = Json.readObject(facade.resourcesJson(suiteId));
        Map<String, Object> icon = find(Json.array(box, "resources"), "icon.png");
        check(Json.bool(icon, "replaced", false), "bảng đánh dấu là đã thay");
        eq("Của tôi", Json.string(icon, "replacedBy", ""), "và nói rõ ai thay");
        eq(1, Json.array(box, "replaced").size(), "danh sách những gì mình đã thay");

        // Thay tệp thứ hai: cả hai cùng nằm trong một bản mod, không đè nhau.
        facade.replaceResource(suiteId, "res/level1.dat", new byte[]{1, 2, 3});
        eq(2, Json.array(Json.readObject(facade.resourcesJson(suiteId)), "replaced").size(),
                "thay thêm tệp nữa thì tệp trước vẫn còn");

        // Đổi ý: trả về bản gốc.
        check(Json.bool(Json.readObject(facade.restoreResource(suiteId, "icon.png")), "ok", false),
                "bỏ được thứ đã thay");
        eq(original.length, facade.resourceAsPlayed(suiteId, "icon.png").length,
                "và game đọc lại đúng ảnh gốc");
        eq(1, Json.array(Json.readObject(facade.resourcesJson(suiteId)), "replaced").size(),
                "tệp còn lại vẫn được giữ");

        // Thay một tệp game không có thì nói thẳng, chứ không lặng lẽ thêm
        // một tệp mà game chẳng bao giờ đọc tới.
        check(!Json.bool(Json.readObject(
                        facade.replaceResource(suiteId, "khong/co/that.png", mine)), "ok", true),
                "thay một tệp game không có thì bị từ chối");

        // Và không cho ghi ra ngoài phạm vi của gói.
        check(!Json.bool(Json.readObject(
                        facade.replaceResource(suiteId, "../../thoat.png", mine)), "ok", true),
                "đường dẫn leo ra ngoài cũng bị từ chối");
    }

    // ------------------------------------------------------------ tiện ích

    private String install(MobiCoreFacade facade) throws Exception {
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        return Json.string(Json.child(imported, "game"), "suiteId", "");
    }

    private Map<String, Object> find(List<Object> rows, String path) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = (Map<String, Object>) rows.get(i);
            if (path.equals(Json.string(row, "path", ""))) {
                return row;
            }
        }
        check(false, "bảng phải có dòng cho " + path);
        return Json.object();
    }
}
