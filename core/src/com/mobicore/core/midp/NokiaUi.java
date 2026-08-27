package com.mobicore.core.midp;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

/**
 * {@code com.nokia.mid.ui}: the API a large share of these games were built
 * against.
 *
 * <p>Nokia sold most of the handsets, so most of the games target Nokia's own
 * additions — and a game that extends {@code FullCanvas} does not run badly
 * without them, it does not load at all: the class loader fails on the
 * superclass before a single frame. That is the worst kind of failure to show
 * someone, because nothing on screen explains it.</p>
 *
 * <p>What is here is what those games actually use. {@code FullCanvas} is a
 * Canvas that owns the whole screen and refuses to carry commands, which is
 * exactly what it was. {@code DirectGraphics} is the drawing surface behind
 * it: pixel arrays in and out, polygons, and images drawn rotated or
 * mirrored — the operations MIDP itself had no answer for. {@code
 * DeviceControl} is the handset's lights and vibration, which the emulator
 * can honestly do nothing about beyond not crashing.</p>
 */
public final class NokiaUi {

    public static final String FULL_CANVAS = "com/nokia/mid/ui/FullCanvas";
    public static final String DIRECT_GRAPHICS = "com/nokia/mid/ui/DirectGraphics";
    public static final String DIRECT_UTILS = "com/nokia/mid/ui/DirectUtils";
    public static final String DEVICE_CONTROL = "com/nokia/mid/ui/DeviceControl";

    /** Pixel layouts {@code DirectGraphics} names; these two are what games ask for. */
    public static final int TYPE_INT_888_RGB = 0x0888;
    public static final int TYPE_INT_8888_ARGB = 0x8888;

    /** Image manipulations, as Nokia numbered them. */
    public static final int ROTATE_90 = 90;
    public static final int ROTATE_180 = 180;
    public static final int ROTATE_270 = 270;
    public static final int FLIP_HORIZONTAL = 0x2000;
    public static final int FLIP_VERTICAL = 0x4000;

    private NokiaUi() {
    }

    public static void install(final Vm vm, final MidpContext context) {
        fullCanvas(vm, context);
        directGraphics(vm);
        directUtils(vm);
        deviceControl(vm, context);
    }

    // --------------------------------------------------------- FullCanvas

