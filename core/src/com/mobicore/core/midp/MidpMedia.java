package com.mobicore.core.midp;

import com.mobicore.core.audio.AudioClip;
import com.mobicore.core.audio.AudioSink;
import com.mobicore.core.audio.ToneSequence;
import com.mobicore.core.audio.ToneSynth;
import com.mobicore.core.audio.WavDecoder;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@code javax.microedition.media}: the sound half of MIDP.
 *
 * <p>Until this existed a game that played so much as a beep did not run
 * badly — it did not run at all, because {@code Manager} was not on the class
 * path and the class loader failed before the first frame. Sound is also the
 * half of a J2ME game people remember: the tone that plays when you die is
 * part of what the game was.</p>
 *
 * <p>What plays: tones, tone sequences, and uncompressed WAV. What does not:
 * MIDI and MP3, which needed a synthesiser and a decoder that a handset had in
 * hardware. Those are refused honestly — the player is created, reports that
 * it cannot realise the media, and the game carries on silently instead of
 * dying. A game that ignores the exception, which most do, plays without
 * music; one that handles it can pick another file.</p>
 */
public final class MidpMedia {

    public static final String MANAGER = "javax/microedition/media/Manager";
    public static final String PLAYER = "javax/microedition/media/Player";
    public static final String PLAYER_LISTENER = "javax/microedition/media/PlayerListener";
    public static final String CONTROLLABLE = "javax/microedition/media/Controllable";
    public static final String CONTROL = "javax/microedition/media/Control";
    public static final String MEDIA_EXCEPTION = "javax/microedition/media/MediaException";
    public static final String VOLUME_CONTROL = "javax/microedition/media/control/VolumeControl";
    public static final String TONE_CONTROL = "javax/microedition/media/control/ToneControl";

    /** The locator that means "play tones", as the specification defines it. */
    public static final String TONE_DEVICE_LOCATOR = "device://tone";

    private MidpMedia() {
    }

    // ------------------------------------------------------------- player

    /** MIDP player states, which the specification fixes to these values. */
    static final int UNREALIZED = 100;
    static final int REALIZED = 200;
    static final int PREFETCHED = 300;
    static final int STARTED = 400;
    static final int CLOSED = 0;

    /** What a player is, host side. */
    static final class PlayerState {

        final String contentType;
        /** Set once the media is decoded; null while it is still bytes. */
        AudioClip clip;
        /** Raw media kept until realise, as the state machine requires. */
        byte[] media;
        /** Tone sequence set through ToneControl, if this is a tone player. */
        byte[] sequence;
        boolean toneDevice;
        String unsupported;

        int state = UNREALIZED;
        int loopCount = 1;
        int volume = 100;
        boolean muted;
        int voice = AudioSink.NO_VOICE;
        long mediaTimeUs;

        PlayerState(String contentType) {
            this.contentType = contentType;
        }
    }

    public static void install(final Vm vm, final MidpContext context) {
        vm.builtin(CONTROL, Vm.OBJECT, new String[0], true).define();

        vm.builtin(CONTROLLABLE, Vm.OBJECT, new String[0], true)
                .abstractMethod("getControl", "(Ljava/lang/String;)Ljavax/microedition/media/Control;")
                .abstractMethod("getControls", "()[Ljavax/microedition/media/Control;")
                .define();

        vm.builtin(PLAYER_LISTENER, Vm.OBJECT, new String[0], true)
                .abstractMethod("playerUpdate",
                        "(Ljavax/microedition/media/Player;Ljava/lang/String;Ljava/lang/Object;)V")
                .define();

        vm.builtin(MEDIA_EXCEPTION, "java/lang/Exception")
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

        player(vm, context);
        volumeControl(vm, context);
        toneControl(vm);
        manager(vm, context);
    }

    // ------------------------------------------------------------- Manager

