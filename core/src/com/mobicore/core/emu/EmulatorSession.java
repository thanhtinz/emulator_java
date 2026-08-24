package com.mobicore.core.emu;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.vm.JarClassSource;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.Midp;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.midp.MidpGfx;
import com.mobicore.core.midp.MidpUi;
import com.mobicore.core.model.MidletEntry;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.rt.Cldc;
import com.mobicore.core.vm.Descriptors;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmError;
import com.mobicore.core.vm.VmHost;
import com.mobicore.core.vm.VmObject;
import com.mobicore.core.vm.VmThrow;

import java.io.IOException;

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
    private final MidpContext context;
    private final MidletSuiteInfo info;
    private final JarClassSource source;
    private final EmulatorLog log;

    private VmObject midlet;
    private int state = STATE_NEW;
    private String midletClass;

    private EmulatorSession(Vm vm, MidpContext context, MidletSuiteInfo info,
                            JarClassSource source, EmulatorLog log) {
        this.vm = vm;
        this.context = context;
        this.info = info;
        this.source = source;
        this.log = log;
    }

    /** Builds a session for an installed suite at the given screen size. */
    public static EmulatorSession create(SuiteLoader suite, int width, int height, VmHost host) {
        EmulatorLog log = new EmulatorLog();
        Vm vm = new Vm();
        vm.setHost(host == null ? log.hostBridge(VmHost.DEFAULT) : log.hostBridge(host));
        MidpContext context = new MidpContext(vm, width, height);
        context.setAttributes(suite.info().attributes());

        Cldc.install(vm);
        Midp.install(vm, context);

        JarClassSource source = new JarClassSource(suite.archive());
        vm.addSource(source);
        return new EmulatorSession(vm, context, suite.info(), source, log);
    }

    public Vm vm() {
        return vm;
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
        context.drainCallbacks();
        VmObject current = context.current();
        if (current == null) {
            return false;
        }
        if (!context.consumeRepaint()) {
            return false;
        }
        if (!isCanvas(current)) {
            // High level screens are drawn by the shell, not by the game.
            return false;
        }
        Framebuffer screen = context.screen();
        screen.setTranslation(0, 0);
        screen.resetClip();
        VmObject graphics = MidpGfx.newGraphics(vm, screen);
        vm.callVirtual(current, "paint", "(Ljavax/microedition/lcdui/Graphics;)V", graphics);
        context.countFrame();
        return true;
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
        deliver("keyPressed", keyCode);
    }

    public void keyReleased(int keyCode) {
        int action = MidpContext.gameAction(keyCode);
        if (action != 0) {
            context.setKeyState(action, false);
        }
        deliver("keyReleased", keyCode);
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
        vm.callVirtual(current, method, "(II)V", Integer.valueOf(x), Integer.valueOf(y));
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
