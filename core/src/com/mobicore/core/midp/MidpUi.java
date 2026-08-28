package com.mobicore.core.midp;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

/**
 * MIDlet lifecycle and the {@code Displayable} hierarchy.
 *
 * <p>Games subclass {@code MIDlet}, {@code Canvas} and {@code GameCanvas}, so
 * these are defined as ordinary classes with working default implementations:
 * a game's {@code super.paint(g)} or {@code super.keyPressed(k)} has to resolve
 * to something, and an unimplemented callback must be a no-op rather than an
 * {@code AbstractMethodError} mid-frame.</p>
 */
public final class MidpUi {

    public static final String MIDLET = "javax/microedition/midlet/MIDlet";
    public static final String DISPLAY = "javax/microedition/lcdui/Display";
    public static final String DISPLAYABLE = "javax/microedition/lcdui/Displayable";
    public static final String CANVAS = "javax/microedition/lcdui/Canvas";
    public static final String GAME_CANVAS = "javax/microedition/lcdui/game/GameCanvas";
    public static final String COMMAND = "javax/microedition/lcdui/Command";
    public static final String COMMAND_LISTENER = "javax/microedition/lcdui/CommandListener";

    private MidpUi() {
    }

    public static void install(final Vm vm, final MidpContext context) {
        midlet(vm, context);
        command(vm);
        displayable(vm, context);
        canvas(vm, context);
        gameCanvas(vm, context);
        display(vm, context);
    }

    // ------------------------------------------------------------- MIDlet

