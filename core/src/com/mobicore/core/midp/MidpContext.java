package com.mobicore.core.midp;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

import com.mobicore.core.jar.AttributeSet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-suite MIDP state: the screen, what is currently displayed and which keys
 * are held.
 *
 * <p>The emulator core owns this rather than the UI layer, so Android, iOS and
 * the desktop preview all drive an identical MIDlet through the same calls.</p>
 */
public final class MidpContext {

    /** Canvas game actions, as defined by MIDP. */
    public static final int ACTION_UP = 1;
    public static final int ACTION_LEFT = 2;
    public static final int ACTION_RIGHT = 5;
    public static final int ACTION_DOWN = 6;
    public static final int ACTION_FIRE = 8;
    public static final int ACTION_GAME_A = 9;
    public static final int ACTION_GAME_B = 10;
    public static final int ACTION_GAME_C = 11;
    public static final int ACTION_GAME_D = 12;

    /** Nokia-style key codes for the directional pad and softkeys. */
    public static final int KEY_UP = -1;
    public static final int KEY_DOWN = -2;
    public static final int KEY_LEFT = -3;
    public static final int KEY_RIGHT = -4;
    public static final int KEY_FIRE = -5;
    public static final int KEY_SOFT_LEFT = -6;
    public static final int KEY_SOFT_RIGHT = -7;
    public static final int KEY_CLEAR = -8;
    public static final int KEY_SEND = -10;
    public static final int KEY_END = -11;

    private final Vm vm;
    private final Framebuffer screen;
    private final List<VmObject> pendingCallbacks = new ArrayList<VmObject>();
    /**
     * Commands attached to each Displayable. Held here rather than in an
     * emulated field so the virtual phone can read a screen's softkeys without
     * calling back into the game.
     */
    private final Map<VmObject, List<VmObject>> commands = new IdentityHashMap<VmObject, List<VmObject>>();
    private AttributeSet attributes = new AttributeSet();

    private VmObject current;
    private VmObject midlet;
    private int keyStates;
    private boolean repaintRequested = true;
    private boolean fullScreen;
    private boolean destroyed;
    private String title;
    private int frames;
    private boolean smoothShapes;

    public MidpContext(Vm vm, int width, int height) {
        this.vm = vm;
        this.screen = new Framebuffer(width, height);
    }

    public Vm vm() {
        return vm;
    }

    /** Suite attributes, as seen by {@code MIDlet.getAppProperty}. */
    public AttributeSet attributes() {
        return attributes;
    }

    public void setAttributes(AttributeSet attributes) {
        this.attributes = attributes == null ? new AttributeSet() : attributes;
    }

    public void addCommand(VmObject displayable, VmObject command) {
        List<VmObject> list = commands.get(displayable);
        if (list == null) {
            list = new ArrayList<VmObject>();
            commands.put(displayable, list);
        }
        if (!list.contains(command)) {
            list.add(command);
        }
    }

    public void removeCommand(VmObject displayable, VmObject command) {
        List<VmObject> list = commands.get(displayable);
        if (list != null) {
            list.remove(command);
        }
    }

    public List<VmObject> commandsOf(VmObject displayable) {
        List<VmObject> list = commands.get(displayable);
        return list == null ? new ArrayList<VmObject>() : list;
    }

    public Framebuffer screen() {
        return screen;
    }

    /** Whether shapes the game draws get anti-aliased edges. */
    public boolean smoothShapes() {
        return smoothShapes;
    }

    public void setSmoothShapes(boolean smoothShapes) {
        this.smoothShapes = smoothShapes;
        screen.setAntialias(smoothShapes);
    }

    public int width() {
        return screen.width();
    }

    public int height() {
        return screen.height();
    }

    public VmObject current() {
        return current;
    }

    public void setCurrent(VmObject displayable) {
        this.current = displayable;
        requestRepaint();
    }

    public VmObject midlet() {
        return midlet;
    }

