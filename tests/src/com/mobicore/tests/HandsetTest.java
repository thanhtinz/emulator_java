package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.emu.SystemProperties;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.List;
import java.util.Map;

/**
 * Máy ảo khai nó là máy gì.
 *
 * <p>Game J2ME hỏi nó đang chạy trên máy nào rồi đổi cách chạy theo câu trả
 * lời: bộ ảnh nào, nhánh vẽ nào, mã phím nào. Câu trả lời cũ — "MobiCore" —
 * là một cái tên chưa game nào từng nghe, nên game rơi vào nhánh dành cho máy
 * lạ.</p>
 *
 * <p>Câu trả lời mới là <b>một câu duy nhất cho mọi game</b>: máy ảo này là
 * một cỗ máy, không có bản nào khác và không có tủ chọn máy. Bài kiểm tra này
 * giữ cho câu ấy là một chiếc điện thoại thật, và giữ cho nó chỉ khai những
 * thứ máy ảo thật sự có.</p>
 */
public final class HandsetTest extends Test {

    private final String fixtureDir;

    public HandsetTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Máy ảo khai nó là máy gì";
    }

    @Override
    public void run() throws Exception {
        theTable();
        whatTheGameReads();
        throughTheBridge();
    }

    // ---------------------------------------------------------------- bảng

    private void theTable() {
        eq("Nokia6233/05.10", SystemProperties.value("microedition.platform"),
                "máy ảo khai tên một chiếc máy thật, không phải tên của chính nó");

        // Chỉ khai những thứ thật sự có. Một chiếc máy khai có 3D rồi để game
        // gọi vào 3D là một chiếc máy nói dối, và game chết ở câu gọi sau.
        eq(null, SystemProperties.value("microedition.m3g.version"),
                "không khai 3D, vì máy ảo không có 3D");
        eq(null, SystemProperties.value("microedition.pim.version"), "cũng không khai danh bạ");
        eq("1.0", SystemProperties.value("microedition.io.file.FileConnection.version"),
                "nhưng khai phần tệp, vì phần tệp thì có thật");
        eq("CLDC-1.1", SystemProperties.value("microedition.configuration"),
                "và khai đúng đời máy ảo");
        eq("MIDP-2.0", SystemProperties.value("microedition.profiles"), "cùng đời hồ sơ");
        eq("ISO-8859-1", SystemProperties.value("microedition.encoding"),
                "bảng mã của máy đời ấy, không phải bảng mã của máy tính hôm nay");
        eq(null, SystemProperties.value("chuyện.trời.ơi"),
                "hỏi cái không có thì trả về không có gì");
        eq(null, SystemProperties.value(null), "hỏi trống cũng vậy, chứ không nổ");

        List<Object> rows = SystemProperties.toJson();
        check(rows.size() > 5, "cả bảng bày ra được, để màn hình thông tin đọc");
        Map<String, Object> first = (Map<String, Object>) rows.get(0);
        eq("microedition.platform", Json.string(first, "name", ""),
                "dòng đầu là dòng game hỏi nhiều nhất");
        eq(SystemProperties.PLATFORM, Json.string(first, "value", ""),
                "và giá trị khớp với thứ game đọc được");
    }

    // ------------------------------------------------- game đọc thấy gì

    /**
     * Chỗ duy nhất thật sự tính: cái game đọc được khi nó hỏi.
     *
     * <p>Bản mẫu {@code demo.DeviceDemo} hỏi đúng câu game đời ấy hỏi và rẽ
     * nhánh theo câu trả lời, nên chỗ này chạy bytecode thật chứ không đọc
     * lại cái bảng vừa dựng ra.</p>
     */
    private void whatTheGameReads() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(
                suite, GameProfile.defaultsFor(suite.info()), null, null, null);
        session.start("demo.DeviceDemo");

        eq("Nokia6233/05.10",
                (String) session.vm().host().property("microedition.platform"),
                "game hỏi máy nào thì nghe thấy một chiếc Nokia");
        eq(null, (String) session.vm().host().property("microedition.m3g.version"),
                "hỏi 3D thì nghe thấy là không có");

        // Một game rẽ nhánh theo tên máy: nhánh Nokia phải là nhánh nó vào.
        session.renderFrame();
        check(drawsNokiaBranch(session), "và game rẽ vào nhánh dành cho Nokia");
        session.destroy();
    }

    /**
     * True khi khung hình đang là nhánh Nokia.
     *
     * <p>Bản mẫu viết dòng tiêu đề bằng màu xanh khi nó tin mình đang ở trên
     * máy Nokia, nên câu hỏi "nó rẽ nhánh nào" trả lời được bằng cách nhìn
     * đúng thứ người chơi nhìn: các điểm ảnh.</p>
     */
    private boolean drawsNokiaBranch(EmulatorSession session) {
        int[] pixels = session.screen().pixels();
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] & 0xFFFFFF) == 0x7FD962) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------- cầu nối

    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> shown = Json.readObject(facade.systemPropertiesJson());
        eq(SystemProperties.PLATFORM, Json.string(shown, "platform", ""),
                "màn hình bày ra đúng chuỗi game đọc");
        check(Json.array(shown, "properties").size() > 5,
                "cùng cả bảng, vì đó là thứ cần nhìn khi một game chạy sai");

        // Không cần thư viện, không cần game: bảng này là của máy ảo, không
        // phải của một bộ cài nào.
        check(Json.bool(shown, "ok", false), "và đọc được cả khi chưa có game nào");
    }
}
