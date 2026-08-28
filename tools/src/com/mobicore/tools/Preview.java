package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.io.IOException;

/**
 * Desktop preview harness.
 *
 * <p>Renders MobiCore screens with the portable framebuffer and writes them as
 * PNGs, which gives every feature a reviewable screenshot without an Android or
 * iOS device in the loop.</p>
 *
 * <pre>./build.sh run com.mobicore.tools.Preview build/screenshots</pre>
 */
public final class Preview {

    /**
     * A phone-shaped canvas. 480 is exactly twice the width of a QVGA handset
     * screen, so an emulated 240x320 game fills it at a clean integer scale
     * with no filtering and no letterboxing at the sides.
     */
    public static final int SCREEN_WIDTH = 480;
    public static final int SCREEN_HEIGHT = 1040;

    private Preview() {
    }

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "build/screenshots";
        // Bắt đầu từ tờ giấy trắng: vết mực của lần chạy trước không được
        // tính vào màn hình đầu tiên của lần này.
        Ui.forgetInk();
        Vfs vfs = new LocalVfs();
        vfs.mkdirs(outDir);

        String fixtures = args.length > 1 ? args[1] : "build/classes/fixtures";
        write(vfs, outDir, "01-import.png", new ImportScreen().render());
        write(vfs, outDir, "02-vm-inspector.png", new VmScreen(fixtures).render());
        write(vfs, outDir, "03-emulator.png", new EmulatorScreen(fixtures).render());
        write(vfs, outDir, "04-game-settings.png", new ProfileScreen(fixtures).render());
        write(vfs, outDir, "05-library.png", new LibraryScreen(fixtures).render());
        write(vfs, outDir, "06-game-detail.png", new DetailScreen(fixtures).render());
        write(vfs, outDir, "07-dev-tools.png", new DevToolsScreen(fixtures).render());
        write(vfs, outDir, "08-list.png", new MenuScreen(fixtures, "list").render());
        write(vfs, outDir, "09-form.png", new MenuScreen(fixtures, "form").render());
        write(vfs, outDir, "10-options-menu.png", new MenuScreen(fixtures, "menu").render());
        write(vfs, outDir, "11-textbox.png", new MenuScreen(fixtures, "textbox").render());
        write(vfs, outDir, "12-alert.png", new MenuScreen(fixtures, "alert").render());
        write(vfs, outDir, "13-sound.png", new SoundScreen(fixtures).render());
        write(vfs, outDir, "16-search.png", new SearchScreen(fixtures).render());
        write(vfs, outDir, "17-keyboard.png", keyboardScreen(fixtures));
        write(vfs, outDir, "18-landscape.png", EmulatorScreen.landscape(fixtures).renderLandscape());
        write(vfs, outDir, "19-keypad-arrows.png", new EmulatorScreen(fixtures)
                .withKeypad(com.mobicore.core.model.GameProfile.KEYPAD_ARROWS).render());
        write(vfs, outDir, "20-game-menu.png", new EmulatorScreen(fixtures).withMenu().render());
        write(vfs, outDir, "21-screenshots.png", new ShotsScreen(fixtures).render());
        write(vfs, outDir, "22-save-slots.png", new SlotsScreen(fixtures).render());
        write(vfs, outDir, "23-nokia.png", nokiaScreen(fixtures));
        write(vfs, outDir, "30-photo.png", photoScreen(fixtures));
        write(vfs, outDir, "35-own-loop.png", loopScreen(fixtures));
        write(vfs, outDir, "36-clock.png", clockScreen(fixtures));
        write(vfs, outDir, "37-flip.png", flipScreen(fixtures));
        write(vfs, outDir, "28-crash.png", new CrashScreen(fixtures).render());
        write(vfs, outDir, "29-hang.png", new CrashScreen(fixtures, "demo.HangDemo").render());
        // The keypad in the other shape and faded back, which is what the
        // two new settings do to it.
        write(vfs, outDir, "26-arrange-keys.png", arrangeScreen(fixtures));
        write(vfs, outDir, "24-keypad-look.png", new EmulatorScreen(fixtures)
                .withKeyLook(com.mobicore.core.model.GameProfile.KEY_SHAPE_ROUND, 45)
                .render());

        // The same screens in the other theme, so both can be reviewed.
        Theme.setMode(Theme.DARK);
        write(vfs, outDir, "14-library-dark.png", new LibraryScreen(fixtures).render());
        write(vfs, outDir, "15-emulator-dark.png", new EmulatorScreen(fixtures).render());
        Theme.setMode(Theme.LIGHT);