    /**
     * A Canvas that is always full screen and never has commands.
     *
     * <p>Nokia's own class predates {@code setFullScreenMode}, and its key
     * codes are the ones its handsets sent — which is why a game written
     * against it reads {@code KEY_SOFTKEY1} rather than a MIDP constant.</p>
     */
    private static void fullCanvas(final Vm vm, final MidpContext context) {
        vm.builtin(FULL_CANVAS, MidpUi.CANVAS)
                .staticField("KEY_SOFTKEY1", "I")
                .staticField("KEY_SOFTKEY2", "I")
                .staticField("KEY_SOFTKEY3", "I")
                .staticField("KEY_UP_ARROW", "I")
                .staticField("KEY_DOWN_ARROW", "I")
                .staticField("KEY_LEFT_ARROW", "I")
                .staticField("KEY_RIGHT_ARROW", "I")
                .staticField("KEY_SEND", "I")
                .staticField("KEY_END", "I")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Full screen from the moment it exists: that is the
                        // whole difference between this and a Canvas.
                        context.setFullScreen(true);
                        return null;
                    }
                })
                // Nokia's FullCanvas throws if a game tries to add a command,
                // and a game that catches that is relying on it.
                .method("addCommand", "(Ljavax/microedition/lcdui/Command;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        throw vm.raise("java/lang/IllegalStateException",
                                "FullCanvas không nhận lệnh");
                    }
                })
                .method("setCommandListener",
                        "(Ljavax/microedition/lcdui/CommandListener;)V", new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                throw vm.raise("java/lang/IllegalStateException",
                                        "FullCanvas không nhận lệnh");
                            }
                        })
                .define();

        VmClass canvas = vm.loadClass(FULL_CANVAS);
        MidpGfx.setStatic(vm, canvas, "KEY_SOFTKEY1", -6);
        MidpGfx.setStatic(vm, canvas, "KEY_SOFTKEY2", -7);
        MidpGfx.setStatic(vm, canvas, "KEY_SOFTKEY3", -5);
        MidpGfx.setStatic(vm, canvas, "KEY_UP_ARROW", -1);
        MidpGfx.setStatic(vm, canvas, "KEY_DOWN_ARROW", -2);
        MidpGfx.setStatic(vm, canvas, "KEY_LEFT_ARROW", -3);
        MidpGfx.setStatic(vm, canvas, "KEY_RIGHT_ARROW", -4);
        MidpGfx.setStatic(vm, canvas, "KEY_SEND", -10);
        MidpGfx.setStatic(vm, canvas, "KEY_END", -11);
    }

    // ------------------------------------------------------ DirectGraphics

    /**
     * The drawing MIDP had no answer for: pixels in and out, filled
     * polygons, and images drawn turned or mirrored.
     *
     * <p>It wraps the same framebuffer the game's {@code Graphics} draws to,
     * because that is what it is: Nokia's class is a second view of one
     * surface, not a second surface.</p>
     */
    private static void directGraphics(final Vm vm) {
        vm.builtin(DIRECT_GRAPHICS, "java/lang/Object")
                .staticField("TYPE_INT_888_RGB", "I")
                .staticField("TYPE_INT_8888_ARGB", "I")
                .staticField("ROTATE_90", "I")
                .staticField("ROTATE_180", "I")
                .staticField("ROTATE_270", "I")
                .staticField("FLIP_HORIZONTAL", "I")
                .staticField("FLIP_VERTICAL", "I")
                .field("argb", "I")
                .method("setARGBColor", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int argb = Rt.i(args, 0);
                        self.set("argb", Integer.valueOf(argb));
                        surface(vm, self).setColor(argb);
                        return null;
                    }
                })
                .method("getARGBColor", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Object stored = self.get("argb");
                        return stored instanceof Integer ? stored : Integer.valueOf(0);
                    }
                })
                .method("fillTriangle", "(IIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).fillTriangle(Rt.i(args, 0), Rt.i(args, 1),
                                Rt.i(args, 2), Rt.i(args, 3),
                                Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("drawPolygon", "([II[IIII)V", polygon(false))
                .method("fillPolygon", "([II[IIII)V", polygon(true))
                .method("drawPixels", "([IZIIIIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int[] pixels = ints(vm, args[0]);
                        boolean hasAlpha = Rt.bool(args, 1);
                        int offset = Rt.i(args, 2);
                        int scanLength = Rt.i(args, 3);
                        int x = Rt.i(args, 4);
                        int y = Rt.i(args, 5);
                        int width = Rt.i(args, 6);
                        int height = Rt.i(args, 7);
                        Framebuffer target = surface(vm, self);
                        for (int row = 0; row < height; row++) {
                            for (int column = 0; column < width; column++) {
                                int at = offset + row * scanLength + column;
                                if (at < 0 || at >= pixels.length) {
                                    continue;
                                }
                                int argb = pixels[at];
                                if (!hasAlpha) {
                                    argb |= 0xFF000000;
                                }
                                target.blendPixel(x + column, y + row, argb);
                            }
                        }
                        return null;
                    }
                })
                .method("getPixels", "([IIIIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int[] pixels = ints(vm, args[0]);
                        int offset = Rt.i(args, 1);
                        int scanLength = Rt.i(args, 2);
                        int x = Rt.i(args, 3);
                        int y = Rt.i(args, 4);
                        int width = Rt.i(args, 5);
                        int height = Rt.i(args, 6);
                        Framebuffer source = surface(vm, self);
                        for (int row = 0; row < height; row++) {
                            for (int column = 0; column < width; column++) {
                                int at = offset + row * scanLength + column;
                                if (at < 0 || at >= pixels.length) {
                                    continue;
                                }
                                pixels[at] = source.pixel(x + column, y + row);
                            }
                        }
                        return null;
                    }
                })
                .method("drawImage", "(Ljavax/microedition/lcdui/Image;IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject image = (VmObject) args[0];
                        Framebuffer source = MidpGfx.imageSurface(vm, image);
                        Framebuffer turned = manipulate(source, Rt.i(args, 4));
                        surface(vm, self).drawFramebuffer(turned,
                                Rt.i(args, 1), Rt.i(args, 2));
                        return null;
                    }
                })
                .define();

        VmClass direct = vm.loadClass(DIRECT_GRAPHICS);
        MidpGfx.setStatic(vm, direct, "TYPE_INT_888_RGB", TYPE_INT_888_RGB);
        MidpGfx.setStatic(vm, direct, "TYPE_INT_8888_ARGB", TYPE_INT_8888_ARGB);
        MidpGfx.setStatic(vm, direct, "ROTATE_90", ROTATE_90);
        MidpGfx.setStatic(vm, direct, "ROTATE_180", ROTATE_180);
        MidpGfx.setStatic(vm, direct, "ROTATE_270", ROTATE_270);
        MidpGfx.setStatic(vm, direct, "FLIP_HORIZONTAL", FLIP_HORIZONTAL);
        MidpGfx.setStatic(vm, direct, "FLIP_VERTICAL", FLIP_VERTICAL);
    }

    /**
     * Draws or fills the polygon Nokia states as separate x and y arrays.
     *
     * <p>Filled by fanning triangles from the first point, which is right for
     * the convex shapes these games draw and is what the framebuffer can do
     * without a scanline filler of its own.</p>
     */
    private static NativeMethod polygon(final boolean fill) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                int[] xs = ints(vm, args[0]);
                int xOffset = Rt.i(args, 1);
                int[] ys = ints(vm, args[2]);
                int yOffset = Rt.i(args, 3);
                int points = Rt.i(args, 4);
                int argb = Rt.i(args, 5);
                Framebuffer target = surface(vm, self);
                target.setColor(argb);
                if (points < 2) {
                    return null;
                }
                // A fan's inner edges are shared by two triangles, and two
                // antialiased edges over each other leave a visible seam
                // through the middle of a solid shape. The silhouette is what
                // smoothing is for, so it is turned off for the fill and put
                // back afterwards.
                boolean smooth = target.antialias();
                if (fill) {
                    target.setAntialias(false);
                }
                for (int i = 0; i < points; i++) {
                    int nextIndex = (i + 1) % points;
                    int x1 = value(xs, xOffset + i);
                    int y1 = value(ys, yOffset + i);
                    int x2 = value(xs, xOffset + nextIndex);
                    int y2 = value(ys, yOffset + nextIndex);
                    if (fill && i >= 1 && nextIndex != 0) {
                        target.fillTriangle(value(xs, xOffset), value(ys, yOffset),
                                x1, y1, x2, y2);
                    } else if (!fill) {
                        target.drawLine(x1, y1, x2, y2);
                    }
                }
                target.setAntialias(smooth);
                return null;
            }
        };
    }

    // -------------------------------------------------------- DirectUtils

    private static void directUtils(final Vm vm) {
        vm.builtin(DIRECT_UTILS, "java/lang/Object")
                .staticMethod("getDirectGraphics",
                        "(Ljavax/microedition/lcdui/Graphics;)Lcom/nokia/mid/ui/DirectGraphics;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject graphics = (VmObject) args[0];
                                if (graphics == null) {
                                    throw vm.nullPointer("graphics is null");
                                }
                                VmObject direct = vm.newInstance(DIRECT_GRAPHICS);
                                // The same surface, not a copy: Nokia's class
                                // is a second view of one framebuffer.
                                direct.host = graphics.host;
                                return direct;
                            }
                        })
                .staticMethod("createImage", "(III)Ljavax/microedition/lcdui/Image;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                int width = Math.max(1, Rt.i(args, 0));
                                int height = Math.max(1, Rt.i(args, 1));
                                Framebuffer pixels = new Framebuffer(width, height);
                                pixels.fill(Rt.i(args, 2));
                                return MidpGfx.newImage(vm, pixels, true);
                            }
                        })
                .define();
    }

    // ------------------------------------------------------ DeviceControl

    /**
     * The handset's lights and vibration.
     *
     * <p>Vibration is real: it goes to the same place MIDP's own
     * {@code Display.vibrate} goes, which on a phone is the motor. The lights
     * are not — there is nothing honest to do with a request for a handset's
     * keypad backlight — so those calls are accepted and ignored. What matters
     * is that they exist: a game that flashes the lights on every explosion
     * would otherwise fail on its first explosion.</p>
     */
    private static void deviceControl(final Vm vm, final MidpContext context) {
        vm.builtin(DEVICE_CONTROL, "java/lang/Object")
                .staticMethod("setLights", "(II)V", ignored())
                .staticMethod("flashLights", "(J)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // False is the honest answer: the lights did not
                        // flash, and Nokia's own method says so this way.
                        return Rt.box(false);
                    }
                })
                .staticMethod("startVibra", "(IJ)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Nokia states a strength and a duration; the strength
                        // is a number no phone today takes, so the duration is
                        // what carries over.
                        context.vibrate((int) Rt.l(args, 1));
                        return null;
                    }
                })
                .staticMethod("stopVibra", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.vibration().cancel();
                        return null;
                    }
                })
                .staticMethod("setBacklightBrightness", "(I)V",
                        ignored())
                .define();
    }

    /**
     * Accepted and ignored.
     *
     * <p>There is nothing honest to do with a request for the handset's
     * lights here. What matters is that the call exists: a game that flashes
     * the lights on every explosion would otherwise fail on its first
     * explosion.</p>
     */
    private static NativeMethod ignored() {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return null;
            }
        };
    }

    // --------------------------------------------------------------- tools

    /** The framebuffer a DirectGraphics is a view of. */
    private static Framebuffer surface(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof Framebuffer)) {
            throw vm.raise("java/lang/IllegalStateException",
                    "DirectGraphics không gắn với bề mặt nào");
        }
        return (Framebuffer) self.host;
    }

    private static int[] ints(Vm vm, Object array) {
        if (!(array instanceof VmArray)) {
            throw vm.nullPointer("array is null");
        }
        return ((VmArray) array).ints();
    }

    private static int value(int[] values, int at) {
        return at >= 0 && at < values.length ? values[at] : 0;
    }

    /**
     * A turned or mirrored copy of an image.
     *
     * <p>Nokia's manipulations are why a game can ship one sprite sheet and
     * draw a character facing both ways; without them it draws every frame
     * the same way round.</p>
     */
    public static Framebuffer manipulate(Framebuffer source, int manipulation) {
        if (manipulation == 0) {
            return source;
        }
        int rotation = manipulation & 0x1FFF;
        boolean flipHorizontal = (manipulation & FLIP_HORIZONTAL) != 0;
        boolean flipVertical = (manipulation & FLIP_VERTICAL) != 0;
        boolean quarter = rotation == ROTATE_90 || rotation == ROTATE_270;

        int width = quarter ? source.height() : source.width();
        int height = quarter ? source.width() : source.height();
        Framebuffer out = new Framebuffer(width, height);
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                int sourceX = x;
                int sourceY = y;
                if (flipHorizontal) {
                    sourceX = source.width() - 1 - sourceX;
                }
                if (flipVertical) {
                    sourceY = source.height() - 1 - sourceY;
                }
                int targetX;
                int targetY;
                if (rotation == ROTATE_90) {
                    targetX = source.height() - 1 - y;
                    targetY = x;
                } else if (rotation == ROTATE_180) {
                    targetX = source.width() - 1 - x;
                    targetY = source.height() - 1 - y;
                } else if (rotation == ROTATE_270) {
                    targetX = y;
                    targetY = source.width() - 1 - x;
                } else {
                    targetX = x;
                    targetY = y;
                }
                out.blendPixel(targetX, targetY, source.pixel(sourceX, sourceY));
            }
        }
        return out;
    }
}
