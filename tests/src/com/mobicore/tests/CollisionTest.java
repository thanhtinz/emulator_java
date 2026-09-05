package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Va chạm của sprite, và cái cờ suốt bấy lâu bị vứt đi.
 *
 * <p>{@code collidesWith(Sprite, pixelLevel)} nhận một tham số nói rõ: so hộp
 * bao, hay so từng điểm ảnh. Máy ảo **đọc tham số ấy rồi bỏ đi**, lúc nào cũng
 * so hộp bao. Hậu quả người chơi cảm thấy ngay mà không giải thích được: đi
 * ngang một thứ, hai góc trong suốt chạm nhau, và chết.</p>
 *
 * <p>Nên phép kiểm này dựng đúng cái hình mà một bản làm dối không đi qua
 * được: hai cái nêm ngược nhau, hộp bao chồng nhau bảy cột trong khi **không
 * có một điểm ảnh nào** cả hai cùng vẽ. Cùng một cặp toạ độ, hỏi hai lần với
 * hai giá trị của cờ, phải ra hai câu trả lời khác nhau. Bỏ qua cờ thì không
 * có cách nào ra hai câu.</p>
 */
public final class CollisionTest extends Test {

    private final String fixtureDir;

    public CollisionTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Va chạm của sprite";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/CollideDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.start("demo.CollideDemo");

        theFlagIsRead(session);
        touchingReallyIsTouching(session);
        againstAnImage(session);
        againstALayer(session);
        theCollisionRectangle(session);
    }

    /**
     * The one question a bounding-box-only implementation cannot answer.
     *
     * <p>Boxes overlap, drawn pixels do not. Two different answers for one
     * pair of positions.</p>
     */
    private void theFlagIsRead(EmulatorSession session) {
        for (int shift = 1; shift <= 7; shift++) {
            eq("true", wedges(session, shift, false),
                    "shifted " + shift + ": the boxes do overlap");
            eq("false", wedges(session, shift, true),
                    "shifted " + shift + ": but nothing drawn does, so pixel level says no");
        }
        eq("false", wedges(session, 8, false), "clear of each other: not even the boxes touch");
    }

    /** And when they really do touch, pixel level still says yes. */
    private void touchingReallyIsTouching(EmulatorSession session) {
        for (int shift = 0; shift <= 7; shift++) {
            eq("true", call(session, "twins", shift, true),
                    "two of the same wedge, " + shift + " apart, share drawn pixels");
        }
        eq("false", call(session, "twins", 8, true), "until they are clear of each other");
    }

    /**
     * The overload against a plain Image, which the emulator did not have at
     * all — a game that called it stopped dead on that line.
     */
    private void againstAnImage(EmulatorSession session) {
        eq("true", call(session, "onImage", 3, false), "image: the boxes overlap");
        eq("false", call(session, "onImage", 3, true), "image: but no drawn pixel does");
        eq("true", call(session, "onImage", 0, true),
                "image: laid exactly on top, the diagonal is drawn in both");
    }

    /**
     * The overload against a TiledLayer, likewise missing.
     *
     * <p>The layer here is one solid cell and one hole. A hole is not a wall:
     * an emulator that answers on the layer's whole rectangle turns the empty
     * half of every level into something to bump into.</p>
     */
    private void againstALayer(EmulatorSession session) {
        eq("true", call(session, "onTiles", 0, false), "on the solid cell");
        eq("true", call(session, "onTiles", 0, true), "and its pixels are drawn there too");
        eq("false", call(session, "onTiles", 8, false),
                "over the hole: an empty cell is not a wall");
        eq("false", call(session, "onTiles", 8, true), "and pixel level agrees");
        eq("true", call(session, "onTiles", 7, false),
                "one column of the sprite still on the solid cell");
        eq("false", call(session, "onTiles", 16, false), "clear of the layer altogether");
    }

    /**
     * {@code defineCollisionRectangle} was stored and never read.
     *
     * <p>It is how a game says "only my body counts, not the sword I am
     * holding". Narrow the rectangle to the two left columns and the same
     * overlap stops being a collision.</p>
     */
    private void theCollisionRectangle(EmulatorSession session) {
        eq("true", wedges(session, 3, false), "the whole sprite overlaps at three across");
        eq("false", call(session, "narrowed", 3, false),
                "but its first two columns do not — the rectangle is read");
        eq("true", call(session, "narrowed", 1, false),
                "and a closer overlap still reaches those two columns");
    }

    // ------------------------------------------------------------- plumbing

    private String wedges(EmulatorSession session, int shift, boolean pixelLevel) {
        return call(session, "wedges", shift, pixelLevel);
    }

    private String call(EmulatorSession session, String method, int shift, boolean pixelLevel) {
        Vm vm = session.vm();
        VmObject midlet = session.context().midlet();
        return vm.stringOf(vm.callVirtual(midlet, method, "(IZ)Ljava/lang/String;",
                Integer.valueOf(shift), Integer.valueOf(pixelLevel ? 1 : 0)));
    }
}
