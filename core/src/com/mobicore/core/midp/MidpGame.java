package com.mobicore.core.midp;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.Transforms;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code javax.microedition.lcdui.game} package: Layer, Sprite, TiledLayer
 * and LayerManager.
 *
 * <p>Position and visibility live in emulated fields because games read them
 * through the getters; the heavier per-object state (frame sequences, tile
 * grids, layer order) is kept host-side where it can be manipulated without
 * allocating in the render loop.</p>
 */
public final class MidpGame {

    public static final String LAYER = "javax/microedition/lcdui/game/Layer";
    public static final String SPRITE = "javax/microedition/lcdui/game/Sprite";
    public static final String TILED_LAYER = "javax/microedition/lcdui/game/TiledLayer";
    public static final String LAYER_MANAGER = "javax/microedition/lcdui/game/LayerManager";

    private MidpGame() {
    }

    /** Host-side sprite state. */
    static final class SpriteState {
        Framebuffer source;
        int frameWidth;
        int frameHeight;
        int columns;
        int frameCount;
        int[] sequence;
        int sequenceIndex;
        int transform = Transforms.NONE;
        int refX;
        int refY;
        int collisionX;
        int collisionY;
        int collisionWidth;
        int collisionHeight;
    }

    /** Host-side tiled layer state. */
    static final class TiledState {
        Framebuffer source;
        int tileWidth;
        int tileHeight;
        int columns;
        int rows;
        int[] cells;
        final List<int[]> animated = new ArrayList<int[]>();
    }

    static final class LayerList {
        final List<VmObject> layers = new ArrayList<VmObject>();
        int viewX;
        int viewY;
        int viewWidth = Integer.MAX_VALUE;
        int viewHeight = Integer.MAX_VALUE;
    }

    /**
     * A host state written down as plain numbers and references.
     *
     * <p>A save state has to write a sprite's frame, a tiled layer's cells
     * and a layer manager's list, but nothing outside this file should know
     * how those are held. So they come out as numbers and references and go
     * back in the same way, and the shapes stay private.</p>
     */
    public static final class HostState {

        public static final String SPRITE = "sprite";
        public static final String TILED = "tiled";
        public static final String LAYERS = "layers";

        public final String kind;
        public final int[] numbers;
        /** Either {@link VmObject} layers or a {@link Framebuffer} source. */
        public final Object[] refs;

        public HostState(String kind, int[] numbers, Object[] refs) {
            this.kind = kind;
            this.numbers = numbers;
            this.refs = refs;
        }
    }

    /** @return the state as plain data, or null if this is not game state */
    public static HostState capture(Object host) {
        if (host instanceof SpriteState) {
            SpriteState sprite = (SpriteState) host;
            int[] sequence = sprite.sequence == null ? new int[0] : sprite.sequence;
            int[] numbers = new int[12 + sequence.length];
            numbers[0] = sprite.frameWidth;
            numbers[1] = sprite.frameHeight;
            numbers[2] = sprite.columns;
            numbers[3] = sprite.frameCount;
            numbers[4] = sprite.sequenceIndex;
            numbers[5] = sprite.transform;
            numbers[6] = sprite.refX;
            numbers[7] = sprite.refY;
            numbers[8] = sprite.collisionX;
            numbers[9] = sprite.collisionY;
            numbers[10] = sprite.collisionWidth;
            numbers[11] = sprite.collisionHeight;
            System.arraycopy(sequence, 0, numbers, 12, sequence.length);
            return new HostState(HostState.SPRITE, numbers, new Object[]{sprite.source});
        }
        if (host instanceof TiledState) {
            TiledState tiled = (TiledState) host;
            int[] cells = tiled.cells == null ? new int[0] : tiled.cells;
            int animatedLength = 0;
            for (int i = 0; i < tiled.animated.size(); i++) {
                animatedLength += 1 + tiled.animated.get(i).length;
            }
            int[] numbers = new int[6 + cells.length + animatedLength];
            numbers[0] = tiled.tileWidth;
            numbers[1] = tiled.tileHeight;
            numbers[2] = tiled.columns;
            numbers[3] = tiled.rows;
            numbers[4] = cells.length;
            System.arraycopy(cells, 0, numbers, 5, cells.length);
            int at = 5 + cells.length;
            numbers[at++] = tiled.animated.size();
            for (int i = 0; i < tiled.animated.size(); i++) {
                int[] entry = tiled.animated.get(i);
                numbers[at++] = entry.length;
                System.arraycopy(entry, 0, numbers, at, entry.length);
                at += entry.length;
            }
            return new HostState(HostState.TILED, numbers, new Object[]{tiled.source});
        }
        if (host instanceof LayerList) {
            LayerList list = (LayerList) host;
            int[] numbers = {list.viewX, list.viewY, list.viewWidth, list.viewHeight};
            return new HostState(HostState.LAYERS, numbers,
                    list.layers.toArray(new Object[list.layers.size()]));
        }
        return null;
    }

