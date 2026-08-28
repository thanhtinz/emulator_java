package com.mobicore.core.tools;

import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tìm số vàng, số ngọc, số mạng trong phần lưu của game — rồi sửa nó.
 *
 * <p>Game J2ME chơi một mình lưu mọi thứ vào RMS: một dãy byte không có nhãn,
 * không có tên trường, mỗi game một kiểu. Không ai đọc dãy byte ấy mà đoán ra
 * "đây là số vàng" được.</p>
 *
 * <p>Nhưng người chơi thì <em>biết</em> mình đang có bao nhiêu vàng. Nên cách
 * làm là đi ngược: hỏi con số đang thấy trên màn hình, rồi tìm nó trong phần
 * lưu. Một lần tìm thường ra vài chục chỗ trùng — 8630 có thể là số vàng, mà
 * cũng có thể là điểm cao, là toạ độ, là một mẩu của con số khác. Nên có lần
 * thứ hai: chơi tiếp cho con số đổi đi, hỏi lại, và <b>giữ những chỗ đổi theo
 * đúng như vậy</b>. Hai lần thường đủ để còn đúng một chỗ.</p>
 *
 * <p>Không đoán kiểu ghi: game ghi số bằng đủ kiểu — bốn byte, hai byte, một
 * byte, đầu to hay đầu nhỏ, thậm chí viết thành chữ số. Chỗ này thử tất cả, và
 * ghi lại kiểu nào khớp để lúc sửa còn ghi <em>đúng kiểu ấy</em> — ghi bốn
 * byte đè lên một ô hai byte là làm hỏng phần lưu.</p>
 */
public final class SaveScanner {

    /** Kiểu ghi một con số trong phần lưu. */
    public static final int AS_INT32_BE = 0;
    public static final int AS_INT32_LE = 1;
    public static final int AS_INT16_BE = 2;
    public static final int AS_INT16_LE = 3;
    public static final int AS_INT8 = 4;
    /** Con số viết thành chữ số, như "8630". */
    public static final int AS_TEXT = 5;

    /** Một chỗ trong phần lưu đang giữ đúng con số đã hỏi. */
    public static final class Hit {

        private final String store;
        private final int recordId;
        private final int offset;
        private final int encoding;
        private final int length;
        private long value;

        Hit(String store, int recordId, int offset, int encoding, int length, long value) {
            this.store = store;
            this.recordId = recordId;
            this.offset = offset;
            this.encoding = encoding;
            this.length = length;
            this.value = value;
        }

        public String store() {
            return store;
        }

        public int recordId() {
            return recordId;
        }

        public int offset() {
            return offset;
        }

        public int encoding() {
            return encoding;
        }

        /** Con số đang nằm ở đó, lần đọc gần nhất. */
        public long value() {
            return value;
        }

        public String encodingName() {
            switch (encoding) {
                case AS_INT32_BE: return "4 byte";
                case AS_INT32_LE: return "4 byte (đảo)";
                case AS_INT16_BE: return "2 byte";
                case AS_INT16_LE: return "2 byte (đảo)";
                case AS_INT8: return "1 byte";
                default: return "chữ số";
            }
        }

        /** Đủ để tìm lại đúng chỗ này, và để màn hình bày ra. */
        public Map<String, Object> toJson() {
            Map<String, Object> json = Json.object();
            json.put("store", store);
            json.put("recordId", Integer.valueOf(recordId));
            json.put("offset", Integer.valueOf(offset));
            json.put("encoding", Integer.valueOf(encoding));
            json.put("encodingName", encodingName());
            json.put("length", Integer.valueOf(length));
            json.put("value", Long.valueOf(value));
            return json;
        }
    }

    /** Con số nhỏ nhất đáng đi tìm. */
    public static final long MIN_VALUE = 1;
    /** Và lớn nhất: quá cỡ này thì không còn là số vàng của một game J2ME. */
    public static final long MAX_VALUE = 2147483647L;

    private SaveScanner() {
    }

    /**
     * Lần tìm đầu tiên: mọi chỗ trong phần lưu đang giữ con số này.
     *
     * @param value con số người chơi đang thấy trên màn hình
     */
    public static List<Hit> find(RecordStoreManager records, long value) throws IOException {
        List<Hit> hits = new ArrayList<Hit>();
        if (value < MIN_VALUE || value > MAX_VALUE) {
            return hits;
        }
        List<String> stores = records.listStoreNames();
        for (int s = 0; s < stores.size(); s++) {
            String name = stores.get(s);
            RecordStoreManager.Store store = records.openStore(name, false);
            List<Integer> ids = store.recordIds();
            for (int i = 0; i < ids.size(); i++) {
                int id = ids.get(i).intValue();
                byte[] data = store.get(id);
                if (data != null) {
                    scanRecord(name, id, data, value, hits);
                }
            }
        }
        return hits;
    }