    private static void midlet(final Vm vm, final MidpContext context) {
        vm.builtin("javax/microedition/midlet/MIDletStateChangeException", "java/lang/Exception")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("message", args[0]);
                        return null;
                    }
                })
                .define();

        vm.builtin(MIDLET, Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.setMidlet(self);
                        return null;
                    }
                })
                .method("getAppProperty", "(Ljava/lang/String;)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String value = context.attributes().get(Rt.s(vm, args, 0));
                        return value == null ? null : vm.newString(value);
                    }
                })
                .method("notifyDestroyed", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.markDestroyed();
                        return null;
                    }
                })
                .method("notifyPaused", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("resumeRequest", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("platformRequest", "(Ljava/lang/String;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Opening a browser or dialler is a host decision; the
                        // emulator refuses rather than silently doing nothing
                        // that looks like success.
                        return Rt.box(false);
                    }
                })
                .method("startApp", "()V", noop())
                .method("pauseApp", "()V", noop())
                .method("destroyApp", "(Z)V", noop())
                .define();
    }

    private static NativeMethod noop() {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return null;
            }
        };
    }

    // ------------------------------------------------------------ Command

    private static void command(final Vm vm) {
        vm.builtin(COMMAND, Vm.OBJECT)
                .field("label", "Ljava/lang/String;")
                .field("longLabel", "Ljava/lang/String;")
                .field("commandType", "I")
                .field("priority", "I")
                .staticField("SCREEN", "I").staticField("BACK", "I").staticField("CANCEL", "I")
                .staticField("OK", "I").staticField("HELP", "I").staticField("STOP", "I")
                .staticField("EXIT", "I").staticField("ITEM", "I")
                .method("<init>", "(Ljava/lang/String;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("commandType", Integer.valueOf(Rt.i(args, 1)));
                        self.set("priority", Integer.valueOf(Rt.i(args, 2)));
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;Ljava/lang/String;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("longLabel", args[1]);
                        self.set("commandType", Integer.valueOf(Rt.i(args, 2)));
                        self.set("priority", Integer.valueOf(Rt.i(args, 3)));
                        return null;
                    }
                })
                .method("getLabel", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("label");
                    }
                })
                .method("getLongLabel", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("longLabel");
                    }
                })
                .method("getCommandType", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("commandType");
                    }
                })
                .method("getPriority", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("priority");
                    }
                })
                .define();

        VmClass command = vm.loadClass(COMMAND);
        vm.initialize(command);
        MidpGfx.setStatic(vm, command, "SCREEN", 1);
        MidpGfx.setStatic(vm, command, "BACK", 2);
        MidpGfx.setStatic(vm, command, "CANCEL", 3);
        MidpGfx.setStatic(vm, command, "OK", 4);
        MidpGfx.setStatic(vm, command, "HELP", 5);
        MidpGfx.setStatic(vm, command, "STOP", 6);
        MidpGfx.setStatic(vm, command, "EXIT", 7);
        MidpGfx.setStatic(vm, command, "ITEM", 8);

        vm.builtin(COMMAND_LISTENER, Vm.OBJECT, new String[0], true)
                .abstractMethod("commandAction",
                        "(Ljavax/microedition/lcdui/Command;Ljavax/microedition/lcdui/Displayable;)V")
                .define();
    }

    // --------------------------------------------------------- Displayable

    private static void displayable(final Vm vm, final MidpContext context) {
        vm.builtin(DISPLAYABLE, Vm.OBJECT)
                .field("title", "Ljava/lang/String;")
                .field("commandListener", "Ljavax/microedition/lcdui/CommandListener;")
                .method("<init>", "()V", noop())
                // A MIDlet lays its whole screen out from these, and a handset
                // reports the drawing area it is actually given rather than the
                // size of the display.
                .method("getWidth", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(context.canvasWidth());
                    }
                })
                .method("getHeight", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(context.canvasHeight());
                    }
                })
                .method("setTitle", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("title", args[0]);
                        if (context.current() == self) {
                            // Titling a screen that is not up must not relabel
                            // the one that is; setCurrent picks the title up
                            // when the screen is actually shown.
                            context.setTitle(Rt.s(vm, args, 0));
                        }
                        // The title bar takes height away from the canvas, and
                        // a game that has already measured needs telling.
                        context.notifySizeChanged(self);
                        return null;
                    }
                })
                .method("getTitle", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("title");
                    }
                })
                .method("addCommand", "(Ljavax/microedition/lcdui/Command;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.addCommand(self, Rt.obj(args, 0));
                        context.notifySizeChanged(self);
                        return null;
                    }
                })
                .method("removeCommand", "(Ljavax/microedition/lcdui/Command;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.removeCommand(self, Rt.obj(args, 0));
                        context.notifySizeChanged(self);
                        return null;
                    }
                })
                .method("setCommandListener", "(Ljavax/microedition/lcdui/CommandListener;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("commandListener", args[0]);
                                return null;
                            }
                        })
                .method("isShown", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(context.current() == self);
                    }
                })
                // Every Displayable answers these, not just a Canvas: the
                // system calls them on whatever screen it puts up, and a Form
                // that cannot be shown is a Form that cannot be used.
                .method("showNotify", "()V", noop())
                .method("hideNotify", "()V", noop())
                .method("sizeChanged", "(II)V", noop())
                .define();
    }

    // ------------------------------------------------------------- Canvas

    private static void canvas(final Vm vm, final MidpContext context) {
        vm.builtin(CANVAS, DISPLAYABLE)
                .staticField("UP", "I").staticField("DOWN", "I").staticField("LEFT", "I")
                .staticField("RIGHT", "I").staticField("FIRE", "I")
                .staticField("GAME_A", "I").staticField("GAME_B", "I")
                .staticField("GAME_C", "I").staticField("GAME_D", "I")
                .staticField("KEY_NUM0", "I").staticField("KEY_NUM1", "I").staticField("KEY_NUM2", "I")
                .staticField("KEY_NUM3", "I").staticField("KEY_NUM4", "I").staticField("KEY_NUM5", "I")
                .staticField("KEY_NUM6", "I").staticField("KEY_NUM7", "I").staticField("KEY_NUM8", "I")
                .staticField("KEY_NUM9", "I")
                .staticField("KEY_STAR", "I").staticField("KEY_POUND", "I")
                .method("<init>", "()V", noop())
                .method("paint", "(Ljavax/microedition/lcdui/Graphics;)V", noop())
                .method("repaint", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("repaint", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("serviceRepaints", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // MIDP: "chặn lại cho tới khi vẽ xong". Một mảng lớn
                        // game đời ấy viết vòng lặp dựa đúng vào lời hứa này —
                        // tính toán, repaint, serviceRepaints, ngủ một nhịp —
                        // nên bỏ trống nó là lấy mất nhịp game tự đặt ra.
                        if (context.current() == self && context.isRepaintRequested()) {
                            context.paintNow(self);
                        }
                        return null;
                    }
                })
                .method("setFullScreenMode", "(Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.setFullScreen(Rt.bool(args, 0));
                        // Leaving full screen mode hands part of the display
                        // back to the system, so the canvas really does resize.
                        context.notifySizeChanged(self);
                        return null;
                    }
                })
                .method("hasPointerEvents", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .method("hasPointerMotionEvents", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .method("hasRepeatEvents", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .method("isDoubleBuffered", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .method("getGameAction", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(MidpContext.gameAction(Rt.i(args, 0)));
                    }
                })
                .method("getKeyCode", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(MidpContext.keyCode(Rt.i(args, 0)));
                    }
                })
                .method("getKeyName", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(MidpContext.keyName(Rt.i(args, 0)));
                    }
                })
                .method("keyPressed", "(I)V", noop())
                .method("keyReleased", "(I)V", noop())
                .method("keyRepeated", "(I)V", noop())
                .method("pointerPressed", "(II)V", noop())
                .method("pointerReleased", "(II)V", noop())
                .method("pointerDragged", "(II)V", noop())
                .method("showNotify", "()V", noop())
                .method("hideNotify", "()V", noop())
                .method("sizeChanged", "(II)V", noop())
                .define();

        VmClass canvas = vm.loadClass(CANVAS);
        vm.initialize(canvas);
        MidpGfx.setStatic(vm, canvas, "UP", MidpContext.ACTION_UP);
        MidpGfx.setStatic(vm, canvas, "DOWN", MidpContext.ACTION_DOWN);
        MidpGfx.setStatic(vm, canvas, "LEFT", MidpContext.ACTION_LEFT);
        MidpGfx.setStatic(vm, canvas, "RIGHT", MidpContext.ACTION_RIGHT);
        MidpGfx.setStatic(vm, canvas, "FIRE", MidpContext.ACTION_FIRE);
        MidpGfx.setStatic(vm, canvas, "GAME_A", MidpContext.ACTION_GAME_A);
        MidpGfx.setStatic(vm, canvas, "GAME_B", MidpContext.ACTION_GAME_B);
        MidpGfx.setStatic(vm, canvas, "GAME_C", MidpContext.ACTION_GAME_C);
        MidpGfx.setStatic(vm, canvas, "GAME_D", MidpContext.ACTION_GAME_D);
        for (int digit = 0; digit <= 9; digit++) {
            MidpGfx.setStatic(vm, canvas, "KEY_NUM" + digit, '0' + digit);
        }
        MidpGfx.setStatic(vm, canvas, "KEY_STAR", '*');
        MidpGfx.setStatic(vm, canvas, "KEY_POUND", '#');
    }

    // --------------------------------------------------------- GameCanvas

    private static void gameCanvas(final Vm vm, final MidpContext context) {
        vm.builtin(GAME_CANVAS, CANVAS)
                .staticField("UP_PRESSED", "I").staticField("DOWN_PRESSED", "I")
                .staticField("LEFT_PRESSED", "I").staticField("RIGHT_PRESSED", "I")
                .staticField("FIRE_PRESSED", "I")
                .staticField("GAME_A_PRESSED", "I").staticField("GAME_B_PRESSED", "I")
                .staticField("GAME_C_PRESSED", "I").staticField("GAME_D_PRESSED", "I")
                .method("<init>", "(Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // The back buffer is the same size as the screen, which
                        // is what flushGraphics assumes, and is shown directly,
                        // so it smooths shapes exactly as the screen does.
                        Framebuffer back = new Framebuffer(context.canvasWidth(),
                                context.canvasHeight());
                        back.setAntialias(context.smoothShapes());
                        self.host = back;
                        return null;
                    }
                })
                .method("getGraphics", "()Ljavax/microedition/lcdui/Graphics;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return MidpGfx.newGraphics(vm, backBuffer(context, self));
                    }
                })
                .method("flushGraphics", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        flush(context, self);
                        return null;
                    }
                })
                .method("flushGraphics", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        flush(context, self);
                        return null;
                    }
                })
                .method("getKeyStates", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(context.consumeKeyStates());
                    }
                })
                .define();

        VmClass gameCanvas = vm.loadClass(GAME_CANVAS);
        vm.initialize(gameCanvas);
        MidpGfx.setStatic(vm, gameCanvas, "UP_PRESSED", 1 << MidpContext.ACTION_UP);
        MidpGfx.setStatic(vm, gameCanvas, "DOWN_PRESSED", 1 << MidpContext.ACTION_DOWN);
        MidpGfx.setStatic(vm, gameCanvas, "LEFT_PRESSED", 1 << MidpContext.ACTION_LEFT);
        MidpGfx.setStatic(vm, gameCanvas, "RIGHT_PRESSED", 1 << MidpContext.ACTION_RIGHT);
        MidpGfx.setStatic(vm, gameCanvas, "FIRE_PRESSED", 1 << MidpContext.ACTION_FIRE);
        MidpGfx.setStatic(vm, gameCanvas, "GAME_A_PRESSED", 1 << MidpContext.ACTION_GAME_A);
        MidpGfx.setStatic(vm, gameCanvas, "GAME_B_PRESSED", 1 << MidpContext.ACTION_GAME_B);
        MidpGfx.setStatic(vm, gameCanvas, "GAME_C_PRESSED", 1 << MidpContext.ACTION_GAME_C);
        MidpGfx.setStatic(vm, gameCanvas, "GAME_D_PRESSED", 1 << MidpContext.ACTION_GAME_D);
    }

    static Framebuffer backBuffer(MidpContext context, VmObject canvas) {
        if (!(canvas.host instanceof Framebuffer)) {
            Framebuffer back = new Framebuffer(context.canvasWidth(), context.canvasHeight());
            back.setAntialias(context.smoothShapes());
            canvas.host = back;
        }
        return (Framebuffer) canvas.host;
    }

    private static void flush(MidpContext context, VmObject canvas) {
        Framebuffer back = backBuffer(context, canvas);
        Framebuffer screen = context.screen();
        screen.setTranslation(0, 0);
        screen.resetClip();
        screen.setBlendMode(Framebuffer.BLEND_REPLACE);
        // The back buffer covers the canvas, not the whole display: the system
        // chrome above and below it stays put.
        screen.drawFramebuffer(back, context.canvasLeft(), context.canvasTop());
        screen.setBlendMode(Framebuffer.BLEND_SRC_OVER);
        context.markChromeDirty();
        context.countFrame();
    }

    // ------------------------------------------------------------ Display

    private static void display(final Vm vm, final MidpContext context) {
        vm.builtin(DISPLAY, Vm.OBJECT)
                .staticMethod("getDisplay", "(Ljavax/microedition/midlet/MIDlet;)Ljavax/microedition/lcdui/Display;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject display = vm.newInstance(DISPLAY);
                                display.host = context;
                                return display;
                            }
                        })
                .method("setCurrent", "(Ljavax/microedition/lcdui/Displayable;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject next = Rt.obj(args, 0);
                        context.setCurrent(next);
                        if (next != null) {
                            vm.callVirtual(next, "showNotify", "()V");
                        }
                        return null;
                    }
                })
                .method("getCurrent", "()Ljavax/microedition/lcdui/Displayable;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return context.current();
                    }
                })
                .method("callSerially", "(Ljava/lang/Runnable;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject runnable = Rt.obj(args, 0);
                        if (runnable != null) {
                            context.queueCallback(runnable);
                        }
                        return null;
                    }
                })
                .method("isColor", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .method("numColors", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(0x1000000);
                    }
                })
                .method("numAlphaLevels", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(256);
                    }
                })
                .method("flashBacklight", "(I)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(false);
                    }
                })
                .method("vibrate", "(I)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // The buzz on a crash or a hit was a J2ME game's only
                        // physical feedback, and until now every request for
                        // one was answered with "no".
                        return Rt.box(context.vibrate(Rt.i(args, 0)));
                    }
                })
                .define();
    }
}
