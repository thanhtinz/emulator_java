package com.mobicore.core.gfx;

import java.io.IOException;

/**
 * Đọc ảnh JPEG, vì game đời J2ME có dùng.
 *
 * <p>MIDP bắt buộc mọi máy phải đọc được PNG, nên máy ảo này lâu nay chỉ đọc
 * PNG. Nhưng máy thật thì đọc thêm JPEG, và game biết thế: ảnh mở đầu, ảnh
 * nền, ảnh chân dung nhân vật — những thứ to và nhiều màu — hay được đóng gói
 * bằng JPEG cho nhẹ. Game gọi {@code Image.createImage} với một tệp như vậy
 * thì trước đây nhận về "Unsupported image format" rồi chết.</p>
 *
 * <p>Đọc được ở đây là <b>baseline</b> (SOF0/SOF1): Huffman, 8 bit, một hoặc
 * ba thành phần màu, và mọi kiểu lấy mẫu màu thường gặp (4:4:4, 4:2:2,
 * 4:2:0), kể cả ảnh có mốc khởi động lại. Ảnh <b>progressive</b> (SOF2) thì
 * nói thẳng là chưa đọc được, chứ không giải mã ra một mớ nhiễu rồi để game
 * vẽ nó lên màn hình.</p>
 */
public final class JpegReader {

    /** Ảnh đã giải mã, cùng hình dạng PngReader trả về. */
    public static final class Image {

        public final int[] pixels;
        public final int width;
        public final int height;

