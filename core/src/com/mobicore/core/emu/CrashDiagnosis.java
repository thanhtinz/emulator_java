package com.mobicore.core.emu;

import com.mobicore.core.model.Compatibility;
import com.mobicore.core.storage.Json;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmError;
import com.mobicore.core.vm.VmThrow;

import java.util.Map;

/**
 * Nói bằng tiếng người vì sao một game vừa hỏng.
 *
 * <p>Khi game chết, thứ duy nhất có trong tay là tên một lớp ngoại lệ và một
 * dòng thông báo do chính game viết — cả hai đều bằng tiếng Anh và đều nói về
 * bên trong máy ảo, chứ không nói người chơi phải làm gì. Màn hình đen kèm
 * dòng {@code NoClassDefFoundError} không giúp được ai.</p>
 *
 * <p>Lớp này biến chỗ đó thành ba câu: hỏng cái gì, vì sao, và làm gì tiếp.
 * Nó không đoán: mỗi kết luận đều rút ra từ tên lớp ngoại lệ, và khi tên đó
 * không nói lên điều gì thì nó nhận là không biết thay vì bịa một lý do nghe
 * hợp lý.</p>
 */
public final class CrashDiagnosis {

    /** Game cần một phần của điện thoại mà bản này chưa làm. */
    public static final int KIND_MISSING_API = 0;
    /** Chính tệp game thiếu lớp của nó — tải về dở dang hoặc bị cắt. */
    public static final int KIND_MISSING_CLASS = 1;
    /** Hết bộ nhớ. */
    public static final int KIND_MEMORY = 2;
    /** Không nối được ra mạng. */
    public static final int KIND_NETWORK = 3;
    /** Không đọc/ghi được phần lưu của game. */
    public static final int KIND_STORAGE = 4;
    /** Âm thanh hoặc video game đòi mà máy ảo không phát được. */
    public static final int KIND_MEDIA = 5;
    /** Lỗi trong chính mã của game. */
    public static final int KIND_GAME_BUG = 6;
    /** Máy ảo tự gãy — không phải lỗi của game. */
    public static final int KIND_EMULATOR = 7;
    /** Không đủ căn cứ để nói gì. */
    public static final int KIND_UNKNOWN = 8;
    /** Game chạy mãi một chỗ, không vẽ xong khung hình. */
    public static final int KIND_HANG = 9;

    private final int kind;
    private final String title;
    private final String reason;
    private final String advice;
    private final String technical;

    private CrashDiagnosis(int kind, String title, String reason, String advice,
                           String technical) {
        this.kind = kind;
        this.title = title;
        this.reason = reason;
        this.advice = advice;
        this.technical = technical;
    }

    public int kind() {
        return kind;
    }

    /** Một dòng ngắn để làm tiêu đề. */
    public String title() {
        return title;
    }

    /** Vì sao game dừng, viết cho người chơi. */
    public String reason() {
        return reason;
    }

    /** Việc người chơi làm được — rỗng khi thật sự không có việc gì. */
    public String advice() {
        return advice;
    }

    /** Tên lớp ngoại lệ và thông báo gốc, để gửi kèm báo lỗi. */
    public String technical() {
        return technical;
    }

    /** True khi lỗi nằm ở game hoặc ở tệp game, không ở người chơi. */
    public boolean blamesGame() {
        return kind == KIND_MISSING_CLASS || kind == KIND_GAME_BUG;
    }

    // --------------------------------------------------------------- dựng

    /** Đọc một thứ vừa ném ra, dù là ngoại lệ của game hay của máy ảo. */
    public static CrashDiagnosis of(Throwable failure) {
        if (failure instanceof VmThrow) {
            VmClass type = ((VmThrow) failure).type();
            return of(type == null ? "" : type.binaryName(), failure.getMessage());
        }
        if (failure instanceof VmError) {
            return of("", failure.getMessage());
        }
        if (failure == null) {
            return of("", "");
        }
        return of(failure.getClass().getName(), failure.getMessage());
    }

