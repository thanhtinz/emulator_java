package com.mobicore.core.midp;

import com.mobicore.core.audio.AudioClip;
import com.mobicore.core.audio.ToneSynth;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

/**
 * The other handset makers' own classes.
 *
 * <p>Nokia was not the only vendor with additions of its own, and a game built
 * for a Siemens or a Samsung fails the same way a Nokia game did: the class
 * loader gives up on a class it cannot find, before anything is drawn. What
 * those games reach for is small and repetitive — buzz the phone, flash the
 * keypad light, play a note — so the classes are small too.</p>
 *
 * <p>Each one is wired to something the emulator already does honestly:
 * vibration goes where MIDP's own {@code vibrate} goes, tones go through the
 * same synthesiser {@code Manager.playTone} uses, and lights are accepted and
 * ignored because there is nothing truthful to do with them.</p>
 *
 * <p>What is deliberately absent is Siemens' own colour-game package and
 * Motorola's 3D — those are whole libraries rather than a handful of static
 * methods, and a stub that pretends to be one would fail later and less
 * clearly than not being there at all.</p>
 */
public final class VendorApis {

    public static final String SIEMENS_LIGHT = "com/siemens/mp/game/Light";
    public static final String SIEMENS_VIBRATOR = "com/siemens/mp/game/Vibrator";
    public static final String SIEMENS_SOUND = "com/siemens/mp/game/Sound";
    public static final String SIEMENS_IMAGE = "com/siemens/mp/game/ExtendedImage";
    public static final String SAMSUNG_VIBRATION = "com/samsung/util/Vibration";
    public static final String SAMSUNG_AUDIO = "com/samsung/util/AudioClip";
    public static final String MOTOROLA_VIBRATOR = "com/motorola/multimedia/Vibrator";

    private VendorApis() {
    }

    public static void install(final Vm vm, final MidpContext context) {
        siemens(vm, context);
        samsung(vm, context);
        motorola(vm, context);
    }

    // ------------------------------------------------------------- Siemens

    private static void siemens(final Vm vm, final MidpContext context) {
        vm.builtin(SIEMENS_LIGHT, "java/lang/Object")
                .staticMethod("setLightOn", "()V", ignored())
                .staticMethod("setLightOff", "()V", ignored())
                .define();

        vm.builtin(SIEMENS_VIBRATOR, "java/lang/Object")
                // Siemens states its buzz in tenths of a second, which is the
                // one thing that has to be got right here.
                .staticMethod("triggerVibrator", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.vibrate(Rt.i(args, 0) * 100);
                        return null;
                    }
                })
                .staticMethod("startVibrator", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // No duration given: a short buzz, since the phone
                        // cannot be left running until someone stops it.
                        context.vibrate(200);
                        return null;
                    }
                })
                .staticMethod("stopVibrator", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.vibration().cancel();
                        return null;
                    }
                })
                .define();

        vm.builtin(SIEMENS_SOUND, "java/lang/Object")
                .staticMethod("playTone", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Siemens asks for a frequency in hertz where MIDP
                        // asks for a note number, so it is converted rather
                        // than passed through as a note nobody meant.
                        int note = noteForFrequency(Rt.i(args, 0));
                        int duration = Rt.i(args, 1);
                        AudioClip clip = ToneSynth.tone(note, duration, 100);
                        context.audio().start(clip, 1, context.effectiveVolume(100));
                        return null;
                    }
                })
                .staticMethod("isPlaying", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(false);
                    }
                })
                .define();

        /**
         * An image a game draws into and then blits, which is what Siemens
         * gave instead of a mutable Image.
         */
        vm.builtin(SIEMENS_IMAGE, "java/lang/Object")
                .method("<init>", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int width = Math.max(1, Rt.i(args, 0));
                        int height = Math.max(1, Rt.i(args, 1));
                        self.host = new Framebuffer(width, height);
                        return null;
                    }
                })
                .method("getGraphics", "()Ljavax/microedition/lcdui/Graphics;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return MidpGfx.newGraphics(vm, frame(vm, self));
                    }
                })
                .method("getWidth", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(frame(vm, self).width());
                    }
                })
                .method("getHeight", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(frame(vm, self).height());
                    }
                })
                .method("getImage", "()Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // The same pixels, not a copy: a game draws into this
                        // and expects what it drew to appear.
                        return MidpGfx.newImage(vm, frame(vm, self), true);
                    }
                })
                .define();
    }

    // ------------------------------------------------------------- Samsung

    private static void samsung(final Vm vm, final MidpContext context) {
        vm.builtin(SAMSUNG_VIBRATION, "java/lang/Object")
                .staticMethod("isSupported", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .staticMethod("start", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Samsung states duration then strength; strength is a
                        // number no phone today takes.
                        context.vibrate(Rt.i(args, 0));
                        return null;
                    }
                })
                .staticMethod("stop", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.vibration().cancel();
                        return null;
                    }
                })
                .define();

        vm.builtin(SAMSUNG_AUDIO, "java/lang/Object")
                .field("note", "I")
                .staticMethod("isSupported", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(true);
                    }
                })
                .method("<init>", "(I[BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("play", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Samsung's clip carries its own encoded data, which
                        // this does not decode; a short tone is the honest
                        // stand-in, and silence would look like a bug.
                        AudioClip clip = ToneSynth.tone(72, 120, 100);
                        context.audio().start(clip, Math.max(1, Rt.i(args, 0)),
                                context.effectiveVolume(100));
                        return null;
                    }
                })
                .method("stop", "()V", ignored())
                .define();
    }

    // ------------------------------------------------------------ Motorola

    private static void motorola(final Vm vm, final MidpContext context) {
        vm.builtin(MOTOROLA_VIBRATOR, "java/lang/Object")
                .staticMethod("vibrateFor", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.vibrate(Rt.i(args, 0));
                        return null;
                    }
                })
                .staticMethod("vibrateOff", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        context.vibration().cancel();
                        return null;
                    }
                })
                .define();
    }

    // --------------------------------------------------------------- tools

    /**
     * The MIDI note nearest a frequency in hertz.
     *
     * <p>Siemens asks for hertz where MIDP asks for a note number. Passing the
     * hertz through as a note would play a game's 440 Hz as note 127 — the top
     * of the scale, for every tone it ever plays.</p>
     */
    public static int noteForFrequency(int hertz) {
        if (hertz <= 0) {
            return 0;
        }
        int note = (int) Math.round(69 + 12 * Math.log(hertz / 440.0) / Math.log(2));
        return note < 0 ? 0 : (note > 127 ? 127 : note);
    }

    private static Framebuffer frame(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof Framebuffer)) {
            throw vm.raise("java/lang/IllegalStateException", "ExtendedImage chưa có bề mặt");
        }
        return (Framebuffer) self.host;
    }

    /** Accepted and ignored, because there is nothing honest to do with it. */
    private static NativeMethod ignored() {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return null;
            }
        };
    }
}
