package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.storage.Json;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.util.List;
import java.util.Map;

/**
 * Game chạy vòng lặp trên luồng riêng của nó.
 *
 * <p>Gần như game J2ME nào cũng có hình dạng này: Canvas mở một Thread, luồng
 * ấy chạy tới khi được bảo dừng, và hai bên nói chuyện với nhau qua một cái
 * khoá — bên này {@code wait}, bên kia {@code notify}. Mọi thứ bộ này kiểm
 * đều là thứ cái vòng lặp ấy cần đúng thì mới chạy được.</p>
 */
public final class ThreadTest extends Test {

    private final String fixtureDir;

    public ThreadTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Vòng lặp trên luồng riêng";
    }

    @Override
    public void run() throws Exception {
        theLoopThread();
        waitMeansWait();
        aHandOffThatMustNotSeizeUp();
        aThreadThatHangs();
        aThreadThatDies();
        theThreadTable();
        theBridgeTable();
    }

    /** Cầu nối đưa bảng luồng sang được cho ứng dụng Android và iOS. */
    private void theBridgeTable() throws Exception {
        com.mobicore.core.bridge.MobiCoreFacade facade =
                new com.mobicore.core.bridge.MobiCoreFacade(
                        new com.mobicore.core.storage.MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");
        Json.readObject(facade.startGame(suiteId, "demo.ThreadDemo"));

        Map<String, Object> table = Json.readObject(facade.threadsJson());
        check(Json.bool(table, "ok", false), "cầu nối trả lời được bảng luồng");
        List<Object> rows = Json.array(table, "threads");
        check(rows.size() >= 1, "bảng luồng qua cầu nối có ít nhất luồng chạy MIDlet");
        Map<String, Object> first = (Map<String, Object>) rows.get(0);
        eq("chính", Json.string(first, "name", ""), "luồng chạy MIDlet có tên trong bảng");
        check(!Json.bool(first, "own", true),
                "luồng chạy MIDlet không bị kể là luồng game tự mở");
        check(Json.bool(first, "alive", false), "luồng chạy MIDlet đang sống");
    }

    private EmulatorSession boot(String midlet) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.start(midlet);
        return session;
    }

    // ------------------------------------------------- luồng game là chính nó

    /**
     * Luồng game mở ra phải là đúng cái luồng nó hỏi lại được.
     *
     * <p>{@code currentThread()} từng dựng một đối tượng mới mỗi lần gọi, nên
     * mọi câu hỏi về luồng đều trả lời sai mà không kêu lên tiếng nào: so
     * sánh nào cũng không bằng, tên nào cũng rỗng.</p>
     */
    private void theLoopThread() throws Exception {
        EmulatorSession session = boot("demo.ThreadDemo");
        VmObject game = session.context().midlet();
        eq("chính", text(session, session.vm().callVirtual(game, "mainName",
                "()Ljava/lang/String;")), "luồng chạy MIDlet có tên");

        session.vm().callVirtual(game, "play", "()V");
        eq(8, ((Integer) game.get("turns")).intValue(), "vòng lặp chạy đủ số vòng của nó");
        check(((Integer) game.get("sawItself")).intValue() != 0,
                "luồng nhận ra chính nó: currentThread() trả về đúng đối tượng đã mở");
        eq("vòng lặp game", text(session, game.get("nameInside")),
                "tên game đặt cho luồng đọc lại được từ bên trong nó");
        eq(2, ((Integer) game.get("aliveDuring")).intValue(),
                "đếm được hai luồng: luồng chạy MIDlet và vòng lặp game");
        check(((Integer) game.get("woken")).intValue() != 0,
                "bên đợi được đánh thức bằng notify, không phải bằng hết giờ");
        eq(8, ((Integer) session.vm().callVirtual(game, "shared", "()I")).intValue(),
                "hàm synchronized không đánh rơi lần đếm nào");
    }

    // ----------------------------------------------------- đợi là phải đợi

    /**
     * {@code wait} chỉ dậy khi được báo, không dậy vì cái khoá bận.
     *
     * <p>Chỗ nằm đợi của {@code wait} từng dùng chung với chỗ giành khoá, nên
     * hễ có luồng nào nhả khoá là bên đang đợi tỉnh dậy như thể vừa được báo.
     * Với hai trăm lần chạm khoá thì bên đợi dậy một trăm chín mươi chín lần —
     * và một vòng lặp game viết theo lối đợi-báo chạy loạn hết cả.</p>
     */
    private void waitMeansWait() throws Exception {
        EmulatorSession session = boot("demo.ThreadDemo");
        VmObject game = session.context().midlet();
        session.vm().callVirtual(game, "waitToBeTold", "()V");
        eq(200, ((Integer) game.get("knocks")).intValue(),
                "bên kia đã chạm vào khoá đủ hai trăm lần");
        int wakeups = ((Integer) game.get("wakeups")).intValue();
        check(wakeups >= 1 && wakeups <= 3,
                "bên đợi dậy vì được báo chứ không vì khoá bận: dậy " + wakeups + " lần");
    }

    /**
     * Hai bên chuyền nhau hai trăm món qua một cái khoá, không được kẹt.
     *
     * <p>Chỗ lấy lại khoá sau khi thôi đợi từng nằm <em>bên trong</em> hàng
     * đợi: luồng đang đợi giữ hàng đợi rồi chờ khoá, còn luồng đang giữ khoá
     * lại chờ hàng đợi để báo — hai bên đứng im nhìn nhau. Và chó canh tám
     * giây cũng không sủa được, vì không luồng nào chạy lệnh nào để nó ngó
     * tới.</p>
     *
     * <p>Một lần chuyền thì hiếm khi trúng khe ấy, nên chỗ này chuyền hai
     * trăm lần. Chạy trên một luồng riêng có hạn giờ, để nếu kẹt thì bộ kiểm
     * báo hỏng chứ không đứng luôn.</p>
     */
    private void aHandOffThatMustNotSeizeUp() throws Exception {
        final EmulatorSession session = boot("demo.ThreadDemo");
        final VmObject game = session.context().midlet();
        final Throwable[] broke = new Throwable[1];
        Thread runner = new Thread(new Runnable() {
            public void run() {
                try {
                    session.vm().callVirtual(game, "handOff", "()V");
                } catch (Throwable t) {
                    broke[0] = t;
                }
            }
        }, "chuyền tay");
        runner.setDaemon(true);
        runner.start();
        runner.join(20000);
        check(!runner.isAlive(), "hai bên chuyền được hai trăm món mà không kẹt khoá");
        eq(null, broke[0] == null ? null : String.valueOf(broke[0]),
                "không bên nào ném ra lỗi giữa chừng");
        if (!runner.isAlive()) {
            eq(200, ((Integer) game.get("handedOver")).intValue(),
                    "bên nhận nhận đủ hai trăm món");
        }
    }

    // ------------------------------------------------- treo trên luồng riêng

    /**
     * Vòng lặp treo trên luồng riêng vẫn bị cắt, và nói rõ luồng nào.
     *
     * <p>Đồng hồ "người chơi đợi bao lâu" từng dùng chung cho mọi luồng: chỉ
     * cần một luồng phụ gọi vào máy ảo là đồng hồ bị đặt lại, và luồng đang
     * treo không bao giờ bị bắt. Game nào cũng có luồng phụ, nên chỗ này
     * hỏng đúng vào lúc nó cần nhất.</p>
     */
    private void aThreadThatHangs() throws Exception {
        EmulatorSession session = boot("demo.ThreadDemo");
        session.vm().setStuckAfterMs(600);
        session.vm().callVirtual(session.context().midlet(), "hangOnThread", "()V");
        // Vừa đợi vừa gọi vào máy ảo, đúng như máy ảo vẫn làm mỗi khung hình.
        // Đồng hồ chờ dùng chung cho mọi luồng thì mấy lời gọi này đặt lại nó
        // liên tục, và luồng đang treo không bao giờ bị bắt.
        Throwable failure = waitForFailure(session, 6000, true);
        check(failure != null, "vòng lặp treo trên luồng riêng bị cắt, không treo mãi");
        String why = failure == null ? "" : String.valueOf(failure.getMessage());
        check(why.startsWith("Game không phản hồi"), "nói rõ là game không phản hồi: " + why);
        check(why.indexOf("vòng lặp game") >= 0,
                "gọi đúng tên luồng đang kẹt, vì hàm ấy có thể vẫn chạy tốt ở luồng khác: " + why);
    }

    /**
     * Luồng game chết thì người chơi phải nghe thấy.
     *
     * <p>Trước đây lỗi trên luồng riêng chỉ được ghi vào nhật ký: màn hình
     * đứng im, mọi nút vẫn bấm được, và không có gì xảy ra nữa.</p>
     */
    private void aThreadThatDies() throws Exception {
        EmulatorSession session = boot("demo.ThreadDemo");
        session.vm().callVirtual(session.context().midlet(), "breakOnThread", "()V");
        Throwable failure = waitForFailure(session, 4000, false);
        check(failure != null, "luồng game chết thì máy ảo giữ lại chỗ hỏng để kể lại");
    }

    private Throwable waitForFailure(EmulatorSession session, long limitMs, boolean busy)
            throws Exception {
        long until = System.currentTimeMillis() + limitMs;
        while (System.currentTimeMillis() < until) {
            Throwable failure = session.vm().threadFailure();
            if (failure != null) {
                return failure;
            }
            if (busy) {
                session.vm().callVirtual(session.context().midlet(), "pauseApp", "()V");
            }
            Thread.sleep(20);
        }
        return null;
    }

    // ------------------------------------------------------- bảng luồng

    /** Bảng luồng nói được luồng nào đang ở trong hàm nào. */
    private void theThreadTable() throws Exception {
        EmulatorSession session = boot("demo.ThreadDemo");
        session.vm().callVirtual(session.context().midlet(), "hangOnThread", "()V");
        // Đợi cho luồng kịp vào vòng lặp của nó.
        Thread.sleep(120);
        List<Object[]> live = session.vm().threads().snapshot();
        boolean sawMain = false;
        boolean sawLoop = false;
        String inside = "";
        for (int i = 0; i < live.size(); i++) {
            Thread host = (Thread) live.get(i)[0];
            VmObject thread = (VmObject) live.get(i)[1];
            String name = text(session, thread.get("name"));
            if ("chính".equals(name)) {
                sawMain = true;
                check(!session.vm().threads().startedByGame(host),
                        "luồng chạy MIDlet không bị kể là luồng game tự mở");
            } else if ("vòng lặp game".equals(name)) {
                sawLoop = true;
                inside = session.vm().interpreter().topFrameOf(host);
                check(session.vm().threads().startedByGame(host),
                        "luồng game tự mở được đánh dấu là của game");
            }
        }
        check(sawMain, "bảng có luồng chạy MIDlet");
        check(sawLoop, "bảng có vòng lặp game");
        check(inside.indexOf("run") >= 0,
                "bảng nói được vòng lặp đang ở trong hàm nào: " + inside);
        session.vm().setCancelled(true);
        Thread.sleep(60);
        session.vm().setCancelled(false);
    }

    private String text(EmulatorSession session, Object reference) {
        return reference == null ? "" : session.vm().stringOf(reference);
    }
}