    /**
     * Lần tìm thứ hai trở đi: giữ lại những chỗ nay mang con số mới.
     *
     * <p>Đây mới là chỗ lọc ra được cái đúng. Sau lần đầu thường còn vài chục
     * chỗ; chơi cho số vàng đổi đi rồi hỏi lại thì gần như chỉ còn một.</p>
     */
    public static List<Hit> narrow(RecordStoreManager records, List<Hit> previous, long value)
            throws IOException {
        List<Hit> kept = new ArrayList<Hit>();
        for (int i = 0; i < previous.size(); i++) {
            Hit hit = previous.get(i);
            byte[] data = read(records, hit);
            if (data == null) {
                continue;
            }
            long now = valueAt(data, hit.offset, hit.encoding, hit.length);
            if (now == value) {
                hit.value = now;
                kept.add(hit);
            }
        }
        return kept;
    }

    /** Đọc lại con số đang nằm ở một chỗ đã tìm được. */
    public static long read(RecordStoreManager records, Hit hit, long fallback)
            throws IOException {
        byte[] data = read(records, hit);
        if (data == null) {
            return fallback;
        }
        return valueAt(data, hit.offset, hit.encoding, hit.length);
    }

    /**
     * Ghi con số mới vào đúng chỗ ấy, đúng kiểu ghi cũ.
     *
     * @return false khi con số mới không vừa chỗ cũ
     */
    public static boolean write(RecordStoreManager records, Hit hit, long value, long timestamp)
            throws IOException {
        byte[] data = read(records, hit);
        if (data == null) {
            return false;
        }
        byte[] updated = replace(data, hit, value);
        if (updated == null) {
            return false;
        }
        RecordStoreManager.Store store = records.openStore(hit.store, false);
        boolean written = store.set(hit.recordId, updated, timestamp);
        if (written) {
            records.flush(hit.store);
            hit.value = value;
        }
        return written;
    }

    /**
     * Con số mới, đặt vào chỗ của con số cũ.
     *
     * <p>Số ghi thành chữ thì độ dài đổi theo giá trị: "9" ngắn hơn "8630".
     * Ghi đè bừa vào đó là đẩy lệch mọi thứ đứng sau trong bản ghi, nên chỗ
     * này dựng lại cả bản ghi cho đúng.</p>
     */
    private static byte[] replace(byte[] data, Hit hit, long value) {
        if (hit.encoding == AS_TEXT) {
            String text = String.valueOf(value);
            byte[] digits;
            try {
                digits = text.getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException impossible) {
                return null;
            }
            byte[] out = new byte[data.length - hit.length + digits.length];
            System.arraycopy(data, 0, out, 0, hit.offset);
            System.arraycopy(digits, 0, out, hit.offset, digits.length);
            System.arraycopy(data, hit.offset + hit.length, out, hit.offset + digits.length,
                    data.length - hit.offset - hit.length);
            return out;
        }
        if (!fits(value, hit.encoding)) {
            return null;
        }
        byte[] out = new byte[data.length];
        System.arraycopy(data, 0, out, 0, data.length);
        switch (hit.encoding) {
            case AS_INT32_BE:
                out[hit.offset] = (byte) (value >> 24);
                out[hit.offset + 1] = (byte) (value >> 16);
                out[hit.offset + 2] = (byte) (value >> 8);
                out[hit.offset + 3] = (byte) value;
                break;
            case AS_INT32_LE:
                out[hit.offset] = (byte) value;
                out[hit.offset + 1] = (byte) (value >> 8);
                out[hit.offset + 2] = (byte) (value >> 16);
                out[hit.offset + 3] = (byte) (value >> 24);
                break;
            case AS_INT16_BE:
                out[hit.offset] = (byte) (value >> 8);
                out[hit.offset + 1] = (byte) value;
                break;
            case AS_INT16_LE:
                out[hit.offset] = (byte) value;
                out[hit.offset + 1] = (byte) (value >> 8);
                break;
            default:
                out[hit.offset] = (byte) value;
                break;
        }
        return out;
    }

    /** Con số có vừa ô cũ không: nhét 70000 vào hai byte là làm hỏng phần lưu. */
    public static boolean fits(long value, int encoding) {
        switch (encoding) {
            case AS_INT32_BE:
            case AS_INT32_LE:
                return value >= 0 && value <= 2147483647L;
            case AS_INT16_BE:
            case AS_INT16_LE:
                return value >= 0 && value <= 65535L;
            case AS_INT8:
                return value >= 0 && value <= 255L;
            default:
                return value >= 0;
        }
    }

    // ------------------------------------------------------------- bên trong

