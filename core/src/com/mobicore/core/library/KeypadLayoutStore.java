package com.mobicore.core.library;

import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.KeypadArrangement;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.util.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Những bộ bàn phím đã sắp, dùng lại được cho game khác.
 *
 * <p>Kéo từng phím về đúng chỗ ngón tay mình là việc mất công, và tay người
 * chơi thì không đổi từ game này sang game khác: sắp một lần rồi dùng lại là
 * đúng cái người ta muốn. Trước đây thứ sắp được nằm trong hồ sơ của <em>một</em>
 * game, nên game thứ hai lại phải sắp lại từ đầu.</p>
 *
 * <p>Bộ bàn phím vì thế nằm chung cho cả máy chứ không nằm theo game, và chỉ
 * mang những gì thuộc về bàn phím: kiểu phím nào hiện ra, hình phím, độ mờ,
 * bao lâu thì mờ đi, và vị trí từng phím. Không mang theo cỡ màn hình hay âm
 * lượng — đó là chuyện của game, không phải của bàn tay.</p>
 */
public final class KeypadLayoutStore {

    /** Một bộ bàn phím. */
    public static final class Layout {

        private final String id;
        private final String name;
        private final boolean builtIn;
        private final Map<String, Object> settings;

        Layout(String id, String name, boolean builtIn, Map<String, Object> settings) {
            this.id = id;
            this.name = name;
            this.builtIn = builtIn;
            this.settings = settings;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        /** True với những bộ có sẵn: dùng được, sửa được, nhưng không xoá được. */
        public boolean isBuiltIn() {
            return builtIn;
        }

        Map<String, Object> settings() {
            return settings;
        }

        public Map<String, Object> toJson() {
            Map<String, Object> json = Json.object();
            json.put("id", id);
            json.put("name", name);
            json.put("builtIn", Boolean.valueOf(builtIn));
            json.putAll(settings);
            return json;
        }
    }

    private final Vfs vfs;
    private final StorageLayout layout;

    public KeypadLayoutStore(Vfs vfs, StorageLayout layout) {
        this.vfs = vfs;
        this.layout = layout;
    }

    private String path() {
        return StorageLayout.join(layout.dir(StorageLayout.PRESETS), "keypads.json");
    }

    /**
     * Mọi bộ bàn phím: có sẵn trước, tự sắp sau.
     *
     * <p>Bộ có sẵn không phải để trang trí: mở máy lần đầu mà danh sách rỗng
     * thì không ai biết một "bộ bàn phím" là cái gì.</p>
     */
    public List<Layout> all() throws IOException {
        List<Layout> layouts = new ArrayList<Layout>(builtIn());
        if (!vfs.exists(path())) {
            return layouts;
        }
        Map<String, Object> json = Json.readObject(new String(vfs.read(path()), "UTF-8"));
        List<Object> rows = Json.array(json, "layouts");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = (Map<String, Object>) rows.get(i);
            String name = Json.string(row, "name", "");
            if (name.length() == 0) {
                continue;
            }
            Map<String, Object> settings = new LinkedHashMap<String, Object>(row);
            settings.remove("id");
            settings.remove("name");
            settings.remove("builtIn");
            layouts.add(new Layout(Json.string(row, "id", Text.slug(Text.searchKey(name))),
                    name, false, settings));
        }
        return layouts;
    }

    public Layout find(String id) throws IOException {
        List<Layout> layouts = all();
        for (int i = 0; i < layouts.size(); i++) {
            if (layouts.get(i).id().equals(id)) {
                return layouts.get(i);
            }
        }
        return null;
    }

