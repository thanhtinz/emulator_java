package com.mobicore.tools;

import com.mobicore.core.gfx.BitmapFont;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.storage.LocalVfs;

/** Renders a sheet of Vietnamese text at every face, to check the font data. */
public final class FontSample {

    private static final String[] LINES = {
            "Thư viện trò chơi",
            "Cài đặt bộ giả lập",
            "Đã lưu 3 bản ghi · 12 B",
            "Bàn phím ảo · Nút mũi tên",
            "Ăn Ấy Ổn Ữợ Ỷ Ẵ Ộ Ị Ỹ Ứ",
            "aAbBcC 0123456789 ,.!?-",
    };

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/screenshots";
        Framebuffer frame = new Framebuffer(560, 470);
        frame.fill(0xFF0E1116);

        int y = 14;
        y = block(frame, BitmapFont.SIZE_TITLE, "TITLE", y);
        y = block(frame, BitmapFont.SIZE_LARGE, "LARGE", y);
        y = block(frame, BitmapFont.SIZE_MEDIUM, "MEDIUM", y);
        block(frame, BitmapFont.SIZE_SMALL, "SMALL", y);

        new LocalVfs().write(out + "/00-font.png", PngWriter.encode(frame));
        System.out.println("Font sample written to " + out + "/00-font.png");
    }

    private static int block(Framebuffer frame, int size, String label, int y) {
        BitmapFont plain = BitmapFont.of(size, BitmapFont.STYLE_PLAIN);
        BitmapFont bold = BitmapFont.of(size, BitmapFont.STYLE_BOLD);
        frame.setColor(0xFF8B98A8);
        BitmapFont.of(BitmapFont.SIZE_SMALL, BitmapFont.STYLE_BOLD)
                .draw(frame, label + "  " + plain.height() + "px", 12, y);
        y += 18;
        frame.setColor(0xFFE6EDF3);
        plain.draw(frame, LINES[0], 12, y);
        y += plain.height() + 2;
        frame.setColor(0xFF4CC2FF);
        bold.draw(frame, LINES[1], 12, y);
        y += plain.height() + 2;
        frame.setColor(0xFFE6EDF3);
        plain.draw(frame, size >= BitmapFont.SIZE_LARGE ? LINES[4] : LINES[2], 12, y);
        return y + plain.height() + 12;
    }
}
