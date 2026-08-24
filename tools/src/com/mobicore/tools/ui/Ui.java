package com.mobicore.tools.ui;

import com.mobicore.core.gfx.BitmapFont;
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

    private final BitmapFont small = BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_PLAIN);
    private final BitmapFont smallBold = BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_BOLD);
    private final BitmapFont medium = BitmapFont.of(BitmapFont.SIZE_MEDIUM, BitmapFont.STYLE_PLAIN);
    private final BitmapFont mediumBold = BitmapFont.of(BitmapFont.SIZE_MEDIUM, BitmapFont.STYLE_BOLD);
    private final BitmapFont large = BitmapFont.of(BitmapFont.SIZE_LARGE, BitmapFont.STYLE_PLAIN);
    private final BitmapFont largeBold = BitmapFont.of(BitmapFont.SIZE_LARGE, BitmapFont.STYLE_BOLD);
    private final BitmapFont title = BitmapFont.of(BitmapFont.SIZE_TITLE, BitmapFont.STYLE_BOLD);

    public Ui(Framebuffer frame) {
        this.frame = frame;
    }

    public Framebuffer frame() {
        return frame;
    }

    public BitmapFont small() {
        return small;
    }

    public BitmapFont smallBold() {
        return smallBold;
    }

    public BitmapFont medium() {
        return medium;
    }

    public BitmapFont mediumBold() {
        return mediumBold;
    }

    public BitmapFont large() {
        return large;
    }

    public BitmapFont largeBold() {
        return largeBold;
    }

    public BitmapFont title() {
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

    public int text(BitmapFont font, String value, int x, int y, int color) {
        frame.setColor(color);
        return font.draw(frame, value, x, y);
    }

    public void textRight(BitmapFont font, String value, int right, int y, int color) {
        text(font, value, right - font.stringWidth(value), y, color);
    }

    public void textCenter(BitmapFont font, String value, int centerX, int y, int color) {
        text(font, value, centerX - font.stringWidth(value) / 2, y, color);
    }

    /** Truncates with an ellipsis so long game titles never overflow a card. */
    public String ellipsize(BitmapFont font, String value, int maxWidth) {
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
            tabGlyph(i, centre, top + 16, color);
            textCenter(small, labels[i], centre, top + 42, color);
            if (active) {
                bar(slot * i + slot / 4, top + 1, slot / 2, 3, Theme.ACCENT);
            }
        }
    }

    /** Simple drawn icons, so the preview needs no image assets. */
    private void tabGlyph(int index, int cx, int cy, int color) {
        frame.setColor(color);
        switch (index) {
            case 0:
                frame.fillTriangle(cx, cy - 2, cx - 11, cy + 7, cx + 11, cy + 7);
                frame.fillRect(cx - 7, cy + 7, 14, 8);
                break;
            case 1:
                frame.fillRoundRect(cx - 12, cy, 24, 15, 8, 8);
                frame.setColor(Theme.SURFACE);
                frame.fillRect(cx - 8, cy + 6, 5, 2);
                frame.fillRect(cx + 5, cy + 6, 3, 3);
                break;
            case 2:
                frame.fillRect(cx - 10, cy + 9, 14, 4);
                frame.fillRoundRect(cx + 2, cy, 9, 9, 6, 6);
                break;
            default:
                frame.fillArc(cx - 10, cy - 1, 20, 20, 0, 360);
                frame.setColor(Theme.SURFACE);
                frame.fillArc(cx - 4, cy + 5, 8, 8, 0, 360);
                break;
        }
    }

    /** Key/value row used across the detail and inspector screens. */
    public void field(String label, String value, int x, int y, int width) {
        text(medium, label, x, y, Theme.TEXT_DIM);
        int labelWidth = medium.stringWidth(label) + 16;
        textRight(mediumBold, ellipsize(mediumBold, value, width - labelWidth), x + width, y,
                Theme.TEXT);
    }

    /** Filled action button; returns the height it occupied. */
    public int button(int x, int y, int w, String label, boolean primary) {
        int height = medium.height() + 24;
        panel(x, y, w, height, primary ? Theme.ACCENT_DIM : Theme.SURFACE_ALT,
                primary ? Theme.ACCENT : Theme.BORDER);
        textCenter(mediumBold, label, x + w / 2, y + 12, primary ? Theme.ACCENT : Theme.TEXT);
        return height;
    }
}