    private static byte[] read(RecordStoreManager records, Hit hit) throws IOException {
        if (!records.exists(hit.store)) {
            return null;
        }
        return records.openStore(hit.store, false).get(hit.recordId);
    }

    private static void scanRecord(String store, int recordId, byte[] data, long value,
                                   List<Hit> hits) {
        for (int at = 0; at + 4 <= data.length; at++) {
            if (int32(data, at, true) == value) {
                hits.add(new Hit(store, recordId, at, AS_INT32_BE, 4, value));
            }
            if (int32(data, at, false) == value) {
                hits.add(new Hit(store, recordId, at, AS_INT32_LE, 4, value));
            }
        }
        if (value <= 65535L) {
            for (int at = 0; at + 2 <= data.length; at++) {
                if (int16(data, at, true) == value) {
                    hits.add(new Hit(store, recordId, at, AS_INT16_BE, 2, value));
                }
                if (int16(data, at, false) == value) {
                    hits.add(new Hit(store, recordId, at, AS_INT16_LE, 2, value));
                }
            }
        }
        if (value <= 255L) {
            for (int at = 0; at < data.length; at++) {
                if ((data[at] & 0xFF) == value) {
                    hits.add(new Hit(store, recordId, at, AS_INT8, 1, value));
                }
            }
        }
        scanDigits(store, recordId, data, value, hits);
    }

    /**
     * Con số viết thành chữ số.
     *
     * <p>Game lưu bằng {@code DataOutputStream.writeUTF} hay ghi thẳng một
     * chuỗi thì số vàng nằm trong phần lưu dưới dạng "8630" — bốn byte chữ,
     * không phải một con số nhị phân.</p>
     */
    private static void scanDigits(String store, int recordId, byte[] data, long value,
                                   List<Hit> hits) {
        String text = String.valueOf(value);
        int length = text.length();
        for (int at = 0; at + length <= data.length; at++) {
            boolean matches = true;
            for (int i = 0; i < length && matches; i++) {
                matches = (data[at + i] & 0xFF) == text.charAt(i);
            }
            if (!matches) {
                continue;
            }
            // Chỉ nhận khi hai đầu không còn chữ số: "8630" nằm trong "186301"
            // là một con số khác, không phải con số đang tìm.
            if (at > 0 && isDigit(data[at - 1])) {
                continue;
            }
            if (at + length < data.length && isDigit(data[at + length])) {
                continue;
            }
            hits.add(new Hit(store, recordId, at, AS_TEXT, length, value));
        }
    }

    private static boolean isDigit(byte value) {
        int c = value & 0xFF;
        return c >= '0' && c <= '9';
    }

    private static long valueAt(byte[] data, int offset, int encoding, int length) {
        switch (encoding) {
            case AS_INT32_BE:
                return offset + 4 <= data.length ? int32(data, offset, true) : Long.MIN_VALUE;
            case AS_INT32_LE:
                return offset + 4 <= data.length ? int32(data, offset, false) : Long.MIN_VALUE;
            case AS_INT16_BE:
                return offset + 2 <= data.length ? int16(data, offset, true) : Long.MIN_VALUE;
            case AS_INT16_LE:
                return offset + 2 <= data.length ? int16(data, offset, false) : Long.MIN_VALUE;
            case AS_INT8:
                return offset < data.length ? (data[offset] & 0xFF) : Long.MIN_VALUE;
            default:
                return digitsAt(data, offset);
        }
    }

    /** Con số chữ bắt đầu ở đây, dài bao nhiêu chữ số thì đọc bấy nhiêu. */
    private static long digitsAt(byte[] data, int offset) {
        long value = 0;
        int read = 0;
        for (int at = offset; at < data.length && isDigit(data[at]); at++) {
            value = value * 10 + (data[at] - '0');
            read++;
            if (value > MAX_VALUE) {
                return Long.MIN_VALUE;
            }
        }
        return read == 0 ? Long.MIN_VALUE : value;
    }

    private static long int32(byte[] data, int at, boolean bigEndian) {
        int a = data[at] & 0xFF;
        int b = data[at + 1] & 0xFF;
        int c = data[at + 2] & 0xFF;
        int d = data[at + 3] & 0xFF;
        long value = bigEndian
                ? ((long) a << 24) | (b << 16) | (c << 8) | d
                : ((long) d << 24) | (c << 16) | (b << 8) | a;
        return value;
    }

    private static long int16(byte[] data, int at, boolean bigEndian) {
        int a = data[at] & 0xFF;
        int b = data[at + 1] & 0xFF;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    /** Cả danh sách, cho màn hình đọc. */
    public static List<Object> toJson(List<Hit> hits) {
        List<Object> rows = new ArrayList<Object>();
        for (int i = 0; i < hits.size(); i++) {
            rows.add(hits.get(i).toJson());
        }
        return rows;
    }
}