        Image(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    private JpegReader() {
    }

    /** Hai byte đầu của mọi tệp JPEG. */
    public static boolean looksLikeJpeg(byte[] data) {
        return data != null && data.length > 3 && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8;
    }

    // ----------------------------------------------------------- các bảng

    /** Thứ tự zigzag: hệ số trong tệp đi theo đường chéo, ảnh thì theo hàng. */
    private static final int[] ZIGZAG = {
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
    };

    /** cos((2x+1) u π / 16) * (u == 0 ? 1/√2 : 1), dựng sẵn một lần. */
    private static final float[] COS = buildCosines();

    private static float[] buildCosines() {
        float[] table = new float[64];
        for (int u = 0; u < 8; u++) {
            double scale = u == 0 ? Math.sqrt(0.5) : 1.0;
            for (int x = 0; x < 8; x++) {
                table[u * 8 + x] = (float) (scale * Math.cos((2 * x + 1) * u * Math.PI / 16));
            }
        }
        return table;
    }

    /** Một bảng Huffman, ở dạng tra được bằng cách đi dần theo độ dài mã. */
    private static final class Huffman {

        final int[] minCode = new int[17];
        final int[] maxCode = new int[17];
        final int[] valPtr = new int[17];
        final int[] values;

        Huffman(int[] counts, int[] values) {
            this.values = values;
            int code = 0;
            int index = 0;
            for (int length = 1; length <= 16; length++) {
                valPtr[length] = index;
                minCode[length] = code;
                code += counts[length];
                index += counts[length];
                maxCode[length] = counts[length] == 0 ? -1 : code - 1;
                code <<= 1;
            }
        }
    }

    /** Một thành phần màu: Y, Cb hay Cr. */
    private static final class Component {

        int id;
        int h = 1;
        int v = 1;
        int quantTable;
        int dcTable;
        int acTable;
        int dcPredictor;
        int[] plane;
        int planeWidth;
        int planeHeight;
    }

    // ------------------------------------------------------------ giải mã

    public static Image decode(byte[] data) throws IOException {
        if (!looksLikeJpeg(data)) {
            throw new IOException("Không phải tệp JPEG");
        }
        int[][] quant = new int[4][];
        Huffman[] dcTables = new Huffman[4];
        Huffman[] acTables = new Huffman[4];
        Component[] components = null;
        int width = 0;
        int height = 0;
        int restartInterval = 0;

        int at = 2;
        while (at + 1 < data.length) {
            if ((data[at] & 0xFF) != 0xFF) {
                at++;
                continue;
            }
            int marker = data[at + 1] & 0xFF;
            at += 2;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (marker == 0xD9) {
                break;
            }
            if (at + 1 >= data.length) {
                break;
            }
            int length = readShort(data, at);
            int body = at + 2;
            int end = at + length;
            if (end > data.length) {
                throw new IOException("Tệp JPEG cụt");
            }

            switch (marker) {
                case 0xDB:  // bảng lượng tử hoá
                    while (body < end) {
                        int precision = (data[body] >> 4) & 0x0F;
                        int id = data[body] & 0x0F;
                        body++;
                        int[] table = new int[64];
                        for (int i = 0; i < 64; i++) {
                            int value = precision == 0
                                    ? data[body + i] & 0xFF
                                    : readShort(data, body + i * 2);
                            table[ZIGZAG[i]] = value;
                        }
                        body += precision == 0 ? 64 : 128;
                        if (id < quant.length) {
                            quant[id] = table;
                        }
                    }
                    break;
                case 0xC4:  // bảng Huffman
                    while (body < end) {
                        int cls = (data[body] >> 4) & 0x0F;
                        int id = data[body] & 0x0F;
                        body++;
                        int[] counts = new int[17];
                        int total = 0;
                        for (int i = 1; i <= 16; i++) {
                            counts[i] = data[body + i - 1] & 0xFF;
                            total += counts[i];
                        }
                        body += 16;
                        int[] values = new int[total];
                        for (int i = 0; i < total; i++) {
                            values[i] = data[body + i] & 0xFF;
                        }
                        body += total;
                        Huffman table = new Huffman(counts, values);
                        if (id < 4) {
                            if (cls == 0) {
                                dcTables[id] = table;
                            } else {
                                acTables[id] = table;
                            }
                        }
                    }
                    break;
                case 0xC0:  // baseline
                case 0xC1:  // extended sequential, giải mã y hệt
                    if ((data[body] & 0xFF) != 8) {
                        throw new IOException("JPEG không phải 8 bit");
                    }
                    height = readShort(data, body + 1);
                    width = readShort(data, body + 3);
                    int count = data[body + 5] & 0xFF;
                    if (count != 1 && count != 3) {
                        throw new IOException("JPEG có " + count
                                + " thành phần màu, chỉ đọc được ảnh xám và ảnh màu thường");
                    }
                    components = new Component[count];
                    for (int i = 0; i < count; i++) {
                        int p = body + 6 + i * 3;
                        Component component = new Component();
                        component.id = data[p] & 0xFF;
                        component.h = (data[p + 1] >> 4) & 0x0F;
                        component.v = data[p + 1] & 0x0F;
                        component.quantTable = data[p + 2] & 0xFF;
                        if (component.h < 1 || component.v < 1) {
                            throw new IOException("JPEG khai kiểu lấy mẫu màu không hợp lệ");
                        }
                        components[i] = component;
                    }
                    break;
                case 0xC2:
                    // Ảnh progressive: quét nhiều lần, mỗi lần thêm chi tiết.
                    // Cách giải mã khác hẳn, nên nói thẳng chứ không giải ra
                    // một mớ nhiễu rồi để game vẽ nó lên.
                    throw new IOException("JPEG kiểu progressive, chưa đọc được");
                case 0xC3:
                case 0xC5:
                case 0xC6:
                case 0xC7:
                case 0xC9:
                case 0xCA:
                case 0xCB:
                case 0xCD:
                case 0xCE:
                case 0xCF:
                    throw new IOException("JPEG kiểu hiếm gặp (SOF" + (marker - 0xC0)
                            + "), chưa đọc được");
                case 0xDD:
                    restartInterval = readShort(data, body);
                    break;
                case 0xDA: {  // bắt đầu phần ảnh
                    if (components == null) {
                        throw new IOException("Tệp JPEG thiếu phần khai kích thước");
                    }
                    int scanCount = data[body] & 0xFF;
                    for (int i = 0; i < scanCount; i++) {
                        int id = data[body + 1 + i * 2] & 0xFF;
                        int tables = data[body + 2 + i * 2] & 0xFF;
                        for (int c = 0; c < components.length; c++) {
                            if (components[c].id == id) {
                                components[c].dcTable = (tables >> 4) & 0x0F;
                                components[c].acTable = tables & 0x0F;
                            }
                        }
                    }
                    int scanStart = end;
                    return scan(data, scanStart, width, height, components, quant,
                            dcTables, acTables, restartInterval);
                }
                default:
                    break;  // APPn, COM và những thứ không ảnh hưởng đến ảnh
            }
            at = end;
        }
        throw new IOException("Tệp JPEG không có phần ảnh");
    }

    /** Đọc phần ảnh: từng MCU một, cho tới khi đủ khung. */
    private static Image scan(byte[] data, int at, int width, int height,
                              Component[] components, int[][] quant, Huffman[] dcTables,
                              Huffman[] acTables, int restartInterval) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException("Ảnh JPEG khai kích thước rỗng");
        }
        int hMax = 1;
        int vMax = 1;
        for (int i = 0; i < components.length; i++) {
            hMax = Math.max(hMax, components[i].h);
            vMax = Math.max(vMax, components[i].v);
        }
        int mcusX = (width + hMax * 8 - 1) / (hMax * 8);
        int mcusY = (height + vMax * 8 - 1) / (vMax * 8);
        for (int i = 0; i < components.length; i++) {
            Component component = components[i];
            component.planeWidth = mcusX * component.h * 8;
            component.planeHeight = mcusY * component.v * 8;
            component.plane = new int[component.planeWidth * component.planeHeight];
            component.dcPredictor = 0;
        }

