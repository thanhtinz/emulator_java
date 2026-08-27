package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.CrashDiagnosis;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.Map;

/**
 * Khi game hỏng, nói rõ vì sao.
 *
 * <p>Cái mà người chơi gặp xưa nay là màn hình đen: game tắt, không một chữ
 * nào. Máy ảo thì biết thừa lý do — nó vừa bắt được ngoại lệ — nhưng lý do ấy
 * là một tên lớp tiếng Anh nói về bên trong máy. Bài kiểm tra này giữ cho
 * đường từ ngoại lệ đến câu tiếng Việt luôn thông, và giữ cho câu ấy nói đúng
 * việc phải làm.</p>
 */
public final class CrashTest extends Test {

    private final String fixtureDir;

    public CrashTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Game hỏng thì nói vì sao";
    }

    @Override
    public void run() throws Exception {
        reading();
        thePlayersJarIsIncomplete();
        aGameThatDiesWhileDrawing();
    }

    // --------------------------------------------------------------- đọc lỗi

    /**
     * Mỗi kiểu hỏng một câu khác nhau, vì việc phải làm cũng khác nhau.
     *
     * <p>"Tải lại tệp game" là lời khuyên đúng khi JAR thiếu lớp và là lời
     * khuyên vô ích khi máy ảo chưa làm 3D — nên hai chuyện đó không được
     * gộp lại thành một câu chung chung.</p>
     */
    private void reading() {
        CrashDiagnosis api = CrashDiagnosis.of("java.lang.NoClassDefFoundError",
                "javax/microedition/m3g/World");
        eq(CrashDiagnosis.KIND_MISSING_API, api.kind(),
                "lớp thiếu thuộc thư viện máy là phần máy ảo chưa làm");
        check(api.reason().indexOf("3D") >= 0,
                "và nói tên phần đó chứ không nói tên lớp: " + api.reason());
        check(!api.blamesGame(), "không phải lỗi của game");
        check(api.reason().indexOf("m3g") < 0,
                "người chơi không cần biết chữ m3g");

        CrashDiagnosis own = CrashDiagnosis.of("java.lang.NoClassDefFoundError",
                "demo/Editor");
        eq(CrashDiagnosis.KIND_MISSING_CLASS, own.kind(),
                "cùng một ngoại lệ, nhưng lớp của chính game thì là tệp thiếu mẩu");
        check(own.advice().indexOf("Tải lại") >= 0,
                "và ở đây tải lại đúng là việc phải làm: " + own.advice());

        eq(CrashDiagnosis.KIND_MEMORY,
                CrashDiagnosis.of("java.lang.OutOfMemoryError", "").kind(),
                "hết bộ nhớ là hết bộ nhớ");
        eq(CrashDiagnosis.KIND_NETWORK,
                CrashDiagnosis.of("javax.microedition.io.ConnectionNotFoundException",
                        "socket://game.example:9000").kind(),
                "không mở được kết nối là chuyện của mạng");
        eq(CrashDiagnosis.KIND_STORAGE,
                CrashDiagnosis.of("javax.microedition.rms.RecordStoreException",
                        "hỏng").kind(),
                "và phần lưu là chuyện của phần lưu");
        eq(CrashDiagnosis.KIND_MEDIA,
                CrashDiagnosis.of("javax.microedition.media.MediaException",
                        "audio/mpeg").kind(), "âm thanh cũng vậy");

        CrashDiagnosis bug = CrashDiagnosis.of("java.lang.NullPointerException", "");
        eq(CrashDiagnosis.KIND_GAME_BUG, bug.kind(), "còn lại là lỗi của chính game");
        check(bug.blamesGame(), "và nói thẳng như thế");
        check(bug.reason().indexOf("chưa được tạo ra") >= 0,
                "bằng lời chứ không bằng tên ngoại lệ: " + bug.reason());
        check(bug.technical().indexOf("NullPointerException") >= 0,
                "tên ngoại lệ vẫn giữ, nhưng để trong phần kỹ thuật");

        // Máy ảo tự gãy thì không được đổ cho game: một cái nút "tải lại tệp
        // game" ở đây là bắt người chơi đi sửa thứ không hỏng.
        CrashDiagnosis vm = CrashDiagnosis.of("", "Unsupported opcode 0xba");
        eq(CrashDiagnosis.KIND_EMULATOR, vm.kind(), "máy ảo gãy là máy ảo gãy");
        check(!vm.blamesGame(), "không đổ cho game");

        CrashDiagnosis odd = CrashDiagnosis.of("com.game.OwnException", "level 4");
        eq(CrashDiagnosis.KIND_UNKNOWN, odd.kind(),
                "và cái gì không biết thì nhận là không biết");
        check(odd.reason().indexOf("OwnException") >= 0,
                "vẫn nói ra được cái tên, còn hơn không nói gì");

        // Không câu nào được bỏ trống: một hộp thoại chỉ có tiêu đề thì cũng
        // vô dụng như màn hình đen.
        int[] kinds = {CrashDiagnosis.KIND_MISSING_API, CrashDiagnosis.KIND_MISSING_CLASS,
                CrashDiagnosis.KIND_MEMORY, CrashDiagnosis.KIND_NETWORK,
                CrashDiagnosis.KIND_STORAGE, CrashDiagnosis.KIND_MEDIA,
                CrashDiagnosis.KIND_GAME_BUG, CrashDiagnosis.KIND_EMULATOR,
                CrashDiagnosis.KIND_UNKNOWN};
        CrashDiagnosis[] all = {api, own,
                CrashDiagnosis.of("java.lang.OutOfMemoryError", ""),
                CrashDiagnosis.of("javax.microedition.io.ConnectionNotFoundException", "x"),
                CrashDiagnosis.of("javax.microedition.rms.RecordStoreException", "x"),
                CrashDiagnosis.of("javax.microedition.media.MediaException", "x"),
                bug, vm, odd};
        for (int i = 0; i < all.length; i++) {
            eq(kinds[i], all[i].kind(), "kiểu " + i + " đúng chỗ của nó");
            check(all[i].title().length() > 0, "kiểu " + i + " có tiêu đề");
            check(all[i].reason().length() > 0, "kiểu " + i + " nói vì sao");
            check(all[i].advice().length() > 0, "kiểu " + i + " nói làm gì tiếp");
        }
    }

    // ------------------------------------------------- tệp game thiếu một mẩu

    /**
     * Bản mẫu khai một MIDlet mà JAR không có — một tệp tải dở, đúng như
     * ngoài đời. Game không khởi động nổi, và chỗ đó phải nói ra được.
     */
    private void thePlayersJarIsIncomplete() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        String suiteId = install(facade);

        check(!Json.bool(Json.readObject(facade.crashJson()), "has", true),
                "chưa chơi thì chưa có gì hỏng");

        Map<String, Object> started = Json.readObject(facade.startGame(suiteId, "demo.Editor"));
        check(!Json.bool(started, "ok", true), "game thiếu lớp thì không mở được");
        check(Json.string(started, "error", "").indexOf("demo.Editor") >= 0,
                "và lời báo lỗi gọi đúng tên lớp còn thiếu: "
                        + Json.string(started, "error", ""));

        Map<String, Object> crash = Json.readObject(facade.crashJson());
        check(Json.bool(crash, "has", false), "lần hỏng được giữ lại");
        eq(CrashDiagnosis.KIND_MISSING_CLASS, Json.integer(crash, "kind", -1),
                "đọc ra đúng là tệp thiếu mẩu");
        eq(suiteId, Json.string(crash, "suiteId", ""), "biết là game nào");
        eq("Sky Runner", Json.string(crash, "game", ""), "và gọi nó bằng tên");
        check(Json.longValue(crash, "when", 0L) > 0, "và biết lúc nào");

        facade.dismissCrash();
        check(!Json.bool(Json.readObject(facade.crashJson()), "has", true),
                "đọc xong thì bỏ đi, chứ không đeo theo mãi");
    }

    // ------------------------------------------------ chết giữa lúc đang vẽ

    /**
     * Trường hợp thật sự khó chịu: game mở ra được, vẽ được, rồi chết.
     *
     * <p>Trước đây khung hình hỏng chỉ ghi một dòng vào nhật ký rồi thôi, nên
     * game đứng hình mà không ai biết vì sao — và mỗi giây lại hỏng thêm mấy
     * chục lần y hệt.</p>
     */
    private void aGameThatDiesWhileDrawing() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        String suiteId = install(facade);

        Map<String, Object> started = Json.readObject(facade.startGame(suiteId, "demo.CrashDemo"));
        check(Json.bool(started, "ok", false),
                "game mở được: " + Json.string(started, "error", ""));
        check(!Json.bool(Json.readObject(facade.crashJson()), "has", true),
                "mở xong vẫn chưa hỏng");

        facade.renderFrame();

        Map<String, Object> crash = Json.readObject(facade.crashJson());
        check(Json.bool(crash, "has", false), "khung hình hỏng được ghi lại");
        eq(CrashDiagnosis.KIND_GAME_BUG, Json.integer(crash, "kind", -1),
                "và đọc ra là lỗi của chính game");
        check(Json.array(crash, "stack").size() > 0,
                "kèm chỗ nó chết, để còn gửi báo lỗi");
        check(Json.string(crash, "technical", "").indexOf("NullPointerException") >= 0,
                "tên ngoại lệ thật vẫn còn nguyên trong phần kỹ thuật");

        // Không chạy tiếp: một game đã chết thì khung hình sau cũng chết y
        // như vậy, và ghi lại cùng một lỗi mỗi giây mấy chục lần.
        long when = Json.longValue(crash, "when", 0L);
        for (int i = 0; i < 30; i++) {
            check(!facade.renderFrame(), "khung hình sau không vẽ nữa");
        }
        eq(when, Json.longValue(Json.readObject(facade.crashJson()), "when", 0L),
                "và lần hỏng vẫn là lần hỏng đầu tiên, không bị ghi đè liên tục");

        facade.stopGame();
        check(Json.bool(Json.readObject(facade.crashJson()), "has", false),
                "tắt game rồi lời giải thích vẫn còn — màn hình báo lỗi hiện ra sau đó");
        facade.dismissCrash();
    }

    private String install(MobiCoreFacade facade) throws Exception {
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        return Json.string(Json.child(imported, "game"), "suiteId", "");
    }
}