    /**
     * Cất bàn phím của một game thành một bộ có tên.
     *
     * <p>Trùng tên thì đè lên: người ta lưu lại cùng một tên là vì bộ cũ đã
     * không còn đúng, chứ không phải để có hai dòng giống nhau.</p>
     */
    public Layout save(String name, GameProfile profile) throws IOException {
        String label = name == null ? "" : name.trim();
        if (label.length() == 0) {
            throw new IOException("Bộ bàn phím cần một cái tên");
        }
        // Bỏ dấu trước khi rút gọn: slug() coi chữ có dấu là dấu ngăn, nên
        // "Tay tôi" sẽ thành "tay-t-i" — một cái tên không ai gõ lại được.
        String id = Text.slug(Text.searchKey(label));
        if (id.length() == 0) {
            id = "bo-" + Math.abs(label.hashCode());
        }
        List<Object> kept = new ArrayList<Object>();
        for (Object row : storedRows()) {
            Map<String, Object> stored = (Map<String, Object>) row;
            if (!id.equals(Json.string(stored, "id", ""))) {
                kept.add(stored);
            }
        }
        Map<String, Object> row = Json.object();
        row.put("id", id);
        row.put("name", label);
        row.putAll(capture(profile));
        kept.add(row);
        write(kept);
        return new Layout(id, label, false, capture(profile));
    }

    /** Xoá một bộ tự sắp; bộ có sẵn thì không xoá được. */
    public boolean delete(String id) throws IOException {
        List<Object> kept = new ArrayList<Object>();
        boolean removed = false;
        for (Object row : storedRows()) {
            Map<String, Object> stored = (Map<String, Object>) row;
            if (id.equals(Json.string(stored, "id", ""))) {
                removed = true;
            } else {
                kept.add(stored);
            }
        }
        if (removed) {
            write(kept);
        }
        return removed;
    }

    /**
     * Đặt một bộ bàn phím lên một game.
     *
     * <p>Chỉ đụng vào bàn phím: game giữ nguyên cỡ màn hình, âm lượng, phần
     * lưu và mọi thứ khác của nó.</p>
     */
    public boolean apply(Layout chosen, GameProfile profile) {
        if (chosen == null) {
            return false;
        }
        Map<String, Object> settings = chosen.settings();
        // Con số này đã đổi nghĩa hai lần, nên phải biết nó viết từ đời nào
        // mới đọc được — đọc thẳng là đổi bàn phím của người ta sau lưng họ.
        int version = Json.integer(settings, "keypadVersion",
                settings.containsKey("keypadHidden") ? 1 : 0);
        int was = Json.integer(settings, "keypadLayout", 0);
        if (version >= GameProfile.KEYPAD_VERSION) {
            profile.setKeypadLayout(was);
            profile.setKeypadHidden(Json.bool(settings, "keypadHidden", false));
        } else if (version == 1) {
            profile.setKeypadHidden(Json.bool(settings, "keypadHidden", false));
            profile.setKeypadLayout(was == 2 ? GameProfile.KEYPAD_GAME : GameProfile.KEYPAD_FULL);
        } else {
            profile.setKeypadHidden(was == 3);
            profile.setKeypadLayout(GameProfile.KEYPAD_FULL);
        }
        profile.setKeypadShape(Json.integer(settings, "keypadShape", profile.keypadShape()));
        profile.setKeypadOpacity(Json.integer(settings, "keypadOpacity", profile.keypadOpacity()));
        profile.setKeypadFadeDelay(
                Json.integer(settings, "keypadFadeDelay", profile.keypadFadeDelay()));
        Map<String, Object> arrangement = Json.child(settings, "keypadArrangement");
        if (arrangement != null) {
            profile.setKeypadArrangement(KeypadArrangement.fromJson(arrangement));
        }
        return true;
    }

    /** True khi bàn phím của game này khớp với bộ ấy. */
    public boolean matches(Layout chosen, GameProfile profile) {
        if (chosen == null) {
            return false;
        }
        return Json.write(chosen.settings()).equals(Json.write(capture(profile)));
    }

