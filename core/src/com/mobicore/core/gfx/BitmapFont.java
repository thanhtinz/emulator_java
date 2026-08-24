package com.mobicore.core.gfx;

/**
 * Renderer for the three MIDP faces baked into {@link FontData}.
 *
 * <p>These are the fonts an emulated game sees, so they are sized like the ones
 * a real handset shipped — around 14, 17 and 20 pixels tall on a QVGA screen —
 * and are one bit per pixel. A MIDlet lays its whole screen out around
 * {@code Font.getHeight()}, and an oversized font pushes its score display and
 * menus out of shape.</p>
 *
 * <p>The character set is not a contiguous range — Vietnamese takes letters
 * from Latin-1, Latin Extended-A and Latin Extended Additional — so a lookup
 * table maps a character to its glyph index in one array read.</p>
 */
public final class BitmapFont {

    public static final int SIZE_SMALL = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_LARGE = 2;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    private static final int FACE_COUNT = 3;
    private static final BitmapFont[] PLAIN_FACES = new BitmapFont[FACE_COUNT];

    /** Character to glyph index; -1 where the face has no glyph. */
    private static final int[] GLYPH_INDEX = buildIndex();
    private static final int FALLBACK_GLYPH = indexOf('?');

    private final int size;
    private final int style;
    private final int height;
    private final int ascent;
    private final int wordsPerRow;
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
        switch (size) {
            case SIZE_SMALL:
                height = FontData.SMALL_HEIGHT;
                ascent = FontData.SMALL_ASCENT;
                wordsPerRow = FontData.SMALL_WORDS;
                advances = FontData.SMALL_ADVANCE;
                tops = FontData.SMALL_TOP;
                rowCounts = FontData.SMALL_ROWS;
                bits = FontData.SMALL_BITS;
                break;
            case SIZE_LARGE:
                height = FontData.LARGE_HEIGHT;
                ascent = FontData.LARGE_ASCENT;
                wordsPerRow = FontData.LARGE_WORDS;
                advances = FontData.LARGE_ADVANCE;
                tops = FontData.LARGE_TOP;
                rowCounts = FontData.LARGE_ROWS;
                bits = FontData.LARGE_BITS;
                break;
            default:
                height = FontData.MEDIUM_HEIGHT;
                ascent = FontData.MEDIUM_ASCENT;
                wordsPerRow = FontData.MEDIUM_WORDS;
                advances = FontData.MEDIUM_ADVANCE;
                tops = FontData.MEDIUM_TOP;
                rowCounts = FontData.MEDIUM_ROWS;
                bits = FontData.MEDIUM_BITS;
                break;
        }

        // Decoding once here keeps the inner draw loop to array reads; parsing
        // hex per pixel row would dominate the cost of every frame.
        int glyphs = advances.length;
        rowStart = new int[glyphs + 1];
        int totalRows = 0;
        for (int i = 0; i < glyphs; i++) {
            rowStart[i] = totalRows;
            totalRows += rowCounts[i] & 0xFF;
        }
        rowStart[glyphs] = totalRows;
        rowData = new int[totalRows * wordsPerRow];
        for (int i = 0; i < rowData.length; i++) {
            int offset = i * 8;
            rowData[i] = (int) Long.parseLong(bits.substring(offset, offset + 8), 16);
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

    /** Styles are a bit set — plain, bold, italic, underlined — so eight. */
    private static final int STYLE_COUNT = 8;

    private static final BitmapFont[] STYLED_FACES = new BitmapFont[FACE_COUNT * STYLE_COUNT];

    public static BitmapFont of(int size, int style) {
        int clamped = size < 0 ? 0 : (size >= FACE_COUNT ? FACE_COUNT - 1 : size);
        if (style == STYLE_PLAIN) {
            if (PLAIN_FACES[clamped] == null) {
                PLAIN_FACES[clamped] = new BitmapFont(clamped, STYLE_PLAIN);
            }
            return PLAIN_FACES[clamped];
        }
        // Bold and italic were rebuilt on every call, which meant decoding a
        // face's rows again for every string a game drew.
        int slot = clamped * STYLE_COUNT + (style & (STYLE_COUNT - 1));
        if (STYLED_FACES[slot] == null) {
            STYLED_FACES[slot] = new BitmapFont(clamped, style);
        }
        return STYLED_FACES[slot];
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
        int start = rowStart[glyph] * wordsPerRow;
        int shearDivisor = Math.max(1, height / 3);
        for (int row = 0; row < count; row++) {
            int line = top + row;
            int shear = isItalic() ? (ascent - line) / shearDivisor : 0;
            for (int word = 0; word < wordsPerRow; word++) {
                int mask = rowData[start + row * wordsPerRow + word];
                if (mask == 0) {
                    continue;
                }
                int base = word * 32;
                for (int bit = 0; bit < 32; bit++) {
                    if ((mask & (1 << (31 - bit))) == 0) {
                        continue;
                    }
                    frame.setPixel(x + base + bit + shear, y + line);
                    if (isBold()) {
                        frame.setPixel(x + base + bit + shear + 1, y + line);
                    }
                }
            }
        }
        return charWidth(c);
    }
}