    /** Rebuilds what {@link #capture} wrote down. */
    public static Object restore(HostState state) {
        int[] numbers = state.numbers;
        if (HostState.SPRITE.equals(state.kind)) {
            SpriteState sprite = new SpriteState();
            sprite.source = (Framebuffer) state.refs[0];
            sprite.frameWidth = numbers[0];
            sprite.frameHeight = numbers[1];
            sprite.columns = numbers[2];
            sprite.frameCount = numbers[3];
            sprite.sequenceIndex = numbers[4];
            sprite.transform = numbers[5];
            sprite.refX = numbers[6];
            sprite.refY = numbers[7];
            sprite.collisionX = numbers[8];
            sprite.collisionY = numbers[9];
            sprite.collisionWidth = numbers[10];
            sprite.collisionHeight = numbers[11];
            sprite.sequence = new int[numbers.length - 12];
            System.arraycopy(numbers, 12, sprite.sequence, 0, sprite.sequence.length);
            return sprite;
        }
        if (HostState.TILED.equals(state.kind)) {
            TiledState tiled = new TiledState();
            tiled.source = (Framebuffer) state.refs[0];
            tiled.tileWidth = numbers[0];
            tiled.tileHeight = numbers[1];
            tiled.columns = numbers[2];
            tiled.rows = numbers[3];
            int cellCount = numbers[4];
            tiled.cells = new int[cellCount];
            System.arraycopy(numbers, 5, tiled.cells, 0, cellCount);
            int at = 5 + cellCount;
            int animatedCount = numbers[at++];
            for (int i = 0; i < animatedCount; i++) {
                int length = numbers[at++];
                int[] entry = new int[length];
                System.arraycopy(numbers, at, entry, 0, length);
                at += length;
                tiled.animated.add(entry);
            }
            return tiled;
        }
        LayerList list = new LayerList();
        list.viewX = numbers[0];
        list.viewY = numbers[1];
        list.viewWidth = numbers[2];
        list.viewHeight = numbers[3];
        for (int i = 0; i < state.refs.length; i++) {
            list.layers.add((VmObject) state.refs[i]);
        }
        return list;
    }

    public static void install(final Vm vm) {
        layer(vm);
        sprite(vm);
        tiledLayer(vm);
        layerManager(vm);
    }

    private static int intField(VmObject self, String name) {
        return ((Integer) self.get(name)).intValue();
    }

    // -------------------------------------------------------------- Layer

