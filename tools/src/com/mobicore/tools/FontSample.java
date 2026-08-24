package com.mobicore.tools;

import com.mobicore.core.gfx.BitmapFont;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.LocalVfs;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.UiFont;

/**
 * Renders both fonts side by side.
 *
 * <p>The interface faces are anti-aliased; the MIDP faces are one bit per pixel
 * and sized as a handset sized them, because that is what a game expects.</p>
 */
public final class FontSample {

    private static final String LINE_A = "Thư viện trò chơi";
    private static final String LINE_B = "Cài đặt bộ giả lập";
    private static final String LINE_C = "Ăn Ấy Ổn Ữợ Ỷ Ẵ Ộ Ị Ỹ Ứ";

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/screenshots";
        Framebuffer frame = new Framebuffer(620, 560);
        frame.fill(Theme.BG);

        int y = 16;
        y = uiBlock(frame, UiFont.SIZE_TITLE, "GIAO DIỆN · TITLE", y);
        y = uiBlock(frame, UiFont.SIZE_LARGE, "GIAO DIỆN · LARGE", y);
        y = uiBlock(frame, UiFont.SIZE_BODY, "GIAO DIỆN · BODY", y);
        y = uiBlock(frame, UiFont.SIZE_SMALL, "GIAO DIỆN · SMALL", y);

        frame.setColor(Theme.BORDER);
        frame.fillRect(14, y + 2, frame.width() - 28, 1);
        y += 14;

        y = midpBlock(frame, BitmapFont.SIZE_LARGE, "MIDP · LARGE", y);
        y = midpBlock(frame, BitmapFont.SIZE_MEDIUM, "MIDP · MEDIUM", y);
        midpBlock(frame, BitmapFont.SIZE_SMALL, "MIDP · SMALL", y);

        new LocalVfs().write(out + "/00-font.png", PngWriter.encode(frame));
        System.out.println("Font sample written to " + out + "/00-font.png");
    }

    private static int uiBlock(Framebuffer frame, int size, String label, int y) {
        UiFont plain = UiFont.of(size, UiFont.STYLE_PLAIN);
        UiFont bold = UiFont.of(size, UiFont.STYLE_BOLD);
        UiFont caption = UiFont.of(UiFont.SIZE_SMALL, UiFont.STYLE_BOLD);
        caption.draw(frame, label + "  " + plain.height() + "px", 14, y, Theme.TEXT_DIM);
        y += caption.height() + 4;
        plain.draw(frame, LINE_A, 14, y, Theme.TEXT);
        bold.draw(frame, LINE_B, 20 + plain.stringWidth(LINE_A), y, Theme.ACCENT);
        return y + plain.height() + 12;
    }

    private static int midpBlock(Framebuffer frame, int size, String label, int y) {
        BitmapFont plain = BitmapFont.of(size, BitmapFont.STYLE_PLAIN);
        BitmapFont bold = BitmapFont.of(size, BitmapFont.STYLE_BOLD);
        UiFont caption = UiFont.of(UiFont.SIZE_SMALL, UiFont.STYLE_BOLD);
        caption.draw(frame, label + "  " + plain.height() + "px", 14, y, Theme.TEXT_DIM);
        y += caption.height() + 4;
        frame.setColor(Theme.TEXT);
        plain.draw(frame, LINE_A, 14, y);
        frame.setColor(Theme.ACCENT);
        bold.draw(frame, LINE_C, 20 + plain.stringWidth(LINE_A), y);
        return y + plain.height() + 12;
    }
}
