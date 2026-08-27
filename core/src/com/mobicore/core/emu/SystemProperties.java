package com.mobicore.core.emu;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Những gì game đọc được khi nó hỏi máy nó đang chạy trên đó là máy gì.
 *
 * <p>Game J2ME hỏi {@code System.getProperty("microedition.platform")} rồi
 * <em>đổi cách chạy</em> theo câu trả lời: chọn bộ ảnh, bật đường vẽ riêng của
 * Nokia, đổi mã phím. Câu trả lời cũ là {@code "MobiCore"} — một cái tên chưa
 * game nào từng nghe, nên game rơi vào đúng nhánh dành cho máy lạ: nhánh ít
 * được thử nhất và hỏng nhiều nhất.</p>
 *
 * <p>Nay câu trả lời là một chiếc Nokia 6233 — cùng con máy
 * <a href="https://github.com/nikita36078/J2ME-Loader/blob/master/app/src/main/assets/defaults/system.props">J2ME
 * Loader khai</a>, và cùng lý do: nhánh Nokia là nhánh được nhiều game chăm
 * chút nhất, còn phần Nokia thì máy ảo này có làm thật (FullCanvas,
 * DirectGraphics, DeviceControl).</p>
 *
 * <p><b>Một câu trả lời, cho mọi game.</b> Không có danh sách máy để chọn và
 * không có bản nào khác: máy ảo này là một cỗ máy duy nhất, một cỡ màn hình
 * 240×320 và một kiểu bàn phím. Một cái tủ chọn máy chỉ đẩy sang người chơi
 * một câu hỏi họ không có cách nào trả lời đúng, và mỗi câu trả lời sai lại
 * là một cỗ máy nữa phải chịu trách nhiệm.</p>
 *
 * <p>Chỉ khai những thứ <em>thật sự có</em>. Một cái máy khai
 * {@code microedition.m3g.version} rồi để game gọi vào 3D là một cái máy nói
 * dối: game không chết ở câu hỏi, nó chết ở câu gọi ngay sau đó, và lúc ấy
 * chẳng ai lần ra vì sao.</p>
 */
public final class SystemProperties {

    /** Tên chiếc máy, đúng chuỗi game đọc được. */
    public static final String PLATFORM = "Nokia6233/05.10";

    private static final String[][] TABLE = {
            {"microedition.platform", PLATFORM},
            {"microedition.configuration", "CLDC-1.1"},
            {"microedition.profiles", "MIDP-2.0"},
            // Cùng bảng mã J2ME Loader khai: game đời ấy đọc chuỗi theo từng
            // byte, và UTF-8 làm lệch chữ có dấu của chính nó.
            {"microedition.encoding", "ISO-8859-1"},
            {"microedition.locale", "vi-VN"},
            {"microedition.io.file.FileConnection.version", "1.0"},
            {"microedition.media.version", "1.0"},
            // Máy ảo vẽ được DirectGraphics, và 565 là cỡ màu máy đời ấy.
            {"com.nokia.mid.ui.DirectGraphics.PIXEL_FORMAT", "565"},
    };

    private SystemProperties() {
    }

    /**
     * Câu trả lời cho một câu {@code System.getProperty}, hoặc null.
     *
     * <p>Null là một câu trả lời đúng, không phải một chỗ thiếu: đó là cách
     * một chiếc máy không có phần đó trả lời, và là cách game dò tìm trước
     * khi dùng.</p>
     */
    public static String value(String name) {
        if (name == null) {
            return null;
        }
        for (int i = 0; i < TABLE.length; i++) {
            if (TABLE[i][0].equals(name)) {
                return TABLE[i][1];
            }
        }
        return null;
    }

    /** Cả bảng, để màn hình thông tin bày ra đúng thứ game đọc được. */
    public static List<Object> toJson() {
        List<Object> rows = new ArrayList<Object>();
        for (int i = 0; i < TABLE.length; i++) {
            Map<String, Object> row = Json.object();
            row.put("name", TABLE[i][0]);
            row.put("value", TABLE[i][1]);
            rows.add(row);
        }
        return rows;
    }
}
