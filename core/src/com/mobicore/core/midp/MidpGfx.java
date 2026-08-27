package com.mobicore.core.midp;

import com.mobicore.core.gfx.BitmapFont;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.JpegReader;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.gfx.Transforms;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

import java.io.IOException;

/**
 * {@code javax.microedition.lcdui} drawing classes: Graphics, Image and Font.
 *
 * <p>All three sit directly on the portable {@link Framebuffer}, so what a game
 * paints, what the emulator shows and what a screenshot captures are by
 * construction the same pixels.</p>
 */
public final class MidpGfx {

    public static final String GRAPHICS = "javax/microedition/lcdui/Graphics";
    public static final String IMAGE = "javax/microedition/lcdui/Image";
    public static final String FONT = "javax/microedition/lcdui/Font";

    // Anchor points.
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    private MidpGfx() {
    }

    static Framebuffer surface(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof Framebuffer)) {
            throw vm.raise("java/lang/IllegalStateException", "Graphics target is not a surface");
        }
        return (Framebuffer) self.host;
    }

    /** Wraps a framebuffer in an emulated {@code Graphics}. */
    public static VmObject newGraphics(Vm vm, Framebuffer target) {
        VmObject graphics = vm.newInstance(GRAPHICS);
        graphics.host = target;
        graphics.setRef(fontSlot(vm), defaultFont(vm));
        return graphics;
    }

    private static int fontSlot(Vm vm) {
        return vm.loadClass(GRAPHICS).findField("font").slot();
    }

    /** Wraps pixels in an emulated {@code Image}. */
    public static VmObject newImage(Vm vm, Framebuffer pixels, boolean mutable) {
        VmObject image = vm.newInstance(IMAGE);
        image.host = pixels;
        image.set("mutable", Integer.valueOf(mutable ? 1 : 0));
        return image;
    }

    public static Framebuffer imageSurface(Vm vm, VmObject image) {
        if (image == null) {
            throw vm.nullPointer("image is null");
        }
        return (Framebuffer) image.host;
    }

    public static void install(final Vm vm) {
        graphics(vm);
        image(vm);
        font(vm);
    }

    // ----------------------------------------------------------- Graphics

    private static void graphics(final Vm vm) {
        vm.builtin(GRAPHICS, Vm.OBJECT)
                .field("font", "Ljavax/microedition/lcdui/Font;")
                .field("stroke", "I")
                .staticField("HCENTER", "I").staticField("VCENTER", "I")
                .staticField("LEFT", "I").staticField("RIGHT", "I")
                .staticField("TOP", "I").staticField("BOTTOM", "I")
                .staticField("BASELINE", "I")
                .staticField("SOLID", "I").staticField("DOTTED", "I")
                .method("setColor", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).setColor(0xFF000000 | (Rt.i(args, 0) & 0xFFFFFF));
                        return null;
                    }
                })
                .method("setColor", "(III)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).setColor(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                        return null;
                    }
                })
                .method("getColor", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(surface(vm, self).color() & 0xFFFFFF);
                    }
                })
                .method("setGrayScale", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int level = Rt.i(args, 0) & 0xFF;
                        surface(vm, self).setColor(level, level, level);
                        return null;
                    }
                })
                .method("getRedComponent", "()I", component(16))
                .method("getGreenComponent", "()I", component(8))
                .method("getBlueComponent", "()I", component(0))
                .method("setStrokeStyle", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("stroke", Integer.valueOf(Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("getStrokeStyle", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("stroke");
                    }
                })
                .method("translate", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).translate(Rt.i(args, 0), Rt.i(args, 1));
                        return null;
                    }
                })
                .method("getTranslateX", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(surface(vm, self).translateX());
                    }
                })
                .method("getTranslateY", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(surface(vm, self).translateY());
                    }
                })
                .method("setClip", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).setClip(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("clipRect", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).clipRect(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("getClipX", "()I", clip(0))
                .method("getClipY", "()I", clip(1))
                .method("getClipWidth", "()I", clip(2))
                .method("getClipHeight", "()I", clip(3))
                .method("drawLine", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).drawLine(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("drawRect", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).drawRect(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("fillRect", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).fillRect(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("drawRoundRect", "(IIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).drawRoundRect(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2),
                                Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("fillRoundRect", "(IIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).fillRoundRect(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2),
                                Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("drawArc", "(IIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).drawArc(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2),
                                Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("fillArc", "(IIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).fillArc(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2),
                                Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("fillTriangle", "(IIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        surface(vm, self).fillTriangle(Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2),
                                Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("setFont", "(Ljavax/microedition/lcdui/Font;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.setRef(fontSlot(vm), args[0] == null ? defaultFont(vm) : args[0]);
                        return null;
                    }
                })
                .method("getFont", "()Ljavax/microedition/lcdui/Font;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Object font = self.getRef(fontSlot(vm));
                        return font == null ? defaultFont(vm) : font;
                    }
                })
                .method("drawString", "(Ljava/lang/String;III)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        drawText(vm, self, Rt.s(vm, args, 0), Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("drawSubstring", "(Ljava/lang/String;IIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String text = Rt.s(vm, args, 0);
                        int offset = Rt.i(args, 1);
                        int length = Rt.i(args, 2);
                        drawText(vm, self, text.substring(offset, offset + length),
                                Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("drawChar", "(CIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        drawText(vm, self, String.valueOf((char) Rt.i(args, 0)),
                                Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        return null;
                    }
                })
                .method("drawChars", "([CIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String text = Rt.chars(Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                        drawText(vm, self, text, Rt.i(args, 3), Rt.i(args, 4), Rt.i(args, 5));
                        return null;
                    }
                })
                .method("drawImage", "(Ljavax/microedition/lcdui/Image;III)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject image = Rt.obj(args, 0);
                        if (image == null) {
                            throw vm.nullPointer("drawImage with a null image");
                        }
                        Framebuffer source = imageSurface(vm, image);
                        int[] anchored = anchor(Rt.i(args, 1), Rt.i(args, 2), Rt.i(args, 3),
                                source.width(), source.height(), 0);
                        surface(vm, self).drawPixels(source.pixels(), source.width(), source.height(),
                                anchored[0], anchored[1]);
                        return null;
                    }
                })
                .method("drawRegion", "(Ljavax/microedition/lcdui/Image;IIIIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        drawRegion(vm, self, args);
                        return null;
                    }
                })
                .method("drawRGB", "([IIIIIIIZ)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray rgb = Rt.array(args, 0);
                        int offset = Rt.i(args, 1);
                        int scanLength = Rt.i(args, 2);
                        int x = Rt.i(args, 3);
                        int y = Rt.i(args, 4);
                        int width = Rt.i(args, 5);
                        int height = Rt.i(args, 6);
                        boolean alpha = Rt.bool(args, 7);
                        Framebuffer target = surface(vm, self);
                        int[] source = rgb.ints();
                        for (int row = 0; row < height; row++) {
                            for (int column = 0; column < width; column++) {
                                int index = offset + row * scanLength + column;
                                if (index < 0 || index >= source.length) {
                                    continue;
                                }
                                int pixel = source[index];
                                target.blend(x + column + target.translateX(),
                                        y + row + target.translateY(),
                                        alpha ? pixel : (0xFF000000 | pixel));
                            }
                        }
                        return null;
                    }
                })
                .define();

        VmClass graphics = vm.loadClass(GRAPHICS);
        vm.initialize(graphics);
        setStatic(vm, graphics, "HCENTER", HCENTER);
        setStatic(vm, graphics, "VCENTER", VCENTER);
        setStatic(vm, graphics, "LEFT", LEFT);
        setStatic(vm, graphics, "RIGHT", RIGHT);
        setStatic(vm, graphics, "TOP", TOP);
        setStatic(vm, graphics, "BOTTOM", BOTTOM);
        setStatic(vm, graphics, "BASELINE", BASELINE);
        setStatic(vm, graphics, "SOLID", 0);
        setStatic(vm, graphics, "DOTTED", 1);
    }

    private static void drawRegion(Vm vm, VmObject self, Object[] args) {
        VmObject image = Rt.obj(args, 0);
        if (image == null) {
            throw vm.nullPointer("drawRegion with a null image");
        }
        Framebuffer source = imageSurface(vm, image);
        int sx = Rt.i(args, 1);
        int sy = Rt.i(args, 2);
        int width = Rt.i(args, 3);
        int height = Rt.i(args, 4);
        int transform = Rt.i(args, 5);
        int dx = Rt.i(args, 6);
        int dy = Rt.i(args, 7);
        int anchorFlags = Rt.i(args, 8);
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] block = Transforms.apply(source.pixels(), source.width(), source.height(),
                sx, sy, width, height, transform);
        int outWidth = Transforms.resultWidth(transform, width, height);
        int outHeight = Transforms.resultHeight(transform, width, height);
        int[] anchored = anchor(dx, dy, anchorFlags, outWidth, outHeight, 0);
        surface(vm, self).drawPixels(block, outWidth, outHeight, anchored[0], anchored[1]);
    }

    private static NativeMethod component(final int shift) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return Integer.valueOf((surface(vm, self).color() >> shift) & 0xFF);
            }
        };
    }

    private static NativeMethod clip(final int which) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                Framebuffer target = surface(vm, self);
                switch (which) {
                    case 0: return Integer.valueOf(target.clipX());
                    case 1: return Integer.valueOf(target.clipY());
                    case 2: return Integer.valueOf(target.clipWidth());
                    default: return Integer.valueOf(target.clipHeight());
                }
            }
        };
    }

    private static void drawText(Vm vm, VmObject self, String text, int x, int y, int anchorFlags) {
        if (text == null) {
            return;
        }
        Object fontRef = self.getRef(fontSlot(vm));
        BitmapFont font = fontOf(vm, (VmObject) fontRef);
        int width = font.stringWidth(text);
        int[] anchored = anchor(x, y, anchorFlags, width, font.height(), font.ascent());
        font.draw(surface(vm, self), text, anchored[0], anchored[1]);
    }

    /** Resolves an anchor to the top-left corner of the drawn block. */
    static int[] anchor(int x, int y, int flags, int width, int height, int ascent) {
        int left = x;
        int top = y;
        if ((flags & HCENTER) != 0) {
            left = x - width / 2;
        } else if ((flags & RIGHT) != 0) {
            left = x - width;
        }
        if ((flags & VCENTER) != 0) {
            top = y - height / 2;
        } else if ((flags & BOTTOM) != 0) {
            top = y - height;
        } else if ((flags & BASELINE) != 0) {
            top = y - ascent;
        }
        return new int[]{left, top};
    }

    static void setStatic(Vm vm, VmClass type, String field, int value) {
        type.staticInts()[type.findField(field).slot()] = value;
    }

    // -------------------------------------------------------------- Image

    private static void image(final Vm vm) {
        vm.builtin(IMAGE, Vm.OBJECT)
                .field("mutable", "I")
                .method("getWidth", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(imageSurface(vm, self).width());
                    }
                })
                .method("getHeight", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(imageSurface(vm, self).height());
                    }
                })
                .method("isMutable", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(((Integer) self.get("mutable")).intValue() != 0);
                    }
                })
                .method("getGraphics", "()Ljavax/microedition/lcdui/Graphics;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (((Integer) self.get("mutable")).intValue() == 0) {
                            throw vm.raise("java/lang/IllegalStateException",
                                    "getGraphics on an immutable image");
                        }
                        return newGraphics(vm, imageSurface(vm, self));
                    }
                })
                .method("getRGB", "([IIIIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray target = Rt.array(args, 0);
                        int offset = Rt.i(args, 1);
                        int scanLength = Rt.i(args, 2);
                        int x = Rt.i(args, 3);
                        int y = Rt.i(args, 4);
                        int width = Rt.i(args, 5);
                        int height = Rt.i(args, 6);
                        Framebuffer source = imageSurface(vm, self);
                        int[] out = target.ints();
                        for (int row = 0; row < height; row++) {
                            for (int column = 0; column < width; column++) {
                                int index = offset + row * scanLength + column;
                                if (index < 0 || index >= out.length) {
                                    continue;
                                }
                                out[index] = source.pixel(x + column, y + row);
                            }
                        }
                        return null;
                    }
                })
                .staticMethod("createImage", "(II)Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int width = Rt.i(args, 0);
                        int height = Rt.i(args, 1);
                        if (width <= 0 || height <= 0) {
                            throw vm.raise("java/lang/IllegalArgumentException", "image size must be positive");
                        }
                        Framebuffer surface = new Framebuffer(width, height);
                        surface.fill(0xFFFFFFFF);
                        return newImage(vm, surface, true);
                    }
                })
                .staticMethod("createImage", "(Ljava/lang/String;)Ljavax/microedition/lcdui/Image;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                String path = Rt.s(vm, args, 0);
                                byte[] data = null;
                                for (int i = vm.sources().size() - 1; i >= 0 && data == null; i--) {
                                    data = vm.sources().get(i).resourceBytes(path);
                                }
                                if (data == null) {
                                    throw vm.raise("java/io/IOException", "No such image: " + path);
                                }
                                return decodeImage(vm, data, 0, data.length);
                            }
                        })
                .staticMethod("createImage", "([BII)Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return decodeImage(vm, Rt.array(args, 0).bytes(), Rt.i(args, 1), Rt.i(args, 2));
                    }
                })
                .staticMethod("createImage", "(Ljavax/microedition/lcdui/Image;)Ljavax/microedition/lcdui/Image;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return newImage(vm, imageSurface(vm, Rt.obj(args, 0)).copy(), false);
                            }
                        })
                .staticMethod("createRGBImage", "([IIIZ)Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray rgb = Rt.array(args, 0);
                        int width = Rt.i(args, 1);
                        int height = Rt.i(args, 2);
                        boolean alpha = Rt.bool(args, 3);
                        int[] pixels = new int[width * height];
                        int[] source = rgb.ints();
                        for (int i = 0; i < pixels.length && i < source.length; i++) {
                            pixels[i] = alpha ? source[i] : (0xFF000000 | source[i]);
                        }
                        return newImage(vm, Framebuffer.wrap(pixels, width, height), false);
                    }
                })
                .staticMethod("createImage",
                        "(Ljavax/microedition/lcdui/Image;IIIII)Ljavax/microedition/lcdui/Image;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                Framebuffer source = imageSurface(vm, Rt.obj(args, 0));
                                int x = Rt.i(args, 1);
                                int y = Rt.i(args, 2);
                                int width = Rt.i(args, 3);
                                int height = Rt.i(args, 4);
                                int transform = Rt.i(args, 5);
                                int[] block = Transforms.apply(source.pixels(), source.width(),
                                        source.height(), x, y, width, height, transform);
                                return newImage(vm, Framebuffer.wrap(block,
                                        Transforms.resultWidth(transform, width, height),
                                        Transforms.resultHeight(transform, width, height)), false);
                            }
                        })
                .staticMethod("createImage", "(Ljava/io/InputStream;)Ljavax/microedition/lcdui/Image;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject stream = Rt.obj(args, 0);
                                if (stream == null || !(stream.host instanceof java.io.InputStream)) {
                                    throw vm.raise("java/io/IOException", "Not a readable stream");
                                }
                                try {
                                    java.io.InputStream in = (java.io.InputStream) stream.host;
                                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                                    byte[] buffer = new byte[4096];
                                    int count;
                                    while ((count = in.read(buffer)) > 0) {
                                        out.write(buffer, 0, count);
                                    }
                                    byte[] data = out.toByteArray();
                                    return decodeImage(vm, data, 0, data.length);
                                } catch (IOException e) {
                                    throw vm.raise("java/io/IOException", "Cannot read image");
                                }
                            }
                        })
                .define();
    }

    private static VmObject decodeImage(Vm vm, byte[] data, int offset, int length) {
        byte[] slice = data;
        if (offset != 0 || length != data.length) {
            slice = new byte[length];
            System.arraycopy(data, offset, slice, 0, length);
        }
        try {
            // MIDP chỉ bắt buộc PNG, nhưng máy thật đọc thêm JPEG và game biết
            // thế: ảnh mở đầu, ảnh nền, ảnh nhân vật — những thứ to và nhiều
            // màu — hay được đóng gói bằng JPEG cho nhẹ.
            if (JpegReader.looksLikeJpeg(slice)) {
                JpegReader.Image photo = JpegReader.decode(slice);
                return newImage(vm, Framebuffer.wrap(photo.pixels, photo.width, photo.height),
                        false);
            }
            PngReader.Image decoded = PngReader.decode(slice);
            return newImage(vm, Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height), false);
        } catch (IOException e) {
            throw vm.raise("java/io/IOException", "Unsupported image format: " + e.getMessage());
        }
    }

    // --------------------------------------------------------------- Font

    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_LARGE = 16;

    /** Maps an emulated Font instance to the bitmap face that renders it. */
    static BitmapFont fontOf(Vm vm, VmObject font) {
        if (font == null) {
            return BitmapFont.of(BitmapFont.SIZE_MEDIUM, BitmapFont.STYLE_PLAIN);
        }
        int size = ((Integer) font.get("size")).intValue();
        int style = ((Integer) font.get("style")).intValue();
        int mapped;
        if (size == SIZE_SMALL) {
            mapped = BitmapFont.SIZE_SMALL;
        } else if (size == SIZE_LARGE) {
            mapped = BitmapFont.SIZE_LARGE;
        } else {
            mapped = BitmapFont.SIZE_MEDIUM;
        }
        return BitmapFont.of(mapped, style);
    }

    static VmObject defaultFont(Vm vm) {
        return newFont(vm, FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
    }

    static VmObject newFont(Vm vm, int face, int style, int size) {
        VmObject font = vm.newInstance(FONT);
        font.set("face", Integer.valueOf(face));
        font.set("style", Integer.valueOf(style));
        font.set("size", Integer.valueOf(size));
        return font;
    }

    private static void font(final Vm vm) {
        vm.builtin(FONT, Vm.OBJECT)
                .field("face", "I").field("style", "I").field("size", "I")
                .staticField("FACE_SYSTEM", "I").staticField("FACE_MONOSPACE", "I")
                .staticField("FACE_PROPORTIONAL", "I")
                .staticField("STYLE_PLAIN", "I").staticField("STYLE_BOLD", "I")
                .staticField("STYLE_ITALIC", "I").staticField("STYLE_UNDERLINED", "I")
                .staticField("SIZE_SMALL", "I").staticField("SIZE_MEDIUM", "I")
                .staticField("SIZE_LARGE", "I")
                .method("getHeight", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(fontOf(vm, self).height());
                    }
                })
                .method("getBaselinePosition", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(fontOf(vm, self).ascent());
                    }
                })
                .method("stringWidth", "(Ljava/lang/String;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(fontOf(vm, self).stringWidth(Rt.s(vm, args, 0)));
                    }
                })
                .method("substringWidth", "(Ljava/lang/String;II)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String text = Rt.s(vm, args, 0);
                        int offset = Rt.i(args, 1);
                        int length = Rt.i(args, 2);
                        return Integer.valueOf(fontOf(vm, self)
                                .stringWidth(text.substring(offset, offset + length)));
                    }
                })
                .method("charWidth", "(C)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(fontOf(vm, self).charWidth((char) Rt.i(args, 0)));
                    }
                })
                .method("charsWidth", "([CII)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(fontOf(vm, self)
                                .stringWidth(Rt.chars(Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2))));
                    }
                })
                .method("getFace", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("face");
                    }
                })
                .method("getStyle", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("style");
                    }
                })
                .method("getSize", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("size");
                    }
                })
                .method("isBold", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box((((Integer) self.get("style")).intValue() & STYLE_BOLD) != 0);
                    }
                })
                .method("isItalic", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box((((Integer) self.get("style")).intValue() & STYLE_ITALIC) != 0);
                    }
                })
                .method("isUnderlined", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box((((Integer) self.get("style")).intValue() & STYLE_UNDERLINED) != 0);
                    }
                })
                .staticMethod("getDefaultFont", "()Ljavax/microedition/lcdui/Font;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return defaultFont(vm);
                    }
                })
                .staticMethod("getFont", "(III)Ljavax/microedition/lcdui/Font;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return newFont(vm, Rt.i(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                    }
                })
                .staticMethod("getFont", "(I)Ljavax/microedition/lcdui/Font;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return defaultFont(vm);
                    }
                })
                .define();

        VmClass font = vm.loadClass(FONT);
        vm.initialize(font);
        setStatic(vm, font, "FACE_SYSTEM", FACE_SYSTEM);
        setStatic(vm, font, "FACE_MONOSPACE", FACE_MONOSPACE);
        setStatic(vm, font, "FACE_PROPORTIONAL", FACE_PROPORTIONAL);
        setStatic(vm, font, "STYLE_PLAIN", STYLE_PLAIN);
        setStatic(vm, font, "STYLE_BOLD", STYLE_BOLD);
        setStatic(vm, font, "STYLE_ITALIC", STYLE_ITALIC);
        setStatic(vm, font, "STYLE_UNDERLINED", STYLE_UNDERLINED);
        setStatic(vm, font, "SIZE_SMALL", SIZE_SMALL);
        setStatic(vm, font, "SIZE_MEDIUM", SIZE_MEDIUM);
        setStatic(vm, font, "SIZE_LARGE", SIZE_LARGE);
    }
}
