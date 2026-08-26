package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.ui.Theme;

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

    static void write(Vfs vfs, String dir, String name, Framebuffer frame) throws IOException {
        vfs.write(dir + "/" + name, PngWriter.encode(frame));
        System.out.println("  " + name + "  " + frame.width() + "x" + frame.height());
    }

    static Framebuffer newScreen() {
        Framebuffer frame = new Framebuffer(SCREEN_WIDTH, SCREEN_HEIGHT);
        // The interface is drawn with the same primitives a game uses, so it
        // gets the same treatment: rounded corners and chips should not have
        // staircase edges.
        frame.setAntialias(true);
        return frame;
    }
}
