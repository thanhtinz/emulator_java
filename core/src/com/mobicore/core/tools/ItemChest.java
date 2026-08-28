package com.mobicore.core.tools;

import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.util.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Những vật phẩm người chơi đã tìm ra, đặt tên và giữ lại.
 *
 * <p>Tìm một con số trong phần lưu là việc mất công: mở game, nhìn số, gõ vào,
 * chơi tiếp cho số đổi, gõ lại. Làm xong một lần cho số vàng rồi lần sau lại
 * làm y hệt cho số thuốc hồi máu, rồi lần sau nữa lại làm lại từ đầu vì đã
 * quên mất chỗ — thì công cụ này chỉ dùng được một lần.</p>
 *
 * <p>Nên chỗ đã tìm ra được <b>đặt tên và cất đi</b>: "Vàng", "Thuốc hồi máu",
 * "Ngọc". Từ lần sau chỉ còn gõ số lượng rồi bấm gửi. Danh sách nằm cạnh hồ sơ
 * của game, nên nó sống qua những lần tắt máy — cái đáng giá ở đây không phải
 * con số, mà là <em>biết con số ấy nằm ở đâu</em>.</p>
 */
public final class ItemChest {

    /** Một vật phẩm đã biết chỗ. */
    public static final class Item {

        private final String id;
        private String name;
        private final List<SaveScanner.Hit> places;
        private long amount;

        Item(String id, String name, List<SaveScanner.Hit> places, long amount) {
            this.id = id;
            this.name = name;
            this.places = places;
            this.amount = amount;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name.trim();
        }

        /** Những chỗ trong phần lưu đang giữ số lượng của nó. */
        public List<SaveScanner.Hit> places() {
            return places;
        }

        /** Số lượng lần đọc gần nhất. */
        public long amount() {
            return amount;
        }

        void setAmount(long amount) {
            this.amount = amount;
        }

        /** Số lớn nhất còn nhét vừa mọi chỗ của nó. */
        public long ceiling() {
            long limit = SaveScanner.MAX_VALUE;
            for (int i = 0; i < places.size(); i++) {
                int encoding = places.get(i).encoding();
                long room = encoding == SaveScanner.AS_INT8 ? 255
                        : (encoding == SaveScanner.AS_INT16_BE || encoding == SaveScanner.AS_INT16_LE
                                ? 65535 : SaveScanner.MAX_VALUE);
                if (room < limit) {
                    limit = room;
                }
            }
            return limit;
        }

        public Map<String, Object> toJson() {
            Map<String, Object> json = Json.object();
            json.put("id", id);
            json.put("name", name);
            json.put("amount", Long.valueOf(amount));
            json.put("places", Integer.valueOf(places.size()));
            json.put("ceiling", Long.valueOf(ceiling()));
            json.put("where", SaveScanner.toJson(places));
            return json;
        }
    }

    private final Vfs vfs;
    private final String path;
    private final List<Item> items = new ArrayList<Item>();

    public ItemChest(Vfs vfs, StorageLayout layout, String suiteId) throws IOException {
        this.vfs = vfs;
        this.path = StorageLayout.join(layout.gameDir(suiteId), "items.json");
        load();
    }

