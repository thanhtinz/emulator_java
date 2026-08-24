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
    private final BitmapFont small = BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_PLAIN);
    private final BitmapFont smallBold = BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_BOLD);
    private final BitmapFont medium = BitmapFont.of(BitmapFont.SIZE_MEDIUM, BitmapFont.STYLE_PLAIN);
    private final BitmapFont mediumBold = BitmapFont.of(BitmapFont.SIZE_MEDIUM, BitmapFont.STYLE_BOLD);
    private final BitmapFont largeBold = BitmapFont.of(BitmapFont.SIZE_LARGE, BitmapFont.STYLE_BOLD);

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

    public BitmapFont largeBold() {
        return largeBold;
    }

    public void background(int color) {
        frame.fill(color);
    }

    public void panel(int x, int y, int w, int h, int fill, int border) {
        frame.setColor(fill);
        frame.fillRoundRect(x, y, w, h, 12, 12);
        frame.setColor(border);
        frame.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
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

    /** Rounded label used for tags such as "MIDP-2.0" or "Favourite". */
    public int chip(String label, int x, int y, int textColor, int fillColor) {
        int padding = 6;
        int width = small.stringWidth(label) + padding * 2;
        int height = small.height() + 2;
        frame.setColor(fillColor);
        frame.fillRoundRect(x, y, width, height, height, height);
        text(small, label, x + padding, y + 1, textColor);
        return width;
    }

    /** Application title bar. */
    public void appBar(String title, String subtitle) {
        bar(0, 0, frame.width(), 46, Theme.SURFACE);
        bar(0, 46, frame.width(), 1, Theme.BORDER);
        text(largeBold, title, 16, 8, Theme.TEXT);
        if (subtitle != null) {
            textRight(small, subtitle, frame.width() - 16, 17, Theme.TEXT_DIM);
        }
    }

    /** Key/value row used across the detail and inspector screens. */
    public void field(String label, String value, int x, int y, int width) {
        text(small, label, x, y, Theme.TEXT_DIM);
        textRight(smallBold, ellipsize(smallBold, value, width - 120), x + width, y, Theme.TEXT);
    }
}
