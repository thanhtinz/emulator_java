package com.mobicore.tools.ui;

import com.mobicore.core.gfx.Framebuffer;

/**
 * Anti-aliased text for the interface previews.
 *
 * <p>Separate from the core's {@code BitmapFont}, which renders the three MIDP
 * faces a game sees. Those are one bit per pixel because that is what MIDP
 * fonts were; interface chrome has no such excuse, and hard-edged text is the
 * thing that makes a mock-up look like a screenshot of an emulator rather than
 * of an app.</p>
 *
 * <p>Coverage is two bits per pixel — four levels — which removes the jagged
 * edges without quadrupling the glyph data.</p>
 */
public final class UiFont {

    public static final int SIZE_SMALL = 0;
    public static final int SIZE_BODY = 1;
    public static final int SIZE_LARGE = 2;
    public static final int SIZE_TITLE = 3;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;

    private static final int FACE_COUNT = 4;
    private static final UiFont[] CACHE = new UiFont[FACE_COUNT * 2];

    private static final int[] GLYPH_INDEX = buildIndex();
    private static final int FALLBACK_GLYPH = indexOf('?');

    private final int style;
    private final int height;
    private final int ascent;
    private final int wordsPerRow;
    private final byte[] advances;
    private final byte[] tops;
    private final byte[] rowCounts;
    private final int[] rowData;
    private final int[] rowStart;

    private UiFont(int size, int style) {
        this.style = style;
        String bits;
        switch (size) {
            case SIZE_SMALL:
                height = UiFontData.SMALL_HEIGHT;
                ascent = UiFontData.SMALL_ASCENT;
                wordsPerRow = UiFontData.SMALL_WORDS;
                advances = UiFontData.SMALL_ADVANCE;
                tops = UiFontData.SMALL_TOP;
                rowCounts = UiFontData.SMALL_ROWS;
                bits = UiFontData.SMALL_BITS;
                break;
            case SIZE_LARGE:
                height = UiFontData.LARGE_HEIGHT;
                ascent = UiFontData.LARGE_ASCENT;
                wordsPerRow = UiFontData.LARGE_WORDS;
                advances = UiFontData.LARGE_ADVANCE;
                tops = UiFontData.LARGE_TOP;
                rowCounts = UiFontData.LARGE_ROWS;
                bits = UiFontData.LARGE_BITS;
                break;
            case SIZE_TITLE:
                height = UiFontData.TITLE_HEIGHT;
                ascent = UiFontData.TITLE_ASCENT;
                wordsPerRow = UiFontData.TITLE_WORDS;
                advances = UiFontData.TITLE_ADVANCE;
                tops = UiFontData.TITLE_TOP;
                rowCounts = UiFontData.TITLE_ROWS;
                bits = UiFontData.TITLE_BITS;
                break;
            default:
                height = UiFontData.BODY_HEIGHT;
                ascent = UiFontData.BODY_ASCENT;
                wordsPerRow = UiFontData.BODY_WORDS;
                advances = UiFontData.BODY_ADVANCE;
                tops = UiFontData.BODY_TOP;
                rowCounts = UiFontData.BODY_ROWS;
                bits = UiFontData.BODY_BITS;
                break;
        }

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
        String charset = UiFontData.CHARSET;
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
        return c >= GLYPH_INDEX.length ? -1 : GLYPH_INDEX[c];
    }

    public static UiFont of(int size, int style) {
        int face = size < 0 ? 0 : (size >= FACE_COUNT ? FACE_COUNT - 1 : size);
        int weight = style == STYLE_BOLD ? 1 : 0;
        int slot = face * 2 + weight;
        if (CACHE[slot] == null) {
            CACHE[slot] = new UiFont(face, weight == 1 ? STYLE_BOLD : STYLE_PLAIN);
        }
        return CACHE[slot];
    }

    public int height() {
        return height;
    }

    public int ascent() {
        return ascent;
    }

    public boolean isBold() {
        return style == STYLE_BOLD;
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

    /** Draws text in {@code argb}, blending each pixel by its coverage. */
    public int draw(Framebuffer frame, String text, int x, int y, int argb) {
        if (text == null) {
            return 0;
        }
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            cursor += drawChar(frame, text.charAt(i), cursor, y, argb);
        }
        return cursor - x;
    }

    private int drawChar(Framebuffer frame, char c, int x, int y, int argb) {
        int glyph = glyphFor(c);
        int top = tops[glyph] & 0xFF;
        int count = rowCounts[glyph] & 0xFF;
        int start = rowStart[glyph] * wordsPerRow;
        int baseAlpha = argb >>> 24;
        int rgb = argb & 0xFFFFFF;

        for (int row = 0; row < count; row++) {
            int line = top + row;
            for (int word = 0; word < wordsPerRow; word++) {
                int packed = rowData[start + row * wordsPerRow + word];
                if (packed == 0) {
                    continue;
                }
                int base = word * 16;
                for (int slot = 0; slot < 16; slot++) {
                    int level = (packed >>> (30 - slot * 2)) & 3;
                    if (level == 0) {
                        continue;
                    }
                    // Four coverage levels map onto 0, 85, 170 and 255.
                    int alpha = level * 85 * baseAlpha / 255;
                    frame.blendPixel(x + base + slot, y + line, (alpha << 24) | rgb);
                    if (isBold()) {
                        frame.blendPixel(x + base + slot + 1, y + line, (alpha << 24) | rgb);
                    }
                }
            }
        }
        return charWidth(c);
    }
}
