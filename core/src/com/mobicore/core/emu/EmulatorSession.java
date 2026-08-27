package com.mobicore.core.emu;

import com.mobicore.core.audio.AudioLog;
import com.mobicore.core.audio.AudioSink;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.vm.JarClassSource;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.Midp;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.midp.MidpGfx;
import com.mobicore.core.midp.MidpUi;
import com.mobicore.core.midp.ScreenInput;
import com.mobicore.core.midp.ScreenRenderer;
import com.mobicore.core.midp.MidpNet;
import com.mobicore.core.midp.MidpRms;
import com.mobicore.core.midp.SystemChrome;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.rt.Cldc;
import com.mobicore.core.rt.TimerClasses;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.Descriptors;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmError;
import com.mobicore.core.vm.VmHost;
import com.mobicore.core.vm.VmObject;
import com.mobicore.core.vm.VmThrow;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One running game.
 *
 * <p>Owns the virtual machine, the MIDP context and the MIDlet lifecycle, and
 * exposes the small surface a UI needs: start, render a frame, deliver input,
 * take a screenshot, pause, resume and destroy. Android, iOS and the desktop
 * preview all drive a session through exactly these calls, which is what keeps
 * the emulator core free of platform assumptions.</p>
 */
public final class EmulatorSession {

    /** Lifecycle states, mirroring the MIDlet specification. */
    public static final int STATE_NEW = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_DESTROYED = 3;

    private final Vm vm;
    /** Scheduled TimerTasks, run from the frame loop; see pumpTimers. */
    private TimerClasses.Queue timers;
    private final MidpContext context;
    private final MidletSuiteInfo info;
    private final JarClassSource source;
    private final EmulatorLog log;
    private final RecordStoreManager rms;
    private final GameProfile profile;
    private final NetworkStack network;

    private VmObject midlet;
    private int state = STATE_NEW;
    private String midletClass;

    private SpeedClock clock;
    private final Rewind rewind = new Rewind();

    /**
     * The last few seconds of play, kept so they can be taken back.
     *
     * <p>These games restart a level on one mistake, because a handset had
     * nowhere to keep anything else. This one does.</p>
     */
    public Rewind rewind() {
        return rewind;
    }

    private EmulatorSession(Vm vm, MidpContext context, MidletSuiteInfo info,
                            JarClassSource source, EmulatorLog log,
                            RecordStoreManager rms, GameProfile profile, NetworkStack network) {
        this.vm = vm;
        this.context = context;
        this.info = info;
        this.source = source;
        this.log = log;
        this.rms = rms;
        this.profile = profile;
        this.network = network;
    }

    /**
     * How fast the game runs, as a percentage of a handset's pace.
     *
     * <p>Lives on the clock rather than on the frame loop: a J2ME game paces
     * itself, so what changes its speed is what it is told the time is.</p>
     */
    public int speed() {
        return clock == null ? SpeedClock.NORMAL : clock.speed();
    }

    public void setSpeed(int percent) {
        if (clock != null) {
            clock.setSpeed(percent);
        }
    }

    /** The next speed in the cycle, for a one-tap control. */
    public int cycleSpeed() {
        if (clock == null) {
            return SpeedClock.NORMAL;
        }
        clock.setSpeed(clock.nextSpeed());
        return clock.speed();
    }

