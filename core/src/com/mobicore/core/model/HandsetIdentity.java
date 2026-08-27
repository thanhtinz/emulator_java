package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cái máy mà game tưởng nó đang chạy trên đó.
 *
 * <p>Game J2ME hỏi máy nó đang nằm trên máy nào — {@code
 * System.getProperty("microedition.platform")} — rồi <em>đổi cách chạy</em>
 * theo câu trả lời: chọn bộ ảnh đúng cỡ màn hình, bật đường vẽ riêng của
 * Nokia, đổi mã phím, có khi chỉ đơn giản là từ chối chạy. Trả lời
 * "MobiCore" là trả lời một cái tên chưa game nào từng nghe, và game rơi vào
 * đúng nhánh dành cho máy lạ.</p>
 *
 * <p>Mặc định vì thế là tên của một chiếc Nokia 6233 — cùng con máy J2ME
 * Loader khai trong {@code assets/defaults/system.props}, và cùng lý do:
 * nhánh Nokia là nhánh được nhiều game chăm chút nhất, còn phần Nokia thì máy
 * ảo này có làm (FullCanvas, DirectGraphics, DeviceControl).</p>
 *
 * <p><b>Đây chỉ là một chuỗi chữ.</b> Máy ảo vẫn có đúng một loại màn hình
 * (240×320, giai đoạn 32) và đúng một kiểu bàn phím; đổi ở đây không đổi cỡ
 * màn hình, không đổi phím, không đổi gì trong máy — chỉ đổi câu trả lời khi
 * game hỏi. Danh sách bảy cỡ máy bỏ đi hồi ấy bị bỏ vì nó bắt người chơi chọn
 * một thứ họ không có cách nào biết; chỗ này thì ngược lại, nó chỉ có một câu
 * trả lời mặc định đúng cho gần như mọi game, và có mặt cho đúng cái game
 * hiếm hoi đòi nghe tên khác.</p>
 *
 * <p>Chỉ khai những thứ thật sự có. Một cái máy khai {@code
 * microedition.m3g.version} rồi để game gọi vào 3D là một cái máy nói dối,
 * và game chết ở câu gọi tiếp theo chứ không phải ở câu hỏi.</p>
 */
public final class HandsetIdentity {

    /** Một chiếc máy có sẵn để chọn. */
    public static final class Handset {

        private final String id;
        private final String name;
        private final String platform;
        private final String note;

        Handset(String id, String name, String platform, String note) {
            this.id = id;
            this.name = name;
            this.platform = platform;
            this.note = note;
        }

        public String id() {
            return id;
        }

        /** Tên để hiện lên màn hình. */
        public String name() {
            return name;
        }

        /** Đúng chuỗi game đọc được. */
        public String platform() {
            return platform;
        }

        /** Vì sao có thể muốn chọn nó. */
        public String note() {
            return note;
        }
    }

    /**
     * Những máy chọn được.
     *
     * <p>Chuỗi khai báo gồm tên máy và số hiệu bản máy, nhưng game hầu như
     * chỉ đọc phần đầu — "có phải Nokia không", "có phải SonyEricsson
     * không" — nên phần đuôi có đúng đến từng chữ hay không ít khi đổi được
     * điều gì.</p>
     */
    public static final Handset[] CATALOG = {
            new Handset("nokia6233", "Nokia 6233", "Nokia6233/05.10",
                    "Mặc định — nhánh nhiều game chăm nhất"),
            new Handset("nokiaN73", "Nokia N73", "NokiaN73/4.0839.42.2.1",
                    "Cho game chỉ nhận đúng máy Nokia đời sau"),
            new Handset("sonyericssonK750", "Sony Ericsson K750", "SonyEricssonK750/R1",
                    "Cho game có nhánh riêng cho Sony Ericsson"),
            new Handset("samsungE250", "Samsung SGH-E250", "SAMSUNG-SGH-E250/1.0",
                    "Cho game có nhánh riêng cho Samsung"),
            new Handset("siemensS65", "Siemens S65", "Siemens/S65",
                    "Cho game có nhánh riêng cho Siemens"),
            new Handset("generic", "Không nói tên hãng", "j2me",
                    "Khi game chạy sai vì tưởng đang ở trên máy hãng"),
    };

    /** Máy mặc định, và lý do nằm ở phần mô tả lớp. */
    public static final String DEFAULT_ID = "nokia6233";

