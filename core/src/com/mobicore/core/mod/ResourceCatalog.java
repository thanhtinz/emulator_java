package com.mobicore.core.mod;

import com.mobicore.core.gfx.JpegReader;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.jar.JarArchive;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.storage.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mọi thứ nằm trong tệp game, bày ra để người chơi tự thay.
 *
 * <p>Game J2ME là một tệp .jar, tức là một cái hộp: bên trong có ảnh, có
 * tiếng, có mấy tệp dữ liệu của riêng nó. Đổi một tấm ảnh trong đó là chuyện
 * người ta vẫn làm — Việt hoá chữ trong ảnh, thay bộ hình nhân vật, đổi ảnh
 * nền cho vừa mắt — và cho tới giờ muốn làm thì phải giải nén tệp .jar ra,
 * sửa, rồi đóng gói lại bằng máy tính.</p>
 *
 * <p>Bảng này đọc thẳng cái hộp ấy: mỗi thứ bên trong là gì, nặng bao nhiêu,
 * ảnh thì bao nhiêu điểm ảnh, và đã bị thay chưa.</p>
 *
 * <p><b>Nhìn vào ruột tệp, không nhìn cái tên.</b> Game đời ấy đặt tên tệp
 * rất tuỳ hứng: ảnh PNG nằm trong {@code data/12.dat}, tiếng nhạc trong
 * {@code r/07}. Đoán theo đuôi tên thì nửa số tệp thành "không rõ"; đọc mấy
 * byte đầu thì biết đúng nó là gì.</p>
 */
public final class ResourceCatalog {

    public static final int KIND_IMAGE = 0;
    public static final int KIND_SOUND = 1;
    public static final int KIND_TEXT = 2;
    public static final int KIND_DATA = 3;

    /** Một thứ nằm trong tệp game. */
    public static final class Entry {

        private final String path;
        private final int kind;
        private final String format;
        private final int bytes;
        private final int width;
        private final int height;
        private final boolean replaced;
        private final String replacedBy;

        Entry(String path, int kind, String format, int bytes, int width, int height,
              boolean replaced, String replacedBy) {
            this.path = path;
            this.kind = kind;
            this.format = format;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
            this.replaced = replaced;
            this.replacedBy = replacedBy;
        }

        public String path() {
            return path;
        }

        public int kind() {
            return kind;
        }

        /** Tên loại, để hiện lên màn hình. */
        public String kindName() {
            switch (kind) {
                case KIND_IMAGE: return "Ảnh";
                case KIND_SOUND: return "Âm thanh";
                case KIND_TEXT: return "Chữ";
                default: return "Dữ liệu";
            }
        }

        /** Đúng thứ nó là, đọc từ mấy byte đầu: PNG, JPEG, MIDI, WAV… */
        public String format() {
            return format;
        }

        public int bytes() {
            return bytes;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** True khi người chơi đã thay thứ này bằng tệp của mình. */
        public boolean isReplaced() {
            return replaced;
        }

        public String replacedBy() {
            return replacedBy;
        }

        public Map<String, Object> toJson() {
            Map<String, Object> json = Json.object();
            json.put("path", path);
            json.put("kind", Integer.valueOf(kind));
            json.put("kindName", kindName());
            json.put("format", format);
            json.put("bytes", Integer.valueOf(bytes));
            json.put("width", Integer.valueOf(width));
            json.put("height", Integer.valueOf(height));
            json.put("replaced", Boolean.valueOf(replaced));
            json.put("replacedBy", replacedBy);
            return json;
        }
    }

    private ResourceCatalog() {
    }

