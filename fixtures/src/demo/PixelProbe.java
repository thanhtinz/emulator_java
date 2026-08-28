package demo;

import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;

/** Vẽ từng hình vào một tấm nhỏ rồi trả về bản đồ điểm, để soi từng điểm ảnh. */
public final class PixelProbe extends MIDlet {

    protected void startApp() { }
    protected void pauseApp() { }
    protected void destroyApp(boolean u) { }

    private static final int W = 12, H = 12;

    private String render(int what) {
        Image img = Image.createImage(W, H);
        Graphics g = img.getGraphics();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, W, H);
        g.setColor(0x000000);
        switch (what) {
            case 0: g.drawRect(2, 2, 5, 4); break;
            case 1: g.fillRect(2, 2, 5, 4); break;
            case 2: g.drawLine(2, 2, 8, 2); break;
            case 3: g.drawLine(2, 2, 2, 8); break;
            case 4: g.fillRect(2, 2, 0, 4); break;
            case 5: g.drawRect(2, 2, 0, 4); break;
            case 6: g.fillArc(1, 1, 9, 9, 0, 90); break;
            case 7: g.fillArc(1, 1, 9, 9, 90, 90); break;
            case 8: g.drawString("|", 6, 6, Graphics.BASELINE | Graphics.HCENTER); break;
            case 9: g.fillTriangle(1, 1, 9, 1, 1, 9); break;
            case 10: g.setClip(3, 3, 4, 4); g.fillRect(0, 0, W, H); break;
            case 11: g.setClip(3, 3, 4, 4); g.clipRect(5, 5, 10, 10); g.fillRect(0, 0, W, H); break;
            case 12: g.translate(3, 3); g.fillRect(0, 0, 2, 2); break;
            case 13: g.fillRect(2, 2, 5, -4); break;
            case 14: g.drawRoundRect(2, 2, 6, 6, 4, 4); break;
            case 15: g.drawArc(1, 1, 9, 9, 0, 360); break;
            default: break;
        }
        return dump(img);
    }

    public String probe(int what) {
        return render(what);
    }

    /** Bốn hàm MIDP mà máy ảo từng thiếu hẳn. */
    public String latecomers() {
        Image img = Image.createImage(W, H);
        Graphics g = img.getGraphics();
        g.setColor(0x808080);
        String grey = "" + g.getGrayScale();
        g.setColor(0x102030);
        String mixed = "" + g.getGrayScale();
        g.setGrayScale(200);
        String set = "" + g.getGrayScale() + "," + Integer.toHexString(g.getColor());
        String shown = Integer.toHexString(g.getDisplayColor(0x445566));
        Font plain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        return grey + "|" + mixed + "|" + set + "|" + shown
                + "|" + plain.isPlain() + bold.isPlain();
    }

    /** Chép một mảnh của tấm vẽ sang chỗ khác, kể cả khi hai vùng chồng nhau. */
    public String copied(int toX, int toY) {
        return copied(toX, toY, 0);
    }

    /**
     * Cùng phép chép, nhưng sau khi đã dời gốc toạ độ.
     *
     * <p>Phép tịnh tiến là chỗ dễ cộng hai lần: một lần ở chỗ tính điểm đích,
     * một lần nữa ở chỗ vẽ. Chép với gốc toạ độ đã dời ra thì lỗi ấy lộ ngay.</p>
     */
    public String copied(int toX, int toY, int shift) {
        Image img = Image.createImage(W, H);
        Graphics g = img.getGraphics();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, W, H);
        g.translate(shift, shift);
        g.setColor(0x000000);
        g.fillRect(1, 1, 2, 1);
        g.copyArea(1, 1, 2, 1, toX, toY, Graphics.TOP | Graphics.LEFT);
        return dump(img);
    }

    /** Chép ra ngoài mép tấm vẽ thì phải kêu, không được lặng lẽ bỏ qua. */
    public String copyOutside() {
        Image img = Image.createImage(W, H);
        Graphics g = img.getGraphics();
        try {
            g.copyArea(0, 0, W + 4, 2, 0, 0, Graphics.TOP | Graphics.LEFT);
            return "không kêu";
        } catch (IllegalArgumentException expected) {
            return "kêu";
        }
    }

    /** Bốn ô 2x2 khác nhau, chép qua drawRegion với từng phép xoay lật. */
    public String region(int transform) {
        Image src = Image.createImage(4, 2);
        Graphics sg = src.getGraphics();
        sg.setColor(0xFFFFFF);
        sg.fillRect(0, 0, 4, 2);
        // A B C D ở hàng trên, chấm ở hàng dưới ô đầu.
        sg.setColor(0x000000);
        sg.fillRect(0, 0, 1, 1);
        sg.setColor(0x808080);
        sg.fillRect(1, 0, 1, 1);
        sg.setColor(0x404040);
        sg.fillRect(0, 1, 1, 1);
        Image out = Image.createImage(W, H);
        Graphics g = out.getGraphics();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, W, H);
        g.drawRegion(src, 0, 0, 4, 2, transform, 2, 2, Graphics.TOP | Graphics.LEFT);
        return dump(out);
    }

    private String dump(Image img) {
        int[] pixels = new int[W * H];
        img.getRGB(pixels, 0, W, 0, 0, W, H);
        StringBuffer out = new StringBuffer();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = pixels[y * W + x] & 0xFFFFFF;
                out.append(rgb == 0xFFFFFF ? '.' : (rgb == 0 ? '#' : (rgb == 0x808080 ? 'o' : 'x')));
            }
            out.append('\n');
        }
        return out.toString();
    }
}