    /**
     * @param typeName tên lớp ngoại lệ, dạng {@code java.lang.Xxx}; rỗng khi
     *                 chính máy ảo hỏng chứ không phải game ném ra
     * @param message  thông báo đi kèm; với lớp không tìm thấy thì đây là tên
     *                 lớp còn thiếu
     */
    public static CrashDiagnosis of(String typeName, String message) {
        String type = typeName == null ? "" : typeName.replace('/', '.');
        String detail = message == null ? "" : message.trim();
        String technical = type.length() == 0
                ? detail
                : (detail.length() == 0 ? type : type + ": " + detail);

        if (type.length() == 0 && detail.startsWith("Game không phản hồi")) {
            return new CrashDiagnosis(KIND_HANG, "Game bị treo",
                    "Game chạy mãi một chỗ mà không vẽ xong khung hình, nên máy ảo "
                            + "cắt ngang để còn thoát ra được.",
                    "Chơi lại từ chỗ đã lưu. Nếu lần nào cũng treo ở đúng chỗ này thì "
                            + "bản game đó vốn đã hỏng — máy ảo không chờ lâu hơn được, "
                            + "vì chờ nữa thì cũng chỉ là màn hình đứng im.",
                    technical);
        }
        if (type.length() == 0) {
            return new CrashDiagnosis(KIND_EMULATOR, "Máy ảo gặp sự cố",
                    detail.length() == 0
                            ? "Máy ảo dừng giữa chừng mà không nói rõ vì sao."
                            : "Máy ảo dừng giữa chừng: " + detail,
                    "Hãy thử mở lại game. Nếu vẫn vậy, gửi báo lỗi kèm tên game.",
                    technical);
        }

        if (type.equals("java.lang.NoClassDefFoundError")
                || type.equals("java.lang.ClassNotFoundException")) {
            return missingClass(detail, technical);
        }
        if (type.equals("java.lang.OutOfMemoryError")) {
            return new CrashDiagnosis(KIND_MEMORY, "Hết bộ nhớ",
                    "Game xin thêm bộ nhớ mà máy ảo không còn chỗ để cấp.",
                    "Đóng bớt game khác đang mở rồi chơi lại. Trong phần cài đặt "
                            + "của game, hạ cỡ màn hình cũng đỡ tốn bộ nhớ.",
                    technical);
        }
        if (type.equals("javax.microedition.io.ConnectionNotFoundException")) {
            return new CrashDiagnosis(KIND_NETWORK, "Không mở được kết nối",
                    "Game muốn mở một kết nối mà máy ảo không nhận ra kiểu đó: "
                            + (detail.length() == 0 ? "không rõ địa chỉ" : detail) + ".",
                    "Game này cần mạng của nhà mạng ngày xưa. Phần chơi mạng của nó "
                            + "sẽ không dùng được, nhưng phần chơi một mình thì vẫn.",
                    technical);
        }
        if (type.equals("java.io.InterruptedIOException")
                || type.equals("java.io.IOException")) {
            return new CrashDiagnosis(KIND_NETWORK, "Kết nối hỏng giữa chừng",
                    "Game đang đọc hoặc gửi dữ liệu thì đứt: "
                            + (detail.length() == 0 ? "không rõ lý do" : detail) + ".",
                    "Kiểm tra mạng rồi chơi lại. Máy chủ của game cũ thường đã tắt "
                            + "từ lâu, khi đó phần chơi mạng sẽ không bao giờ chạy được.",
                    technical);
        }
        if (type.equals("java.lang.SecurityException")) {
            return new CrashDiagnosis(KIND_NETWORK, "Game bị chặn",
                    "Game xin làm một việc mà máy ảo không cho phép: "
                            + (detail.length() == 0 ? "không rõ việc gì" : detail) + ".",
                    "Xem lại phần quyền trong cài đặt của game rồi chơi lại.",
                    technical);
        }
        if (type.startsWith("javax.microedition.rms.")) {
            return new CrashDiagnosis(KIND_STORAGE, "Không đọc được phần đã lưu",
                    "Game mở phần lưu của nó thì gặp lỗi: "
                            + (detail.length() == 0 ? type : detail) + ".",
                    "Trong phần lưu của game, xoá dữ liệu rồi chơi lại từ đầu. "
                            + "Điểm và màn đã qua sẽ mất.",
                    technical);
        }
        if (type.equals("javax.microedition.media.MediaException")) {
            return new CrashDiagnosis(KIND_MEDIA, "Không phát được âm thanh",
                    "Game đòi một kiểu âm thanh máy ảo chưa phát được: "
                            + (detail.length() == 0 ? "không rõ kiểu" : detail) + ".",
                    "Tắt âm trong cài đặt của game rồi chơi lại — phần còn lại vẫn chạy.",
                    technical);
        }
        if (isGameBug(type)) {
            return new CrashDiagnosis(KIND_GAME_BUG, "Game gặp lỗi của chính nó",
                    // Thông báo gốc là tiếng Anh và nói về bên trong máy ảo,
                    // nên nó ở lại phần kỹ thuật chứ không chen vào câu này.
                    "Mã của game làm sai một bước: " + bugPhrase(type) + ".",
                    "Đây là lỗi có sẵn trong game, không phải do máy. Chơi lại từ chỗ "
                            + "đã lưu thường qua được; nếu lần nào cũng hỏng ở đúng chỗ "
                            + "này thì bản game đó vốn đã hỏng.",
                    technical);
        }
        return new CrashDiagnosis(KIND_UNKNOWN, "Game dừng đột ngột",
                "Game ném ra " + shortName(type)
                        + (detail.length() == 0 ? "" : ": " + detail) + ".",
                "Chơi lại từ chỗ đã lưu. Nếu vẫn hỏng, gửi báo lỗi để xem chi tiết.",
                technical);
    }