    /**
     * Đọc cả tệp game.
     *
     * @param mods những bản mod đang bật, để biết thứ nào đã bị thay; có thể null
     */
    public static List<Entry> scan(SuiteLoader suite, List<ModPackage> mods) {
        Map<String, String> replaced = replacements(mods);
        List<Entry> entries = new ArrayList<Entry>();
        JarArchive archive = suite.archive();
        List<String> names = archive.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name.endsWith(".class") || name.startsWith("META-INF/")) {
                // Lớp Java là mã của game, không phải thứ để thay bằng một
                // tấm ảnh; phần khai báo cũng vậy.
                continue;
            }
            byte[] data = archive.read(name);
            entries.add(describe(name, data, replaced.get(name)));
        }
        return entries;
    }

    /** Một dòng của bảng, đọc từ chính mấy byte đầu của tệp. */
    public static Entry describe(String path, byte[] data, String replacedBy) {
        int length = data == null ? 0 : data.length;
        int width = 0;
        int height = 0;
        String format = formatOf(data, path);
        int kind = kindOf(format, path);
        if ("PNG".equals(format)) {
            try {
                PngReader.Image image = PngReader.decode(data);
                width = image.width;
                height = image.height;
            } catch (IOException broken) {
                // Ảnh hỏng vẫn là một dòng trong bảng: người chơi cần thấy nó
                // để mà thay, chứ không phải để nó biến mất.
            } catch (RuntimeException broken) {
                // như trên
            }
        } else if ("JPEG".equals(format)) {
            int[] size = jpegSize(data);
            width = size[0];
            height = size[1];
        }
        return new Entry(path, kind, format, length, width, height,
                replacedBy != null, replacedBy == null ? "" : replacedBy);
    }

    /**
     * Kích thước ảnh JPEG, đọc từ phần khai báo.
     *
     * <p>Chỉ đọc phần đầu chứ không giải mã cả tấm: bảng này liệt kê hàng
     * trăm tệp, và giải mã tất cả để lấy hai con số là việc thừa.</p>
     */
    private static int[] jpegSize(byte[] data) {
        int at = 2;
        while (at + 9 < data.length) {
            if ((data[at] & 0xFF) != 0xFF) {
                at++;
                continue;
            }
            int marker = data[at + 1] & 0xFF;
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8
                    && marker != 0xCC) {
                int height = ((data[at + 5] & 0xFF) << 8) | (data[at + 6] & 0xFF);
                int width = ((data[at + 7] & 0xFF) << 8) | (data[at + 8] & 0xFF);
                return new int[]{width, height};
            }
            int length = ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
            if (length < 2) {
                break;
            }
            at += 2 + length;
        }
        return new int[]{0, 0};
    }

    /** Đúng thứ tệp này là, đọc từ mấy byte đầu chứ không đoán theo tên. */
    public static String formatOf(byte[] data, String path) {
        if (data != null && data.length >= 4) {
            if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                return "PNG";
            }
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return "JPEG";
            }
            if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
                return "GIF";
            }
            if (data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') {
                return "MIDI";
            }
            if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
                return "WAV";
            }
            if (data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S') {
                return "OGG";
            }
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0) {
                return "MP3";
            }
            if (data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
                return "MP3";
            }
            if (data[0] == '#' && data[1] == '!' && data[2] == 'A' && data[3] == 'M') {
                return "AMR";
            }
        }
        if (looksLikeText(data)) {
            return "Chữ";
        }
        String lower = path.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            return lower.substring(dot + 1).toUpperCase();
        }
        return "?";
    }

    /**
     * Tệp toàn chữ đọc được thì là chữ.
     *
     * <p>Game hay để bảng chữ, bảng màn chơi, danh sách câu thoại dưới dạng
     * tệp chữ thuần — và đó lại đúng là thứ người ta muốn sửa nhất khi Việt
     * hoá một game.</p>
     */
    private static boolean looksLikeText(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        int checked = Math.min(data.length, 512);
        int printable = 0;
        for (int i = 0; i < checked; i++) {
            int value = data[i] & 0xFF;
            boolean control = value < 0x20 && value != '\n' && value != '\r' && value != '\t';
            if (control || value == 0x7F) {
                // Một byte điều khiển giữa dòng là dấu hiệu chắc chắn nhất
                // của tệp nhị phân, nên chỉ cần một cái là đủ kết luận.
                return false;
            }
            // Chữ có dấu đi thành nhiều byte, và byte nối của UTF-8 nằm trong
            // khoảng 0x80–0xBF: đếm cả chúng, không thì mọi tệp tiếng Việt
            // đều bị nhận nhầm là dữ liệu.
            printable++;
        }
        return printable * 10 >= checked * 9;
    }

    private static int kindOf(String format, String path) {
        if ("PNG".equals(format) || "JPEG".equals(format) || "GIF".equals(format)) {
            return KIND_IMAGE;
        }
        if ("MIDI".equals(format) || "WAV".equals(format) || "MP3".equals(format)
                || "AMR".equals(format) || "OGG".equals(format)) {
            return KIND_SOUND;
        }
        if ("Chữ".equals(format)) {
            return KIND_TEXT;
        }
        return KIND_DATA;
    }

    /** Đường dẫn nào đang bị mod nào thay. */
    private static Map<String, String> replacements(List<ModPackage> mods) {
        Map<String, String> replaced = new LinkedHashMap<String, String>();
        if (mods == null) {
            return replaced;
        }
        for (int i = 0; i < mods.size(); i++) {
            ModPackage mod = mods.get(i);
            if (!mod.isEnabled()) {
                continue;
            }
            List<String> paths = mod.replacedResources();
            for (int p = 0; p < paths.size(); p++) {
                // Mod cài sau đè lên mod cài trước, nên ghi đè luôn ở đây.
                replaced.put(paths.get(p), mod.name());
            }
        }
        return replaced;
    }

    /** Cả bảng, cho màn hình đọc. */
    public static List<Object> toJson(List<Entry> entries) {
        List<Object> rows = new ArrayList<Object>();
        for (int i = 0; i < entries.size(); i++) {
            rows.add(entries.get(i).toJson());
        }
        return rows;
    }
}
