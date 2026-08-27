package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.CrashDiagnosis;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.vm.VmCancelled;
import com.mobicore.tools.SampleSuite;

import java.util.Map;

/**
 * Game treo thì vẫn thoát ra được.
 *
 * <p>Game J2ME viết vòng lặp của chính nó, và một vòng lặp không có lối ra là
 * chuyện thường trong đám game viết cho đúng một đời máy. Trước đây luồng chạy
 * game kẹt trong đó mãi mãi: màn hình đứng im, không nút nào bấm được, và cách
 * duy nhất thoát ra là tắt hẳn ứng dụng — người chơi mất luôn cả phần chưa
 * lưu.</p>
 *
 * <p>Hai lối ra, và cả hai đều phải chạy được từ trong một vòng lặp vô tận:
 * hết giờ chờ thì máy ảo tự cắt, còn người chơi bấm thoát thì cắt ngay.</p>
 */
public final class HangTest extends Test {

    private final String fixtureDir;

    public HangTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Game treo thì thoát được";
    }

    @Override
    public void run() throws Exception {
        cutShort();
        theExitButton();
        aGameThatIsMerelyBusy();
    }

    // ------------------------------------------------------ hết giờ thì cắt

    private void cutShort() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        Map<String, Object> started = Json.readObject(facade.startGame(suiteId, "demo.HangDemo"));
        check(Json.bool(started, "ok", false),
                "game mở được: " + Json.string(started, "error", ""));

        // Hạn thật là tám giây, rộng tay vì máy ảo dịch từng lệnh. Ở đây rút
        // xuống một phần tư giây: cái cần kiểm là nó có cắt hay không, chứ
        // không phải ngồi đợi cho đủ tám giây.
        facade.session().vm().setStuckAfterMs(250);

        long before = System.currentTimeMillis();
        facade.renderFrame();
        long waited = System.currentTimeMillis() - before;
        check(waited < 5000, "khung hình treo bị cắt chứ không kẹt mãi (" + waited + "ms)");

        Map<String, Object> crash = Json.readObject(facade.crashJson());
        check(Json.bool(crash, "has", false), "và được ghi lại như một lần hỏng");
        eq(CrashDiagnosis.KIND_HANG, Json.integer(crash, "kind", -1),
                "đọc ra đúng là treo, không phải một lỗi nào khác");
        check(Json.string(crash, "reason", "").indexOf("không vẽ xong") >= 0,
                "và nói bằng lời: " + Json.string(crash, "reason", ""));
        check(Json.string(crash, "technical", "").indexOf("HangDemo") >= 0,
                "kèm chỗ nó kẹt, cho người sửa game");

        facade.dismissCrash();
    }

    // ---------------------------------------------------- nút thoát vẫn ăn

    /**
     * Người chơi bấm thoát trong lúc game đang kẹt.
     *
     * <p>Đây mới là lối ra hay dùng: không ai ngồi đợi cho hết giờ chờ. Lệnh
     * dừng đến từ luồng khác, nên nó phải xuyên được vào giữa một vòng lặp
     * đang chạy.</p>
     */
    private void theExitButton() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        final EmulatorSession session = EmulatorSession.create(
                suite, GameProfile.defaultsFor(suite.info()), null, null, null);
        session.start("demo.HangDemo");
        // Không có hạn giờ: chỗ này kiểm đúng một chuyện, là lệnh dừng.
        session.vm().setStuckAfterMs(0);

        final boolean[] cancelled = {false};
        Thread player = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                session.requestStop();
            }
        });
        player.start();

        long before = System.currentTimeMillis();
        try {
            session.renderFrame();
        } catch (VmCancelled stopped) {
            cancelled[0] = true;
        }
        long waited = System.currentTimeMillis() - before;
        player.join();

        check(cancelled[0], "lệnh dừng xuyên được vào giữa vòng lặp của game");
        check(waited < 5000, "và dừng ngay chứ không đợi hết giờ chờ (" + waited + "ms)");

        // Dọn dẹp vẫn phải chạy: destroyApp và việc ghi nốt phần lưu cũng là
        // mã chạy trong máy ảo, nên lệnh dừng phải được gỡ trước.
        session.destroy();
        check(!session.vm().isCancelled(), "dừng xong thì lệnh dừng được gỡ để còn dọn dẹp");
    }

    // ------------------------------------------- game chạy lâu chứ không treo

    /**
     * Cắt nhầm một game đang chạy đúng thì tệ hơn là đợi thêm.
     *
     * <p>Bản mẫu thật vẽ một khung hình mất vài phần nghìn giây; với hạn tám
     * giây thì nó phải chạy xong bình thường, không lần nào bị coi là treo.</p>
     */
    private void aGameThatIsMerelyBusy() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");
        facade.startGame(suiteId);
        eq(8000L, facade.session().vm().stuckAfterMs(),
                "hạn mặc định rộng tay, vì máy ảo dịch từng lệnh");
        for (int i = 0; i < 30; i++) {
            facade.renderFrame();
        }
        check(!Json.bool(Json.readObject(facade.crashJson()), "has", true),
                "một game chạy đúng không bao giờ bị coi là treo");
        facade.stopGame();
    }
}
