package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Nửa còn lại của {@code RecordEnumeration}, và cái tai của {@code RecordStore}.
 *
 * <p>Đọc một kho bản ghi từ đầu tới cuối cần bốn hàm, và máy ảo có đủ bốn. Mọi
 * thứ một bảng điểm thật sự làm thì cần bảy hàm còn lại — cuộn ngược lên
 * ({@code previousRecord}), nhảy tới một hàng ({@code getRecordId}), và không
 * nói dối khi có điểm mới được ghi trong lúc bảng đang mở ({@code rebuild},
 * {@code keepUpdated}) — cộng thêm việc nghe được rằng có thay đổi
 * ({@code RecordListener}). Cả bảy đều **thiếu hẳn**, nghĩa là game gọi tới là
 * dừng ngay tại dòng đó.</p>
 *
 * <p>Và {@code Timer.schedule(TimerTask, Date)} cũng vậy: game muốn một việc
 * xảy ra *lúc* nào đó chứ không phải *sau* bao lâu nữa thì gọi bản này, và gọi
 * bản kia không thay được vì bản kia nhận một thứ khác hẳn.</p>
 */
public final class RecordsTest extends Test {

    private final String fixtureDir;

    public RecordsTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Bảng điểm: duyệt ngược, dựng lại, và nghe";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/ScoreBoard.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        EmulatorSession session = boot("demo.ScoreBoard");
        walkingBothWays(session);
        jumpingToARow(session);
        catchingUpWhileOpen(session);
        theFilterSurvivesARebuild(session);
        listening(session);
        session.destroy();

        timerAtAMoment();
    }

    private EmulatorSession boot(String midlet) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.start(midlet);
        return session;
    }

    /**
     * Forwards to the end and back to the start.
     *
     * <p>The cursor sits between two records, so walking back is not walking
     * forward and subtracting one: coming back must land on the same rows in
     * the opposite order, and land on all of them.</p>
     */
    private void walkingBothWays(EmulatorSession session) {
        begin(session, "10,20,30");
        eq("102030|302010", call(session, "bothWays"),
                "forwards then backwards covers the same rows, reversed");

        begin(session, "");
        eq("|", call(session, "bothWays"), "an empty table walks both ways without complaining");
    }

    /** A row by its place in the order, without walking there. */
    private void jumpingToARow(EmulatorSession session) {
        begin(session, "10,20,30");
        eq("10", callInt(session, "rowAt", 0), "the first row");
        eq("30", callInt(session, "rowAt", 2), "and the last");
    }

    /**
     * The question a table has to get right: a score arrives while it is open.
     *
     * <p>Three answers, and they must differ. Left alone, the enumeration is
     * the list from when it was built. Asked to rebuild, it catches up. Told
     * to keep itself updated, it was already right.</p>
     */
    private void catchingUpWhileOpen(EmulatorSession session) {
        begin(session, "10,20,30");
        eq("3|không", whileOpen(session, false, false, "40"),
                "left alone, the table is the one from when it was opened");

        begin(session, "10,20,30");
        eq("4|không", whileOpen(session, false, true, "40"),
                "rebuild catches it up");

        begin(session, "10,20,30");
        eq("4|giữ", whileOpen(session, true, false, "40"),
                "and keepUpdated means it never fell behind in the first place");
    }

    /** A rebuilt enumeration asks the same question, not a simpler one. */
    private void theFilterSurvivesARebuild(EmulatorSession session) {
        begin(session, "12,25,13,20");
        eq("1312", callString(session, "filtered", "1"),
                "the filter picks the ones starting with 1, the comparator orders them");
        eq("2520", callString(session, "filtered", "2"), "and likewise for 2");
    }

    /** The listener hears every kind of change, and hears the record id. */
    private void listening(EmulatorSession session) {
        begin(session, "10,20");
        call(session, "listen");
        eq("0,0,0,0", call(session, "heard"), "nothing has happened yet");

        callString(session, "add", "30");
        eq("1,0,0,3", call(session, "heard"), "an added record, and which one");

        callInt2(session, "change", 1, "99");
        eq("1,1,0,1", call(session, "heard"), "a changed record");

        callInt(session, "remove", 2);
        eq("1,1,1,2", call(session, "heard"), "and a deleted one");

        // Removing a listener that was never added is a no-op, not a crash:
        // MIDlets do this on the way out without checking.
        call(session, "stopListening");
        callString(session, "add", "40");
        eq("2,1,1,4", call(session, "heard"), "the real listener is still listening");
    }

    /**
     * {@code Timer.schedule(TimerTask, Date)} — a moment, not a delay.
     *
     * <p>Driven straight through the emulated API rather than through a
     * fixture, because what is being checked is that the overload exists at
     * all and that a moment already past runs at once rather than being
     * refused.</p>
     */
    private void timerAtAMoment() throws Exception {
        EmulatorSession session = boot("demo.TimerDemo");
        Vm vm = session.vm();
        check(vm.loadClass("java/util/Timer").findMethod("schedule",
                        "(Ljava/util/TimerTask;Ljava/util/Date;)V") != null,
                "Timer takes a Date, which is how a game says when rather than how long");
        check(vm.loadClass("java/util/Timer").findMethod("schedule",
                        "(Ljava/util/TimerTask;Ljava/util/Date;J)V") != null,
                "and a repeating one from a moment too");
        session.destroy();
    }

    // ------------------------------------------------------------- plumbing

    private void begin(EmulatorSession session, String scores) {
        Vm vm = session.vm();
        vm.callVirtual(session.context().midlet(), "begin", "(Ljava/lang/String;)V",
                vm.newString(scores));
    }

    private String call(EmulatorSession session, String method) {
        Vm vm = session.vm();
        Object answer = vm.callVirtual(session.context().midlet(), method,
                method.equals("listen") || method.equals("stopListening")
                        ? "()V" : "()Ljava/lang/String;");
        return answer == null ? "" : vm.stringOf(answer);
    }

    private String callInt(EmulatorSession session, String method, int value) {
        Vm vm = session.vm();
        Object answer = vm.callVirtual(session.context().midlet(), method,
                method.equals("remove") ? "(I)V" : "(I)Ljava/lang/String;",
                Integer.valueOf(value));
        return answer == null ? "" : vm.stringOf(answer);
    }

    private String callString(EmulatorSession session, String method, String value) {
        Vm vm = session.vm();
        Object answer = vm.callVirtual(session.context().midlet(), method,
                method.equals("add") ? "(Ljava/lang/String;)V"
                        : "(Ljava/lang/String;)Ljava/lang/String;",
                vm.newString(value));
        return answer == null ? "" : vm.stringOf(answer);
    }

    private void callInt2(EmulatorSession session, String method, int id, String value) {
        Vm vm = session.vm();
        vm.callVirtual(session.context().midlet(), method, "(ILjava/lang/String;)V",
                Integer.valueOf(id), vm.newString(value));
    }

    private String whileOpen(EmulatorSession session, boolean kept, boolean rebuilt, String score) {
        Vm vm = session.vm();
        return vm.stringOf(vm.callVirtual(session.context().midlet(), "whileOpen",
                "(ZZLjava/lang/String;)Ljava/lang/String;",
                Integer.valueOf(kept ? 1 : 0), Integer.valueOf(rebuilt ? 1 : 0),
                vm.newString(score)));
    }
}