    /** Phần bàn phím của một hồ sơ, và chỉ phần ấy. */
    public static Map<String, Object> capture(GameProfile profile) {
        Map<String, Object> settings = Json.object();
        settings.put("keypadLayout", Integer.valueOf(profile.keypadLayout()));
        settings.put("keypadHidden", Boolean.valueOf(profile.keypadHidden()));
        settings.put("keypadVersion", Integer.valueOf(GameProfile.KEYPAD_VERSION));
        settings.put("keypadShape", Integer.valueOf(profile.keypadShape()));
        settings.put("keypadOpacity", Integer.valueOf(profile.keypadOpacity()));
        settings.put("keypadFadeDelay", Integer.valueOf(profile.keypadFadeDelay()));
        settings.put("keypadArrangement", profile.keypadArrangement().toJson());
        return settings;
    }

    // ------------------------------------------------------------ có sẵn

    /**
     * Ba bộ có sẵn, mỗi bộ giải một chuyện có thật.
     *
     * <p>Không phải ba biến thể cho vui: một bộ để bàn phím đứng yên như cũ,
     * một bộ cho người cầm máy một tay, một bộ cho người muốn nhìn thấy game
     * nhiều hơn nhìn thấy phím.</p>
     */
    private List<Layout> builtIn() {
        List<Layout> layouts = new ArrayList<Layout>();
        layouts.add(new Layout("mac-dinh", "Mặc định", true,
                arrangementOf(new KeypadArrangement(), GameProfile.KEYPAD_FULL,
                        GameProfile.KEY_SHAPE_ROUNDED, 100, 0)));

        // Một tay: cả bàn phím dồn về phía ngón cái phải, và to lên một chút
        // vì ngón cái với xa thì kém chính xác.
        KeypadArrangement oneHand = new KeypadArrangement();
        oneHand.setScale(115);
        String[] keys = {"up", "down", "left", "right", "fire",
                "num1", "num3", "num7", "num9"};
        for (int i = 0; i < keys.length; i++) {
            oneHand.move(keys[i], 0.35f, 0.15f);
        }
        layouts.add(new Layout("mot-tay", "Cầm một tay", true,
                arrangementOf(oneHand, GameProfile.KEYPAD_GAME,
                        GameProfile.KEY_SHAPE_ROUND, 100, 0)));

        // Nhìn game là chính: phím mờ đi khi không dùng, và nhỏ lại.
        KeypadArrangement quiet = new KeypadArrangement();
        quiet.setScale(85);
        layouts.add(new Layout("nhe-nhang", "Nhẹ nhàng", true,
                arrangementOf(quiet, GameProfile.KEYPAD_FULL,
                        GameProfile.KEY_SHAPE_ROUNDED, 45, 3)));
        return layouts;
    }

    private Map<String, Object> arrangementOf(KeypadArrangement arrangement, int kind, int shape,
                                              int opacity, int fadeDelay) {
        Map<String, Object> settings = Json.object();
        settings.put("keypadLayout", Integer.valueOf(kind));
        settings.put("keypadHidden", Boolean.FALSE);
        settings.put("keypadVersion", Integer.valueOf(GameProfile.KEYPAD_VERSION));
        settings.put("keypadShape", Integer.valueOf(shape));
        settings.put("keypadOpacity", Integer.valueOf(opacity));
        settings.put("keypadFadeDelay", Integer.valueOf(fadeDelay));
        settings.put("keypadArrangement", arrangement.toJson());
        return settings;
    }

    // ------------------------------------------------------------- lưu, đọc

    private List<Object> storedRows() throws IOException {
        if (!vfs.exists(path())) {
            return new ArrayList<Object>();
        }
        Map<String, Object> json = Json.readObject(new String(vfs.read(path()), "UTF-8"));
        return new ArrayList<Object>(Json.array(json, "layouts"));
    }

    private void write(List<Object> rows) throws IOException {
        Map<String, Object> json = Json.object();
        json.put("layouts", rows);
        vfs.mkdirs(layout.dir(StorageLayout.PRESETS));
        vfs.write(path(), Json.write(json).getBytes("UTF-8"));
    }

    /** Cả danh sách, cho màn hình đọc. */
    public static List<Object> toJson(List<Layout> layouts) {
        List<Object> rows = new ArrayList<Object>();
        for (int i = 0; i < layouts.size(); i++) {
            rows.add(layouts.get(i).toJson());
        }
        return rows;
    }
}
