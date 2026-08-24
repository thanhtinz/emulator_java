package com.mobicore.core.midp;

import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

import java.util.List;

/**
 * Keypad and touch handling for the screens the emulator draws.
 *
 * <p>A {@code Canvas} gets its key codes raw, because the game is the thing
 * interpreting them. A {@code List} does not: the handset walked the selection,
 * ticked the boxes and typed the letters, and only told the MIDlet the result.
 * This is that half of the device.</p>
 */
public final class ScreenInput {

    /** How the caller runs a command the player picked. */
    public interface Commands {
        void invoke(VmObject command);
    }

    /** Multi-tap letters, as they were printed on a handset's keys. */
    private static final String[] KEY_LETTERS = {
            " 0", ".,?!1", "abc2", "def3", "ghi4", "jkl5", "mno6", "pqrs7", "tuv8", "wxyz9",
    };

    /**
     * How long the same key keeps cycling letters instead of committing one.
     * A second is what handsets settled on; shorter makes "cc" impossible to
     * type, longer makes every word feel stuck.
     */
    private static final long MULTITAP_MS = 900;

    private ScreenInput() {
    }

    /**
     * Delivers a key to the current high level screen.
     *
     * @return true when the screen consumed it
     */
    public static boolean keyPressed(MidpContext context, int keyCode, Commands commands) {
        if (context.isMenuOpen()) {
            return menuKey(context, keyCode, commands);
        }
        Vm vm = context.vm();
        VmObject screen = context.current();
        if (!ScreenRenderer.isHighLevel(vm, screen)) {
            return false;
        }
        int action = MidpContext.gameAction(keyCode);
        if (isType(vm, screen, MidpForms.TEXT_BOX)) {
            return textKey(context, screen, keyCode);
        }
        if (isType(vm, screen, MidpForms.LIST)) {
            return listKey(context, screen, action, commands);
        }
        if (isType(vm, screen, MidpForms.FORM)) {
            return formKey(context, screen, keyCode, action);
        }
        // An Alert has nothing to move through; any key dismisses it the way a
        // handset's did, by running whatever command it carries.
        if (isType(vm, screen, MidpForms.ALERT) && action == MidpContext.ACTION_FIRE) {
            VmObject command = context.leftCommand();
            if (command != null) {
                commands.invoke(command);
                return true;
            }
        }
        return false;
    }

    private static boolean isType(Vm vm, VmObject object, String internalName) {
        return object != null && object.type().isAssignableTo(vm.loadClass(internalName));
    }

    // ------------------------------------------------------------------ menu

    private static boolean menuKey(MidpContext context, int keyCode, Commands commands) {
        int action = MidpContext.gameAction(keyCode);
        if (action == MidpContext.ACTION_UP) {
            context.moveMenu(-1);
            return true;
        }
        if (action == MidpContext.ACTION_DOWN) {
            context.moveMenu(1);
            return true;
        }
        if (action == MidpContext.ACTION_FIRE) {
            VmObject command = context.menuSelection();
            context.closeMenu();
            if (command != null) {
                commands.invoke(command);
            }
            return true;
        }
        if (keyCode == MidpContext.KEY_CLEAR) {
            context.closeMenu();
            return true;
        }
        // Everything else is swallowed: the menu is modal, as a handset's was.
        return true;
    }

    // ------------------------------------------------------------------ List

    private static boolean listKey(MidpContext context, VmObject list,
                                   int action, Commands commands) {
        MidpForms.Choices choices = MidpForms.choicesOf(list);
        if (choices.size() == 0) {
            return false;
        }
        int focus = MidpForms.intField(list, "focus");
        if (action == MidpContext.ACTION_UP || action == MidpContext.ACTION_DOWN) {
            int delta = action == MidpContext.ACTION_UP ? -1 : 1;
            focus = wrap(focus + delta, choices.size());
            list.set("focus", Integer.valueOf(focus));
            if (MidpForms.intField(list, "choiceType") == MidpForms.EXCLUSIVE) {
                // An exclusive list selects as it moves, which is what makes it
                // usable with two keys and no confirm step.
                choices.selectOnly(focus);
            }
            context.requestRepaint();
            return true;
        }
        if (action == MidpContext.ACTION_FIRE) {
            return activateList(context, list, choices, focus, commands);
        }
        // No digit shortcuts: 2, 4, 6 and 8 are the directions on a keypad, so
        // a list that also read them as row numbers would move twice at once.
        return false;
    }

