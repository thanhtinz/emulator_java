package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.Transforms;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.vm.Vm;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Bốn chỗ game **không chết** mà chạy sai.
 *
 * <p>Khác với những hàm thiếu hẳn: ở đây game chạy, và chạy hơi lệch theo cái
 * cách người chơi cảm thấy nhưng không tả nổi, nên cũng chẳng ai báo. Nút bắn
 * "trượt". Nhân vật quay mặt là nhảy ngang. Một dải ở đáy màn hình thôi không
 * vẽ lại nữa. Nhạc màn menu vẫn phát dưới màn chơi.</p>
 *
 * <p>Cả bốn đều chỉ hiện ra khi hỏi đúng câu, nên đây là bốn câu ấy.</p>
 */
public final class BehaviourTest extends Test {

    private final String fixtureDir;

    public BehaviourTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Bốn chỗ game chạy sai";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/InputProbe.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        theQuickTap();
        thePointAndThePixels();
        theReferencePixelStaysPut();
        theBackBufferFollowsTheCanvas();
        theScreenIsToldItIsLeaving();
    }

    private EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.start("demo.InputProbe");
        return session;
    }

    /**
     * A key pressed and released between two calls to {@code getKeyStates}.
     *
     * <p>MIDP latches: the game must still be told once. A game loop asks once
     * a frame, and a tap is shorter than a frame — so without the latch the
     * fire button simply misses, and the player has no way to know why.</p>
     */
    private void theQuickTap() throws Exception {
        EmulatorSession session = boot();
        int fire = 1 << MidpContext.ACTION_FIRE;

        eq(0, keys(session), "nothing held, nothing reported");

        // The whole tap happens between two asks.
        session.keyPressed(MidpContext.KEY_FIRE);
        session.keyReleased(MidpContext.KEY_FIRE);
        eq(fire, keys(session) & fire, "a tap that came and went is still reported once");
        eq(0, keys(session) & fire, "and only once — the latch was cleared by the asking");

        // A key still held is reported every time, latch or no latch.
        session.keyPressed(MidpContext.KEY_FIRE);
        eq(fire, keys(session) & fire, "a held key reports while it is held");
        eq(fire, keys(session) & fire, "and goes on reporting");
        // Letting go of a key that was already reported as held is not news:
        // the game has been told about that press. Only a press it never saw
        // is owed a report.
        session.keyReleased(MidpContext.KEY_FIRE);
        eq(0, keys(session) & fire, "letting go of a key already reported says nothing new");
        session.destroy();
    }

    /**
     * The two ways of asking where a pixel goes must agree.
     *
     * <p>{@code Transforms.apply} moves a whole picture; {@code mapX}/
     * {@code mapY} move one point. The reference pixel is asked about with the
     * second and drawn with the first, so a disagreement between them is a
     * sprite drawn one way and reported another.</p>
     */
    private void thePointAndThePixels() {
        int width = 5;
        int height = 3;
        int[] transforms = {
                Transforms.NONE, Transforms.MIRROR, Transforms.ROT90, Transforms.ROT180,
                Transforms.ROT270, Transforms.MIRROR_ROT90, Transforms.MIRROR_ROT180,
                Transforms.MIRROR_ROT270,
        };
        for (int t = 0; t < transforms.length; t++) {
            int transform = transforms[t];
            int outWidth = Transforms.resultWidth(transform, width, height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // One marked pixel, moved by apply; then found again.
                    int[] block = new int[width * height];
                    block[y * width + x] = 0xFFFFFFFF;
                    int[] moved = Transforms.apply(block, width, height, 0, 0,
                            width, height, transform);
                    int at = -1;
                    for (int i = 0; i < moved.length; i++) {
                        if (moved[i] != 0) {
                            at = i;
                        }
                    }
                    int wanted = Transforms.mapY(transform, width, height, x, y) * outWidth
                            + Transforms.mapX(transform, width, height, x, y);
                    eq(wanted, at, "transform " + transform + ": the point at " + x + "," + y
                            + " lands where the picture put it");
                }
            }
        }
    }

    /**
     * Turning a sprite turns it about its reference pixel.
     *
     * <p>MIDP is explicit: after {@code setTransform} the reference pixel is
     * at the same place on screen it was before. The top-left corner is what
     * moves. Getting this backwards makes a character step sideways by its own
     * width every time it turns around — visible in every walk cycle ever
     * drawn for a J2ME game.</p>
     */
    private void theReferencePixelStaysPut() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        // Off centre in both directions, on a frame that is not square: a
        // centred reference pixel on a square frame hides the whole mistake.
        vm.callVirtual(session.context().midlet(), "place", "(IIII)V",
                Integer.valueOf(1), Integer.valueOf(3),
                Integer.valueOf(40), Integer.valueOf(60));

        int[] transforms = {
                Transforms.MIRROR, Transforms.ROT90, Transforms.ROT180, Transforms.ROT270,
                Transforms.MIRROR_ROT90, Transforms.MIRROR_ROT180, Transforms.MIRROR_ROT270,
                Transforms.NONE,
        };
        for (int i = 0; i < transforms.length; i++) {
            eq("40,60", turn(session, transforms[i]),
                    "transform " + transforms[i] + ": the reference pixel has not moved");
        }

        // And the corner really did move, so this is not passing by standing
        // still: a sprite that never turns would also answer "40,60".
        turn(session, Transforms.NONE);
        String upright = corner(session);
        turn(session, Transforms.ROT90);
        check(!upright.equals(corner(session)),
                "the corner is what moves instead — the sprite really turned");
        session.destroy();
    }

    /**
     * Going full screen grows the canvas, so the buffer has to grow with it.
     *
     * <p>Otherwise the strip the command bar used to occupy keeps whatever was
     * underneath it: a band along the bottom that never changes again while
     * the game plays on above it.</p>
     */
    private void theBackBufferFollowsTheCanvas() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();

        // Built while the menu is up: the menu has a title and a command, so
        // the system is keeping a strip and the canvas is short.
        vm.callVirtual(session.context().midlet(), "buildWhileMenuIsUp", "()V");
        String cramped = vm.stringOf(vm.callVirtual(session.context().midlet(),
                "canvasSize", "()Ljava/lang/String;"));

        // Now it goes up itself, with no chrome of its own, and paints.
        vm.callVirtual(session.context().midlet(), "playOn", "()V");
        String roomy = vm.stringOf(vm.callVirtual(session.context().midlet(),
                "laterSize", "()Ljava/lang/String;"));
        check(!cramped.equals(roomy),
                "the play screen really is taller than the menu was: "
                        + cramped + " → " + roomy);

        // The very last row of the screen. With a buffer still built for the
        // menu's size, this row is never reached and keeps what the command
        // bar left behind.
        Framebuffer screen = session.screen();
        int bottom = screen.pixels()[(screen.height() - 1) * screen.width() + screen.width() / 2];
        eq(0x00FF00, bottom & 0xFFFFFF,
                "the bottom row is the colour the game just painted, not a frozen band");

        // And the same again through setFullScreenMode, which is the other
        // way a canvas grows under a buffer that was already made.
        vm.callVirtual(session.context().midlet(), "repaintFullScreen", "()V");
        screen = session.screen();
        bottom = screen.pixels()[(screen.height() - 1) * screen.width() + screen.width() / 2];
        eq(0x00FF00, bottom & 0xFFFFFF, "and likewise after going full screen");
        session.destroy();
    }

    /**
     * A screen being replaced is told so.
     *
     * <p>A MIDlet stops its music, stops the thread that animates the screen
     * and lets go of its images in {@code hideNotify}. Never calling it leaves
     * the menu's music playing under the game for the rest of the session.</p>
     */
    private void theScreenIsToldItIsLeaving() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        eq(1, count(session, "shown"), "the first screen was shown once");
        eq(0, count(session, "hidden"), "and has not been put away");

        vm.callVirtual(session.context().midlet(), "goAway", "()V");
        eq(1, count(session, "hidden"), "moving to another screen puts the first one away");
        eq(1, count(session, "shown"), "and does not show it again");

        vm.callVirtual(session.context().midlet(), "comeBack", "()V");
        eq(2, count(session, "shown"), "coming back shows it again");
        eq(1, count(session, "hidden"), "without putting it away a second time");
        session.destroy();
    }

    // ------------------------------------------------------------- plumbing

    private int keys(EmulatorSession session) {
        Vm vm = session.vm();
        return ((Integer) vm.callVirtual(session.context().midlet(), "keys", "()I")).intValue();
    }

    private int count(EmulatorSession session, String what) {
        Vm vm = session.vm();
        return ((Integer) vm.callVirtual(session.context().midlet(), what, "()I")).intValue();
    }

    private String turn(EmulatorSession session, int transform) {
        Vm vm = session.vm();
        return vm.stringOf(vm.callVirtual(session.context().midlet(), "turn",
                "(I)Ljava/lang/String;", Integer.valueOf(transform)));
    }

    private String corner(EmulatorSession session) {
        Vm vm = session.vm();
        return vm.stringOf(vm.callVirtual(session.context().midlet(), "corner",
                "()Ljava/lang/String;"));
    }
}
