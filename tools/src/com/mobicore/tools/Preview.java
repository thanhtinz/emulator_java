package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.core.storage.Vfs;

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

        System.out.println("Screenshots written to " + outDir);
    }

    static void write(Vfs vfs, String dir, String name, Framebuffer frame) throws IOException {
        vfs.write(dir + "/" + name, PngWriter.encode(frame));
        System.out.println("  " + name + "  " + frame.width() + "x" + frame.height());
    }

    static Framebuffer newScreen() {
        return new Framebuffer(SCREEN_WIDTH, SCREEN_HEIGHT);
    }
}
