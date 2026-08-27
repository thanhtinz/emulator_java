package com.mobicore.core.midp;

import com.mobicore.core.gfx.BitmapFont;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.vm.VmObject;

import java.util.List;

/**
 * The strips a handset draws around a MIDlet's canvas: the screen title along
 * the top and the softkey labels along the bottom.
 *
 * <p>This is not decoration. MIDP gives a game no way to show its own
 * {@code Command}s — the device is required to. Without this bar the Pause and
 * Exit a MIDlet registers are unreachable, which is how the emulator behaved
 * before: the commands were parsed, stored, and never shown to anybody.</p>
 *
 * <p>Drawn with the MIDP fonts rather than the emulator's own, because that is
 * what a handset used and it keeps the proportions right at 128x128 as well as
 * at 240x320.</p>
 */
public final class SystemChrome {

    /** Colours follow the muted grey-blue of a Series 40 style handset. */
    private static final int DARK_BAR = 0xFF2A3442;
    private static final int DARK_BAR_EDGE = 0xFF141A22;
    private static final int DARK_LABEL = 0xFFF2F5F8;
    private static final int DARK_TITLE = 0xFFDCE6F0;

    /**
     * The same bars for a light interface.
     *
     * <p>Handsets came both ways, so neither is less authentic; what would
     * look wrong is a dark strip stapled to the top of a light app.</p>
     */
    private static final int LIGHT_BAR = 0xFFE3E8EF;
    private static final int LIGHT_BAR_EDGE = 0xFFB9C3CF;
    private static final int LIGHT_LABEL = 0xFF16202B;
    private static final int LIGHT_TITLE = 0xFF33414F;

    private static boolean dark = true;

    /** Follows the app's own theme; see {@code Theme} in the tools module. */
    public static void setDark(boolean value) {
        dark = value;
    }

    public static boolean isDark() {
        return dark;
    }

    private static int bar() {
        return dark ? DARK_BAR : LIGHT_BAR;
    }

    private static int barEdge() {
        return dark ? DARK_BAR_EDGE : LIGHT_BAR_EDGE;
    }

    private static int label() {
        return dark ? DARK_LABEL : LIGHT_LABEL;
    }

    private static int title() {
        return dark ? DARK_TITLE : LIGHT_TITLE;
    }

    private SystemChrome() {
    }

    private static BitmapFont font() {
        return BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_PLAIN);
    }

    private static BitmapFont labelFont() {
        return BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_BOLD);
    }

    /** Height of the title strip for the current font. */
    public static int titleBarHeight() {
        return font().height() + 5;
    }

    /** Height of the softkey strip for the current font. */
    public static int softKeyBarHeight() {
        return labelFont().height() + 5;
    }

    /** Nothing was hit; the tap belongs to the game. */
    public static final int HIT_NONE = 0;
    public static final int HIT_LEFT = 1;
    public static final int HIT_RIGHT = 2;

    /**
     * Which softkey a tap landed on, if any.
     *
     * <p>A touchscreen handset ran these games with the command bar itself as
     * the button: the labels are drawn along the bottom of the screen, and
     * tapping a label runs its command. Two more buttons underneath, saying
     * the same two words, are two ways to do one thing.</p>
     */
    public static int softKeyHit(MidpContext context, int x, int y) {
        if (context.isFullScreen() || !context.hasSoftKeys()) {
            return HIT_NONE;
        }
        Framebuffer screen = context.screen();
        if (y < screen.height() - softKeyBarHeight() || y >= screen.height()) {
            return HIT_NONE;
        }
        if (x < screen.width() / 2) {
            return leftLabel(context) == null ? HIT_NONE : HIT_LEFT;
        }
        return rightLabel(context) == null ? HIT_NONE : HIT_RIGHT;
    }

    /** Applies the measured heights to a context. */
    public static void measure(MidpContext context) {
        context.setChromeHeights(titleBarHeight(), softKeyBarHeight());
    }

    /** Draws both strips over whatever the game has already painted. */
    public static void draw(MidpContext context) {
        Framebuffer screen = context.screen();
        screen.setTranslation(0, 0);
        screen.resetClip();
        if (context.isFullScreen()) {
            return;
        }
        if (context.hasTitle()) {
            drawTitle(context, screen);
        }
        if (context.hasSoftKeys()) {
            drawSoftKeys(context, screen);
        }
    }

    private static void drawTitle(MidpContext context, Framebuffer screen) {
        int height = titleBarHeight();
        screen.setColor(bar());
        screen.fillRect(0, 0, screen.width(), height);
        screen.setColor(barEdge());
        screen.fillRect(0, height - 1, screen.width(), 1);
        BitmapFont font = font();
        screen.setColor(title());
        font.draw(screen, clip(font, context.title(), screen.width() - 8), 4, 2);
    }

    private static void drawSoftKeys(MidpContext context, Framebuffer screen) {
        int height = softKeyBarHeight();
        int top = screen.height() - height;
        screen.setColor(bar());
        screen.fillRect(0, top, screen.width(), height);
        screen.setColor(barEdge());
        screen.fillRect(0, top, screen.width(), 1);

        BitmapFont font = labelFont();
        int textY = top + 3;
        int half = screen.width() / 2 - 6;

        String left = leftLabel(context);
        if (left != null) {
            screen.setColor(label());
            font.draw(screen, clip(font, left, half), 4, textY);
        }
        String right = rightLabel(context);
        if (right != null) {
            String text = clip(font, right, half);
            screen.setColor(label());
            font.draw(screen, text, screen.width() - 4 - font.stringWidth(text), textY);
        }
    }

    /**
     * More than one command wants the left key, so it becomes a menu — which is
     * exactly what a handset does.
     */
    public static String leftLabel(MidpContext context) {
        if (context.isMenuOpen()) {
            return "Chọn";
        }
        VmObject command = context.leftCommand();
        if (command == null) {
            return null;
        }
        return context.menuCommands().isEmpty() ? context.labelOf(command) : "Tuỳ chọn";
    }

    public static String rightLabel(MidpContext context) {
        // While the menu is open both keys belong to the menu: the right one
        // backs out of it, whatever the screen's own right command is.
        if (context.isMenuOpen()) {
            return "Huỷ";
        }
        return context.labelOf(context.rightCommand());
    }

    /** Truncates a label that will not fit, as a narrow handset screen must. */
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