        BitReader bits = new BitReader(data, at);
        int[] block = new int[64];
        int[] samples = new int[64];
        int untilRestart = restartInterval;
        for (int mcuY = 0; mcuY < mcusY; mcuY++) {
            for (int mcuX = 0; mcuX < mcusX; mcuX++) {
                if (restartInterval > 0 && untilRestart == 0) {
                    bits.restart();
                    for (int i = 0; i < components.length; i++) {
                        components[i].dcPredictor = 0;
                    }
                    untilRestart = restartInterval;
                }
                for (int i = 0; i < components.length; i++) {
                    Component component = components[i];
                    int[] table = quant[component.quantTable];
                    if (table == null) {
                        throw new IOException("Tệp JPEG thiếu bảng lượng tử hoá");
                    }
                    Huffman dc = dcTables[component.dcTable];
                    Huffman ac = acTables[component.acTable];
                    if (dc == null || ac == null) {
                        throw new IOException("Tệp JPEG thiếu bảng Huffman");
                    }
                    for (int by = 0; by < component.v; by++) {
                        for (int bx = 0; bx < component.h; bx++) {
                            decodeBlock(bits, component, dc, ac, table, block);
                            idct(block, samples);
                            int originX = (mcuX * component.h + bx) * 8;
                            int originY = (mcuY * component.v + by) * 8;
                            for (int y = 0; y < 8; y++) {
                                int row = (originY + y) * component.planeWidth + originX;
                                for (int x = 0; x < 8; x++) {
                                    component.plane[row + x] = samples[y * 8 + x];
                                }
                            }
                        }
                    }
                }
                untilRestart--;
            }
        }
        return toPixels(width, height, components, hMax, vMax);
    }

    /** Một khối 8×8: một hệ số một chiều, rồi các hệ số xoay chiều. */
    private static void decodeBlock(BitReader bits, Component component, Huffman dc,
                                    Huffman ac, int[] quant, int[] block) throws IOException {
        for (int i = 0; i < 64; i++) {
            block[i] = 0;
        }
        int t = decodeHuffman(bits, dc);
        int diff = t == 0 ? 0 : extend(bits.read(t), t);
        component.dcPredictor += diff;
        block[0] = component.dcPredictor * quant[0];

        int index = 1;
        while (index < 64) {
            int rs = decodeHuffman(bits, ac);
            int run = (rs >> 4) & 0x0F;
            int size = rs & 0x0F;
            if (size == 0) {
                if (run != 15) {
                    break;  // hết khối
                }
                index += 16;
                continue;
            }
            index += run;
            if (index > 63) {
                break;
            }
            int position = ZIGZAG[index];
            block[position] = extend(bits.read(size), size) * quant[position];
            index++;
        }
    }

    private static int decodeHuffman(BitReader bits, Huffman table) throws IOException {
        int code = bits.read(1);
        for (int length = 1; length <= 16; length++) {
            if (table.maxCode[length] >= 0 && code <= table.maxCode[length]) {
                int index = table.valPtr[length] + code - table.minCode[length];
                if (index < 0 || index >= table.values.length) {
                    throw new IOException("Mã Huffman hỏng trong tệp JPEG");
                }
                return table.values[index];
            }
            code = (code << 1) | bits.read(1);
        }
        throw new IOException("Mã Huffman hỏng trong tệp JPEG");
    }

    /** Số có dấu kiểu JPEG: bit đầu 0 nghĩa là số âm. */
    private static int extend(int value, int size) {
        return value < (1 << (size - 1)) ? value - (1 << size) + 1 : value;
    }

