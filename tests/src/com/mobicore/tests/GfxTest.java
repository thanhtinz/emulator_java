package com.mobicore.tests;

import com.mobicore.core.gfx.BitmapFont;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.gfx.Transforms;

public final class GfxTest extends Test {

    @Override
    public String name() {
        return "Framebuffer, PNG and fonts";
    }

    @Override
    public void run() throws Exception {
        Framebuffer frame = new Framebuffer(40, 30);
        frame.fill(0xFF102030);
        eq(0xFF102030, frame.pixel(0, 0), "fill covers the surface");

        frame.setColor(0xFF00FF00);
        frame.fillRect(10, 10, 5, 5);
        eq(0xFF00FF00, frame.pixel(12, 12), "fillRect paints inside");
        eq(0xFF102030, frame.pixel(9, 12), "fillRect stops at its edge");

        frame.setClip(0, 0, 8, 8);
        frame.setColor(0xFFFF0000);
        frame.fillRect(0, 0, 40, 30);
        eq(0xFFFF0000, frame.pixel(7, 7), "clip lets the inside through");
        eq(0xFF00FF00, frame.pixel(12, 12), "clip blocks the outside");
        frame.resetClip();

        frame.translate(20, 0);
        frame.setColor(0xFF0000FF);
        frame.fillRect(0, 0, 4, 4);
        eq(0xFF0000FF, frame.pixel(21, 1), "translate offsets drawing");
        frame.setTranslation(0, 0);

        // Alpha blending: half-transparent white over black is mid grey.
        Framebuffer blend = new Framebuffer(4, 4);
        blend.fill(0xFF000000);
        blend.setColor(0x80FFFFFF);
        blend.fillRect(0, 0, 4, 4);
        int mid = blend.pixel(1, 1) & 0xFF;
        check(mid > 100 && mid < 155, "50% alpha lands near mid grey, was " + mid);

        Framebuffer line = new Framebuffer(10, 10);
        line.fill(0);
        line.setColor(0xFFFFFFFF);
        line.drawLine(0, 0, 9, 9);
        eq(0xFFFFFFFF, line.pixel(5, 5), "a diagonal line covers the diagonal");
        eq(0, line.pixel(0, 9), "a diagonal line misses the corner");

        Framebuffer scaled = frame.scaleNearest(80, 60);
        eq(80, scaled.width(), "nearest scale resizes");
        eq(frame.pixel(12, 12), scaled.pixel(24, 24), "nearest scale keeps exact pixels");
        eq(3, frame.integerScaleFor(130, 95), "integer scale picks the largest fit");
        eq(1, frame.integerScaleFor(10, 10), "integer scale never goes below one");

        // PNG must survive a full round trip, alpha included.
        Framebuffer source = new Framebuffer(17, 9);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 17; x++) {
                source.blend(x, y, ((x * 13 + y) << 24) | (x * 15 << 16) | (y * 28 << 8) | (x + y));
            }
        }
        byte[] png = PngWriter.encode(source);
        check(PngReader.looksLikePng(png), "the encoder writes a PNG signature");
        PngReader.Image decoded = PngReader.decode(png);
        eq(17, decoded.width, "decoded width matches");
        eq(9, decoded.height, "decoded height matches");
        boolean identical = true;
        for (int i = 0; i < decoded.pixels.length; i++) {
            if (decoded.pixels[i] != source.pixels()[i]) {
                identical = false;
                break;
            }
        }
        check(identical, "PNG round-trips every pixel exactly");

        int[] block = {1, 2, 3, 4, 5, 6};
        int[] mirrored = Transforms.apply(block, 3, 2, 0, 0, 3, 2, Transforms.MIRROR);
        eq(3, mirrored[0], "mirror flips the first row");
        eq(1, mirrored[2], "mirror moves the first pixel to the end");
        int[] rotated = Transforms.apply(block, 3, 2, 0, 0, 3, 2, Transforms.ROT90);
        eq(2, Transforms.resultWidth(Transforms.ROT90, 3, 2), "rot90 swaps the axes");
        eq(3, Transforms.resultHeight(Transforms.ROT90, 3, 2), "rot90 swaps the axes back");
        eq(6, rotated.length, "rot90 keeps the pixel count");
        eq(4, rotated[0], "rot90 moves the bottom-left pixel to the top-left");

        BitmapFont small = BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_PLAIN);
        BitmapFont bold = BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_BOLD);
        check(small.stringWidth("MobiCore") > 30, "text has a sensible width");
        check(bold.stringWidth("MobiCore") > small.stringWidth("MobiCore"), "bold is wider than plain");
        eq(0, small.stringWidth(""), "an empty string is zero wide");
        check(small.height() > small.ascent(), "the face has descenders");
        check(BitmapFont.of(BitmapFont.SIZE_LARGE, 0).height()
                > BitmapFont.of(BitmapFont.SIZE_SMALL, 0).height(), "large is taller than small");

        // Vietnamese has to render, not fall back to question marks: the whole
        // interface and any Vietnamese game text depends on it.
        String vietnamese = "Thư viện trò chơi — Cài đặt bộ giả lập";
        for (int i = 0; i < vietnamese.length(); i++) {
            char c = vietnamese.charAt(i);
            check(BitmapFont.supports(c), "the face has a glyph for '" + c + "'");
        }
        String stacked = "ỶẴỠỰẪỘỀỔỳẵữựậộềổĐđĂăƠơƯư";
        for (int i = 0; i < stacked.length(); i++) {
            check(BitmapFont.supports(stacked.charAt(i)),
                    "stacked tone marks are covered: '" + stacked.charAt(i) + "'");
        }
        check(!BitmapFont.supports('\u4E2D'), "characters outside the set are reported unsupported");
        eq(small.charWidth('?'), small.charWidth('\u4E2D'),
                "an unsupported character falls back to the question mark glyph");
        check(small.stringWidth("Thư viện") > small.stringWidth("Thu vien") - 4,
                "accented text is measured, not skipped");

        BitmapFont title = BitmapFont.of(BitmapFont.SIZE_TITLE, BitmapFont.STYLE_PLAIN);
        check(title.height() > BitmapFont.of(BitmapFont.SIZE_LARGE, 0).height(),
                "the title face is the tallest");
        check(title.ascent() < title.height(), "the title face leaves room for descenders");

        // A stacked mark must survive: Ẫ has to light more rows than A does.
        Framebuffer plain = new Framebuffer(60, title.height() + 4);
        Framebuffer marked = new Framebuffer(60, title.height() + 4);
        plain.fill(0xFF000000);
        marked.fill(0xFF000000);
        plain.setColor(0xFFFFFFFF);
        marked.setColor(0xFFFFFFFF);
        title.draw(plain, "A", 2, 2);
        title.draw(marked, "\u1EAA", 2, 2);
        check(topmostRow(marked) < topmostRow(plain),
                "the tone marks above a capital are not clipped");

        Framebuffer text = new Framebuffer(80, 20);
        text.fill(0xFF000000);
        text.setColor(0xFFFFFFFF);
        int advanced = small.draw(text, "Mobi", 2, 2);
        eq(small.stringWidth("Mobi"), advanced, "draw advances by the string width");
        int lit = 0;
        for (int i = 0; i < text.pixels().length; i++) {
            if (text.pixels()[i] == 0xFFFFFFFF) {
                lit++;
            }
        }
        check(lit > 20, "drawing text actually lights pixels, was " + lit);
    }

    /** Row index of the highest lit pixel, or the height when nothing is lit. */
    private static int topmostRow(Framebuffer frame) {
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                if (frame.pixel(x, y) == 0xFFFFFFFF) {
                    return y;
                }
            }
        }
        return frame.height();
    }
}
