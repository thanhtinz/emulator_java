package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.HandsetIdentity;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.tools.SampleSuite;

import java.util.List;
import java.util.Map;

/**
 * Giả làm máy khác.
 *
 * <p>Game J2ME hỏi nó đang chạy trên máy nào rồi đổi cách chạy theo câu trả
 * lời: bộ ảnh nào, nhánh vẽ nào, mã phím nào. Câu trả lời cũ — "MobiCore" —
 * là một cái tên chưa game nào từng nghe, nên game rơi vào nhánh dành cho máy
 * lạ. Bài kiểm tra này giữ cho câu trả lời ấy là một chiếc điện thoại thật, và
 * giữ cho nó chỉ khai những thứ máy ảo thật sự có.</p>
 */
public final class HandsetTest extends Test {

    private final String fixtureDir;

    public HandsetTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Giả làm máy khác";
    }

    @Override
    public void run() throws Exception {
        theTable();
        whatTheGameReads();
        throughTheBridge();
    }

    // ---------------------------------------------------------------- bảng

    private void theTable() {
        HandsetIdentity identity = new HandsetIdentity();
        eq("Nokia6233/05.10", identity.value("microedition.platform"),
                "mặc định là một chiếc máy thật, không phải tên của máy ảo");
        check(!identity.isCustom(), "và đó là bản chưa ai sửa");

        // Chỉ khai những thứ thật sự có. Một chiếc máy khai có 3D rồi để game
        // gọi vào 3D là một chiếc máy nói dối, và game chết ở câu gọi sau.
        eq(null, identity.value("microedition.m3g.version"),
                "không khai 3D, vì máy ảo không có 3D");
        eq(null, identity.value("microedition.pim.version"), "cũng không khai danh bạ");
        eq("1.0", identity.value("microedition.io.file.FileConnection.version"),
                "nhưng khai phần tệp, vì phần tệp thì có thật");
        eq("CLDC-1.1", identity.value("microedition.configuration"), "và khai đúng đời máy ảo");
        eq(null, identity.value("chuyện.trời.ơi"), "hỏi cái không có thì trả về không có gì");

        identity.setHandset("sonyericssonK750");
        eq("SonyEricssonK750/R1", identity.value("microedition.platform"), "đổi máy thì đổi câu trả lời");
        check(identity.isCustom(), "và hồ sơ không còn là bản mặc định");
        identity.setHandset("máy-không-có-thật");
        eq(HandsetIdentity.DEFAULT_ID, identity.handsetId(),
                "chọn một cái máy không có trong danh sách thì quay về máy mặc định");

        // Danh sách máy không bao giờ đủ: một game duy nhất đòi đúng một
        // chuỗi lạ thì sửa một dòng vẫn hơn thêm hẳn một chiếc máy.
        identity.set("microedition.platform", "MotoV3/08.BD");
        eq("MotoV3/08.BD", identity.value("microedition.platform"), "sửa tay thì lời sửa thắng");
        identity.set("microedition.platform", "");
        eq("Nokia6233/05.10", identity.value("microedition.platform"),
                "xoá lời sửa thì quay về máy đang giả, chứ không thành trống");
        identity.set("   ", "gì đó");
        eq(0, identity.custom().size(), "một cái tên rỗng thì không phải một dòng");

        identity.setHandset("nokiaN73");
        identity.set("com.nokia.mid.ui.version", "1.4");
        HandsetIdentity restored = HandsetIdentity.fromJson(
                Json.readObject(Json.write(identity.toJson())));
        eq("nokiaN73", restored.handsetId(), "máy đã chọn sống qua một lần tắt máy");
        eq("1.4", restored.value("com.nokia.mid.ui.version"), "cùng với dòng đã sửa tay");

        // Bảng bày ra màn hình phải là đúng những gì game đọc được.
        Map<String, String> all = restored.all();
        eq(restored.value("microedition.platform"), all.get("microedition.platform"),
                "bảng bày ra khớp với câu game hỏi được");
        check(all.containsKey("com.nokia.mid.ui.version"), "kể cả dòng sửa tay");

        List<Object> catalog = HandsetIdentity.catalogJson("nokiaN73");
        check(catalog.size() > 1, "có nhiều máy để chọn");
        boolean marked = false;
        for (Object entry : catalog) {
            if (Json.bool((Map<String, Object>) entry, "chosen", false)) {
                marked = true;
                eq("nokiaN73", Json.string((Map<String, Object>) entry, "id", ""),
                        "và máy đang chọn được đánh dấu");
            }
        }
        check(marked, "đúng một cái được đánh dấu");
    }

    // ------------------------------------------------- game đọc thấy gì

    /**
     * Chỗ duy nhất thật sự tính: cái game đọc được khi nó hỏi.
     *
     * <p>Bản mẫu {@code demo.DeviceDemo} hỏi đúng câu game đời ấy hỏi và rẽ
     * nhánh theo câu trả lời, nên chỗ này chạy bytecode thật chứ không đọc
     * lại cái bảng vừa dựng ra.</p>
     */
    private void whatTheGameReads() throws Exception {
        GameProfile profile = profile();
        EmulatorSession session = boot(profile);
        eq("Nokia6233/05.10",
                (String) session.vm().host().property("microedition.platform"),
                "game hỏi máy nào thì nghe thấy một chiếc Nokia");
        eq("ISO-8859-1", (String) session.vm().host().property("microedition.encoding"),
                "và bảng mã của máy đời ấy, không phải bảng mã của máy tính hôm nay");
        eq(null, (String) session.vm().host().property("microedition.m3g.version"),
                "hỏi 3D thì nghe thấy là không có");

        // Một game rẽ nhánh theo tên máy: nhánh Nokia phải là nhánh nó vào.
        session.renderFrame();
        check(drawsNokiaBranch(session), "và game rẽ vào nhánh dành cho Nokia");
        session.destroy();

        // Đổi máy rồi mở lại: cũng game ấy, cũng lớp ấy, nhánh khác.
        profile.identity().setHandset("generic");
        EmulatorSession plain = boot(profile);
        eq("j2me", (String) plain.vm().host().property("microedition.platform"),
                "giả làm máy chung thì game nghe thấy máy chung");
        plain.renderFrame();
        check(!drawsNokiaBranch(plain), "và cùng một game rẽ sang nhánh khác");
        plain.destroy();
    }

    /**
     * True khi khung hình đang là nhánh Nokia.
     *
     * <p>Bản mẫu viết dòng tiêu đề bằng màu xanh khi nó tin mình đang ở trên
     * máy Nokia, nên câu hỏi "nó rẽ nhánh nào" trả lời được bằng cách nhìn
     * đúng thứ người chơi nhìn: các điểm ảnh.</p>
     */
    private boolean drawsNokiaBranch(EmulatorSession session) {
        int[] pixels = session.screen().pixels();
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] & 0xFFFFFF) == 0x7FD962) {
                return true;
            }
        }
        return false;
    }

    private GameProfile profile() throws Exception {
        return GameProfile.defaultsFor(
                com.mobicore.core.jar.SuiteLoader.load(SampleSuite.jar(fixtureDir),
                        SampleSuite.jad()).info());
    }

    private EmulatorSession boot(GameProfile profile) throws Exception {
        EmulatorSession session = EmulatorSession.create(
                com.mobicore.core.jar.SuiteLoader.load(SampleSuite.jar(fixtureDir),
                        SampleSuite.jad()),
                profile, null, null, null);
        session.start("demo.DeviceDemo");
        return session;
    }

    // --------------------------------------------------------------- cầu nối

    private void throughTheBridge() throws Exception {
        MemoryVfs disk = new MemoryVfs();
        MobiCoreFacade facade = new MobiCoreFacade(disk);
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        Map<String, Object> shown = Json.readObject(facade.handsetJson(suiteId));
        eq("Nokia 6233", Json.string(shown, "name", ""), "màn hình gọi máy bằng tên người đọc được");
        eq("Nokia6233/05.10", Json.string(shown, "platform", ""), "và bày ra đúng chuỗi game đọc");
        check(Json.array(shown, "properties").size() > 3,
                "cùng cả bảng, vì đó là thứ cần nhìn khi một game chạy sai");

        Map<String, Object> chosen = Json.readObject(
                facade.setHandset(suiteId, "sonyericssonK750"));
        eq("SonyEricssonK750/R1", Json.string(chosen, "platform", ""), "đổi được sang máy khác");
        check(!Json.bool(chosen, "restartNeeded", true),
                "chưa chơi thì không có gì phải mở lại");

        Map<String, Object> edited = Json.readObject(facade.setSystemProperty(
                suiteId, "com.nokia.mid.ui.version", "1.4"));
        check(Json.bool(edited, "custom", false), "sửa tay được một dòng");
        boolean found = false;
        for (Object row : Json.array(edited, "properties")) {
            Map<String, Object> entry = (Map<String, Object>) row;
            if ("com.nokia.mid.ui.version".equals(Json.string(entry, "name", ""))) {
                found = true;
                check(Json.bool(entry, "edited", false), "và dòng ấy được đánh dấu là đã sửa");
            }
        }
        check(found, "dòng sửa tay nằm trong bảng");

        // Đang chơi thì lời sửa chỉ có tác dụng ở lần mở sau: game đã đọc
        // xong máy nó đang chạy trên đó ngay lúc mở màn.
        facade.startGame(suiteId);
        facade.renderFrame();
        check(Json.bool(Json.readObject(facade.setHandset(suiteId, "nokiaN73")),
                        "restartNeeded", false),
                "đang chơi mà đổi máy thì phải mở lại game mới ăn");
        facade.stopGame();

        Map<String, Object> back = Json.readObject(facade.resetHandset(suiteId));
        eq("Nokia6233/05.10", Json.string(back, "platform", ""), "đặt lại thì về máy mặc định");
        check(!Json.bool(back, "custom", true), "và không còn dòng nào sửa tay");

        // Máy đã chọn sống qua một lần tắt ứng dụng, vì nó là thứ chỉnh một
        // lần cho một game rồi không ai chỉnh lại.
        facade.setHandset(suiteId, "samsungE250");
        MobiCoreFacade reopened = new MobiCoreFacade(disk);
        reopened.open("/data");
        eq("SAMSUNG-SGH-E250/1.0",
                Json.string(Json.readObject(reopened.handsetJson(suiteId)), "platform", ""),
                "và vẫn còn đó ở lần mở sau");
    }
}
