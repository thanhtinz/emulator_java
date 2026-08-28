package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.Map;

/**
 * {@code serviceRepaints} phải vẽ ngay, vì game viết dựa vào lời hứa đó.
 *
 * <p>Hầu hết game J2ME tự chạy vòng lặp của mình, và vòng lặp ấy luôn cùng một
 * hình: tính toán, gọi {@code repaint()}, rồi gọi {@code serviceRepaints()} và
 * <em>đợi khung hình vẽ xong</em> trước khi tính bước tiếp theo. MIDP nói rõ
 * hàm ấy chặn lại cho tới khi vẽ xong.</p>
 *
 * <p>Bỏ trống nó thì game vẫn chạy — nên lỗi này rất dễ lọt — nhưng nhịp của
 * game không còn là nhịp nó tự đặt: nó tính hàng trăm bước giữa hai khung
 * hình, và người chơi thấy nhân vật nhảy cóc.</p>
 */
public final class RepaintTest extends Test {

    private final String fixtureDir;

    public RepaintTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Vẽ ngay khi game bảo vẽ";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/LoopDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");
        Map<String, Object> started = Json.readObject(facade.startGame(suiteId, "demo.LoopDemo"));
        check(Json.bool(started, "ok", false),
                "game mở được: " + Json.string(started, "error", ""));
        VmObject game = facade.session().context().midlet();

        // Khung hình đầu tiên do vòng lặp của máy ảo vẽ.
        facade.renderFrame();
        int first = painted(game);
        check(first > 0, "khung hình đầu được vẽ");

        // Rồi game tự chạy vòng lặp của nó, không nhờ tới vòng lặp máy ảo.
        for (int i = 0; i < 5; i++) {
            facade.session().vm().callVirtual(game, "step", "()V");
        }
        eq(5, asked(game), "game xin vẽ năm lần");
        eq(first + 5, painted(game), "và được vẽ đúng năm lần, ngay lúc nó xin");
        check(flag(game, "paintedInTime"),
                "khung hình vẽ xong trước khi serviceRepaints trả về — đúng lời hứa của MIDP");

        // Khung hình game tự vẽ vẫn phải được đưa lên màn hình: vòng lặp máy
        // ảo không thấy ai xin vẽ, nhưng màn hình thì đã đổi.
        check(facade.renderFrame(),
                "khung game tự vẽ vẫn được đưa lên màn hình");
        check(!facade.renderFrame(),
                "còn khi không có gì mới thì không đưa lên nữa");

        // Gọi lồng: game gọi serviceRepaints từ trong chính paint của nó. Có
        // thật, và vẽ tiếp ở đó là gọi đệ quy không đáy.
        int before = painted(game);
        facade.session().vm().callVirtual(game, "stepReentrant", "()V");
        eq(before + 1, painted(game),
                "gọi lồng chỉ vẽ một lần, không rơi vào đệ quy");

        facade.stopGame();
    }

    private int painted(VmObject game) {
        return ((Integer) game.get("painted")).intValue();
    }

    private int asked(VmObject game) {
        return ((Integer) game.get("asked")).intValue();
    }

    private boolean flag(VmObject game, String field) {
        Object value = game.get(field);
        return value instanceof Integer
                ? ((Integer) value).intValue() != 0
                : Boolean.TRUE.equals(value);
    }
}
