package com.mobicore.core.midp;

import com.mobicore.core.gfx.BitmapFont;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints the high level screens.
 *
 * <p>MIDP says what a {@code List} is and never what it looks like: the
 * handset drew it, which is why the same MIDlet looked like a Nokia on a Nokia
 * and like a Samsung on a Samsung. This class is the emulator's answer, drawn
 * with the same muted grey-blue the system chrome uses so the whole device
 * looks like one device.</p>
 *
 * <p>It draws into the canvas area only — the strip between the title bar and
 * the softkey labels — because those two belong to {@link SystemChrome} and are
 * shared with games that draw their own pixels.</p>
 */
public final class ScreenRenderer {

    private static final int BACKGROUND = 0xFF10151C;
    private static final int TEXT = 0xFFE8EEF5;
    private static final int TEXT_DIM = 0xFF8C9AAB;
    private static final int SELECTION = 0xFF3D6EA5;
    private static final int SELECTION_TEXT = 0xFFFFFFFF;
    private static final int RULE = 0xFF243040;
    private static final int FIELD = 0xFF1B2431;
    private static final int ACCENT = 0xFF6FA8DC;

    private static final int PAD = 6;
    private static final int ROW_EXTRA = 8;

    private ScreenRenderer() {
    }

    private static BitmapFont plain() {
        return BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_PLAIN);
    }

    private static BitmapFont bold() {
        return BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_BOLD);
    }

    /** True when this screen is one the emulator draws rather than the game. */
    public static boolean isHighLevel(Vm vm, VmObject displayable) {
        if (displayable == null) {
            return false;
        }
        return displayable.type().isAssignableTo(vm.loadClass(MidpForms.SCREEN));
    }

    /**
     * Draws the current screen. Returns false when the current displayable is
     * not one of ours, which leaves the caller to paint a Canvas instead.
     */
    public static boolean render(MidpContext context) {
        Vm vm = context.vm();
        VmObject screen = context.current();
        if (!isHighLevel(vm, screen)) {
            return false;
        }
        Framebuffer frame = context.screen();
        frame.setTranslation(0, 0);
        frame.resetClip();

        int top = context.canvasTop();
        int height = context.canvasHeight();
        frame.setColor(BACKGROUND);
        frame.fillRect(0, top, frame.width(), height);
        frame.setClip(0, top, frame.width(), height);
        frame.translate(0, top);

        int inner = height;
        if (screen.type().isAssignableTo(vm.loadClass(MidpForms.LIST))) {
            drawList(vm, frame, screen, inner);
        } else if (screen.type().isAssignableTo(vm.loadClass(MidpForms.TEXT_BOX))) {
            drawTextBox(vm, frame, screen, inner);
        } else if (screen.type().isAssignableTo(vm.loadClass(MidpForms.ALERT))) {
            drawAlert(vm, frame, screen, inner);
        } else if (screen.type().isAssignableTo(vm.loadClass(MidpForms.FORM))) {
            drawForm(vm, frame, screen, inner);
        }

        frame.setTranslation(0, 0);
        frame.resetClip();
        SystemChrome.draw(context);
        drawMenu(context, frame);
        context.countFrame();
        return true;
    }

    // ------------------------------------------------------------------ List

    static int rowHeight() {
        return plain().height() + ROW_EXTRA;
    }

    private static void drawList(Vm vm, Framebuffer frame, VmObject list, int height) {
        MidpForms.Choices choices = MidpForms.choicesOf(list);
        if (choices.size() == 0) {
            drawEmpty(frame, height, "Danh sách trống");
            return;
        }
        int type = MidpForms.intField(list, "choiceType");
        int row = rowHeight();
        int visible = Math.max(1, height / row);
        int focus = MidpForms.intField(list, "focus");
        int scroll = scrollFor(list, focus, visible, choices.size());

        BitmapFont font = plain();
        for (int i = 0; i < visible && scroll + i < choices.size(); i++) {
            int index = scroll + i;
            int y = i * row;
            boolean focused = index == focus;
            if (focused) {
                frame.setColor(SELECTION);
                frame.fillRect(0, y, frame.width(), row);
            }
            int x = PAD;
            if (type == MidpForms.MULTIPLE || type == MidpForms.EXCLUSIVE) {
                drawMarker(frame, x, y + (row - 10) / 2, type, choices.selected(index), focused);
                x += 16;
            }
            VmObject image = choices.image(index);
            if (image != null && image.host instanceof Framebuffer) {
                Framebuffer icon = (Framebuffer) image.host;
                frame.drawFramebuffer(icon, x, y + (row - icon.height()) / 2);
                x += icon.width() + 4;
            }
            frame.setColor(focused ? SELECTION_TEXT : TEXT);
            font.draw(frame, clip(font, choices.string(index), frame.width() - x - PAD),
                    x, y + (row - font.height()) / 2);
            if (!focused) {
                frame.setColor(RULE);
                frame.fillRect(0, y + row - 1, frame.width(), 1);
            }
        }
        drawScrollbar(frame, height, scroll, visible, choices.size());
    }

    /** The radio dot or tick box an exclusive or multiple choice shows. */
    private static void drawMarker(Framebuffer frame, int x, int y, int type,
                                   boolean selected, boolean focused) {
        int outline = focused ? SELECTION_TEXT : ACCENT;
        if (type == MidpForms.MULTIPLE) {
            frame.setColor(outline);
            frame.drawRect(x, y, 10, 10);
            if (selected) {
                frame.fillRect(x + 3, y + 3, 5, 5);
            }
        } else {
            frame.setColor(outline);
            frame.drawArc(x, y, 10, 10, 0, 360);
            if (selected) {
                frame.fillArc(x + 3, y + 3, 5, 5, 0, 360);
            }
        }
    }

    /**
     * Keeps the focused row on screen and remembers where the screen was
     * scrolled to, as a handset does between repaints.
     */
    private static int scrollFor(VmObject screen, int focus, int visible, int count) {
        int scroll = MidpForms.intField(screen, "scroll");
        if (focus < scroll) {
            scroll = focus;
        } else if (focus >= scroll + visible) {
            scroll = focus - visible + 1;
        }
        if (scroll > count - visible) {
            scroll = count - visible;
        }
        if (scroll < 0) {
            scroll = 0;
        }
        screen.set("scroll", Integer.valueOf(scroll));
        return scroll;
    }

    private static void drawScrollbar(Framebuffer frame, int height, int scroll,
                                      int visible, int count) {
        if (count <= visible) {
            return;
        }
        int x = frame.width() - 3;
        frame.setColor(RULE);
        frame.fillRect(x, 0, 3, height);
        int barHeight = Math.max(8, height * visible / count);
        int barTop = height * scroll / count;
        frame.setColor(ACCENT);
        frame.fillRect(x, barTop, 3, barHeight);
    }

    // ------------------------------------------------------------------ Form

    private static void drawForm(Vm vm, Framebuffer frame, VmObject form, int height) {
        List<VmObject> items = MidpForms.itemsOf(form);
        if (items.isEmpty()) {
            drawEmpty(frame, height, "Biểu mẫu trống");
            return;
        }
        int focus = MidpForms.intField(form, "focus");
        // Lay every item out first, so the scroll offset can be measured
        // against real heights rather than a guess.
        int[] tops = new int[items.size()];
        int[] heights = new int[items.size()];
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            tops[i] = total;
            heights[i] = itemHeight(vm, items.get(i));
            total += heights[i];
        }
        int scroll = MidpForms.intField(form, "scroll");
        if (focus >= 0 && focus < items.size()) {
            if (tops[focus] < scroll) {
                scroll = tops[focus];
            } else if (tops[focus] + heights[focus] > scroll + height) {
                scroll = tops[focus] + heights[focus] - height;
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - height)));
        form.set("scroll", Integer.valueOf(scroll));

        for (int i = 0; i < items.size(); i++) {
            int y = tops[i] - scroll;
            if (y + heights[i] < 0 || y > height) {
                continue;
            }
            drawItem(vm, frame, items.get(i), y, heights[i], i == focus);
        }
    }

    private static boolean isType(Vm vm, VmObject object, String internalName) {
        return object != null && object.type().isAssignableTo(vm.loadClass(internalName));
    }

    /** Height one form item occupies, label included. */
    private static int itemHeight(Vm vm, VmObject item) {
        BitmapFont font = plain();
        int line = font.height() + 2;
        int height = hasLabel(item) ? line : 0;
        if (isType(vm, item, MidpForms.CHOICE_GROUP)) {
            return height + Math.max(1, MidpForms.choicesOf(item).size()) * rowHeight() + 4;
        }
        if (isType(vm, item, MidpForms.IMAGE_ITEM)) {
            VmObject image = (VmObject) item.get("image");
            int imageHeight = image != null && image.host instanceof Framebuffer
                    ? ((Framebuffer) image.host).height() : line;
            return height + imageHeight + 6;
        }
        if (isType(vm, item, MidpForms.TEXT_FIELD) || isType(vm, item, MidpForms.GAUGE)
                || isType(vm, item, MidpForms.DATE_FIELD)) {
            return height + line + 10;
        }
        return height + line + 4;
    }

    private static boolean hasLabel(VmObject item) {
        Object label = item.get("label");
        return label != null;
    }

    private static void drawItem(Vm vm, Framebuffer frame, VmObject item,
                                 int y, int height, boolean focused) {
        BitmapFont font = plain();
        if (focused) {
            frame.setColor(FIELD);
            frame.fillRect(0, y, frame.width(), height);
            frame.setColor(ACCENT);
            frame.fillRect(0, y, 2, height);
        }
        int cursor = y + 2;
        if (hasLabel(item)) {
            frame.setColor(TEXT_DIM);
            bold().draw(frame, clip(font, vm.stringOf(item.get("label")), frame.width() - PAD * 2),
                    PAD, cursor);
            cursor += font.height() + 2;
        }
        if (isType(vm, item, MidpForms.CHOICE_GROUP)) {
            drawChoiceGroup(vm, frame, item, cursor, focused);
            return;
        }
        if (isType(vm, item, MidpForms.TEXT_FIELD)) {
            drawTextValue(frame, cursor, displayText(item), focused);
            return;
        }
        if (isType(vm, item, MidpForms.GAUGE)) {
            drawGauge(frame, cursor, item);
            return;
        }
        if (isType(vm, item, MidpForms.DATE_FIELD)) {
            Object date = item.get("date");
            drawTextValue(frame, cursor, date == null ? "—" : "…", focused);
            return;
        }
        if (isType(vm, item, MidpForms.IMAGE_ITEM)) {
            VmObject image = (VmObject) item.get("image");
            if (image != null && image.host instanceof Framebuffer) {
                Framebuffer picture = (Framebuffer) image.host;
                frame.drawFramebuffer(picture, (frame.width() - picture.width()) / 2, cursor);
            }
            return;
        }
        Object text = item.get("text");
        frame.setColor(TEXT);
        font.draw(frame, clip(font, text == null ? "" : vm.stringOf(text), frame.width() - PAD * 2),
                PAD, cursor);
    }

    /** A password field shows what a handset showed: how long it is, not what it is. */
    private static String displayText(VmObject field) {
        String text = MidpForms.textOf(field).toString();
        if ((MidpForms.intField(field, "constraints") & MidpForms.PASSWORD) == 0) {
            return text;
        }
        StringBuilder masked = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            masked.append('*');
        }
        return masked.toString();
    }

    private static void drawTextValue(Framebuffer frame, int y, String value, boolean focused) {
        BitmapFont font = plain();
        int height = font.height() + 6;
        frame.setColor(focused ? SELECTION : FIELD);
        frame.fillRect(PAD, y, frame.width() - PAD * 2, height);
        frame.setColor(focused ? SELECTION_TEXT : TEXT);
        String text = clip(font, value, frame.width() - PAD * 2 - 8);
        font.draw(frame, text, PAD + 4, y + 3);
        if (focused) {
            // The caret sits after the text, which is where typing lands.
            int caretX = PAD + 4 + font.stringWidth(text);
            frame.fillRect(Math.min(caretX + 1, frame.width() - PAD - 2), y + 3, 1, font.height());
        }
    }

    private static void drawGauge(Framebuffer frame, int y, VmObject gauge) {
        int max = MidpForms.intField(gauge, "maxValue");
        int value = MidpForms.intField(gauge, "value");
        int width = frame.width() - PAD * 2;
        int height = plain().height() - 2;
        frame.setColor(FIELD);
        frame.fillRect(PAD, y, width, height);
        frame.setColor(ACCENT);
        if (max <= 0) {
            // An indefinite gauge has nothing to measure, so it shows a stub
            // rather than a full or an empty bar, both of which would lie.
            frame.fillRect(PAD, y, width / 4, height);
        } else {
            int filled = Math.max(0, Math.min(value, max)) * width / max;
            frame.fillRect(PAD, y, filled, height);
        }
        frame.setColor(RULE);
        frame.drawRect(PAD, y, width, height);
    }

    private static void drawChoiceGroup(Vm vm, Framebuffer frame, VmObject group,
                                        int y, boolean focused) {
        MidpForms.Choices choices = MidpForms.choicesOf(group);
        int type = MidpForms.intField(group, "choiceType");
        int row = rowHeight();
        int focus = MidpForms.intField(group, "focus");
        BitmapFont font = plain();
        for (int i = 0; i < choices.size(); i++) {
            int rowY = y + i * row;
            boolean here = focused && i == focus;
            if (here) {
                frame.setColor(SELECTION);
                frame.fillRect(PAD, rowY, frame.width() - PAD * 2, row);
            }
            drawMarker(frame, PAD + 4, rowY + (row - 10) / 2, type, choices.selected(i), here);
            frame.setColor(here ? SELECTION_TEXT : TEXT);
            font.draw(frame, clip(font, choices.string(i), frame.width() - PAD * 2 - 24),
                    PAD + 22, rowY + (row - font.height()) / 2);
        }
    }

    // --------------------------------------------------------------- TextBox

    private static void drawTextBox(Vm vm, Framebuffer frame, VmObject box, int height) {
        BitmapFont font = plain();
        String text = displayText(box);
        int width = frame.width() - PAD * 2;
        frame.setColor(FIELD);
        frame.fillRect(PAD, PAD, width, height - PAD * 2);
        frame.setColor(RULE);
        frame.drawRect(PAD, PAD, width, height - PAD * 2);

        List<String> lines = wrap(font, text, width - 8);
        frame.setColor(TEXT);
        int y = PAD + 4;
        for (int i = 0; i < lines.size() && y + font.height() < height - PAD; i++) {
            font.draw(frame, lines.get(i), PAD + 4, y);
            y += font.height() + 2;
        }
        // Caret at the end of the last line, which is where the next key lands.
        String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
        int caretX = PAD + 4 + font.stringWidth(last);
        int caretY = y - font.height() - 2;
        if (lines.isEmpty()) {
            caretX = PAD + 4;
            caretY = PAD + 4;
        }
        frame.setColor(ACCENT);
        frame.fillRect(Math.min(caretX + 1, frame.width() - PAD - 2), caretY, 1, font.height());

        int max = MidpForms.intField(box, "maxSize");
        String counter = MidpForms.textOf(box).length() + "/" + max;
        frame.setColor(TEXT_DIM);
        font.draw(frame, counter, frame.width() - PAD - 4 - font.stringWidth(counter),
                height - PAD - font.height() - 2);
    }

    // ----------------------------------------------------------------- Alert

    private static final String[] ALERT_NAMES = {
            "Thông tin", "Cảnh báo", "Lỗi", "Báo thức", "Xác nhận",
    };
    private static final int[] ALERT_COLORS = {
            0xFF6FA8DC, 0xFFE0B341, 0xFFD9534F, 0xFFB07BD9, 0xFF5CB85C,
    };

    private static void drawAlert(Vm vm, Framebuffer frame, VmObject alert, int height) {
        BitmapFont font = plain();
        int kind = MidpForms.alertKind((VmObject) alert.get("alertType"));
        int accent = ALERT_COLORS[Math.max(0, Math.min(kind, ALERT_COLORS.length - 1))];

        frame.setColor(accent);
        frame.fillRect(0, 0, frame.width(), 3);
        int y = 8;
        frame.setColor(accent);
        bold().draw(frame, ALERT_NAMES[Math.max(0, Math.min(kind, ALERT_NAMES.length - 1))], PAD, y);
        y += font.height() + 6;

        VmObject image = (VmObject) alert.get("image");
        if (image != null && image.host instanceof Framebuffer) {
            Framebuffer picture = (Framebuffer) image.host;
            frame.drawFramebuffer(picture, (frame.width() - picture.width()) / 2, y);
            y += picture.height() + 6;
        }

        Object message = alert.get("string");
        if (message != null) {
            frame.setColor(TEXT);
            for (String line : wrap(font, vm.stringOf(message), frame.width() - PAD * 2)) {
                if (y + font.height() > height - 4) {
                    break;
                }
                font.draw(frame, line, (frame.width() - font.stringWidth(line)) / 2, y);
                y += font.height() + 2;
            }
        }

        VmObject indicator = (VmObject) alert.get("indicator");
        if (indicator != null) {
            drawGauge(frame, height - 20, indicator);
        }
    }

    // ------------------------------------------------------------ menu popup

    /**
     * The commands that did not fit on the two softkeys, shown as the list a
     * handset showed behind its "Options" key.
     */
    private static void drawMenu(MidpContext context, Framebuffer frame) {
        if (!context.isMenuOpen()) {
            return;
        }
        List<VmObject> commands = context.menuCommands();
        if (commands.isEmpty()) {
            return;
        }
        BitmapFont font = plain();
        int row = rowHeight();
        int height = Math.min(commands.size() * row + 8, frame.height() - 40);
        int visible = Math.max(1, (height - 8) / row);
        int width = frame.width() - 24;
        int x = 12;
        int y = frame.height() - SystemChrome.softKeyBarHeight() - height - 4;

        frame.setTranslation(0, 0);
        frame.resetClip();
        frame.setColor(0xFF0B0F14);
        frame.fillRect(x, y, width, height);
        frame.setColor(ACCENT);
        frame.drawRect(x, y, width, height);

        int focus = Math.max(0, Math.min(context.menuIndex(), commands.size() - 1));
        int first = Math.max(0, Math.min(focus - visible + 1, commands.size() - visible));
        for (int i = 0; i < visible && first + i < commands.size(); i++) {
            int index = first + i;
            int rowY = y + 4 + i * row;
            boolean here = index == focus;
            if (here) {
                frame.setColor(SELECTION);
                frame.fillRect(x + 2, rowY, width - 4, row);
            }
            frame.setColor(here ? SELECTION_TEXT : TEXT);
            String label = context.labelOf(commands.get(index));
            font.draw(frame, clip(font, label == null ? "" : label, width - 16),
                    x + 8, rowY + (row - font.height()) / 2);
        }
    }

    // ----------------------------------------------------------------- text

    private static void drawEmpty(Framebuffer frame, int height, String message) {
        BitmapFont font = plain();
        frame.setColor(TEXT_DIM);
        font.draw(frame, message, (frame.width() - font.stringWidth(message)) / 2,
                (height - font.height()) / 2);
    }

    /** Breaks a message at spaces, and mid-word when a word cannot fit. */
    static List<String> wrap(BitmapFont font, String text, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.length() == 0) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            line.append(c);
            if (font.stringWidth(line.toString()) <= maxWidth) {
                continue;
            }
            int space = line.lastIndexOf(" ");
            if (space > 0) {
                lines.add(line.substring(0, space));
                line.delete(0, space + 1);
            } else {
                lines.add(line.substring(0, line.length() - 1));
                line.delete(0, line.length() - 1);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    private static String clip(BitmapFont font, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.stringWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.stringWidth(out.toString() + text.charAt(i) + "…") > maxWidth) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out.append("…").toString();
    }
}
