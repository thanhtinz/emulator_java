package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.midp.ScreenRenderer;
import com.mobicore.core.midp.SystemChrome;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.Vm;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Năm chỗ máy ảo **đánh mất** một thứ, và không nói gì cả.
 *
 * <p>Giai đoạn trước là những chỗ game chạy sai. Ở đây game chạy đúng, chỉ có
 * thứ nó giao cho máy ảo là không tới nơi: phần lưu không xuống đĩa, tiến trình
 * cả buổi bay mất lúc thoát, tấm vẽ kẹt lại ở phép tịnh tiến của một lớp, cú
 * chạm phía trên menu bấm nhầm mục đầu, và ngón tay nhấc ra ngoài khung thì
 * game không bao giờ biết là đã nhấc.</p>
 *
 * <p>Không cái nào làm game dừng, nên không cái nào bị ai báo. Đây là năm câu
 * hỏi làm chúng lộ ra.</p>
 */
public final class LossTest extends Test {

    private final String fixtureDir;

    public LossTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Năm chỗ mất dữ liệu và mất cú chạm";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/Losses.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        theSaveSurvivesAClosedHandle();
        theStoreIsOneStore();
        theSaveSurvivesAFailureOnTheWayOut();
        theCanvasIsHandedBack();
        theTapAboveTheMenuClosesIt();
        theLongMenuLandsOnTheRowYouSee();
        theGestureBelongsToTheGame();
    }

    private EmulatorSession boot() throws Exception {
        return boot(null);
    }

    private EmulatorSession boot(Vfs storage) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        profile.setDevice(com.mobicore.core.model.DeviceProfile.custom(240, 320));
        EmulatorSession session = EmulatorSession.create(suite, profile, storage,
                new StorageLayout("MobiCore"), new EmulatorScreen.FixedClock());
        session.start("demo.Losses");
        return session;
    }

    // ------------------------------------------------------------------ RMS

    /**
     * A write through the handle that is still open must reach storage.
     *
     * <p>MIDP gives both {@code openRecordStore} calls the same store, so
     * closing one of them is not closing the store. If the emulator forgets
     * the store on the first close, the write that follows still says it
     * succeeded and simply never happens: the player plays all evening and
     * loses the lot.</p>
     */
    private void theSaveSurvivesAClosedHandle() throws Exception {
        EmulatorSession session = boot();
        eq("9910", call(session, "writeAfterOneHandleCloses"),
                "the write through the open handle is there when the store is read afresh");
        session.destroy();
    }

    /** A handle opened later joins the store in hand, not a stale copy of it. */
    private void theStoreIsOneStore() throws Exception {
        EmulatorSession session = boot();
        eq("1:7", call(session, "thirdHandleSeesTheSameStore"),
                "a third handle sees what the second one just wrote");
        session.destroy();
    }

    /**
     * The game falls over on the way out; the save goes to storage anyway.
     *
     * <p>{@code destroyApp} is the last chance to write, and it is also a
     * place games are careless. Whatever it throws, the flush below it is not
     * its hostage — which is why that flush lives in a {@code finally}.</p>
     */
    private void theSaveSurvivesAFailureOnTheWayOut() throws Exception {
        Vfs storage = new MemoryVfs();
        EmulatorSession session = boot(storage);
        String suiteId = session.rms().suiteId();
        session.destroy();

        // Read it back through a manager that shares nothing with the session
        // but the storage itself: only what really reached the disk counts.
        RecordStoreManager fresh = new RecordStoreManager(storage,
                new StorageLayout("MobiCore"), suiteId);
        check(fresh.listStoreNames().contains("lucCuoi"),
                "the store opened on the way out reached storage even though destroyApp threw");
    }

    // --------------------------------------------------------------- canvas

    /**
     * A layer that throws must not leave the canvas translated and clipped.
     *
     * <p>A layer's {@code paint} is the game's own code, and game code
     * throws. If the manager only puts the canvas back after the loop, an
     * exception the game catches upstairs leaves every later drawing offset
     * and cut off for the rest of the level.</p>
     */
    private void theCanvasIsHandedBack() throws Exception {
        EmulatorSession session = boot();
        eq("nem:lớp này hỏng [12,16] 3,5 1,2 20x30", call(session, "canvasAfterALayerFails"),
                "the translation and the clip are the ones the game set, not the layer's");
        session.destroy();
    }

    // ----------------------------------------------------------------- menu

    /**
     * A tap above the menu closes it; it does not run the first item.
     *
     * <p>The old arithmetic divided a negative number by the row height, and
     * Java rounds that towards zero — so a tap anywhere in the row-high band
     * above the panel came out as row 0. Missing a menu ran a command.</p>
     */
    private void theTapAboveTheMenuClosesIt() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        MidpContext context = session.context();
        vm.callVirtual(session.context().midlet(), "showBoard", "()V");

        int row = ScreenRenderer.rowHeight();
        int count = context.menuCommands().size();
        check(count > 0, "the canvas has more commands than softkeys, so there is a menu");
        int height = Math.min(count * row + 8, context.screen().height() - 40);
        int first = context.screen().height() - SystemChrome.softKeyBarHeight() - height - 4 + 4;

        int[] misses = { first - 1, first - row / 2, first - row + 1 };
        for (int i = 0; i < misses.length; i++) {
            context.openMenu();
            check(context.isMenuOpen(), "the menu is open before the tap");
            session.pointerPressed(context.screen().width() / 2, misses[i]);
            check(!context.isMenuOpen(),
                    "a tap " + (first - misses[i]) + "px above the panel closes it");
            // Closing is not enough: the old arithmetic also closed the menu,
            // by running the first command on the way out.
            eq("", call(session, "ranCommand"),
                    "and closes it without running anything");
        }

        // And the fix must not have closed the door on the honest tap: the
        // first row still selects the first row.
        context.openMenu();
        context.moveMenu(1);
        session.pointerPressed(context.screen().width() / 2, first + row / 2);
        check(context.isMenuOpen(), "a tap on the first row does not close the menu");
        eq(0, context.menuIndex(), "it moves the selection there, which is what a first tap does");
        session.destroy();
    }

    /**
     * A menu too long for the panel scrolls, and the touch scrolls with it.
     *
     * <p>Asked as arithmetic rather than through a screen, because the point
     * is that one piece of code decides where a row is drawn and where it is
     * touched: two copies of it agree until a menu is long enough to scroll,
     * and then they quietly stop.</p>
     */
    private void theLongMenuLandsOnTheRowYouSee() {
        int screen = 320;
        int row = ScreenRenderer.rowHeight();
        int many = 40;
        int visible = ScreenRenderer.menuVisible(screen, many);
        check(visible < many, "forty commands do not fit, so the panel really scrolls");

        // Sitting on the last command, the panel shows the tail of the list.
        int focus = many - 1;
        int first = ScreenRenderer.menuTop(screen, many) + 4;
        eq(many - visible, ScreenRenderer.menuRowAt(screen, many, focus, first + row / 2),
                "the top row of a scrolled panel is not command 0");
        eq(focus, ScreenRenderer.menuRowAt(screen, many, focus,
                        first + (visible - 1) * row + row / 2),
                "and the bottom row is the one under the finger");
        eq(-1, ScreenRenderer.menuRowAt(screen, many, focus, first - 1),
                "just above the panel is a miss");
        eq(-1, ScreenRenderer.menuRowAt(screen, many, focus, first + visible * row),
                "and so is just below the last row on show");
    }

    // -------------------------------------------------------------- gesture

    /**
     * A gesture that began on the game stays the game's until the finger lifts.
     *
     * <p>Dragging off the canvas and letting go over the on-screen keypad used
     * to lose the release entirely, so the game went on believing the button
     * was held: a stuck fire key that nothing but a restart clears.</p>
     */
    private void theGestureBelongsToTheGame() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        MidpContext context = session.context();
        vm.callVirtual(session.context().midlet(), "showBoard", "()V");

        int left = context.canvasLeft();
        int top = context.canvasTop();
        int width = context.canvasWidth();
        int height = context.canvasHeight();

        // Pressed inside, dragged off the bottom, released well outside.
        session.pointerPressed(left + 10, top + 10);
        session.pointerDragged(left + 10, top + height + 40);
        session.pointerReleased(left - 30, top + height + 90);
        eq("P10,10 D10," + (height - 1) + " R0," + (height - 1), touches(session),
                "all three events arrive, the ones outside held to the edge");

        // A gesture that never touched the game is not the game's.
        vm.callVirtual(session.context().midlet(), "forgetTouches", "()V");
        session.pointerPressed(left + 10, top + height + 60);
        session.pointerDragged(left + 10, top + 10);
        session.pointerReleased(left + 10, top + 10);
        eq("D10,10 R10,10", touches(session),
                "a press outside is not forwarded; once the finger reaches the canvas it is");
        session.destroy();
    }

    // ------------------------------------------------------------- plumbing

    private String call(EmulatorSession session, String method) {
        Vm vm = session.vm();
        return vm.stringOf(vm.callVirtual(session.context().midlet(), method,
                "()Ljava/lang/String;"));
    }

    private String touches(EmulatorSession session) {
        return call(session, "touches");
    }
}