    /** Biến 64 hệ số trở lại thành 64 điểm sáng, đã dịch về 0..255. */
    private static void idct(int[] block, int[] out) {
        float[] rows = new float[64];
        for (int y = 0; y < 8; y++) {
            int offset = y * 8;
            for (int x = 0; x < 8; x++) {
                float sum = 0;
                for (int u = 0; u < 8; u++) {
                    float coefficient = block[offset + u];
                    if (coefficient != 0) {
                        sum += coefficient * COS[u * 8 + x];
                    }
                }
                rows[offset + x] = sum * 0.5f;
            }
        }
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                float sum = 0;
                for (int v = 0; v < 8; v++) {
                    sum += rows[v * 8 + x] * COS[v * 8 + y];
                }
                int value = Math.round(sum * 0.5f) + 128;
                out[y * 8 + x] = value < 0 ? 0 : (value > 255 ? 255 : value);
            }
        }
    }

    /** Ghép các thành phần lại thành ảnh, giãn phần màu ra cho khớp độ sáng. */
    private static Image toPixels(int width, int height, Component[] components,
                                  int hMax, int vMax) {
        int[] pixels = new int[width * height];
        if (components.length == 1) {
            Component grey = components[0];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int value = sample(grey, x, y, hMax, vMax);
                    pixels[y * width + x] = 0xFF000000 | (value << 16) | (value << 8) | value;
                }
            }
            return new Image(pixels, width, height);
        }
        Component luma = components[0];
        Component blue = components[1];
        Component red = components[2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lightness = sample(luma, x, y, hMax, vMax);
                int cb = sample(blue, x, y, hMax, vMax) - 128;
                int cr = sample(red, x, y, hMax, vMax) - 128;
                int r = clamp(lightness + ((91881 * cr) >> 16));
                int g = clamp(lightness - ((22554 * cb + 46802 * cr) >> 16));
                int b = clamp(lightness + ((116130 * cb) >> 16));
                pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return new Image(pixels, width, height);
    }

    /**
     * Một điểm của thành phần màu, tại toạ độ của ảnh.
     *
     * <p>Phần màu thường được lưu thưa hơn phần sáng — mắt người nhạy với
     * sáng tối hơn nhiều so với màu — nên lấy mẫu ở đây là phép chia, chứ
     * không phải một phép giãn ảnh riêng.</p>
     */
    private static int sample(Component component, int x, int y, int hMax, int vMax) {
        int sx = x * component.h / hMax;
        int sy = y * component.v / vMax;
        if (sx >= component.planeWidth) {
            sx = component.planeWidth - 1;
        }
        if (sy >= component.planeHeight) {
            sy = component.planeHeight - 1;
        }
        return component.plane[sy * component.planeWidth + sx];
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    private static int readShort(byte[] data, int at) {
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }

    /**
     * Đọc từng bit của phần ảnh.
     *
     * <p>Trong phần ảnh, byte {@code FF} được viết thành {@code FF 00} để
     * không lẫn với mốc đánh dấu; đọc tới đó thì bỏ byte {@code 00} đi.</p>
     */
    private static final class BitReader {

        private final byte[] data;
        private int at;
        private int current;
        private int left;

        BitReader(byte[] data, int at) {
            this.data = data;
            this.at = at;
        }

        int read(int count) throws IOException {
            int value = 0;
            for (int i = 0; i < count; i++) {
                if (left == 0) {
                    fill();
                }
                left--;
                value = (value << 1) | ((current >> left) & 1);
            }
            return value;
        }

        private void fill() throws IOException {
            if (at >= data.length) {
                // Có tệp thiếu vài byte cuối mà phần ảnh vẫn dùng được: cho
                // đọc tiếp bằng số 0 thay vì vứt cả tấm ảnh đi.
                current = 0;
                left = 8;
                return;
            }
            int next = data[at++] & 0xFF;
            if (next == 0xFF) {
                int marker = at < data.length ? data[at] & 0xFF : 0xD9;
                if (marker == 0x00) {
                    at++;
                } else if (marker >= 0xD0 && marker <= 0xD7) {
                    at++;
                    next = at < data.length ? data[at++] & 0xFF : 0;
                } else {
                    current = 0;
                    left = 8;
                    return;
                }
            }
            current = next;
            left = 8;
        }

        /** Sang mốc khởi động lại kế tiếp, bỏ nốt phần bit lẻ đang dở. */
        void restart() {
            left = 0;
            while (at + 1 < data.length) {
                if ((data[at] & 0xFF) == 0xFF) {
                    int marker = data[at + 1] & 0xFF;
                    if (marker >= 0xD0 && marker <= 0xD7) {
                        at += 2;
                        return;
                    }
                }
                at++;
            }
        }
    }
}