    private static void layer(final Vm vm) {
        vm.builtin(LAYER, Vm.OBJECT)
                .field("x", "I").field("y", "I")
                .field("width", "I").field("height", "I")
                .field("visible", "I")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("visible", Integer.valueOf(1));
                        return null;
                    }
                })
                .method("setPosition", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("x", Integer.valueOf(Rt.i(args, 0)));
                        self.set("y", Integer.valueOf(Rt.i(args, 1)));
                        return null;
                    }
                })
                .method("move", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("x", Integer.valueOf(intField(self, "x") + Rt.i(args, 0)));
                        self.set("y", Integer.valueOf(intField(self, "y") + Rt.i(args, 1)));
                        return null;
                    }
                })
                .method("getX", "()I", getter("x"))
                .method("getY", "()I", getter("y"))
                .method("getWidth", "()I", getter("width"))
                .method("getHeight", "()I", getter("height"))
                .method("setVisible", "(Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("visible", Integer.valueOf(Rt.bool(args, 0) ? 1 : 0));
                        return null;
                    }
                })
                .method("isVisible", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(intField(self, "visible") != 0);
                    }
                })
                .method("paint", "(Ljavax/microedition/lcdui/Graphics;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .define();
    }

    private static NativeMethod getter(final String field) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return self.get(field);
            }
        };
    }

    // ------------------------------------------------------------- Sprite

    private static void sprite(final Vm vm) {
        vm.builtin(SPRITE, LAYER)
                .staticField("TRANS_NONE", "I").staticField("TRANS_MIRROR", "I")
                .staticField("TRANS_MIRROR_ROT90", "I").staticField("TRANS_MIRROR_ROT180", "I")
                .staticField("TRANS_MIRROR_ROT270", "I").staticField("TRANS_ROT90", "I")
                .staticField("TRANS_ROT180", "I").staticField("TRANS_ROT270", "I")
                .method("<init>", "(Ljavax/microedition/lcdui/Image;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Framebuffer image = MidpGfx.imageSurface(vm, Rt.obj(args, 0));
                        init(self, image, image.width(), image.height());
                        return null;
                    }
                })
                .method("<init>", "(Ljavax/microedition/lcdui/Image;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Framebuffer image = MidpGfx.imageSurface(vm, Rt.obj(args, 0));
                        int frameWidth = Rt.i(args, 1);
                        int frameHeight = Rt.i(args, 2);
                        if (frameWidth <= 0 || frameHeight <= 0
                                || image.width() % frameWidth != 0 || image.height() % frameHeight != 0) {
                            throw vm.raise("java/lang/IllegalArgumentException",
                                    "Frame size does not tile the sprite image");
                        }
                        init(self, image, frameWidth, frameHeight);
                        return null;
                    }
                })
                .method("<init>", "(Ljavax/microedition/lcdui/game/Sprite;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState other = state(vm, Rt.obj(args, 0));
                        init(self, other.source, other.frameWidth, other.frameHeight);
                        return null;
                    }
                })
                .method("setImage", "(Ljavax/microedition/lcdui/Image;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        init(self, MidpGfx.imageSurface(vm, Rt.obj(args, 0)), Rt.i(args, 1), Rt.i(args, 2));
                        return null;
                    }
                })
                .method("getRawFrameCount", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(state(vm, self).frameCount);
                    }
                })
                .method("getFrameSequenceLength", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(state(vm, self).sequence.length);
                    }
                })
                .method("setFrameSequence", "([I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        VmArray sequence = Rt.array(args, 0);
                        if (sequence == null) {
                            sprite.sequence = defaultSequence(sprite.frameCount);
                        } else {
                            int[] values = sequence.ints();
                            for (int value : values) {
                                if (value < 0 || value >= sprite.frameCount) {
                                    throw vm.raise("java/lang/ArrayIndexOutOfBoundsException",
                                            "Frame " + value + " is outside the sprite");
                                }
                            }
                            sprite.sequence = values.clone();
                        }
                        sprite.sequenceIndex = 0;
                        return null;
                    }
                })
                .method("setFrame", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        int index = Rt.i(args, 0);
                        if (index < 0 || index >= sprite.sequence.length) {
                            throw vm.raise("java/lang/IndexOutOfBoundsException",
                                    "Frame index " + index);
                        }
                        sprite.sequenceIndex = index;
                        return null;
                    }
                })
                .method("getFrame", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(state(vm, self).sequenceIndex);
                    }
                })
                .method("nextFrame", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        sprite.sequenceIndex = (sprite.sequenceIndex + 1) % sprite.sequence.length;
                        return null;
                    }
                })
                .method("prevFrame", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        sprite.sequenceIndex = (sprite.sequenceIndex + sprite.sequence.length - 1)
                                % sprite.sequence.length;
                        return null;
                    }
                })
                .method("setTransform", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        sprite.transform = Rt.i(args, 0);
                        self.set("width", Integer.valueOf(Transforms.resultWidth(sprite.transform,
                                sprite.frameWidth, sprite.frameHeight)));
                        self.set("height", Integer.valueOf(Transforms.resultHeight(sprite.transform,
                                sprite.frameWidth, sprite.frameHeight)));
                        return null;
                    }
                })
                .method("defineReferencePixel", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        sprite.refX = Rt.i(args, 0);
                        sprite.refY = Rt.i(args, 1);
                        return null;
                    }
                })
                .method("setRefPixelPosition", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        self.set("x", Integer.valueOf(Rt.i(args, 0) - sprite.refX));
                        self.set("y", Integer.valueOf(Rt.i(args, 1) - sprite.refY));
                        return null;
                    }
                })
                .method("getRefPixelX", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(intField(self, "x") + state(vm, self).refX);
                    }
                })
                .method("getRefPixelY", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(intField(self, "y") + state(vm, self).refY);
                    }
                })
                .method("defineCollisionRectangle", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SpriteState sprite = state(vm, self);
                        sprite.collisionX = Rt.i(args, 0);
                        sprite.collisionY = Rt.i(args, 1);
                        sprite.collisionWidth = Rt.i(args, 2);
                        sprite.collisionHeight = Rt.i(args, 3);
                        return null;
                    }
                })
                .method("collidesWith", "(Ljavax/microedition/lcdui/game/Sprite;Z)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject other = Rt.obj(args, 0);
                        if (other == null || intField(self, "visible") == 0
                                || intField(other, "visible") == 0) {
                            return Rt.box(false);
                        }
                        int[] box = overlap(vm, self, other);
                        if (box == null) {
                            return Rt.box(false);
                        }
                        if (!Rt.bool(args, 1)) {
                            return Rt.box(true);
                        }
                        return Rt.box(pixelsTouch(frameOf(vm, self), intField(self, "x"),
                                intField(self, "y"), frameOf(vm, other),
                                intField(other, "x"), intField(other, "y"), box));
                    }
                })
                .method("collidesWith", "(Ljavax/microedition/lcdui/Image;IIZ)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject image = Rt.obj(args, 0);
                        if (image == null || intField(self, "visible") == 0) {
                            return Rt.box(false);
                        }
                        Framebuffer surface = MidpGfx.imageSurface(vm, image);
                        int imageX = Rt.i(args, 1);
                        int imageY = Rt.i(args, 2);
                        Block picture = new Block(surface.pixels(), surface.width(), surface.height());
                        int[] box = overlap(collisionBox(vm, self),
                                new int[]{imageX, imageY, surface.width(), surface.height()});
                        if (box == null) {
                            return Rt.box(false);
                        }
                        if (!Rt.bool(args, 3)) {
                            return Rt.box(true);
                        }
                        return Rt.box(pixelsTouch(frameOf(vm, self), intField(self, "x"),
                                intField(self, "y"), picture, imageX, imageY, box));
                    }
                })
                .method("collidesWith", "(Ljavax/microedition/lcdui/game/TiledLayer;Z)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject layer = Rt.obj(args, 0);
                        if (layer == null || intField(self, "visible") == 0
                                || intField(layer, "visible") == 0) {
                            return Rt.box(false);
                        }
                        return Rt.box(hitsTiles(vm, self, layer, Rt.bool(args, 1)));
                    }
                })
                .method("paint", "(Ljavax/microedition/lcdui/Graphics;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (intField(self, "visible") == 0) {
                            return null;
                        }
                        SpriteState sprite = state(vm, self);
                        int frame = sprite.sequence[sprite.sequenceIndex];
                        int sx = (frame % sprite.columns) * sprite.frameWidth;
                        int sy = (frame / sprite.columns) * sprite.frameHeight;
                        int[] block = Transforms.apply(sprite.source.pixels(), sprite.source.width(),
                                sprite.source.height(), sx, sy, sprite.frameWidth, sprite.frameHeight,
                                sprite.transform);
                        Framebuffer target = MidpGfx.surface(vm, Rt.obj(args, 0));
                        target.drawPixels(block,
                                Transforms.resultWidth(sprite.transform, sprite.frameWidth, sprite.frameHeight),
                                Transforms.resultHeight(sprite.transform, sprite.frameWidth, sprite.frameHeight),
                                intField(self, "x"), intField(self, "y"));
                        return null;
                    }
                })
                .define();

        com.mobicore.core.vm.VmClass sprite = vm.loadClass(SPRITE);
        vm.initialize(sprite);
        MidpGfx.setStatic(vm, sprite, "TRANS_NONE", Transforms.NONE);
        MidpGfx.setStatic(vm, sprite, "TRANS_MIRROR", Transforms.MIRROR);
        MidpGfx.setStatic(vm, sprite, "TRANS_MIRROR_ROT90", Transforms.MIRROR_ROT90);
        MidpGfx.setStatic(vm, sprite, "TRANS_MIRROR_ROT180", Transforms.MIRROR_ROT180);
        MidpGfx.setStatic(vm, sprite, "TRANS_MIRROR_ROT270", Transforms.MIRROR_ROT270);
        MidpGfx.setStatic(vm, sprite, "TRANS_ROT90", Transforms.ROT90);
        MidpGfx.setStatic(vm, sprite, "TRANS_ROT180", Transforms.ROT180);
        MidpGfx.setStatic(vm, sprite, "TRANS_ROT270", Transforms.ROT270);
    }

    private static void init(VmObject self, Framebuffer image, int frameWidth, int frameHeight) {
        SpriteState sprite = new SpriteState();
        sprite.source = image;
        sprite.frameWidth = frameWidth;
        sprite.frameHeight = frameHeight;
        sprite.columns = Math.max(1, image.width() / frameWidth);
        sprite.frameCount = sprite.columns * Math.max(1, image.height() / frameHeight);
        sprite.sequence = defaultSequence(sprite.frameCount);
        sprite.collisionWidth = frameWidth;
        sprite.collisionHeight = frameHeight;
        self.host = sprite;
        self.set("visible", Integer.valueOf(1));
        self.set("width", Integer.valueOf(frameWidth));
        self.set("height", Integer.valueOf(frameHeight));
    }

    private static int[] defaultSequence(int frameCount) {
        int[] sequence = new int[Math.max(1, frameCount)];
        for (int i = 0; i < sequence.length; i++) {
            sequence[i] = i;
        }
        return sequence;
    }

    static SpriteState state(Vm vm, VmObject self) {
        if (!(self.host instanceof SpriteState)) {
            throw vm.raise("java/lang/IllegalStateException", "Sprite was not initialised");
        }
        return (SpriteState) self.host;
    }

    // ----------------------------------------------------------- collisions

    /**
     * A rectangle of pixels, kept alongside its size so a collision can look
     * one up without going back through the object it came from.
     */
    static final class Block {

        final int[] pixels;
        final int width;
        final int height;

        Block(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }

        /** True when this pixel is drawn at all — anything but fully clear. */
        boolean opaque(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return false;
            }
            return (pixels[y * width + x] >>> 24) != 0;
        }
    }

    /** The sprite's current frame, turned the way it is drawn. */
    private static Block frameOf(Vm vm, VmObject self) {
        SpriteState sprite = state(vm, self);
        int frame = sprite.sequence[sprite.sequenceIndex];
        int sx = (frame % sprite.columns) * sprite.frameWidth;
        int sy = (frame / sprite.columns) * sprite.frameHeight;
        int[] block = Transforms.apply(sprite.source.pixels(), sprite.source.width(),
                sprite.source.height(), sx, sy, sprite.frameWidth, sprite.frameHeight,
                sprite.transform);
        return new Block(block,
                Transforms.resultWidth(sprite.transform, sprite.frameWidth, sprite.frameHeight),
                Transforms.resultHeight(sprite.transform, sprite.frameWidth, sprite.frameHeight));
    }

    /** Where on screen this sprite can be hit: x, y, width, height. */
    private static int[] collisionBox(Vm vm, VmObject self) {
        SpriteState sprite = state(vm, self);
        return new int[]{
                intField(self, "x") + sprite.collisionX,
                intField(self, "y") + sprite.collisionY,
                sprite.collisionWidth,
                sprite.collisionHeight,
        };
    }

    /** The part two collision rectangles share, or null when they miss. */
    private static int[] overlap(int[] a, int[] b) {
        int left = Math.max(a[0], b[0]);
        int top = Math.max(a[1], b[1]);
        int right = Math.min(a[0] + a[2], b[0] + b[2]);
        int bottom = Math.min(a[1] + a[3], b[1] + b[3]);
        if (left >= right || top >= bottom) {
            return null;
        }
        return new int[]{left, top, right - left, bottom - top};
    }

    private static int[] overlap(Vm vm, VmObject a, VmObject b) {
        return overlap(collisionBox(vm, a), collisionBox(vm, b));
    }

    /**
     * True when the two pictures have a drawn pixel in the same place.
     *
     * <p>This is what {@code pixelLevel} was always meant to mean and what
     * the emulator used to throw away: two sprites whose boxes touch at a
     * transparent corner have not collided, and a game that says they have
     * kills the player for walking near something.</p>
     */
    private static boolean pixelsTouch(Block first, int firstX, int firstY,
                                       Block second, int secondX, int secondY, int[] box) {
        for (int y = box[1]; y < box[1] + box[3]; y++) {
            for (int x = box[0]; x < box[0] + box[2]; x++) {
                if (first.opaque(x - firstX, y - firstY)
                        && second.opaque(x - secondX, y - secondY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A sprite against a tiled layer, cell by cell.
     *
     * <p>Only the cells the sprite actually reaches are looked at, and an
     * empty cell is not a collision however wide the layer is — a layer is
     * mostly holes, and treating its bounding box as solid would make the
     * whole screen a wall.</p>
     */
    private static boolean hitsTiles(Vm vm, VmObject self, VmObject layer, boolean pixelLevel) {
        TiledState tiled = tiled(vm, layer);
        int[] reach = collisionBox(vm, self);
        int originX = intField(layer, "x");
        int originY = intField(layer, "y");
        int firstColumn = Math.max(0, (reach[0] - originX) / tiled.tileWidth);
        int firstRow = Math.max(0, (reach[1] - originY) / tiled.tileHeight);
        int lastColumn = Math.min(tiled.columns - 1,
                (reach[0] + reach[2] - 1 - originX) / tiled.tileWidth);
        int lastRow = Math.min(tiled.rows - 1,
                (reach[1] + reach[3] - 1 - originY) / tiled.tileHeight);
        if (reach[0] + reach[2] <= originX || reach[1] + reach[3] <= originY) {
            return false;
        }

        Block frame = pixelLevel ? frameOf(vm, self) : null;
        int tilesPerRow = Math.max(1, tiled.source.width() / tiled.tileWidth);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = firstColumn; column <= lastColumn; column++) {
                int tile = tiled.cells[row * tiled.columns + column];
                if (tile < 0) {
                    int index = -tile - 1;
                    tile = index < tiled.animated.size() ? tiled.animated.get(index)[0] : 0;
                }
                if (tile <= 0) {
                    continue;
                }
                int cellX = originX + column * tiled.tileWidth;
                int cellY = originY + row * tiled.tileHeight;
                int[] box = overlap(reach,
                        new int[]{cellX, cellY, tiled.tileWidth, tiled.tileHeight});
                if (box == null) {
                    continue;
                }
                if (!pixelLevel) {
                    return true;
                }
                int sx = ((tile - 1) % tilesPerRow) * tiled.tileWidth;
                int sy = ((tile - 1) / tilesPerRow) * tiled.tileHeight;
                Block cell = new Block(Transforms.apply(tiled.source.pixels(),
                        tiled.source.width(), tiled.source.height(), sx, sy,
                        tiled.tileWidth, tiled.tileHeight, Transforms.NONE),
                        tiled.tileWidth, tiled.tileHeight);
                if (pixelsTouch(frame, intField(self, "x"), intField(self, "y"),
                        cell, cellX, cellY, box)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --------------------------------------------------------- TiledLayer

    private static void tiledLayer(final Vm vm) {
        vm.builtin(TILED_LAYER, LAYER)
                .method("<init>", "(IILjavax/microedition/lcdui/Image;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int columns = Rt.i(args, 0);
                        int rows = Rt.i(args, 1);
                        Framebuffer image = MidpGfx.imageSurface(vm, Rt.obj(args, 2));
                        int tileWidth = Rt.i(args, 3);
                        int tileHeight = Rt.i(args, 4);
                        if (columns <= 0 || rows <= 0 || tileWidth <= 0 || tileHeight <= 0) {
                            throw vm.raise("java/lang/IllegalArgumentException", "Invalid tiled layer size");
                        }
                        TiledState tiled = new TiledState();
                        tiled.source = image;
                        tiled.tileWidth = tileWidth;
                        tiled.tileHeight = tileHeight;
                        tiled.columns = columns;
                        tiled.rows = rows;
                        tiled.cells = new int[columns * rows];
                        self.host = tiled;
                        self.set("visible", Integer.valueOf(1));
                        self.set("width", Integer.valueOf(columns * tileWidth));
                        self.set("height", Integer.valueOf(rows * tileHeight));
                        return null;
                    }
                })
                .method("setCell", "(III)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        TiledState tiled = tiled(vm, self);
                        tiled.cells[cellIndex(vm, tiled, Rt.i(args, 0), Rt.i(args, 1))] = Rt.i(args, 2);
                        return null;
                    }
                })
                .method("getCell", "(II)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        TiledState tiled = tiled(vm, self);
                        return Integer.valueOf(tiled.cells[cellIndex(vm, tiled, Rt.i(args, 0), Rt.i(args, 1))]);
                    }
                })
                .method("fillCells", "(IIIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        TiledState tiled = tiled(vm, self);
                        int column = Rt.i(args, 0);
                        int row = Rt.i(args, 1);
                        int columns = Rt.i(args, 2);
                        int rows = Rt.i(args, 3);
                        int tile = Rt.i(args, 4);
                        for (int r = row; r < row + rows; r++) {
                            for (int c = column; c < column + columns; c++) {
                                tiled.cells[cellIndex(vm, tiled, c, r)] = tile;
                            }
                        }
                        return null;
                    }
                })
                .method("createAnimatedTile", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        TiledState tiled = tiled(vm, self);
                        tiled.animated.add(new int[]{Rt.i(args, 0)});
                        return Integer.valueOf(-tiled.animated.size());
                    }
                })
                .method("setAnimatedTile", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        TiledState tiled = tiled(vm, self);
                        int index = -Rt.i(args, 0) - 1;
                        if (index < 0 || index >= tiled.animated.size()) {
                            throw vm.raise("java/lang/IndexOutOfBoundsException", "No such animated tile");
                        }
                        tiled.animated.get(index)[0] = Rt.i(args, 1);
                        return null;
                    }
                })
                .method("getAnimatedTile", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        TiledState tiled = tiled(vm, self);
                        int index = -Rt.i(args, 0) - 1;
                        if (index < 0 || index >= tiled.animated.size()) {
                            throw vm.raise("java/lang/IndexOutOfBoundsException", "No such animated tile");
                        }
                        return Integer.valueOf(tiled.animated.get(index)[0]);
                    }
                })
                .method("getCellWidth", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(tiled(vm, self).tileWidth);
                    }
                })
                .method("getCellHeight", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(tiled(vm, self).tileHeight);
                    }
                })
                .method("getColumns", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(tiled(vm, self).columns);
                    }
                })
                .method("getRows", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(tiled(vm, self).rows);
                    }
                })
                .method("paint", "(Ljavax/microedition/lcdui/Graphics;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (intField(self, "visible") == 0) {
                            return null;
                        }
                        TiledState tiled = tiled(vm, self);
                        Framebuffer target = MidpGfx.surface(vm, Rt.obj(args, 0));
                        int originX = intField(self, "x");
                        int originY = intField(self, "y");
                        int tilesPerRow = Math.max(1, tiled.source.width() / tiled.tileWidth);
                        for (int row = 0; row < tiled.rows; row++) {
                            for (int column = 0; column < tiled.columns; column++) {
                                int tile = tiled.cells[row * tiled.columns + column];
                                if (tile < 0) {
                                    int index = -tile - 1;
                                    tile = index < tiled.animated.size() ? tiled.animated.get(index)[0] : 0;
                                }
                                if (tile <= 0) {
                                    continue;
                                }
                                int sx = ((tile - 1) % tilesPerRow) * tiled.tileWidth;
                                int sy = ((tile - 1) / tilesPerRow) * tiled.tileHeight;
                                target.drawRegion(tiled.source.pixels(), tiled.source.width(),
                                        tiled.source.height(), sx, sy, tiled.tileWidth, tiled.tileHeight,
                                        originX + column * tiled.tileWidth,
                                        originY + row * tiled.tileHeight);
                            }
                        }
                        return null;
                    }
                })
                .define();
    }

    static TiledState tiled(Vm vm, VmObject self) {
        if (!(self.host instanceof TiledState)) {
            throw vm.raise("java/lang/IllegalStateException", "TiledLayer was not initialised");
        }
        return (TiledState) self.host;
    }

    private static int cellIndex(Vm vm, TiledState tiled, int column, int row) {
        if (column < 0 || row < 0 || column >= tiled.columns || row >= tiled.rows) {
            throw vm.raise("java/lang/IndexOutOfBoundsException", "Cell " + column + "," + row);
        }
        return row * tiled.columns + column;
    }

    // ------------------------------------------------------- LayerManager

    private static void layerManager(final Vm vm) {
        vm.builtin(LAYER_MANAGER, Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new LayerList();
                        return null;
                    }
                })
                .method("append", "(Ljavax/microedition/lcdui/game/Layer;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(vm, self).layers.add(Rt.obj(args, 0));
                        return null;
                    }
                })
                .method("insert", "(Ljavax/microedition/lcdui/game/Layer;I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        LayerList layers = list(vm, self);
                        int index = Rt.i(args, 1);
                        if (index < 0 || index > layers.layers.size()) {
                            throw vm.raise("java/lang/IndexOutOfBoundsException", "Layer index " + index);
                        }
                        layers.layers.add(index, Rt.obj(args, 0));
                        return null;
                    }
                })
                .method("remove", "(Ljavax/microedition/lcdui/game/Layer;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(vm, self).layers.remove(Rt.obj(args, 0));
                        return null;
                    }
                })
                .method("getSize", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(list(vm, self).layers.size());
                    }
                })
                .method("getLayerAt", "(I)Ljavax/microedition/lcdui/game/Layer;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        LayerList layers = list(vm, self);
                        int index = Rt.i(args, 0);
                        if (index < 0 || index >= layers.layers.size()) {
                            throw vm.raise("java/lang/IndexOutOfBoundsException", "Layer index " + index);
                        }
                        return layers.layers.get(index);
                    }
                })
                .method("setViewWindow", "(IIII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        LayerList layers = list(vm, self);
                        layers.viewX = Rt.i(args, 0);
                        layers.viewY = Rt.i(args, 1);
                        layers.viewWidth = Rt.i(args, 2);
                        layers.viewHeight = Rt.i(args, 3);
                        return null;
                    }
                })
                .method("paint", "(Ljavax/microedition/lcdui/Graphics;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        LayerList layers = list(vm, self);
                        VmObject graphics = Rt.obj(args, 0);
                        Framebuffer target = MidpGfx.surface(vm, graphics);
                        int x = Rt.i(args, 1);
                        int y = Rt.i(args, 2);

                        int savedX = target.translateX();
                        int savedY = target.translateY();
                        int clipX = target.clipX();
                        int clipY = target.clipY();
                        int clipWidth = target.clipWidth();
                        int clipHeight = target.clipHeight();

                        if (layers.viewWidth != Integer.MAX_VALUE) {
                            target.clipRect(x, y, layers.viewWidth, layers.viewHeight);
                        }
                        target.translate(x - layers.viewX, y - layers.viewY);
                        // Later layers paint on top, matching the specification's
                        // "index 0 is closest to the viewer" order reversed.
                        for (int i = layers.layers.size() - 1; i >= 0; i--) {
                            VmObject layer = layers.layers.get(i);
                            if (layer != null) {
                                vm.callVirtual(layer, "paint", "(Ljavax/microedition/lcdui/Graphics;)V", graphics);
                            }
                        }
                        target.setTranslation(savedX, savedY);
                        target.setClip(clipX, clipY, clipWidth, clipHeight);
                        return null;
                    }
                })
                .define();
    }

    static LayerList list(Vm vm, VmObject self) {
        if (!(self.host instanceof LayerList)) {
            self.host = new LayerList();
        }
        return (LayerList) self.host;
    }
}
