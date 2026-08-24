package com.mobicore.tools;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.rt.Cldc;
import com.mobicore.core.vm.ClassSource;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmHost;
import com.mobicore.core.vm.VmObject;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Preview of the VM inspector: runs the fixture program through the
 * interpreter and reports what the runtime actually did.
 */
public final class VmScreen {

    private final String fixtureDir;
    private final StringBuilder console = new StringBuilder();

    public VmScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    private static final class Row {
        final String call;
        final String result;

        Row(String call, String result) {
            this.call = call;
            this.result = result;
        }
    }

    public Framebuffer render() {
        final Vm vm = new Vm();
        Cldc.install(vm);
        vm.setHost(new VmHost() {
            public long currentTimeMillis() {
                return 1_700_000_000_000L;
            }

            public void print(boolean error, String text) {
                console.append(text);
            }

            public void exit(int code) {
            }

            public String property(String name) {
                return "microedition.platform".equals(name) ? "MobiCore" : null;
            }

            public void sleep(long millis) throws InterruptedException {
                Thread.sleep(millis);
            }
        });
        vm.addSource(new DirectoryClasses(fixtureDir));

        List<Row> rows = new ArrayList<Row>();
        String probe = "demo/VmProbe";
        rows.add(new Row("arithmetic(37, 5)", String.valueOf(vm.callStatic(probe, "arithmetic", "(II)I",
                Integer.valueOf(37), Integer.valueOf(5)))));
        rows.add(new Row("bitwise(0xF0F0, 0x0FF0)", String.valueOf(vm.callStatic(probe, "bitwise", "(II)I",
                Integer.valueOf(0xF0F0), Integer.valueOf(0x0FF0)))));
        rows.add(new Row("longMath(123456789, 987)", String.valueOf(vm.callStatic(probe, "longMath", "(JJ)J",
                Long.valueOf(123456789L), Long.valueOf(987L)))));
        rows.add(new Row("floatMath(9.5, 2.25)", String.valueOf(vm.callStatic(probe, "floatMath", "(DD)D",
                Double.valueOf(9.5), Double.valueOf(2.25)))));
        rows.add(new Row("recursion(10)", String.valueOf(vm.callStatic(probe, "recursion", "(I)I",
                Integer.valueOf(10)))));
        rows.add(new Row("polymorphism()", String.valueOf(vm.callStatic(probe, "polymorphism", "()I"))));
        rows.add(new Row("collections()", String.valueOf(vm.callStatic(probe, "collections", "()I"))));
        rows.add(new Row("streams()", String.valueOf(vm.callStatic(probe, "streams", "()I"))));
        rows.add(new Row("exceptions(2)", String.valueOf(vm.callStatic(probe, "exceptions", "(I)I",
                Integer.valueOf(2)))));
        rows.add(new Row("threading()", String.valueOf(vm.callStatic(probe, "threading", "()I"))));
        Object text = vm.callStatic(probe, "strings", "(Ljava/lang/String;)Ljava/lang/String;",
                vm.newString("world"));
        rows.add(new Row("strings(\"world\")", "\"" + vm.stringOf((VmObject) text) + "\""));

        vm.callStatic(probe, "printBanner", "()V");

        return draw(vm, rows);
    }

    private Framebuffer draw(Vm vm, List<Row> rows) {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("VM Inspector", "Developer Tools");

        int margin = 16;
        int width = frame.width() - margin * 2;
        int y = 62;

        ui.panel(margin, y, width, 92, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "RUNTIME", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.field("Classes loaded", String.valueOf(vm.loadedClasses().size()), margin + 14, y + 30, width - 28);
        ui.field("Bytecodes executed", group(vm.interpreter().executed()), margin + 14, y + 50, width - 28);
        ui.field("Configuration", "CLDC-1.1 / MIDP-2.0", margin + 14, y + 70, width - 28);
        y += 104;

        int rowHeight = 22;
        int panelHeight = 30 + rows.size() * rowHeight;
        ui.panel(margin, y, width, panelHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "INTERPRETED CALLS", margin + 14, y + 10, Theme.TEXT_DIM);
        int rowY = y + 30;
        for (Row row : rows) {
            ui.text(ui.small(), row.call, margin + 14, rowY, Theme.TEXT);
            ui.textRight(ui.smallBold(), ui.ellipsize(ui.smallBold(), row.result, 150),
                    margin + width - 14, rowY, Theme.GOOD);
            rowY += rowHeight;
        }
        y += panelHeight + 12;

        ui.panel(margin, y, width, 74, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "CONSOLE", margin + 14, y + 10, Theme.TEXT_DIM);
        int lineY = y + 28;
        for (String line : console.toString().split("\n")) {
            if (line.length() == 0) {
                continue;
            }
            ui.text(ui.small(), ui.ellipsize(ui.small(), line, width - 28), margin + 14, lineY, Theme.ACCENT);
            lineY += 16;
        }
        y += 86;

        ui.panel(margin, y, width, 118, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "LOADED CLASSES (SAMPLE)", margin + 14, y + 10, Theme.TEXT_DIM);
        int listY = y + 30;
        int shown = 0;
        for (VmClass type : vm.loadedClasses()) {
            if (!type.name().startsWith("demo/")) {
                continue;
            }
            ui.text(ui.small(), type.binaryName(), margin + 14, listY, Theme.TEXT);
            ui.textRight(ui.small(), type.methods().length + " methods", margin + width - 14, listY, Theme.TEXT_DIM);
            listY += 18;
            if (++shown >= 5) {
                break;
            }
        }

        return frame;
    }

    private static String group(long value) {
        String digits = String.valueOf(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    /** Reads fixture classes straight from the build output directory. */
    static final class DirectoryClasses implements ClassSource {

        private final String root;
        private final LocalVfs vfs = new LocalVfs();

        DirectoryClasses(String root) {
            this.root = root;
        }

        public byte[] classBytes(String internalName) {
            return resourceBytes(internalName + ".class");
        }

        public byte[] resourceBytes(String path) {
            String full = root + "/" + (path.startsWith("/") ? path.substring(1) : path);
            if (!vfs.exists(full)) {
                return null;
            }
            try {
                return vfs.read(full);
            } catch (java.io.IOException e) {
                return null;
            }
        }
    }
}