    /**
     * Những thứ máy ảo này thật sự có, khai cho game biết.
     *
     * <p>Không có ở đây nghĩa là {@code getProperty} trả về null — đúng cách
     * một chiếc máy không có phần đó trả lời, và là cách game dò tìm trước
     * khi dùng.</p>
     */
    private static final String[][] BASE = {
            {"microedition.configuration", "CLDC-1.1"},
            {"microedition.profiles", "MIDP-2.0"},
            // Cùng bảng mã J2ME Loader khai: game đời ấy đọc chuỗi theo
            // từng byte, và UTF-8 làm lệch chữ có dấu của chính nó.
            {"microedition.encoding", "ISO-8859-1"},
            {"microedition.locale", "vi-VN"},
            {"microedition.io.file.FileConnection.version", "1.0"},
            {"microedition.media.version", "1.0"},
            // Máy ảo vẽ được DirectGraphics, và 565 là cỡ màu máy đời ấy.
            {"com.nokia.mid.ui.DirectGraphics.PIXEL_FORMAT", "565"},
    };

    private String handsetId = DEFAULT_ID;
    /** Những gì người dùng tự đặt, đè lên tất cả. */
    private final Map<String, String> custom = new LinkedHashMap<String, String>();

    public String handsetId() {
        return handsetId;
    }

    /** Đổi máy; tên không có trong danh sách thì quay về máy mặc định. */
    public void setHandset(String id) {
        handsetId = find(id) == null ? DEFAULT_ID : id;
    }

    public Handset handset() {
        Handset chosen = find(handsetId);
        return chosen == null ? CATALOG[0] : chosen;
    }

    public static Handset find(String id) {
        for (int i = 0; i < CATALOG.length; i++) {
            if (CATALOG[i].id().equals(id)) {
                return CATALOG[i];
            }
        }
        return null;
    }

    /** Những gì người dùng tự đặt thêm. */
    public Map<String, String> custom() {
        return custom;
    }

    /**
     * Đặt một thuộc tính bằng tay.
     *
     * <p>Có vì danh sách máy không bao giờ đủ: một game duy nhất đòi đúng
     * một chuỗi lạ thì sửa một dòng vẫn hơn là thêm hẳn một chiếc máy vào
     * danh sách cho mọi người cùng nhìn.</p>
     *
     * @param value rỗng hoặc null thì xoá dòng đó đi
     */
    public void set(String name, String value) {
        if (name == null || name.trim().length() == 0) {
            return;
        }
        String key = name.trim();
        if (value == null || value.length() == 0) {
            custom.remove(key);
        } else {
            custom.put(key, value);
        }
    }

    public void clear() {
        custom.clear();
    }

    /** True khi hồ sơ này không còn giống bản mặc định nữa. */
    public boolean isCustom() {
        return !DEFAULT_ID.equals(handsetId) || !custom.isEmpty();
    }

    /**
     * Câu trả lời cho một câu {@code System.getProperty}.
     *
     * <p>Thứ tự: người dùng đặt tay, rồi chiếc máy đang giả, rồi những gì
     * máy ảo thật sự có. Không khớp gì thì null — và null là một câu trả lời
     * đúng, không phải một chỗ thiếu.</p>
     */
    public String value(String name) {
        if (name == null) {
            return null;
        }
        String set = custom.get(name);
        if (set != null) {
            return set;
        }
        if ("microedition.platform".equals(name)) {
            return handset().platform();
        }
        for (int i = 0; i < BASE.length; i++) {
            if (BASE[i][0].equals(name)) {
                return BASE[i][1];
            }
        }
        return null;
    }

    /** Mọi thứ game hỏi được, để màn hình cài đặt bày ra. */
    public Map<String, String> all() {
        Map<String, String> table = new LinkedHashMap<String, String>();
        table.put("microedition.platform", handset().platform());
        for (int i = 0; i < BASE.length; i++) {
            table.put(BASE[i][0], BASE[i][1]);
        }
        table.putAll(custom);
        return table;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("handset", handsetId);
        Map<String, Object> entries = Json.object();
        for (Map.Entry<String, String> entry : custom.entrySet()) {
            entries.put(entry.getKey(), entry.getValue());
        }
        json.put("custom", entries);
        return json;
    }

    public static HandsetIdentity fromJson(Map<String, Object> json) {
        HandsetIdentity identity = new HandsetIdentity();
        if (json == null) {
            return identity;
        }
        identity.setHandset(Json.string(json, "handset", DEFAULT_ID));
        Map<String, Object> entries = Json.child(json, "custom");
        if (entries != null) {
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                identity.set(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return identity;
    }

    /** Danh sách máy, cho màn hình chọn. */
    public static List<Object> catalogJson(String chosen) {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < CATALOG.length; i++) {
            Map<String, Object> entry = Json.object();
            entry.put("id", CATALOG[i].id());
            entry.put("name", CATALOG[i].name());
            entry.put("platform", CATALOG[i].platform());
            entry.put("note", CATALOG[i].note());
            entry.put("chosen", Boolean.valueOf(CATALOG[i].id().equals(chosen)));
            list.add(entry);
        }
        return list;
    }
}
