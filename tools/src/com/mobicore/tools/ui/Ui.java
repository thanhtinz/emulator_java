package com.mobicore.tools.ui;

import com.mobicore.core.gfx.Framebuffer;

/**
 * Tiny immediate-mode drawing kit used by the desktop preview screens.
 *
 * <p>It exists so the previews exercise the same {@link Framebuffer} the
 * emulator itself renders into: if a primitive is broken, the preview shows it
 * rather than hiding it behind a native toolkit.</p>
 */
public final class Ui {

    private final Framebuffer frame;
    /** Vertical space one key/value row occupies, including its gap. */
    public static final int ROW = 26;
    /** Inner padding of a panel. */
    public static final int PAD = 16;
    /** Height of the application bar. */
    public static final int APP_BAR = 62;

    private final UiFont small = UiFont.of(UiFont.SIZE_SMALL, UiFont.STYLE_PLAIN);
    private final UiFont smallBold = UiFont.of(UiFont.SIZE_SMALL, UiFont.STYLE_BOLD);
    private final UiFont medium = UiFont.of(UiFont.SIZE_BODY, UiFont.STYLE_PLAIN);
    private final UiFont mediumBold = UiFont.of(UiFont.SIZE_BODY, UiFont.STYLE_BOLD);
    private final UiFont large = UiFont.of(UiFont.SIZE_LARGE, UiFont.STYLE_PLAIN);
    private final UiFont largeBold = UiFont.of(UiFont.SIZE_LARGE, UiFont.STYLE_BOLD);
    private final UiFont title = UiFont.of(UiFont.SIZE_TITLE, UiFont.STYLE_BOLD);

    public Ui(Framebuffer frame) {
        this.frame = frame;
    }

    public Framebuffer frame() {
        return frame;
    }

    public UiFont small() {
        return small;
    }

    public UiFont smallBold() {
        return smallBold;
    }

    public UiFont medium() {
        return medium;
    }

    public UiFont mediumBold() {
        return mediumBold;
    }

    public UiFont large() {
        return large;
    }

    public UiFont largeBold() {
        return largeBold;
    }

    public UiFont title() {
        return title;
    }

    public void background(int color) {
        frame.fill(color);
    }

    public void panel(int x, int y, int w, int h, int fill, int border) {
        frame.setColor(fill);
        frame.fillRoundRect(x, y, w, h, 18, 18);
        frame.setColor(border);
        frame.drawRoundRect(x, y, w - 1, h - 1, 18, 18);
    }

    /** Panel with a section caption; returns the y of the first content row. */
    public int section(int x, int y, int w, int h, String caption, String trailing) {
        panel(x, y, w, h, Theme.SURFACE, Theme.BORDER);
        text(small, caption, x + PAD, y + 12, Theme.TEXT_DIM);
        if (trailing != null) {
            textRight(small, trailing, x + w - PAD, y + 12, Theme.ACCENT);
        }
        return y + 12 + small.height() + 8;
    }

    /** Height a section needs for a caption plus {@code rows} value rows. */
    public int sectionHeight(int rows) {
        return 12 + small.height() + 8 + rows * ROW + 6;
    }

    public void bar(int x, int y, int w, int h, int color) {
        frame.setColor(color);
        frame.fillRect(x, y, w, h);
    }

    public int text(UiFont font, String value, int x, int y, int color) {
        return font.draw(frame, value, x, y, color);
    }

    public void textRight(UiFont font, String value, int right, int y, int color) {
        text(font, value, right - font.stringWidth(value), y, color);
    }

    public void textCenter(UiFont font, String value, int centerX, int y, int color) {
        text(font, value, centerX - font.stringWidth(value) / 2, y, color);
    }

