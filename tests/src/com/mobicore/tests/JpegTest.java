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
        smoothColour();
        oddSize();
        grey();
        refusals();
        brokenFiles();
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

    // ------------------------------------------------ giãn màu cho mượt

    /**
     * Phần màu giãn ra phải mượt, không thành mảng vuông.
     *
     * <p>Giãn bằng cách lặp lại điểm gần nhất thì rẻ, nhưng mỗi mẫu màu phủ
     * đúng 2×2 điểm ảnh, nên chỗ màu đổi gắt hiện thành ô vuông: viền áo
     * nhân vật, chữ màu trên nền màu. Ảnh mẫu là một dải màu tăng đều, nên
     * cách kiểm là nhìn <em>bước nhảy</em> giữa hai điểm kề nhau: giãn mượt
     * thì bước nào cũng gần bằng nhau, còn lặp lại thì bước nhảy so le —
     * không, rồi gấp đôi, rồi lại không.</p>
     */
    private void smoothColour() throws IOException {
        JpegReader.Image image = JpegReader.decode(JpegSamples.subsampled());
        double ripple = 0;
        int counted = 0;
        for (int y = 8; y < image.height - 8; y++) {
            int previous = 0;
            for (int x = 1; x < image.width; x++) {
                int step = red(image, x, y) - red(image, x - 1, y);
                if (x > 1) {
                    ripple += Math.abs(step - previous);
                    counted++;
                }
                previous = step;
            }
        }
        double average = ripple / counted;
        // Dải màu này tăng khoảng 2,7 mức mỗi điểm. Giãn mượt thì gợn quanh
        // 1,4; lặp lại điểm gần nhất thì bước nhảy so le 0 và 5,3, tức là
        // gợn quanh 5.
        check(average < 3.0, "màu giãn ra mượt, không thành ô vuông 2×2 (gợn "
                + Math.round(average * 100) / 100.0 + ")");
    }

    // ---------------------------------------------- kích thước lẻ so với khối

    /**
     * Ảnh 37×23: không chia hết cho khối 8×8, cũng không cho 16×16.
     *
     * <p>JPEG luôn mã hoá tròn khối, nên một ảnh lẻ được nhồi thêm cho đủ
     * rồi cắt lại khi vẽ. Quên cắt thì ảnh thừa ra một dải nhoè ở mép phải và
     * mép dưới; cắt sai thì lệch cả ảnh.</p>
     */
    private void oddSize() throws IOException {
        JpegReader.Image image = JpegReader.decode(JpegSamples.odd());
        eq(37, image.width, "ảnh lẻ vẫn đúng chiều ngang");
        eq(23, image.height, "và đúng chiều dọc");
        eq(37 * 23, image.pixels.length, "không thừa dải nhồi thêm");
        near(0xFF, red(image, 36, 0), 14, "cột cuối cùng vẫn là màu thật, không phải mép nhồi");
        near(0xFF, green(image, 0, 22), 14, "hàng cuối cùng cũng vậy");
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

    // ------------------------------------------------------- tệp hỏng

    /**
     * Tệp hỏng phải ra {@code IOException}, không phải một lỗi làm chết game.
     *
     * <p>Đây là chuyện xảy ra thật: tệp tải dở, thẻ nhớ lỗi, gói game bị cắt.
     * Game viết sẵn {@code try/catch (IOException)} quanh chỗ đọc ảnh và tự
     * xử lý được; còn một {@code ArrayIndexOutOfBoundsException} lọt ra thì
     * không ai bắt, và cả khung hình chết theo.</p>
     *
     * <p>Cắt ở mọi độ dài và lật byte ngẫu nhiên — nhưng bằng một bộ số ngẫu
     * nhiên cố định, để lần chạy nào cũng thử đúng những tệp hỏng ấy.</p>
     */
    private void brokenFiles() {
        byte[][] originals = {JpegSamples.fullColour(), JpegSamples.subsampled(),
                JpegSamples.grey(), JpegSamples.odd()};
        int escaped = 0;
        String first = "";
        int tried = 0;
        long seed = 12345;
        for (int i = 0; i < originals.length; i++) {
            byte[] base = originals[i];
            for (int length = 2; length < base.length; length += 11) {
                byte[] cut = new byte[length];
                System.arraycopy(base, 0, cut, 0, length);
                tried++;
                String escape = attempt(cut);
                if (escape != null) {
                    escaped++;
                    first = first.length() == 0 ? "cắt còn " + length + ": " + escape : first;
                }
            }
            for (int round = 0; round < 400; round++) {
                byte[] broken = new byte[base.length];
                System.arraycopy(base, 0, broken, 0, base.length);
                for (int flip = 0; flip < 3; flip++) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    int at = (int) ((seed >>> 33) % broken.length);
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    broken[at] = (byte) ((seed >>> 33) & 0xFF);
                }
                tried++;
                String escape = attempt(broken);
                if (escape != null) {
                    escaped++;
                    first = first.length() == 0 ? escape : first;
                }
            }
        }
        check(tried > 1500, "thử đủ nhiều tệp hỏng (" + tried + ")");
        eq(0, escaped, "không tệp hỏng nào làm lọt ra một lỗi game không bắt được"
                + (first.length() == 0 ? "" : " — " + first));

        // Một tệp khai ảnh to hơn mọi máy J2ME từng vẽ: đọc tiếp là xin vài
        // tỉ điểm ảnh và hết bộ nhớ trước khi kịp biết tệp hỏng.
        byte[] huge = JpegSamples.fullColour();
        int sof = indexOfSof(huge);
        check(sof > 0, "tìm được chỗ khai kích thước");
        huge[sof + 5] = (byte) 0xF0;
        huge[sof + 6] = 0x00;
        huge[sof + 7] = (byte) 0xF0;
        huge[sof + 8] = 0x00;
        try {
            JpegReader.decode(huge);
            check(false, "ảnh khai to vô lý phải bị từ chối");
        } catch (IOException expected) {
            check(expected.getMessage().length() > 0, "và nói rõ: " + expected.getMessage());
        } catch (OutOfMemoryError e) {
            check(false, "không được cố xin bộ nhớ cho một ảnh khai to vô lý");
        }
    }

    /** Trả về tên lỗi nếu có gì lọt ra ngoài IOException, không thì null. */
    private String attempt(byte[] data) {
        try {
            JpegReader.decode(data);
            return null;
        } catch (IOException expected) {
            return null;
        } catch (RuntimeException escaped) {
            return escaped.toString();
        }
    }

    /** Chỗ khai kích thước (SOF0) trong một tệp JPEG. */
    private int indexOfSof(byte[] data) {
        for (int i = 2; i + 1 < data.length; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xC0) {
                return i;
            }
        }
        return -1;
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
