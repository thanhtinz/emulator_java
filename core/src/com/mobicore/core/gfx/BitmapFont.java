package com.mobicore.core.gfx;

/**
 * Renderer for the baked {@link FontData} faces.
 *
 * <p>The character set is not a contiguous range — Vietnamese takes letters
 * from Latin-1, Latin Extended-A and Latin Extended Additional — so a lookup
 * table maps a character to its glyph index in one array read rather than a
 * search.</p>
 *
 * <p>Bold is synthesised by drawing each glyph twice one pixel apart, and
 * italic by shearing the rows. That keeps the data to one face per size while
 * still offering the style combinations MIDP exposes.</p>
 */
public final class BitmapFont {

    public static final int SIZE_SMALL = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_LARGE = 2;
    /** Interface headings; not offered to emulated games. */
    public static final int SIZE_TITLE = 3;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    private static final int FACE_COUNT = 4;
    private static final BitmapFont[] PLAIN_FACES = new BitmapFont[FACE_COUNT];

    /** Character to glyph index; -1 where the face has no glyph. */
    private static final int[] GLYPH_INDEX = buildIndex();
    private static final int FALLBACK_GLYPH = indexOf('?');

    private final int size;
    private final int style;
    private final int height;
    private final int ascent;
    private final byte[] advances;
    private final byte[] tops;
    private final byte[] rowCounts;
    /** Decoded rows, laid end to end; {@link #rowStart} indexes into it. */
    private final int[] rowData;
    private final int[] rowStart;

    private BitmapFont(int size, int style) {
        this.size = size;
        this.style = style;
        String bits;
        int hexDigits;
        switch (size) {
            case SIZE_SMALL:
                height = FontData.SMALL_HEIGHT;
                ascent = FontData.SMALL_ASCENT;
                advances = FontData.SMALL_ADVANCE;
                tops = FontData.SMALL_TOP;
                rowCounts = FontData.SMALL_ROWS;
                bits = FontData.SMALL_BITS;
                hexDigits = FontData.SMALL_HEX;
                break;
            case SIZE_LARGE:
                height = FontData.LARGE_HEIGHT;
                ascent = FontData.LARGE_ASCENT;
                advances = FontData.LARGE_ADVANCE;
                tops = FontData.LARGE_TOP;
                rowCounts = FontData.LARGE_ROWS;
                bits = FontData.LARGE_BITS;
                hexDigits = FontData.LARGE_HEX;
                break;
            case SIZE_TITLE:
                height = FontData.TITLE_HEIGHT;
                ascent = FontData.TITLE_ASCENT;
                advances = FontData.TITLE_ADVANCE;
                tops = FontData.TITLE_TOP;
                rowCounts = FontData.TITLE_ROWS;
                bits = FontData.TITLE_BITS;
                hexDigits = FontData.TITLE_HEX;
                break;
            default:
                height = FontData.MEDIUM_HEIGHT;
                ascent = FontData.MEDIUM_ASCENT;
                advances = FontData.MEDIUM_ADVANCE;
                tops = FontData.MEDIUM_TOP;
                rowCounts = FontData.MEDIUM_ROWS;
                bits = FontData.MEDIUM_BITS;
                hexDigits = FontData.MEDIUM_HEX;
                break;
        }

        // Decoding once here keeps the inner draw loop to array reads; parsing
        // hex per pixel row would dominate the cost of every frame.
        int glyphs = advances.length;
        rowStart = new int[glyphs + 1];
        int total = 0;
        for (int i = 0; i < glyphs; i++) {
            rowStart[i] = total;
            total += rowCounts[i] & 0xFF;
        }
        rowStart[glyphs] = total;
        rowData = new int[total];
        int shift = hexDigits == 4 ? 16 : 0;
        for (int i = 0; i < total; i++) {
            int offset = i * hexDigits;
            long value = Long.parseLong(bits.substring(offset, offset + hexDigits), 16);
            rowData[i] = (int) (value << shift);
        }
    }

    private static int[] buildIndex() {
        String charset = FontData.CHARSET;
        int highest = 0;
        for (int i = 0; i < charset.length(); i++) {
            highest = Math.max(highest, charset.charAt(i));
        }
        int[] table = new int[highest + 1];
        for (int i = 0; i < table.length; i++) {
            table[i] = -1;
        }
        for (int i = 0; i < charset.length(); i++) {
            table[charset.charAt(i)] = i;
        }
        return table;
    }

    private static int indexOf(char c) {
        if (c >= GLYPH_INDEX.length) {
            return -1;
        }
        return GLYPH_INDEX[c];
    }

    public static BitmapFont of(int size, int style) {
        int clamped = size < 0 ? 0 : (size >= FACE_COUNT ? FACE_COUNT - 1 : size);
        if (style == STYLE_PLAIN) {
            if (PLAIN_FACES[clamped] == null) {
                PLAIN_FACES[clamped] = new BitmapFont(clamped, STYLE_PLAIN);
            }
            return PLAIN_FACES[clamped];
        }
        return new BitmapFont(clamped, style);
    }

    /** True when the faces can render a character rather than substituting. */
    public static boolean supports(char c) {
        return indexOf(c) >= 0;
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

    private int glyphFor(char c) {
        int index = indexOf(c);
        return index >= 0 ? index : FALLBACK_GLYPH;
    }

    public int charWidth(char c) {
        int advance = advances[glyphFor(c)] & 0xFF;
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

    /**
     * Draws {@code text} with its top-left corner at {@code (x, y)} using the
     * framebuffer's current colour.
     *
     * @return the width drawn
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
        int glyph = glyphFor(c);
        int top = tops[glyph] & 0xFF;
        int count = rowCounts[glyph] & 0xFF;
        int start = rowStart[glyph];
        int shearDivisor = Math.max(1, height / 3);
        for (int row = 0; row < count; row++) {
            int mask = rowData[start + row];
            if (mask == 0) {
                continue;
            }
            int line = top + row;
            int shear = isItalic() ? (ascent - line) / shearDivisor : 0;
            for (int column = 0; column < 32; column++) {
                if ((mask & (1 << (31 - column))) == 0) {
                    continue;
                }
                frame.setPixel(x + column + shear, y + line);
                if (isBold()) {
                    frame.setPixel(x + column + shear + 1, y + line);
                }
            }
        }
        return charWidth(c);
    }
}
