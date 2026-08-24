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
    /** Height reserved by the system chrome; see {@link #canvasHeight()}. */
    private int titleBarHeight;
    private int softKeyBarHeight;
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

    /** Commands on the screen currently shown. */
    public List<VmObject> currentCommands() {
        return commandsOf(current);
    }

    /**
     * The command the left softkey triggers, or {@code null}.
     *
     * <p>MIDP does not dictate the mapping, but every handset settled on the
     * same convention: anything that goes back or gets out sits on the right,
     * everything else on the left. A player who has used a J2ME phone reaches
     * for the right key to quit without reading the label.</p>
     */
    public VmObject leftCommand() {
        List<VmObject> positive = commandsByPlacement(false);
        return positive.isEmpty() ? null : positive.get(0);
    }

    public VmObject rightCommand() {
        List<VmObject> negative = commandsByPlacement(true);
        return negative.isEmpty() ? null : negative.get(0);
    }

    /**
     * Commands that do not fit on the two softkeys, which a handset would put
     * behind an "Options" menu.
     */
    public List<VmObject> menuCommands() {
        List<VmObject> positive = commandsByPlacement(false);
        return positive.size() <= 1 ? new ArrayList<VmObject>()
                : new ArrayList<VmObject>(positive.subList(1, positive.size()));
    }

    /** Command types a handset puts on the right softkey. */
    private static boolean isNegative(int commandType) {
        return commandType == COMMAND_BACK || commandType == COMMAND_CANCEL
                || commandType == COMMAND_EXIT || commandType == COMMAND_STOP;
    }

    public static final int COMMAND_SCREEN = 1;
    public static final int COMMAND_BACK = 2;
    public static final int COMMAND_CANCEL = 3;
    public static final int COMMAND_OK = 4;
    public static final int COMMAND_HELP = 5;
    public static final int COMMAND_STOP = 6;
    public static final int COMMAND_EXIT = 7;
    public static final int COMMAND_ITEM = 8;

    private List<VmObject> commandsByPlacement(boolean negative) {
        List<VmObject> matching = new ArrayList<VmObject>();
        for (VmObject command : commandsOf(current)) {
            int type = ((Integer) command.get("commandType")).intValue();
            if (isNegative(type) == negative) {
                matching.add(command);
            }
        }
        // Lower priority wins the softkey, as the specification defines it.
        for (int i = 1; i < matching.size(); i++) {
            VmObject value = matching.get(i);
            int priority = priorityOf(value);
            int j = i - 1;
            while (j >= 0 && priorityOf(matching.get(j)) > priority) {
                matching.set(j + 1, matching.get(j));
                j--;
            }
            matching.set(j + 1, value);
        }
        return matching;
    }

    private static int priorityOf(VmObject command) {
        return ((Integer) command.get("priority")).intValue();
    }

    /** Label a softkey should show for a command. */
    public String labelOf(VmObject command) {
        if (command == null) {
            return null;
        }
        Object label = command.get("label");
        return label == null ? null : vm.stringOf(label);
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
        // The menu listed the old screen's commands; it cannot outlive it.
        menuOpen = false;
        menuIndex = 0;
        // A handset shows the title of whatever screen it is on. Only setTitle
        // used to reach here, so switching screens left the previous screen's
        // title in the bar.
        Object next = displayable == null ? null : displayable.get("title");
        this.title = next == null ? null : vm.stringOf(next);
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
        requestRepaint();
    }

    /**
     * Reserves the strips the system draws in, in device pixels.
     *
     * <p>Set once by the emulator from the font it draws the chrome with, so
     * the core does not have to know how the chrome looks.</p>
     */
    public void setChromeHeights(int titleBar, int softKeyBar) {
        this.titleBarHeight = Math.max(0, titleBar);
        this.softKeyBarHeight = Math.max(0, softKeyBar);
    }

    /**
     * Left edge of the area the game draws into. Always zero today; kept so
     * callers do not assume a full-width canvas.
     */
    public int canvasLeft() {
        return 0;
    }

    /**
     * Top of the game's drawing area.
     *
     * <p>A MIDlet that has not asked for full screen mode gets a smaller canvas
     * than the display, exactly as a handset gives it: the system keeps a strip
     * at the top for the screen title and one at the bottom for the softkey
     * labels. A game reads {@code getHeight()} and lays its whole screen out
     * from it, so getting this wrong pushes its HUD off the display.</p>
     */
    public int canvasTop() {
        return fullScreen || !hasTitle() ? 0 : titleBarHeight;
    }

    public int canvasWidth() {
        return width();
    }

    public int canvasHeight() {
        if (fullScreen) {
            return height();
        }
        return height() - canvasTop() - (hasSoftKeys() ? softKeyBarHeight : 0);
    }

    // ---------------------------------------------------------- multi-tap

    /**
     * Where multi-tap text entry got to: the field being typed into, the key
     * held down, how far around its letters the player has cycled and when.
     *
     * <p>State per running MIDlet rather than per process, so two sessions in
     * one emulator do not type into each other.</p>
     */
    private VmObject tapField;
    private int tapKey;
    private int tapIndex;
    private long tapAt;

    public VmObject tapField() {
        return tapField;
    }

    public int tapKey() {
        return tapKey;
    }

    public int tapIndex() {
        return tapIndex;
    }

    public long tapAt() {
        return tapAt;
    }

    public void setTap(VmObject field, int keyCode, int index, long at) {
        this.tapField = field;
        this.tapKey = keyCode;
        this.tapIndex = index;
        this.tapAt = at;
    }

    public void clearTap() {
        this.tapField = null;
    }

    // ------------------------------------------------------------- menu

    private boolean menuOpen;
    private int menuIndex;

    /**
     * Whether the "Options" list is open over the screen.
     *
     * <p>A handset could only ever label two commands, so everything past the
     * first went behind a menu. Without the menu those commands exist, are
     * counted, and can never be run.</p>
     */
    public boolean isMenuOpen() {
        return menuOpen;
    }

    public int menuIndex() {
        return menuIndex;
    }

    public void openMenu() {
        if (menuCommands().isEmpty()) {
            return;
        }
        menuOpen = true;
        menuIndex = 0;
        requestRepaint();
    }

    public void closeMenu() {
        if (!menuOpen) {
            return;
        }
        menuOpen = false;
        menuIndex = 0;
        requestRepaint();
    }

    /** Moves the menu selection, wrapping as a handset's list does. */
    public void moveMenu(int delta) {
        List<VmObject> commands = menuCommands();
        if (!menuOpen || commands.isEmpty()) {
            return;
        }
        int size = commands.size();
        menuIndex = ((menuIndex + delta) % size + size) % size;
        requestRepaint();
    }

    /** The command the menu is sitting on, or {@code null}. */
    public VmObject menuSelection() {
        List<VmObject> commands = menuCommands();
        if (!menuOpen || commands.isEmpty()) {
            return null;
        }
        return commands.get(Math.max(0, Math.min(menuIndex, commands.size() - 1)));
    }

    /** True when the current screen has a title the system should show. */
    public boolean hasTitle() {
        return title != null && title.length() > 0;
    }

    /** True when the system should reserve room for softkey labels. */
    public boolean hasSoftKeys() {
        return !fullScreen && !commandsOf(current).isEmpty();
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

    private boolean chromeDirty = true;

    public void requestRepaint() {
        repaintRequested = true;
        chromeDirty = true;
    }

    /** The system chrome needs redrawing over the freshly painted screen. */
    public void markChromeDirty() {
        chromeDirty = true;
    }

    public boolean consumeChromeDirty() {
        boolean dirty = chromeDirty;
        chromeDirty = false;
        return dirty;
    }

    /**
     * Tells a Canvas its drawing area changed size, as MIDP requires whenever
     * the system takes room away or gives it back.
     */
    public void notifySizeChanged(VmObject displayable) {
        requestRepaint();
        if (displayable == null) {
            return;
        }
        vm.callVirtual(displayable, "sizeChanged", "(II)V",
                Integer.valueOf(canvasWidth()), Integer.valueOf(canvasHeight()));
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