    /**
     * Builds a session for an installed suite.
     *
     * @param storage where record stores live; an in-memory filesystem is used
     *                when {@code null}, which is what previews and tests want
     */
    public static EmulatorSession create(SuiteLoader suite, GameProfile profile,
                                         Vfs storage, StorageLayout layout, VmHost host) {
        EmulatorLog log = new EmulatorLog();
        Vm vm = new Vm();
        // A game paces itself off the clock it is given, so the clock is the
        // one place a speed control can live without the game noticing.
        SpeedClock speedClock = new SpeedClock(host == null ? VmHost.DEFAULT : host);
        vm.setHost(log.hostBridge(speedClock));

        int width = profile.device().width();
        int height = profile.device().height();
        MidpContext context = new MidpContext(vm, width, height);
        context.setAttributes(suite.info().attributes());
        // Smooth the game's own diagonals and curves when the profile asks for
        // it. Off-screen images a game draws into are deliberately left alone;
        // see Framebuffer.setAntialias.
        context.setSmoothShapes(profile.smoothing());
        // Sound goes to a recorder unless the platform hands over a speaker.
        // It keeps the emulator's own clock, so a game that waits for a sound
        // to end waits the same length of time whatever is listening.
        context.setAudio(new AudioLog(new AudioLog.Clock() {
            public long nowMs() {
                return vm.host().currentTimeMillis();
            }
        }));
        context.setMasterVolume(profile.volume(), profile.isMuted());

        Vfs vfs = storage == null ? new MemoryVfs() : storage;
        StorageLayout paths = layout == null ? new StorageLayout("MobiCore") : layout;
        RecordStoreManager rms = new RecordStoreManager(vfs, paths, profile.suiteId());

        NetworkPolicy policy = new NetworkPolicy();
        policy.setMode(profile.networkMode());
        NetworkStack network = new NetworkStack(policy);
        network.setClock(new NetworkStack.Clock() {
            public long now() {
                return vm.host().currentTimeMillis();
            }
        });

        SystemChrome.measure(context);

        TimerClasses.Queue timers = Cldc.install(vm);
        Midp.install(vm, context);
        MidpRms.install(vm, rms, context);
        MidpNet.install(vm, network);

        JarClassSource source = new JarClassSource(suite.archive());
        vm.addSource(source);
        EmulatorSession created = new EmulatorSession(vm, context, suite.info(), source, log,
                rms, profile, network);
        created.timers = timers;
        created.clock = speedClock;
        return created;
    }

    /** Convenience for previews and tests: default profile at a fixed size. */
    public static EmulatorSession create(SuiteLoader suite, int width, int height, VmHost host) {
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        profile.setDevice(com.mobicore.core.model.DeviceProfile.custom(width, height));
        return create(suite, profile, null, null, host);
    }

    public Vm vm() {
        return vm;
    }

    /**
     * Sends sound somewhere real. Android passes an {@code AudioTrack} sink
     * and iOS an audio queue; left alone, everything the game plays is
     * recorded and nothing is heard.
     */
    public void setAudio(AudioSink sink) {
        context.setAudio(sink);
    }

    /** What the game has played, when the sink is the recording one. */
    public AudioSink audio() {
        return context.audio();
    }

    /**
     * Runs any TimerTask that has come due.
     *
     * <p>Called from the frame loop rather than from a thread: a MIDlet's
     * timer callback almost always touches the screen, and the games of the
     * era were written expecting that to happen while nothing else did.</p>
     *
     * @return how many tasks ran
     */
    public int pumpTimers() {
        if (timers == null || state != STATE_ACTIVE) {
            return 0;
        }
        return timers.runDue(vm, vm.host().currentTimeMillis());
    }

    /** How many timer tasks are waiting; for the developer tools. */
    public int scheduledTimers() {
        return timers == null ? 0 : timers.size();
    }

    public MidpContext context() {
        return context;
    }

    public MidletSuiteInfo info() {
        return info;
    }

    public EmulatorLog log() {
        return log;
    }

    public JarClassSource source() {
        return source;
    }

    public RecordStoreManager rms() {
        return rms;
    }

    public GameProfile profile() {
        return profile;
    }

    /**
     * The connection layer this game sees. The UI installs a transport and a
     * permission prompt here; until it does, every connection is refused.
     */
    public NetworkStack network() {
        return network;
    }

    /**
     * Presses a virtual keypad button, translating it through the game's input
     * mapping. This is the entry point the on-screen keypad uses.
     */
    public void pressButton(String button) {
        pressButton2(button);
    }

    // ---------------------------------------------------------------- turbo

    /** Buttons being held that repeat on their own, and when each last fired. */
    private final Map<String, Long> turboHeld = new LinkedHashMap<String, Long>();