    /** Mọi vật phẩm đã cất, đọc lại số lượng đang có trong phần lưu. */
    public List<Item> all(RecordStoreManager records) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            List<SaveScanner.Hit> places = item.places();
            if (!places.isEmpty()) {
                item.setAmount(SaveScanner.read(records, places.get(0), item.amount()));
            }
        }
        return new ArrayList<Item>(items);
    }

    /**
     * Lọc theo tên — ô tìm kiếm của bảng vật phẩm.
     *
     * <p>Bỏ dấu trước khi so: người ta gõ "vang" chứ ít khi gõ "vàng".</p>
     */
    public List<Item> search(RecordStoreManager records, String query) throws IOException {
        List<Item> all = all(records);
        String needle = Text.searchKey(query);
        if (needle.length() == 0) {
            return all;
        }
        List<Item> found = new ArrayList<Item>();
        for (int i = 0; i < all.size(); i++) {
            if (Text.searchKey(all.get(i).name()).indexOf(needle) >= 0) {
                found.add(all.get(i));
            }
        }
        return found;
    }

    public Item find(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return items.get(i);
            }
        }
        return null;
    }

    /**
     * Cất một chỗ vừa tìm được dưới một cái tên.
     *
     * <p>Tên trùng thì ghi đè lên vật phẩm cũ: người ta tìm lại một thứ là vì
     * chỗ cũ đã sai, chứ không phải để có hai dòng cùng tên.</p>
     */
    public Item keep(String name, List<SaveScanner.Hit> places, long amount) throws IOException {
        String label = name == null || name.trim().length() == 0 ? "Chưa đặt tên" : name.trim();
        Item existing = byName(label);
        if (existing != null) {
            items.remove(existing);
        }
        // Một ô bốn byte cũng khớp khi đọc hai byte cuối của nó: giữ cả hai
        // là ghi đè lên chính mình lúc gửi số mới.
        Item item = new Item(nextId(), label,
                new ArrayList<SaveScanner.Hit>(SaveScanner.widest(places)), amount);
        items.add(item);
        save();
        return item;
    }

    public boolean rename(String id, String name) throws IOException {
        Item item = find(id);
        if (item == null) {
            return false;
        }
        item.setName(name);
        save();
        return true;
    }

    public boolean forget(String id) throws IOException {
        Item item = find(id);
        if (item == null) {
            return false;
        }
        items.remove(item);
        save();
        return true;
    }

    /**
     * Gửi một số lượng mới vào game.
     *
     * @return số chỗ đã ghi được; 0 khi con số không vừa chỗ nào
     */
    public int send(RecordStoreManager records, Item item, long amount, long timestamp)
            throws IOException {
        int written = 0;
        List<SaveScanner.Hit> places = item.places();
        for (int i = 0; i < places.size(); i++) {
            SaveScanner.Hit place = places.get(i);
            if (!SaveScanner.fits(amount, place.encoding())) {
                continue;
            }
            if (SaveScanner.write(records, place, amount, timestamp)) {
                written++;
            }
        }
        if (written > 0) {
            item.setAmount(amount);
            save();
        }
        return written;
    }

    // ------------------------------------------------------------ lưu, đọc

    private Item byName(String name) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).name().equalsIgnoreCase(name)) {
                return items.get(i);
            }
        }
        return null;
    }

    private String nextId() {
        long highest = 0;
        for (int i = 0; i < items.size(); i++) {
            try {
                long value = Long.parseLong(items.get(i).id());
                if (value > highest) {
                    highest = value;
                }
            } catch (NumberFormatException notANumber) {
                // Tên cũ kiểu khác thì bỏ qua, số mới vẫn không đụng nó.
            }
        }
        return String.valueOf(highest + 1);
    }

    private void load() throws IOException {
        items.clear();
        if (!vfs.exists(path)) {
            return;
        }
        Map<String, Object> json = Json.readObject(new String(vfs.read(path), "UTF-8"));
        List<Object> rows = Json.array(json, "items");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = (Map<String, Object>) rows.get(i);
            List<SaveScanner.Hit> places = new ArrayList<SaveScanner.Hit>();
            List<Object> where = Json.array(row, "where");
            for (int p = 0; p < where.size(); p++) {
                Map<String, Object> place = (Map<String, Object>) where.get(p);
                places.add(SaveScanner.hit(
                        Json.string(place, "store", ""),
                        Json.integer(place, "recordId", 0),
                        Json.integer(place, "offset", 0),
                        Json.integer(place, "encoding", SaveScanner.AS_INT32_BE),
                        Json.integer(place, "length", 4),
                        Json.longValue(place, "value", 0L)));
            }
            items.add(new Item(Json.string(row, "id", String.valueOf(i + 1)),
                    Json.string(row, "name", "Chưa đặt tên"), places,
                    Json.longValue(row, "amount", 0L)));
        }
    }

    private void save() throws IOException {
        List<Object> rows = new ArrayList<Object>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Map<String, Object> row = Json.object();
            row.put("id", item.id());
            row.put("name", item.name());
            row.put("amount", Long.valueOf(item.amount()));
            row.put("where", SaveScanner.toJson(item.places()));
            rows.add(row);
        }
        Map<String, Object> json = Json.object();
        json.put("items", rows);
        vfs.write(path, Json.write(json).getBytes("UTF-8"));
    }

    /** Cả tủ, cho màn hình đọc. */
    public static List<Object> toJson(List<Item> items) {
        List<Object> rows = new ArrayList<Object>();
        for (int i = 0; i < items.size(); i++) {
            rows.add(items.get(i).toJson());
        }
        return rows;
    }
}