    /**
     * Lớp không tìm thấy — hai chuyện rất khác nhau đội chung một cái tên.
     *
     * <p>Nếu lớp thiếu thuộc về thư viện của điện thoại thì là máy ảo chưa
     * làm phần đó, và người chơi không sửa được. Nếu nó là lớp của chính
     * game thì tệp game thiếu mất một mẩu, và tải lại là xong.</p>
     */
    private static CrashDiagnosis missingClass(String missing, String technical) {
        String internal = missing.replace('.', '/');
        String friendly = Compatibility.describe(internal);
        if (friendly.length() > 0) {
            return new CrashDiagnosis(KIND_MISSING_API, "Bản này chưa có phần đó",
                    "Game cần " + friendly + ", mà máy ảo chưa làm phần này.",
                    "Không có cách nào bật lên được — game sẽ chạy tiếp khi phần đó "
                            + "được thêm vào. Trong thư viện, thẻ của game có ghi sẵn "
                            + "điều này trước khi bấm chơi.",
                    technical);
        }
        if (internal.startsWith("java/") || internal.startsWith("javax/")
                || internal.startsWith("com/nokia/") || internal.startsWith("com/siemens/")
                || internal.startsWith("com/samsung/") || internal.startsWith("com/motorola/")) {
            return new CrashDiagnosis(KIND_MISSING_API, "Bản này chưa có phần đó",
                    "Game gọi lớp " + missing.replace('/', '.')
                            + " của thư viện điện thoại, mà máy ảo chưa có lớp đó.",
                    "Không có cách nào bật lên được. Gửi báo lỗi kèm tên game để phần "
                            + "này được thêm vào.",
                    technical);
        }
        return new CrashDiagnosis(KIND_MISSING_CLASS, "Tệp game thiếu một phần",
                "Game gọi lớp " + missing.replace('/', '.')
                        + " của chính nó, mà lớp đó không có trong tệp đã cài.",
                "Tệp game tải về dở dang hoặc đã bị cắt bớt. Tải lại tệp gốc rồi cài "
                        + "đè lên — phần đã lưu vẫn còn.",
                technical);
    }

    private static boolean isGameBug(String type) {
        return type.equals("java.lang.NullPointerException")
                || type.equals("java.lang.ArrayIndexOutOfBoundsException")
                || type.equals("java.lang.IndexOutOfBoundsException")
                || type.equals("java.lang.StringIndexOutOfBoundsException")
                || type.equals("java.lang.ArithmeticException")
                || type.equals("java.lang.ClassCastException")
                || type.equals("java.lang.NegativeArraySizeException")
                || type.equals("java.lang.IllegalArgumentException")
                || type.equals("java.lang.IllegalStateException")
                || type.equals("java.lang.NumberFormatException")
                || type.equals("java.lang.StackOverflowError");
    }

    /** Cùng một câu, nói theo kiểu người chơi hiểu được. */
    private static String bugPhrase(String type) {
        if (type.equals("java.lang.NullPointerException")) {
            return "dùng một thứ chưa được tạo ra";
        }
        if (type.endsWith("IndexOutOfBoundsException")) {
            return "với tới một ô nằm ngoài bảng của nó";
        }
        if (type.equals("java.lang.ArithmeticException")) {
            return "chia cho không";
        }
        if (type.equals("java.lang.ClassCastException")) {
            return "nhầm kiểu của một thứ";
        }
        if (type.equals("java.lang.NegativeArraySizeException")) {
            return "xin một bảng có kích thước âm";
        }
        if (type.equals("java.lang.StackOverflowError")) {
            return "gọi vòng quanh không dứt";
        }
        if (type.equals("java.lang.NumberFormatException")) {
            return "đọc một con số không phải là số";
        }
        return "làm một việc không hợp lệ (" + shortName(type) + ")";
    }

    private static String shortName(String type) {
        int cut = type.lastIndexOf('.');
        return cut < 0 ? type : type.substring(cut + 1);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("kind", Integer.valueOf(kind));
        json.put("title", title);
        json.put("reason", reason);
        json.put("advice", advice);
        json.put("technical", technical);
        json.put("blamesGame", Boolean.valueOf(blamesGame()));
        return json;
    }
}
