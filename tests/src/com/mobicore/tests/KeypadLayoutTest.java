package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.List;
import java.util.Map;

/**
 * Bộ bàn phím: sắp một lần, dùng cho mọi game.
 *
 * <p>Kéo từng phím về đúng chỗ ngón tay mình là việc mất công, và tay người
 * chơi thì không đổi từ game này sang game khác. Trước đây thứ sắp được nằm
 * trong hồ sơ của <em>một</em> game, nên game thứ hai lại phải sắp lại từ
 * đầu — đó mới là chỗ đáng sửa, không phải chuyện có thêm một danh sách.</p>
 */
public final class KeypadLayoutTest extends Test {

    private final String fixtureDir;

    public KeypadLayoutTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Bộ bàn phím dùng lại được";
    }

    @Override
    public void run() throws Exception {
        MemoryVfs disk = new MemoryVfs();
        MobiCoreFacade facade = new MobiCoreFacade(disk);
        facade.open("/data");
        String first = install(facade, SampleSuite.jad());
        String second = install(facade, otherDescriptor());
        check(!first.equals(second), "hai game khác nhau để thử mang bộ qua lại");

        // Có sẵn vài bộ: mở máy lần đầu mà danh sách rỗng thì không ai biết
        // "bộ bàn phím" nghĩa là gì.
        Map<String, Object> table = Json.readObject(facade.keypadLayoutsJson(first));
        List<Object> layouts = Json.array(table, "layouts");
        check(layouts.size() >= 3, "có sẵn vài bộ để bắt đầu, được " + layouts.size());
        eq("mac-dinh", Json.string(table, "current", ""),
                "game mới thì đang dùng bộ mặc định");
        check(Json.bool(row(layouts, "mot-tay"), "builtIn", false), "bộ có sẵn được đánh dấu");

        // Đặt một bộ có sẵn lên game: chỉ bàn phím đổi. Vặn âm lượng trước
        // để có một thứ ngoài bàn phím mà canh chừng.
        Map<String, Object> tweak = Json.readObject(facade.profileJson(first));
        tweak.put("volume", Integer.valueOf(42));
        facade.updateProfile(Json.write(tweak));
        Map<String, Object> applied = Json.readObject(
                facade.applyKeypadLayout(first, "mot-tay"));
        check(Json.bool(applied, "ok", false),
                "đặt được bộ có sẵn: " + Json.string(applied, "error", ""));
        Map<String, Object> profile = Json.readObject(facade.profileJson(first));
        eq(GameProfile.KEYPAD_GAME, Json.integer(profile, "keypadLayout", -1),
                "bàn phím đổi theo bộ");
        eq(115, Json.integer(Json.child(profile, "keypadArrangement"), "scale", 0),
                "kể cả cỡ phím");
        eq(42, Json.integer(profile, "volume", 0),
                "còn âm lượng của game thì không đụng tới — bộ này là bàn phím, không phải hồ sơ");

        // Tự sắp rồi cất thành bộ của mình.
        facade.moveKey(first, "fire", -300, 250);
        facade.setKeyScale(first, 140);
        Map<String, Object> saved = Json.readObject(
                facade.saveKeypadLayout(first, "Tay tôi"));
        check(Json.bool(saved, "ok", false), "cất được bộ của mình");

        // Và mang sang game thứ hai — đây là toàn bộ lý do có tính năng này.
        check(Json.bool(Json.readObject(facade.applyKeypadLayout(second, "tay-toi")), "ok", false),
                "mang được sang game khác");
        Map<String, Object> other = Json.readObject(facade.profileJson(second));
        eq(140, Json.integer(Json.child(other, "keypadArrangement"), "scale", 0),
                "game thứ hai nhận đúng cỡ phím đã sắp");
        Map<String, Object> moved = Json.child(Json.child(other, "keypadArrangement"), "keys");
        check(moved != null && moved.containsKey("fire"),
                "và nhận đúng những phím đã kéo");

        eq("tay-toi", Json.string(Json.readObject(facade.keypadLayoutsJson(second)), "current", ""),
                "màn hình biết game này đang dùng bộ nào");

        // Lưu lại cùng tên thì đè lên, chứ không sinh ra hai dòng giống nhau.
        facade.setKeyScale(first, 90);
        facade.saveKeypadLayout(first, "Tay tôi");
        int count = Json.array(Json.readObject(facade.keypadLayoutsJson(first)), "layouts").size();
        facade.saveKeypadLayout(first, "Tay tôi");
        eq(count, Json.array(Json.readObject(facade.keypadLayoutsJson(first)), "layouts").size(),
                "lưu lại cùng tên thì đè lên bộ cũ");

        // Bộ có sẵn không xoá được: xoá xong thì không ai dựng lại được nó.
        check(!Json.bool(Json.readObject(facade.deleteKeypadLayout("mac-dinh")), "ok", true),
                "bộ có sẵn không xoá được");
        check(Json.bool(Json.readObject(facade.deleteKeypadLayout("tay-toi")), "ok", false),
                "còn bộ tự sắp thì xoá được");
        check(!Json.bool(Json.readObject(facade.applyKeypadLayout(second, "tay-toi")), "ok", true),
                "xoá rồi thì không đặt lại được");

        // Bộ nằm chung cho cả máy, nên nó sống qua một lần tắt ứng dụng.
        facade.saveKeypadLayout(first, "Ngón cái trái");
        MobiCoreFacade reopened = new MobiCoreFacade(disk);
        reopened.open("/data");
        check(row(Json.array(Json.readObject(reopened.keypadLayoutsJson(first)), "layouts"),
                        "ngon-cai-trai") != null,
                "mở lại ứng dụng thì bộ đã cất vẫn còn");

        // Tên rỗng thì không thành một bộ.
        check(!Json.bool(Json.readObject(facade.saveKeypadLayout(first, "   ")), "ok", true),
                "bộ bàn phím cần một cái tên");
    }

    private String install(MobiCoreFacade facade, byte[] descriptor) throws Exception {
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), descriptor));
        return Json.string(Json.child(imported, "game"), "suiteId", "");
    }

    private Map<String, Object> row(List<Object> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = (Map<String, Object>) rows.get(i);
            if (id.equals(Json.string(row, "id", ""))) {
                return row;
            }
        }
        return null;
    }

    /** Cùng một tệp .jar, cài dưới một cái tên khác thành game thứ hai. */
    private byte[] otherDescriptor() {
        return SampleSuite.utf8("MIDlet-Name: Tile Rush\n"
                + "MIDlet-Version: 1.0.4\n"
                + "MIDlet-Vendor: MobiCore Samples\n"
                + "MIDlet-Jar-URL: SkyRunner.jar\n"
                + "MIDlet-1: Tile Rush,,demo.SkyRunner\n");
    }
}