    /**
     * Taps the held turbo buttons again when their interval is up.
     *
     * <p>Turbo exists because these games were written for a keypad that a
     * thumb could hammer, and half the shooters of the era expect exactly
     * that: a shot per press, no auto-fire, and a level that is unwinnable
     * unless the player mashes. Holding the key is not the same input — a
     * game reading {@code keyPressed} sees one press however long it is held
     * — so the emulator has to let go and press again, which is what this
     * does.</p>
     *
     * @return how many presses were sent
     */
    public int pumpTurbo() {
        if (turboHeld.isEmpty() || state != STATE_ACTIVE) {
            return 0;
        }
        long now = vm.host().currentTimeMillis();
        int fired = 0;
        for (Map.Entry<String, Long> held : turboHeld.entrySet()) {
            String button = held.getKey();
            int interval = profile.input().turboFor(button);
            if (interval <= 0) {
                continue;
            }
            if (now - held.getValue().longValue() < interval) {
                continue;
            }
            held.setValue(Long.valueOf(now));
            int keyCode = profile.input().keyCodeFor(button);
            if (keyCode != 0) {
                // Released first: a game that only counts presses would see
                // nothing at all from a key that never came back up.
                keyReleased(keyCode);
                keyPressed(keyCode);
                fired++;
            }
        }
        return fired;
    }

    /** True while {@code button} is being held and repeating. */
    public boolean isTurboHeld(String button) {
        return turboHeld.containsKey(button);
    }

    /**
     * Presses a virtual keypad button.
     *
     * <p>The softkeys are what a handset labels from the game's commands, so
     * they run the command rather than delivering a bare key code.</p>
     *
     * @return true when the press ran one of the screen's commands
     */
    public boolean pressButton2(String button) {
        if ("softLeft".equals(button)) {
            return pressSoftKey(true);
        }
        if ("softRight".equals(button)) {
            return pressSoftKey(false);
        }
        String[] diagonal = InputProfile.diagonalOf(button);
        if (diagonal != null) {
            // MIDP has no diagonal key, and no game of the era expected one:
            // a corner on a handset's pad was two directions held at once, so
            // that is what a corner button sends.
            pressButton2(diagonal[0]);
            pressButton2(diagonal[1]);
            return false;
        }
        int keyCode = profile.input().keyCodeFor(button);
        if (keyCode != 0) {
            keyPressed(keyCode);
            if (profile.input().turboFor(button) > 0) {
                turboHeld.put(button, Long.valueOf(vm.host().currentTimeMillis()));
            }
        }
        return false;
    }

    public void releaseButton(String button) {
        if ("softLeft".equals(button) || "softRight".equals(button)) {
            return;
        }
        String[] diagonal = InputProfile.diagonalOf(button);
        if (diagonal != null) {
            releaseButton(diagonal[0]);
            releaseButton(diagonal[1]);
            return;
        }
        turboHeld.remove(button);
        int keyCode = profile.input().keyCodeFor(button);
        if (keyCode != 0) {
            keyReleased(keyCode);
        }
    }

    // -------------------------------------------------------- text entry

    /** True while the game is asking for text — a TextBox or a focused field. */
    public boolean isTextInputActive() {
        return ScreenInput.textInputTarget(context) != null;
    }

    /** What that field holds now, so the phone's keyboard opens on it. */
    public String textInput() {
        String value = ScreenInput.textInputValue(context);
        return value == null ? "" : value;
    }

    /** Puts what the phone's keyboard produced into the field. */
    public boolean setTextInput(String value) {
        return ScreenInput.setTextInput(context, value);
    }

    /** Replays a macro bound to a button, or returns false when none is bound. */
    public boolean runMacro(String button) {
        com.mobicore.core.model.InputProfile.Macro macro = profile.input().macroFor(button);
        if (macro == null) {
            return false;
        }
        for (String step : macro.steps()) {
            pressButton(step);
            releaseButton(step);
        }
        return true;
    }

    public int state() {
        return state;
    }

    public String midletClass() {
        return midletClass;
    }

    public Framebuffer screen() {
        return context.screen();
    }

    /** Layers a mod archive in front of the base JAR. */
    public void addModOverlay(JarArchive overlay) {
        source.addOverlay(overlay);
    }

    // ----------------------------------------------------------- lifecycle

    /** Starts the suite's default MIDlet. */
    public void start() {
        MidletEntry entry = info.primaryMidlet();
        if (entry == null) {
            throw new VmError("The suite declares no MIDlet to start");
        }
        start(entry.className());
    }

    public void start(String binaryClassName) {
        if (state == STATE_ACTIVE) {
            return;
        }
        midletClass = binaryClassName;
        String internal = Descriptors.toInternalName(binaryClassName);
        log.info("Starting " + binaryClassName);
        VmClass type = vm.loadClass(internal);
        vm.initialize(type);
        midlet = vm.newInstance(type);
        context.setMidlet(midlet);
        vm.invoke(type.findMethod("<init>", "()V"), midlet, new Object[0]);
        vm.callVirtual(midlet, "startApp", "()V");
        state = STATE_ACTIVE;
        log.info("MIDlet is active");
    }