    public void setMidlet(VmObject midlet) {
        this.midlet = midlet;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    public void setFullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void markDestroyed() {
        destroyed = true;
    }

    public int frames() {
        return frames;
    }

    public void countFrame() {
        frames++;
    }

    // ------------------------------------------------------------ repainting

    public void requestRepaint() {
        repaintRequested = true;
    }

    public boolean consumeRepaint() {
        boolean requested = repaintRequested;
        repaintRequested = false;
        return requested;
    }

    public boolean isRepaintRequested() {
        return repaintRequested;
    }

    /** Queues a {@code Display.callSerially} runnable. */
    public void queueCallback(VmObject runnable) {
        synchronized (pendingCallbacks) {
            pendingCallbacks.add(runnable);
        }
    }

    /** Runs and clears the queued callbacks; called between frames. */
    public void drainCallbacks() {
        List<VmObject> due;
        synchronized (pendingCallbacks) {
            if (pendingCallbacks.isEmpty()) {
                return;
            }
            due = new ArrayList<VmObject>(pendingCallbacks);
            pendingCallbacks.clear();
        }
        for (VmObject runnable : due) {
            vm.callVirtual(runnable, "run", "()V");
        }
    }

    // ----------------------------------------------------------------- input

    public int keyStates() {
        return keyStates;
    }

    /** Clears the latched key state, as {@code GameCanvas.getKeyStates} does. */
    public int consumeKeyStates() {
        return keyStates;
    }

    public void setKeyState(int gameAction, boolean pressed) {
        int bit = 1 << gameAction;
        if (pressed) {
            keyStates |= bit;
        } else {
            keyStates &= ~bit;
        }
    }

    /** Maps a device key code to a MIDP game action. */
    public static int gameAction(int keyCode) {
        switch (keyCode) {
            case KEY_UP: case '2': return ACTION_UP;
            case KEY_DOWN: case '8': return ACTION_DOWN;
            case KEY_LEFT: case '4': return ACTION_LEFT;
            case KEY_RIGHT: case '6': return ACTION_RIGHT;
            case KEY_FIRE: case '5': return ACTION_FIRE;
            case '7': return ACTION_GAME_A;
            case '9': return ACTION_GAME_B;
            case '*': return ACTION_GAME_C;
            case '#': return ACTION_GAME_D;
            default: return 0;
        }
    }

    /** Reverse mapping used when a controller reports an action, not a key. */
    public static int keyCode(int gameAction) {
        switch (gameAction) {
            case ACTION_UP: return KEY_UP;
            case ACTION_DOWN: return KEY_DOWN;
            case ACTION_LEFT: return KEY_LEFT;
            case ACTION_RIGHT: return KEY_RIGHT;
            case ACTION_FIRE: return KEY_FIRE;
            case ACTION_GAME_A: return '7';
            case ACTION_GAME_B: return '9';
            case ACTION_GAME_C: return '*';
            case ACTION_GAME_D: return '#';
            default: return 0;
        }
    }

    /**
     * Human readable key name, as {@code Canvas.getKeyName} returns it. Games
     * print this on screen, so it is localised along with the rest of the
     * interface.
     */
    public static String keyName(int keyCode) {
        switch (keyCode) {
            case KEY_UP: return "Lên";
            case KEY_DOWN: return "Xuống";
            case KEY_LEFT: return "Trái";
            case KEY_RIGHT: return "Phải";
            case KEY_FIRE: return "Chọn";
            case KEY_SOFT_LEFT: return "Mềm 1";
            case KEY_SOFT_RIGHT: return "Mềm 2";
            case KEY_CLEAR: return "Xóa";
            case KEY_SEND: return "Gọi";
            case KEY_END: return "Kết thúc";
            default: return keyCode >= 32 && keyCode < 127 ? String.valueOf((char) keyCode)
                    : "Phím " + keyCode;
        }
    }
}
