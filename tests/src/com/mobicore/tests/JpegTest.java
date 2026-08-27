package com.mobicore.tests;

import com.mobicore.core.gfx.JpegReader;

import java.io.IOException;

/**
 * Đọc ảnh JPEG.
 *
 * <p>MIDP chỉ bắt buộc máy đọc được PNG, nên máy ảo này lâu nay chỉ đọc PNG.
 * Nhưng máy thật thì đọc thêm JPEG, và game biết thế: ảnh mở đầu, ảnh nền,
 * ảnh chân dung nhân vật — những thứ to và nhiều màu — hay được đóng gói bằng
 * JPEG cho nhẹ. Game gọi {@code Image.createImage} với một tệp như vậy thì
 * trước đây nhận về "Unsupported image format" rồi chết.</p>
 *
 * <p>Ảnh dùng ở đây là ảnh JPEG thật (xem {@link JpegSamples}), và cái được
 * kiểm là <b>màu đọc ra có đúng không</b> — chứ không phải "có chạy mà không
 * nổ không": một bộ đọc sai vẫn chạy trơn tru và trả về một tấm nhiễu.</p>
 */
public final class JpegTest extends Test {

    @Override
    public String name() {
        return "Đọc ảnh JPEG";
    }

    @Override
    public void run() throws Exception {
        fullColour();
        subsampled();
        grey();
        refusals();
    }

    // ------------------------------------------------- lấy mẫu màu đầy đủ

    /**
     * Ảnh gốc là một dải màu: đỏ tăng dần sang phải, xanh lá tăng dần xuống
     * dưới, xanh dương giữ nguyên. Bốn góc vì thế là bốn màu biết trước.
     */
    private void fullColour() throws IOException {
        JpegReader.Image image = JpegReader.decode(JpegSamples.fullColour());
        eq(48, image.width, "đọc đúng chiều ngang");
        eq(32, image.height, "và chiều dọc");
        eq(48 * 32, image.pixels.length, "đủ số điểm ảnh");

        // JPEG là nén có mất mát, nên không đòi đúng từng số. Sai mười mức
        // trên hai trăm năm mươi sáu là còn đúng màu; sai năm chục thì không.
        near(0x00, red(image, 0, 0), 10, "góc trái trên gần như không có đỏ");
        near(0xFF, red(image, 47, 0), 12, "góc phải trên đỏ hết cỡ");
        near(0x00, green(image, 0, 0), 10, "góc trái trên chưa có xanh lá");
        near(0xFF, green(image, 0, 31), 12, "góc trái dưới xanh lá hết cỡ");
        near(0x40, blue(image, 24, 16), 12, "màu xanh dương giữ nguyên khắp ảnh");

        // Dải màu phải tăng đều: đọc sai thứ tự khối thì chỗ này đảo lộn ngay,
        // dù từng màu vẫn nằm trong khoảng cho phép.
        int previous = -1;
        for (int x = 0; x < 48; x += 4) {
            int value = red(image, x, 16);
            check(value >= previous - 6, "đỏ tăng dần sang phải, tại x=" + x
                    + " (" + previous + " rồi " + value + ")");
            previous = value;
        }
    }

    // -------------------------------------------------- lấy mẫu màu thưa

    /**
     * Kiểu hay gặp nhất: phần màu lưu thưa gấp đôi phần sáng theo cả hai
     * chiều, vì mắt người nhạy với sáng tối hơn nhiều so với màu. Đọc kiểu
     * này mà quên giãn phần màu ra thì ảnh ra đúng một phần tư, và ba phần
     * còn lại xám ngoét.
     */
    private void subsampled() throws IOException {
        JpegReader.Image image = JpegReader.decode(JpegSamples.subsampled());
        eq(96, image.width, "đọc đúng chiều ngang");
        eq(64, image.height, "và chiều dọc");

        near(0xFF, red(image, 95, 0), 14, "góc phải trên vẫn đỏ hết cỡ");
        near(0xFF, green(image, 0, 63), 14, "góc trái dưới vẫn xanh lá hết cỡ");
        near(0x40, blue(image, 48, 32), 14, "và giữa ảnh vẫn đúng màu nền");

        // Phần màu phải phủ hết ảnh: nếu quên giãn, ba phần tư bên phải và
        // bên dưới sẽ mất màu.
        near(0xFF, red(image, 95, 63), 16, "màu phủ tới tận góc xa nhất");
    }

    // -------------------------------------------------------- ảnh một màu

    private void grey() throws IOException {
        JpegReader.Image image = JpegReader.decode(JpegSamples.grey());
        eq(32, image.width, "ảnh xám cũng đọc được");
        near(0x00, red(image, 0, 0), 12, "một góc gần đen");
        near(0xFF, red(image, 31, 31), 12, "góc đối diện gần trắng");
        for (int i = 0; i < image.pixels.length; i += 37) {
            int pixel = image.pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            check(r == g && g == b, "ảnh xám thì ba kênh bằng nhau, không ám màu");
            eq(0xFF, (pixel >>> 24), "và đục hoàn toàn");
        }
    }

    // ------------------------------------------------------- từ chối đúng

    /**
     * Từ chối thẳng còn hơn giải ra một mớ nhiễu.
     *
     * <p>Ảnh progressive giải mã theo cách khác hẳn. Đọc nó bằng cách của ảnh
     * thường thì vẫn ra một tấm ảnh — một tấm nhiễu — và game sẽ vẽ tấm nhiễu
     * ấy lên màn hình mà không ai hiểu vì sao.</p>
     */
    private void refusals() {
        try {
            JpegReader.decode(JpegSamples.progressive());
            check(false, "ảnh progressive phải bị từ chối");
        } catch (IOException expected) {
            check(expected.getMessage().indexOf("progressive") >= 0,
                    "và nói rõ vì sao: " + expected.getMessage());
        }

        check(!JpegReader.looksLikeJpeg(new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
                "một tệp PNG không bị nhầm là JPEG");
        check(!JpegReader.looksLikeJpeg(new byte[0]), "tệp rỗng cũng vậy");
        check(!JpegReader.looksLikeJpeg(null), "và không có tệp nào thì không nổ");
        check(JpegReader.looksLikeJpeg(JpegSamples.grey()), "còn JPEG thì nhận ra");

        try {
            JpegReader.decode(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
            check(false, "một tệp JPEG rỗng ruột phải bị từ chối");
        } catch (IOException expected) {
            check(expected.getMessage().length() > 0, "kèm lý do");
        }
    }

    // ------------------------------------------------------------ tiện ích

    private int red(JpegReader.Image image, int x, int y) {
        return (image.pixels[y * image.width + x] >> 16) & 0xFF;
    }

    private int green(JpegReader.Image image, int x, int y) {
        return (image.pixels[y * image.width + x] >> 8) & 0xFF;
    }

    private int blue(JpegReader.Image image, int x, int y) {
        return image.pixels[y * image.width + x] & 0xFF;
    }

    private void near(int expected, int actual, int tolerance, String what) {
        check(Math.abs(expected - actual) <= tolerance,
                what + " (mong " + expected + ", đọc ra " + actual + ")");
    }
}