        System.out.println("Screenshots written to " + outDir);
    }

    /**
     * The emulator while a game is asking for text: the MIDlet's own TextBox
     * on screen, and the keypad replaced by the note that the phone's
     * keyboard has taken that space.
     */
    private static Framebuffer keyboardScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures, "demo.MenuDemo");
        EmulatorSession session = screen.boot();
        session.renderFrame();
        // Into "Nhập tên", which is a TextBox.
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_FIRE);
        session.renderFrame();
        return screen.render();
    }

    /**
     * Một game vẽ tấm ảnh JPEG nó mang theo.
     *
     * <p>Ảnh thật, do bộ đọc của máy ảo giải mã, vẽ bởi một MIDlet thật: cái
     * đáng chụp là chỗ đó chứ không phải một bảng cài đặt nói rằng đã đọc
     * được JPEG.</p>
     */
    /**
     * Một game tự chạy vòng lặp của nó, vẽ bằng serviceRepaints.
     *
     * <p>Khung hình trong ảnh do chính game vẽ khi nó gọi — không phải do vòng
     * lặp của máy ảo vẽ hộ.</p>
     */
    private static Framebuffer loopScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures, "demo.LoopDemo");
        EmulatorSession session = screen.boot();
        for (int i = 0; i < 40; i++) {
            session.vm().callVirtual(session.context().midlet(), "step", "()V");
        }
        return screen.render();
    }

    /**
     * Một game xem giờ, như game phần thưởng mỗi ngày vẫn làm.
     *
     * <p>Giờ, thứ và ngày trong ảnh do chính máy ảo tính ra từ lịch của nó,
     * và dòng chữ dưới cùng là một tệp chữ trong gói game, đọc bằng
     * {@code InputStreamReader}.</p>
     */
    private static Framebuffer clockScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures, "demo.ClockDemo");
        EmulatorSession session = screen.boot();
        // Múi giờ Việt Nam, để cái đồng hồ trong ảnh là một giờ có thật ở đây.
        session.vm().setTimeZone("Asia/Ho_Chi_Minh", 7 * 60 * 60000);
        session.renderFrame();
        return screen.render();
    }

    /**
     * Cùng một hình dưới cả tám phép lật xoay của MIDP.
     *
     * <p>Hình cố ý không đối xứng: hình đối xứng giấu đúng cái lỗi đáng bắt,
     * vì lật rồi xoay không ra cùng kết quả với xoay rồi lật.</p>
     */
    private static Framebuffer flipScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures, "demo.FlipDemo");
        screen.boot();
        screen.session().renderFrame();
        return screen.render();
    }

    private static Framebuffer photoScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures, "demo.PhotoDemo");
        screen.boot();
        screen.session().renderFrame();
        return screen.render();
    }

    /**
     * A game written against Nokia's own API, running.
     *
     * <p>Everything on that screen is drawn through {@code DirectGraphics}
     * from a {@code FullCanvas} — the combination that, until it was
     * implemented, stopped a large share of these games at the class
     * loader.</p>
     */
    private static Framebuffer nokiaScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures, "demo.NokiaDemo");
        EmulatorSession session = screen.boot();
        session.renderFrame();
        return screen.render();
    }

    /**
     * The arranging screen, with a few keys actually moved.
     *
     * <p>Moved through the model the app itself edits, so the screenshot
     * cannot show an arrangement the emulator would not draw.</p>
     */
    private static Framebuffer arrangeScreen(String fixtures) throws Exception {
        EmulatorScreen screen = new EmulatorScreen(fixtures).arranging();
        EmulatorSession session = screen.boot();
        com.mobicore.core.model.KeypadArrangement keys =
                session.profile().keypadArrangement();
        // A left-handed player's pad: fire pulled in under the thumb, the two
        // corners nobody uses pushed out of the way.
        keys.setScale(112);
        keys.move("fire", 0f, 0.55f);
        keys.move("upLeft", -0.15f, -0.4f);
        keys.move("upRight", 0.15f, -0.4f);
        return screen.render();
    }

    static void write(Vfs vfs, String dir, String name, Framebuffer frame) throws IOException {
        // Chữ tràn khung thì dừng ngay ở đây. Đây là lỗi lặp lại nhiều lần
        // và lần nào cũng do mắt người soi ảnh bỏ sót: một cái chip không vừa
        // hàng, một cái nhãn dài hơn hôm qua, một chiều cao khung gõ tay
        // không theo kịp nội dung. Ui đã tự ghi lại từng khung và từng dòng
        // chữ nó vẽ, nên chỗ này chỉ việc hỏi lại rồi ném.
        java.util.List<String> spilled = Ui.overflows(frame.width(), frame.height());
        Ui.forgetInk();
        if (!spilled.isEmpty()) {
            StringBuilder why = new StringBuilder("Chữ tràn khung trong " + name + ":");
            for (String line : spilled) {
                why.append("\n  ").append(line);
            }
            throw new IllegalStateException(why.toString());
        }
        vfs.write(dir + "/" + name, PngWriter.encode(frame));
        System.out.println("  " + name + "  " + frame.width() + "x" + frame.height());
    }

    /**
     * Một trang dài, để cắt lại cho vừa sau khi vẽ xong.
     *
     * <p>Trang cài đặt trên điện thoại thì cuộn được; ảnh xem trước thì
     * không, nên nó được vẽ hết chiều dài rồi cắt. Con số này chỉ cần đủ
     * rộng tay: phần thừa bị {@link #fit} cắt đi.</p>
     */
    static Framebuffer newPage() {
        return newScreen(2600);
    }

    static Framebuffer newScreen() {
        return newScreen(SCREEN_HEIGHT);
    }

    /**
     * A screen taller than a phone's, for a settings page that scrolls.
     *
     * <p>The preview cannot scroll, so a page that would be scrolled on a
     * phone is drawn at full length instead — a screenshot cut off at the
     * fold shows less than the page has.</p>
     */
    /**
     * Cắt trang cho vừa đúng phần đã vẽ.
     *
     * <p>Bản xem trước không cuộn được, nên một trang dài hơn màn hình bị
     * cắt ngang — và chỗ bị cắt luôn rơi vào giữa một cái thẻ, tức là chữ
     * lọt ra khỏi khung. Vẽ vào một tấm thừa rồi cắt về đúng chiều cao thật
     * thì trang nào cũng hiện đủ, kể cả khi nội dung của nó đổi: một bộ cài
     * có thêm hai ứng dụng là trang dài thêm hai dòng, và không ai phải nhớ
     * sửa lại con số chiều cao.</p>
     *
     * <p>Không bao giờ ngắn hơn một màn hình điện thoại: một trang ngắn thì
     * chỗ trống dưới nó cũng là một phần của cái người ta nhìn thấy.</p>
     */
    static Framebuffer fit(Framebuffer frame) {
        return fit(frame, 0);
    }

    /**
     * @param reserve chỗ chừa thêm dưới cùng, cho thứ vẽ đè lên đáy trang
     */
    static Framebuffer fit(Framebuffer frame, int reserve) {
        int[] pixels = frame.pixels();
        int lastInk = -1;
        for (int y = frame.height() - 1; y >= 0 && lastInk < 0; y--) {
            for (int x = 0; x < frame.width(); x++) {
                if (pixels[y * frame.width() + x] != Theme.BG) {
                    lastInk = y;
                    break;
                }
            }
        }
        if (lastInk >= frame.height() - 1) {
            // Chạm đáy tấm vẽ nghĩa là phần dưới đã mất chứ không phải trang
            // vừa vặn. Cắt thêm khoảng trống vào đây chỉ giấu chỗ cụt đi, nên
            // chỗ này kêu lên thay vì lặng lẽ trả về một trang thiếu.
            throw new IllegalStateException(
                    "Trang bị cắt: nội dung chạm đáy tấm vẽ cao " + frame.height()
                            + "px. Vẽ vào Preview.newPage() rộng tay hơn.");
        }
        int height = Math.max(SCREEN_HEIGHT, lastInk + 1 + Ui.PAD + reserve);
        if (height == frame.height()) {
            return frame;
        }
        Framebuffer cut = new Framebuffer(frame.width(), height);
        cut.setAntialias(true);
        cut.setColor(Theme.BG);
        cut.fillRect(0, 0, cut.width(), cut.height());
        cut.setBlendMode(Framebuffer.BLEND_REPLACE);
        cut.drawFramebuffer(frame, 0, 0);
        cut.setBlendMode(Framebuffer.BLEND_SRC_OVER);
        return cut;
    }

    static Framebuffer newScreen(int height) {
        Framebuffer frame = new Framebuffer(SCREEN_WIDTH, height);
        // The interface is drawn with the same primitives a game uses, so it
        // gets the same treatment: rounded corners and chips should not have
        // staircase edges.
        frame.setAntialias(true);
        return frame;
    }
}