    /** Truncates with an ellipsis so long game titles never overflow a card. */
    public String ellipsize(UiFont font, String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        if (font.stringWidth(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int budget = maxWidth - font.stringWidth(suffix);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (font.stringWidth(out.toString() + value.charAt(i)) > budget) {
                break;
            }
            out.append(value.charAt(i));
        }
        return out.append(suffix).toString();
    }

    /**
     * A search field with what has been typed in it.
     *
     * <p>Drawn rather than described because a library of forty games is
     * found through this box, not by scrolling.</p>
     *
     * @param typed what the user has entered; empty shows the hint
     * @return the height it occupied
     */
    public int searchField(int x, int y, int width, String typed, String hint) {
        int height = medium.height() + 20;
        panel(x, y, width, height, Theme.SURFACE, Theme.BORDER);
        int glyph = medium.height() + 2;
        Icons.draw(frame, Icons.SEARCH, x + 12, y + (height - glyph) / 2, glyph, Theme.TEXT_DIM);
        boolean empty = typed == null || typed.length() == 0;
        text(medium, empty ? hint : typed, x + 12 + glyph + 10, y + 10,
                empty ? Theme.TEXT_DIM : Theme.TEXT);
        if (!empty) {
            // The caret: a field with text in it and no caret reads as a
            // label, not as something being typed into.
            int caretX = x + 12 + glyph + 10 + medium.stringWidth(typed) + 2;
            bar(caretX, y + 8, 2, height - 16, Theme.ACCENT);
        }
        return height;
    }

    /** Rounded label used for tags such as "MIDP-2.0" or "Yêu thích". */
    public int chip(String label, int x, int y, int textColor, int fillColor) {
        int padding = 9;
        int width = small.stringWidth(label) + padding * 2;
        int height = small.height() + 6;
        frame.setColor(fillColor);
        frame.fillRoundRect(x, y, width, height, height, height);
        text(small, label, x + padding, y + 3, textColor);
        return width;
    }

    public int chipHeight() {
        return small.height() + 6;
    }

    /** Application title bar. */
    public void appBar(String heading, String subtitle) {
        bar(0, 0, frame.width(), APP_BAR, Theme.SURFACE);
        bar(0, APP_BAR, frame.width(), 1, Theme.BORDER);
        text(title, heading, PAD, (APP_BAR - title.height()) / 2, Theme.TEXT);
        if (subtitle != null) {
            textRight(medium, subtitle, frame.width() - PAD,
                    (APP_BAR - medium.height()) / 2 + 1, Theme.TEXT_DIM);
        }
    }

    /** Diameter of the floating action button. */
    public static final int FAB = 56;

    /**
     * Round action button, floating clear of the content above the tab bar.
     *
     * <p>One action per screen earns this, and on the home screen that is
     * importing. It stays reachable while the list scrolls without taking a
     * band of the screen away from the games themselves.</p>
     */
    public void fab(String icon) {
        int size = FAB;
        int x = frame.width() - PAD - size;
        int y = frame.height() - TAB_BAR - PAD - size;
        frame.setColor(0x33000000);
        frame.fillArc(x - 1, y + 2, size + 2, size + 2, 0, 360);
        frame.setColor(Theme.ACCENT);
        frame.fillArc(x, y, size, size, 0, 360);
        Icons.drawCentred(frame, icon, x + size / 2, y + size / 2, 26, Theme.BG);
    }

    /** Height of the bottom navigation bar. */
    public static final int TAB_BAR = 74;

    /**
     * Bottom navigation, drawn as the four destinations the product defines.
     * {@code selected} indexes into {@code labels}.
     */
    public void tabBar(String[] labels, int selected) {
        int top = frame.height() - TAB_BAR;
        bar(0, top, frame.width(), TAB_BAR, Theme.SURFACE);
        bar(0, top, frame.width(), 1, Theme.BORDER);
        int slot = frame.width() / labels.length;
        for (int i = 0; i < labels.length; i++) {
            int centre = slot * i + slot / 2;
            boolean active = i == selected;
            int color = active ? Theme.ACCENT : Theme.TEXT_DIM;
            Icons.drawCentred(frame, TAB_ICONS[i], centre, top + 24, 26, color);
            textCenter(small, labels[i], centre, top + 42, color);
            if (active) {
                bar(slot * i + slot / 4, top + 1, slot / 2, 3, Theme.ACCENT);
            }
        }
    }

    /** The Material icon for each destination, in the order the bar shows. */
    private static final String[] TAB_ICONS = {
            Icons.HOME, Icons.LIBRARY, Icons.TOOLS, Icons.SETTINGS,
    };

    /** Key/value row used across the detail and inspector screens. */
    public void field(String label, String value, int x, int y, int width) {
        text(medium, label, x, y, Theme.TEXT_DIM);
        int labelWidth = medium.stringWidth(label) + 16;
        textRight(mediumBold, ellipsize(mediumBold, value, width - labelWidth), x + width, y,
                Theme.TEXT);
    }

    /** Filled action button; returns the height it occupied. */
    public int button(int x, int y, int w, String label, boolean primary) {
        return button(x, y, w, label, primary, null);
    }

    /**
     * Filled action button carrying a Material icon before its label. The
     * icon and the text are centred together, so the pair reads as one thing
     * rather than as a label with something stuck to the side.
     */
    public int button(int x, int y, int w, String label, boolean primary, String icon) {
        int height = medium.height() + 24;
        int color = primary ? Theme.ACCENT : Theme.TEXT;
        panel(x, y, w, height, primary ? Theme.ACCENT_DIM : Theme.SURFACE_ALT,
                primary ? Theme.ACCENT : Theme.BORDER);
        if (icon == null) {
            textCenter(mediumBold, label, x + w / 2, y + 12, color);
            return height;
        }
        int glyph = mediumBold.height() + 4;
        int span = glyph + 8 + mediumBold.stringWidth(label);
        int left = x + (w - span) / 2;
        Icons.draw(frame, icon, left, y + (height - glyph) / 2, glyph, color);
        text(mediumBold, label, left + glyph + 8, y + 12, color);
        return height;
    }

    /**
     * Rounded label with an icon in front of it, for a state a word alone
     * states weakly — a favourite, for one.
     */
    public int iconChip(String icon, String label, int x, int y, int textColor, int fillColor) {
        int padding = 9;
        int glyph = small.height() + 2;
        int width = glyph + 5 + small.stringWidth(label) + padding * 2;
        int height = small.height() + 6;
        frame.setColor(fillColor);
        frame.fillRoundRect(x, y, width, height, height, height);
        Icons.draw(frame, icon, x + padding, y + (height - glyph) / 2, glyph, textColor);
        text(small, label, x + padding + glyph + 5, y + 3, textColor);
        return width;
    }

    /** Width {@link #iconChip} would occupy, for laying a row out first. */
    public int iconChipWidth(String label) {
        return small.height() + 2 + 5 + small.stringWidth(label) + 18;
    }
}