    public void pause() {
        if (state != STATE_ACTIVE) {
            return;
        }
        vm.callVirtual(midlet, "pauseApp", "()V");
        state = STATE_PAUSED;
        log.info("MIDlet paused");
    }

    public void resume() {
        if (state != STATE_PAUSED) {
            return;
        }
        vm.callVirtual(midlet, "startApp", "()V");
        state = STATE_ACTIVE;
        context.requestRepaint();
        log.info("MIDlet resumed");
    }

    public void destroy() {
        if (state == STATE_DESTROYED || midlet == null) {
            state = STATE_DESTROYED;
            return;
        }
        try {
            vm.callVirtual(midlet, "destroyApp", "(Z)V", Integer.valueOf(1));
        } catch (VmThrow e) {
            log.error("destroyApp threw " + e);
        }
        try {
            // A record store the game left open still has to reach storage.
            rms.flushAll();
        } catch (IOException e) {
            log.error("Cannot flush record stores: " + e.getMessage());
        }
        state = STATE_DESTROYED;
        log.info("MIDlet destroyed");
    }

    /** True once the MIDlet has called {@code notifyDestroyed}. */
    public boolean isFinished() {
        return state == STATE_DESTROYED || context.isDestroyed();
    }

    // ------------------------------------------------------------ rendering

    /**
     * Advances one frame: runs queued callbacks, then repaints the current
     * Canvas if anything asked for it.
     *
     * @return true when the screen changed
     */
    public boolean renderFrame() {
        if (state != STATE_ACTIVE) {
            return false;
        }
        // Timers first: a MIDlet that drives itself from a TimerTask expects
        // its tick to have happened before the frame it paints.
        pumpTimers();
        pumpTurbo();
        // History is kept off the game's own clock, so rewinding covers the
        // same amount of play whatever speed it is running at.
        rewind.tick(this, vm.host().currentTimeMillis());
        context.drainCallbacks();
        VmObject current = context.current();
        if (current == null) {
            return false;
        }
        if (!context.consumeRepaint()) {
            return false;
        }
        if (!isCanvas(current)) {
            // Form, List, TextBox and Alert are the device's to draw: MIDP
            // describes what they hold and leaves the look to the handset.
            return ScreenRenderer.render(context);
        }
        Framebuffer screen = context.screen();
        screen.setTranslation(0, 0);
        screen.resetClip();
        // The game paints into its canvas area only. Clipping here is what
        // stops a MIDlet that ignores getHeight() from scribbling over the
        // title and softkey strips the system owns.
        screen.setClip(context.canvasLeft(), context.canvasTop(),
                context.canvasWidth(), context.canvasHeight());
        screen.translate(context.canvasLeft(), context.canvasTop());
        VmObject graphics = MidpGfx.newGraphics(vm, screen);
        vm.callVirtual(current, "paint", "(Ljavax/microedition/lcdui/Graphics;)V", graphics);
        SystemChrome.draw(context);
        context.countFrame();
        return true;
    }

    /**
     * Presses a softkey, invoking whatever command the current screen has
     * mapped to it.
     *
     * @param left true for the left key, false for the right
     * @return true when a command was actually run
     */
    public boolean pressSoftKey(boolean left) {
        if (context.isMenuOpen()) {
            // Both keys belong to the open menu: the left one runs the row it
            // is sitting on, the right one backs out.
            if (!left) {
                context.closeMenu();
                return false;
            }
            VmObject selected = context.menuSelection();
            context.closeMenu();
            return selected != null && invokeCommand(selected);
        }
        if (left && !context.menuCommands().isEmpty()) {
            // More commands than the two keys can label, so the left key opens
            // the list instead of running the first one. Without this the rest
            // of a screen's commands can never be reached.
            context.openMenu();
            return false;
        }
        VmObject command = left ? context.leftCommand() : context.rightCommand();
        if (command == null) {
            // No command there: the game may still want the raw key.
            keyPressed(left ? MidpContext.KEY_SOFT_LEFT : MidpContext.KEY_SOFT_RIGHT);
            keyReleased(left ? MidpContext.KEY_SOFT_LEFT : MidpContext.KEY_SOFT_RIGHT);
            return false;
        }
        return invokeCommand(command);
    }

