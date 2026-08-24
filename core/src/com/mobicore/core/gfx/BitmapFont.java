package com.mobicore.core.gfx;

/**
 * Renderer for the baked {@link FontData} faces.
 *
 * <p>Bold is synthesised by drawing each glyph twice, one pixel apart, and
 * italic by shearing the rows. That keeps the data small while still offering
 * the style combinations MIDP exposes.</p>
 */
public final class BitmapFont {

    public static final int SIZE_SMALL = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_LARGE = 2;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    private static final BitmapFont[] PLAIN_FACES = new BitmapFont[3];

    private final int size;
    private final int style;
    private final int height;
    private final int ascent;
    private final byte[] advances;
    private final String bits;

    private BitmapFont(int size, int style) {
        this.size = size;
        this.style = style;
        switch (size) {
            case SIZE_SMALL:
                height = FontData.SMALL_HEIGHT;
                ascent = FontData.SMALL_ASCENT;
                advances = FontData.SMALL_ADVANCE;
                bits = FontData.SMALL_BITS;
                break;
            case SIZE_LARGE:
                height = FontData.LARGE_HEIGHT;
                ascent = FontData.LARGE_ASCENT;
                advances = FontData.LARGE_ADVANCE;
                bits = FontData.LARGE_BITS;
                break;
            default:
                height = FontData.MEDIUM_HEIGHT;
                ascent = FontData.MEDIUM_ASCENT;
                advances = FontData.MEDIUM_ADVANCE;
                bits = FontData.MEDIUM_BITS;
                break;
        }
    }

    public static BitmapFont of(int size, int style) {
        int clamped = size < 0 ? 0 : (size > 2 ? 2 : size);
        if (style == STYLE_PLAIN) {
            if (PLAIN_FACES[clamped] == null) {
                PLAIN_FACES[clamped] = new BitmapFont(clamped, STYLE_PLAIN);
            }
            return PLAIN_FACES[clamped];
        }
        return new BitmapFont(clamped, style);
    }

    public int size() {
        return size;
    }

    public int style() {
        return style;
    }

    public int height() {
        return height;
    }

    public int ascent() {
        return ascent;
    }

    public int descent() {
        return height - ascent;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public int charWidth(char c) {
        int index = glyphIndex(c);
        int advance = advances[index] & 0xFF;
        return isBold() ? advance + 1 : advance;
    }

    public int stringWidth(String text) {
        if (text == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += charWidth(text.charAt(i));
        }
        return total;
    }

    private int glyphIndex(char c) {
        if (c < FontData.FIRST_CHAR || c > FontData.LAST_CHAR) {
            return '?' - FontData.FIRST_CHAR;
        }
        return c - FontData.FIRST_CHAR;
    }

    private int row(int glyph, int y) {
        int offset = (glyph * height + y) * 4;
        return Integer.parseInt(bits.substring(offset, offset + 4), 16);
    }

    /**
     * Draws {@code text} with its top-left corner at {@code (x, y)} using the
     * framebuffer's current colour.
     */
    public int draw(Framebuffer frame, String text, int x, int y) {
        if (text == null) {
            return 0;
        }
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            cursor += drawChar(frame, text.charAt(i), cursor, y);
        }
        if (isUnderlined() && cursor > x) {
            frame.drawLine(x, y + ascent + 1, cursor - 1, y + ascent + 1);
        }
        return cursor - x;
    }

    private int drawChar(Framebuffer frame, char c, int x, int y) {
        int glyph = glyphIndex(c);
        int shearDivisor = Math.max(1, height / 3);
        for (int row = 0; row < height; row++) {
            int mask = row(glyph, row);
            if (mask == 0) {
                continue;
            }
            int shear = isItalic() ? (ascent - row) / shearDivisor : 0;
            for (int column = 0; column < 16; column++) {
                if ((mask & (1 << (15 - column))) == 0) {
                    continue;
                }
                frame.setPixel(x + column + shear, y + row);
                if (isBold()) {
                    frame.setPixel(x + column + shear + 1, y + row);
                }
            }
        }
        return charWidth(c);
    }
}