    private static boolean activateList(MidpContext context, VmObject list,
                                        MidpForms.Choices choices, int focus, Commands commands) {
        int type = MidpForms.intField(list, "choiceType");
        if (type == MidpForms.MULTIPLE) {
            choices.setSelected(focus, !choices.selected(focus));
            context.requestRepaint();
            return true;
        }
        choices.selectOnly(focus);
        context.requestRepaint();
        if (type == MidpForms.IMPLICIT) {
            VmObject select = (VmObject) list.get("selectCommand");
            if (select != null) {
                commands.invoke(select);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Form

    private static boolean formKey(MidpContext context, VmObject form, int keyCode, int action) {
        Vm vm = context.vm();
        List<VmObject> items = MidpForms.itemsOf(form);
        if (items.isEmpty()) {
            return false;
        }
        int focus = clamp(MidpForms.intField(form, "focus"), 0, items.size() - 1);
        VmObject item = items.get(focus);

        // A text field owns the keypad while it has the focus. The digits are
        // its letters there, not directions: 2 types "a" rather than moving up,
        // which is how a handset behaved and what a name field needs.
        if (isType(vm, item, MidpForms.TEXT_FIELD) && isTypingKey(keyCode)) {
            boolean typed = textKey(context, item, keyCode);
            if (typed) {
                notifyItemChanged(context, form, item);
            }
            return typed;
        }

        if (action == MidpContext.ACTION_UP || action == MidpContext.ACTION_DOWN) {
            int delta = action == MidpContext.ACTION_UP ? -1 : 1;
            // A choice group is walked row by row before the focus leaves it,
            // exactly as it reads on screen.
            if (isType(vm, item, MidpForms.CHOICE_GROUP) && moveInside(context, item, delta)) {
                return true;
            }
            form.set("focus", Integer.valueOf(wrap(focus + delta, items.size())));
            context.requestRepaint();
            return true;
        }
        if (action == MidpContext.ACTION_FIRE) {
            if (isType(vm, item, MidpForms.CHOICE_GROUP)) {
                toggleChoice(context, form, item);
                return true;
            }
            return false;
        }
        if ((action == MidpContext.ACTION_LEFT || action == MidpContext.ACTION_RIGHT)
                && isType(vm, item, MidpForms.GAUGE)
                && MidpForms.intField(item, "interactive") != 0) {
            int step = action == MidpContext.ACTION_LEFT ? -1 : 1;
            int max = MidpForms.intField(item, "maxValue");
            int value = clamp(MidpForms.intField(item, "value") + step, 0, Math.max(0, max));
            item.set("value", Integer.valueOf(value));
            notifyItemChanged(context, form, item);
            context.requestRepaint();
            return true;
        }
        return false;
    }

    /** Keys a text field claims: its letters, its symbols and its backspace. */
    private static boolean isTypingKey(int keyCode) {
        return (keyCode >= '0' && keyCode <= '9') || keyCode == '*' || keyCode == '#'
                || keyCode == MidpContext.KEY_CLEAR;
    }

    /** Moves within a choice group; false once the focus falls off its end. */
    private static boolean moveInside(MidpContext context, VmObject group, int delta) {
        MidpForms.Choices choices = MidpForms.choicesOf(group);
        int inner = MidpForms.intField(group, "focus") + delta;
        if (inner < 0 || inner >= choices.size()) {
            return false;
        }
        group.set("focus", Integer.valueOf(inner));
        context.requestRepaint();
        return true;
    }

    private static void toggleChoice(MidpContext context, VmObject form, VmObject group) {
        MidpForms.Choices choices = MidpForms.choicesOf(group);
        int inner = clamp(MidpForms.intField(group, "focus"), 0, Math.max(0, choices.size() - 1));
        if (choices.size() == 0) {
            return;
        }
        if (MidpForms.intField(group, "choiceType") == MidpForms.MULTIPLE) {
            choices.setSelected(inner, !choices.selected(inner));
        } else {
            choices.selectOnly(inner);
        }
        notifyItemChanged(context, form, group);
        context.requestRepaint();
    }

    /**
     * Tells the MIDlet the player changed an item, which is how a Form reports
     * anything at all.
     */
    private static void notifyItemChanged(MidpContext context, VmObject form, VmObject item) {
        Object listener = form.get("itemStateListener");
        if (listener == null) {
            return;
        }
        context.vm().callVirtual((VmObject) listener, "itemStateChanged",
                "(Ljavax/microedition/lcdui/Item;)V", item);
    }

    // ---------------------------------------------------------------- typing

    /**
     * Types into a TextBox or TextField.
     *
     * <p>Multi-tap, because that is the only way a numeric keypad ever entered
     * letters, and a game that asks for a name expects it.</p>
     */
    private static boolean textKey(MidpContext context, VmObject field, int keyCode) {
        StringBuilder text = MidpForms.textOf(field);
        int max = Math.max(1, MidpForms.intField(field, "maxSize"));
        if (keyCode == MidpContext.KEY_CLEAR) {
            if (text.length() == 0) {
                return false;
            }
            text.setLength(text.length() - 1);
            context.clearTap();
            field.set("caret", Integer.valueOf(text.length()));
            context.requestRepaint();
            return true;
        }
        int constraints = MidpForms.intField(field, "constraints") & MidpForms.CONSTRAINT_MASK;
        boolean digitsOnly = constraints == MidpForms.NUMERIC
                || constraints == MidpForms.PHONENUMBER
                || constraints == MidpForms.DECIMAL;

        if (keyCode < '0' || keyCode > '9') {
            if (keyCode == '*' || keyCode == '#') {
                return typeChar(context, field, text, max, (char) keyCode);
            }
            return false;
        }
        if (digitsOnly) {
            return typeChar(context, field, text, max, (char) keyCode);
        }

        long now = context.vm().host().currentTimeMillis();
        String letters = KEY_LETTERS[keyCode - '0'];
        boolean cycling = context.tapField() == field && context.tapKey() == keyCode
                && now - context.tapAt() < MULTITAP_MS && text.length() > 0;
        int index;
        if (cycling) {
            index = (context.tapIndex() + 1) % letters.length();
            text.setCharAt(text.length() - 1, letters.charAt(index));
        } else {
            if (text.length() >= max) {
                return false;
            }
            index = 0;
            text.append(letters.charAt(0));
        }
        context.setTap(field, keyCode, index, now);
        field.set("caret", Integer.valueOf(text.length()));
        context.requestRepaint();
        return true;
    }

    private static boolean typeChar(MidpContext context, VmObject field, StringBuilder text,
                                    int max, char c) {
        if (text.length() >= max) {
            return false;
        }
        text.append(c);
        context.clearTap();
        field.set("caret", Integer.valueOf(text.length()));
        context.requestRepaint();
        return true;
    }

    // ----------------------------------------------------------------- touch

    /**
     * A tap on a high level screen, in display coordinates.
     *
     * <p>Handsets with these screens were keypad devices, so this is not
     * something MIDP describes. It is here because the emulator runs on a phone
     * that has no keypad at all, and a list you cannot tap is a list you cannot
     * use.</p>
     */
    public static boolean pointerPressed(MidpContext context, int x, int y, Commands commands) {
        Vm vm = context.vm();
        if (context.isMenuOpen()) {
            return pointerOnMenu(context, y, commands);
        }
        VmObject screen = context.current();
        if (!ScreenRenderer.isHighLevel(vm, screen) || !isType(vm, screen, MidpForms.LIST)) {
            return false;
        }
        MidpForms.Choices choices = MidpForms.choicesOf(screen);
        int row = ScreenRenderer.rowHeight();
        int index = MidpForms.intField(screen, "scroll") + (y - context.canvasTop()) / row;
        if (y < context.canvasTop() || index < 0 || index >= choices.size()) {
            return false;
        }
        boolean again = index == MidpForms.intField(screen, "focus");
        screen.set("focus", Integer.valueOf(index));
        context.requestRepaint();
        // The first tap moves the selection, the second runs it: a single tap
        // that both moves and confirms makes a mis-tap unrecoverable.
        return !again || activateList(context, screen, choices, index, commands);
    }

    private static boolean pointerOnMenu(MidpContext context, int y, Commands commands) {
        List<VmObject> commandList = context.menuCommands();
        if (commandList.isEmpty()) {
            return false;
        }
        int row = ScreenRenderer.rowHeight();
        int height = Math.min(commandList.size() * row + 8,
                context.screen().height() - 40);
        int top = context.screen().height() - SystemChrome.softKeyBarHeight() - height - 4;
        int index = (y - top - 4) / row;
        if (index < 0 || index >= commandList.size()) {
            context.closeMenu();
            return true;
        }
        if (index == context.menuIndex()) {
            VmObject command = context.menuSelection();
            context.closeMenu();
            if (command != null) {
                commands.invoke(command);
            }
            return true;
        }
        context.moveMenu(index - context.menuIndex());
        return true;
    }

    private static int wrap(int value, int size) {
        return size <= 0 ? 0 : ((value % size) + size) % size;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