    /**
     * True while the system draws the command bar inside the screen.
     *
     * <p>When it does, the bar is the softkeys and the virtual keypad has no
     * business repeating them. A game that goes full screen takes the bar
     * away, and then the keypad is the only way left to reach a command.</p>
     */
    public boolean showsSoftKeyBar() {
        return !context.isFullScreen() && context.hasSoftKeys();
    }

    /** Label the left softkey should show, or {@code null} when it has none. */
    public String leftSoftKeyLabel() {
        return SystemChrome.leftLabel(context);
    }

    public String rightSoftKeyLabel() {
        return SystemChrome.rightLabel(context);
    }

    private boolean isCanvas(VmObject displayable) {
        return displayable.type().isAssignableTo(vm.loadClass(MidpUi.CANVAS));
    }

    public byte[] screenshotPng() throws IOException {
        return PngWriter.encode(context.screen());
    }

    // ---------------------------------------------------------------- input

    public void keyPressed(int keyCode) {
        int action = MidpContext.gameAction(keyCode);
        if (action != 0) {
            context.setKeyState(action, true);
        }
        if (ScreenInput.keyPressed(context, keyCode, commandSink())) {
            // The menu or a high level screen took it. A Canvas underneath must
            // not also see the key that walked a menu.
            return;
        }
        deliver("keyPressed", keyCode);
    }

    /** Lets the screen layer run a command through the MIDlet's listener. */
    private ScreenInput.Commands commandSink() {
        return new ScreenInput.Commands() {
            public void invoke(VmObject command) {
                invokeCommand(command);
            }
        };
    }

    public void keyReleased(int keyCode) {
        int action = MidpContext.gameAction(keyCode);
        if (action != 0) {
            context.setKeyState(action, false);
        }
        deliver("keyReleased", keyCode);
    }

    /** True while the "Options" list is over the screen. */
    public boolean isMenuOpen() {
        return context.isMenuOpen();
    }

    public void keyRepeated(int keyCode) {
        deliver("keyRepeated", keyCode);
    }

    private void deliver(String method, int keyCode) {
        VmObject current = context.current();
        if (current == null || state != STATE_ACTIVE || !isCanvas(current)) {
            return;
        }
        vm.callVirtual(current, method, "(I)V", Integer.valueOf(keyCode));
    }

    public void pointerPressed(int x, int y) {
        // The command bar along the bottom of the screen is a button on a
        // touchscreen, which is how these games were played on one.
        int hit = SystemChrome.softKeyHit(context, x, y);
        if (hit != SystemChrome.HIT_NONE) {
            pressSoftKey(hit == SystemChrome.HIT_LEFT);
            return;
        }
        if (ScreenInput.pointerPressed(context, x, y, commandSink())) {
            return;
        }
        deliverPointer("pointerPressed", x, y);
    }

    public void pointerReleased(int x, int y) {
        deliverPointer("pointerReleased", x, y);
    }

    public void pointerDragged(int x, int y) {
        deliverPointer("pointerDragged", x, y);
    }

    private void deliverPointer(String method, int x, int y) {
        VmObject current = context.current();
        if (current == null || state != STATE_ACTIVE || !isCanvas(current)) {
            return;
        }
        // Touches arrive in display coordinates; the game thinks in canvas
        // coordinates, which start below the title bar.
        int canvasX = x - context.canvasLeft();
        int canvasY = y - context.canvasTop();
        if (canvasX < 0 || canvasY < 0
                || canvasX >= context.canvasWidth() || canvasY >= context.canvasHeight()) {
            return;
        }
        vm.callVirtual(current, method, "(II)V",
                Integer.valueOf(canvasX), Integer.valueOf(canvasY));
    }

    /** Invokes the current screen's command listener, as a softkey press does. */
    public boolean invokeCommand(VmObject command) {
        VmObject current = context.current();
        if (current == null || command == null) {
            return false;
        }
        Object listener = current.get("commandListener");
        if (listener == null) {
            return false;
        }
        vm.callVirtual((VmObject) listener, "commandAction",
                "(Ljavax/microedition/lcdui/Command;Ljavax/microedition/lcdui/Displayable;)V",
                command, current);
        return true;
    }
}