    private static void manager(final Vm vm, final MidpContext context) {
        vm.builtin(MANAGER, Vm.OBJECT)
                .staticField("TONE_DEVICE_LOCATOR", "Ljava/lang/String;")
                .staticMethod("playTone", "(III)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int note = Rt.i(args, 0);
                        int duration = Rt.i(args, 1);
                        int volume = Rt.i(args, 2);
                        if (note < 0 || note > 127) {
                            throw vm.raise("java/lang/IllegalArgumentException",
                                    "A tone must be a MIDI note between 0 and 127");
                        }
                        AudioClip clip = ToneSynth.tone(note, duration, volume);
                        // Fire and forget, exactly as the specification says:
                        // playTone has no player and nothing to stop.
                        context.audio().start(clip, 1, context.effectiveVolume(volume));
                        return null;
                    }
                })
                .staticMethod("createPlayer",
                        "(Ljava/lang/String;)Ljavax/microedition/media/Player;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return fromLocator(vm, Rt.s(vm, args, 0));
                            }
                        })
                .staticMethod("createPlayer",
                        "(Ljava/io/InputStream;Ljava/lang/String;)Ljavax/microedition/media/Player;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return fromStream(vm, Rt.obj(args, 0), Rt.s(vm, args, 1));
                            }
                        })
                .staticMethod("getSupportedContentTypes",
                        "(Ljava/lang/String;)[Ljava/lang/String;", new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return strings(vm, new String[]{"audio/x-wav", "audio/x-tone-seq"});
                            }
                        })
                .staticMethod("getSupportedProtocols",
                        "(Ljava/lang/String;)[Ljava/lang/String;", new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return strings(vm, new String[]{"device"});
                            }
                        })
                .define();

        VmClass manager = vm.loadClass(MANAGER);
        vm.initialize(manager);
        manager.staticRefs()[manager.findField("TONE_DEVICE_LOCATOR").slot()] =
                vm.newString(TONE_DEVICE_LOCATOR);
    }

    private static VmObject fromLocator(Vm vm, String locator) {
        if (locator == null) {
            throw vm.raise("java/lang/IllegalArgumentException", "A locator is required");
        }
        VmObject player = vm.newInstance(PLAYER);
        PlayerState state = new PlayerState("audio/x-tone-seq");
        if (TONE_DEVICE_LOCATOR.equals(locator)) {
            state.toneDevice = true;
        } else {
            // No streaming, no capture: a handset had them, this does not, and
            // a player that says so is better than one that silently never
            // starts.
            state.unsupported = "MobiCore plays " + TONE_DEVICE_LOCATOR
                    + " and media passed as a stream, not " + locator;
        }
        player.host = state;
        return player;
    }

    private static VmObject fromStream(Vm vm, VmObject stream, String type) {
        byte[] media = readAll(vm, stream);
        VmObject player = vm.newInstance(PLAYER);
        PlayerState state = new PlayerState(type == null ? "" : type);
        state.media = media;
        if (!WavDecoder.looksLikeWav(media) && !isToneSequenceType(type)) {
            state.unsupported = "MobiCore plays uncompressed WAV and tone sequences, not "
                    + (type == null || type.length() == 0 ? "this file" : type);
        }
        player.host = state;
        return player;
    }

    private static boolean isToneSequenceType(String type) {
        return type != null && type.indexOf("tone") >= 0;
    }

    private static byte[] readAll(Vm vm, VmObject stream) {
        if (stream == null || !(stream.host instanceof InputStream)) {
            throw vm.raise("java/lang/IllegalArgumentException", "A media stream is required");
        }
        try {
            InputStream in = (InputStream) stream.host;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw vm.raise("java/io/IOException", e.getMessage());
        }
    }

    // -------------------------------------------------------------- Player

    private static void player(final Vm vm, final MidpContext context) {
        vm.builtin(PLAYER, Vm.OBJECT, new String[]{CONTROLLABLE}, false)
                .field("listener", "Ljavax/microedition/media/PlayerListener;")
                .staticField("UNREALIZED", "I")
                .staticField("REALIZED", "I")
                .staticField("PREFETCHED", "I")
                .staticField("STARTED", "I")
                .staticField("CLOSED", "I")
                .method("realize", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.state >= REALIZED) {
                            return null;
                        }
                        decode(vm, state);
                        state.state = REALIZED;
                        return null;
                    }
                })
                .method("prefetch", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.state < REALIZED) {
                            decode(vm, state);
                        }
                        if (state.state < PREFETCHED) {
                            state.state = PREFETCHED;
                        }
                        return null;
                    }
                })
                .method("start", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.state == CLOSED) {
                            throw vm.raise("java/lang/IllegalStateException",
                                    "The player is closed");
                        }
                        if (state.state == STARTED) {
                            return null;
                        }
                        decode(vm, state);
                        AudioClip clip = state.clip;
                        if (clip == null) {
                            return null;
                        }
                        int volume = state.muted ? 0 : context.effectiveVolume(state.volume);
                        state.voice = context.audio().start(clip, state.loopCount, volume);
                        state.state = STARTED;
                        notifyListener(vm, self, "started",
                                Long.valueOf(context.audio().positionMs(state.voice) * 1000L));
                        return null;
                    }
                })
                .method("stop", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.state != STARTED) {
                            return null;
                        }
                        state.mediaTimeUs = context.audio().positionMs(state.voice) * 1000L;
                        context.audio().stop(state.voice);
                        state.voice = AudioSink.NO_VOICE;
                        state.state = PREFETCHED;
                        notifyListener(vm, self, "stopped", Long.valueOf(state.mediaTimeUs));
                        return null;
                    }
                })
                .method("deallocate", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.state == STARTED) {
                            context.audio().stop(state.voice);
                            state.voice = AudioSink.NO_VOICE;
                        }
                        if (state.state > REALIZED) {
                            state.state = REALIZED;
                        }
                        return null;
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.voice != AudioSink.NO_VOICE) {
                            context.audio().stop(state.voice);
                            state.voice = AudioSink.NO_VOICE;
                        }
                        state.state = CLOSED;
                        notifyListener(vm, self, "closed", null);
                        return null;
                    }
                })
                .method("getState", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        // A player that has run to the end is prefetched
                        // again, not started: a game polling getState to know
                        // when a sound finished depends on that.
                        if (state.state == STARTED && !context.audio().isPlaying(state.voice)) {
                            state.state = PREFETCHED;
                            state.mediaTimeUs = state.clip == null
                                    ? 0 : state.clip.durationMs() * 1000L;
                            notifyListener(vm, self, "endOfMedia", Long.valueOf(state.mediaTimeUs));
                        }
                        return Integer.valueOf(state.state);
                    }
                })
                .method("setLoopCount", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        int count = Rt.i(args, 0);
                        if (count == 0) {
                            throw vm.raise("java/lang/IllegalArgumentException",
                                    "A loop count of zero plays nothing; use -1 to repeat");
                        }
                        // MIDP says -1 repeats indefinitely; the sink says 0.
                        state.loopCount = count < 0 ? 0 : count;
                        return null;
                    }
                })
                .method("getDuration", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.clip == null) {
                            // TIME_UNKNOWN, which is what a player that has
                            // not decoded its media must report.
                            return Long.valueOf(-1L);
                        }
                        return Long.valueOf(state.clip.durationMs() * 1000L);
                    }
                })
                .method("getMediaTime", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        if (state.state == STARTED) {
                            return Long.valueOf(context.audio().positionMs(state.voice) * 1000L);
                        }
                        return Long.valueOf(state.mediaTimeUs);
                    }
                })
                .method("setMediaTime", "(J)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        long requested = Math.max(0L, Rt.l(args, 0));
                        long limit = state.clip == null ? 0L : state.clip.durationMs() * 1000L;
                        state.mediaTimeUs = Math.min(requested, limit);
                        return Long.valueOf(state.mediaTimeUs);
                    }
                })
                .method("getContentType", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(state(vm, self).contentType);
                    }
                })
                .method("addPlayerListener", "(Ljavax/microedition/media/PlayerListener;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("listener", Rt.obj(args, 0));
                                return null;
                            }
                        })
                .method("removePlayerListener", "(Ljavax/microedition/media/PlayerListener;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("listener", null);
                                return null;
                            }
                        })
                .method("getControl", "(Ljava/lang/String;)Ljavax/microedition/media/Control;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                String name = Rt.s(vm, args, 0);
                                return controlFor(vm, self, name);
                            }
                        })
                .method("getControls", "()[Ljavax/microedition/media/Control;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        VmObject volume = controlFor(vm, self, "VolumeControl");
                        VmObject tone = state.toneDevice ? controlFor(vm, self, "ToneControl") : null;
                        VmArray array = vm.newArray("L" + CONTROL + ";", tone == null ? 1 : 2);
                        array.objects()[0] = volume;
                        if (tone != null) {
                            array.objects()[1] = tone;
                        }
                        return array;
                    }
                })
                .define();

        VmClass player = vm.loadClass(PLAYER);
        vm.initialize(player);
        MidpGfx.setStatic(vm, player, "UNREALIZED", UNREALIZED);
        MidpGfx.setStatic(vm, player, "REALIZED", REALIZED);
        MidpGfx.setStatic(vm, player, "PREFETCHED", PREFETCHED);
        MidpGfx.setStatic(vm, player, "STARTED", STARTED);
        MidpGfx.setStatic(vm, player, "CLOSED", CLOSED);
    }

    /**
     * Decodes the media, or refuses it.
     *
     * <p>This is where an unsupported format is reported, and it happens at
     * realise rather than at creation because that is where MIDP puts it: a
     * game is entitled to create a player for a file it cannot play and to
     * find out when it tries.</p>
     */
    private static void decode(Vm vm, PlayerState state) {
        if (state.clip != null || state.state == CLOSED) {
            return;
        }
        if (state.unsupported != null) {
            throw vm.raise(MEDIA_EXCEPTION, state.unsupported);
        }
        try {
            if (state.toneDevice) {
                // A tone player with no sequence yet is legal: the game sets
                // one through ToneControl after realising.
                state.clip = state.sequence == null
                        ? ToneSynth.silence(0)
                        : ToneSequence.render(state.sequence, state.volume);
            } else if (WavDecoder.looksLikeWav(state.media)) {
                state.clip = WavDecoder.decode(state.media);
            } else {
                state.clip = ToneSequence.render(state.media, state.volume);
            }
        } catch (IOException e) {
            throw vm.raise(MEDIA_EXCEPTION, e.getMessage());
        }
    }

    private static VmObject controlFor(Vm vm, VmObject player, String name) {
        if (name == null) {
            return null;
        }
        PlayerState state = state(vm, player);
        String simple = name.substring(name.lastIndexOf('.') + 1);
        if ("VolumeControl".equals(simple)) {
            VmObject control = vm.newInstance(VOLUME_CONTROL);
            control.host = state;
            return control;
        }
        if ("ToneControl".equals(simple) && state.toneDevice) {
            VmObject control = vm.newInstance(TONE_CONTROL);
            control.host = state;
            return control;
        }
        return null;
    }

    /**
     * Tells the game's listener what happened, if it registered one.
     *
     * <p>MIDP delivers these on a separate thread. The emulator runs the
     * MIDlet on one thread on purpose — a game's callbacks then arrive in the
     * order the game caused them, which is what the games of the era assumed
     * even where the specification did not promise it.</p>
     */
    private static void notifyListener(Vm vm, VmObject player, String event, Object data) {
        Object listener = player.get("listener");
        if (!(listener instanceof VmObject)) {
            return;
        }
        VmObject boxed = null;
        if (data instanceof Long) {
            // MIDP hands the media time to the listener as a Long, and the
            // emulated boxes keep their value host-side rather than in a field.
            boxed = vm.newInstance("java/lang/Long");
            boxed.host = data;
        }
        vm.callVirtual((VmObject) listener, "playerUpdate",
                "(Ljavax/microedition/media/Player;Ljava/lang/String;Ljava/lang/Object;)V",
                player, vm.newString(event), boxed);
    }

    // ------------------------------------------------------------ controls

    private static void volumeControl(final Vm vm, final MidpContext context) {
        vm.builtin(VOLUME_CONTROL, Vm.OBJECT, new String[]{CONTROL}, false)
                .method("setLevel", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        state.volume = Math.max(0, Math.min(100, Rt.i(args, 0)));
                        if (state.voice != AudioSink.NO_VOICE) {
                            context.audio().setVolume(state.voice,
                                    state.muted ? 0 : context.effectiveVolume(state.volume));
                        }
                        return Integer.valueOf(state.volume);
                    }
                })
                .method("getLevel", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(state(vm, self).volume);
                    }
                })
                .method("setMute", "(Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        state.muted = Rt.bool(args, 0);
                        if (state.voice != AudioSink.NO_VOICE) {
                            context.audio().setVolume(state.voice,
                                    state.muted ? 0 : context.effectiveVolume(state.volume));
                        }
                        return null;
                    }
                })
                .method("isMuted", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(state(vm, self).muted);
                    }
                })
                .define();
    }

    private static void toneControl(final Vm vm) {
        vm.builtin(TONE_CONTROL, Vm.OBJECT, new String[]{CONTROL}, false)
                .staticField("VERSION", "B")
                .staticField("TEMPO", "B")
                .staticField("RESOLUTION", "B")
                .staticField("BLOCK_START", "B")
                .staticField("BLOCK_END", "B")
                .staticField("PLAY_BLOCK", "B")
                .staticField("SET_VOLUME", "B")
                .staticField("REPEAT", "B")
                .staticField("SILENCE", "B")
                .staticField("C4", "B")
                .method("setSequence", "([B)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        PlayerState state = state(vm, self);
                        VmArray array = Rt.array(args, 0);
                        if (array == null) {
                            throw vm.raise("java/lang/IllegalArgumentException",
                                    "A tone sequence is required");
                        }
                        byte[] source = array.bytes();
                        byte[] sequence = new byte[array.length()];
                        System.arraycopy(source, 0, sequence, 0, sequence.length);
                        try {
                            // Rendered now rather than at start, so a broken
                            // sequence is refused where the game set it.
                            state.clip = ToneSequence.render(sequence, state.volume);
                            state.sequence = sequence;
                        } catch (IOException e) {
                            throw vm.raise("java/lang/IllegalArgumentException", e.getMessage());
                        }
                        return null;
                    }
                })
                .define();

        vm.initialize(vm.loadClass(TONE_CONTROL));
        setByte(vm, "VERSION", ToneSequence.VERSION);
        setByte(vm, "TEMPO", ToneSequence.TEMPO);
        setByte(vm, "RESOLUTION", ToneSequence.RESOLUTION);
        setByte(vm, "BLOCK_START", ToneSequence.BLOCK_START);
        setByte(vm, "BLOCK_END", ToneSequence.BLOCK_END);
        setByte(vm, "PLAY_BLOCK", ToneSequence.PLAY_BLOCK);
        setByte(vm, "SET_VOLUME", ToneSequence.SET_VOLUME);
        setByte(vm, "REPEAT", ToneSequence.REPEAT);
        setByte(vm, "SILENCE", ToneSequence.SILENCE);
        // Middle C, the note a sequence's own numbering is relative to.
        setByte(vm, "C4", (byte) 60);
    }

    private static void setByte(Vm vm, String name, byte value) {
        MidpGfx.setStatic(vm, vm.loadClass(TONE_CONTROL), name, value);
    }

    /** A String[] the game can hold, since the VM has no helper for one. */
    private static VmArray strings(Vm vm, String[] values) {
        VmArray array = vm.newArray("Ljava/lang/String;", values.length);
        for (int i = 0; i < values.length; i++) {
            array.objects()[i] = vm.newString(values[i]);
        }
        return array;
    }

    static PlayerState state(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof PlayerState)) {
            throw vm.raise("java/lang/IllegalStateException", "This player is not usable");
        }
        return (PlayerState) self.host;
    }
}
