package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Tìm và sửa số vàng trong game chơi một mình.
 *
 * <p>Phần lưu của game J2ME là một dãy byte không nhãn: không có chỗ nào ghi
 * "đây là số vàng", và mỗi game một kiểu. Không đọc dãy byte ấy mà đoán ra
 * được.</p>
 *
 * <p>Nhưng người chơi thì biết mình đang có bao nhiêu. Nên cách làm là đi
 * ngược: hỏi con số trên màn hình, tìm nó trong phần lưu, rồi chơi cho con số
 * đổi đi và hỏi lại — <b>chỗ nào đổi theo đúng như vậy mới là chỗ thật</b>.
 * Bài kiểm tra này chạy đúng vòng đó trên một game thật, và chốt bằng câu hỏi
 * duy nhất đáng hỏi: <b>game có đọc ra con số mới không</b>.</p>
 */
public final class TreasureTest extends Test {

    private final String fixtureDir;

    public TreasureTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Tìm và sửa vàng trong game";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/PiggyBank.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        // Một ván mới với 8630 vàng, do chính game ghi ra phần lưu của nó.
        VmObject bank = start(facade, suiteId);
        call(facade, bank, "newGame", "(I)V", Integer.valueOf(8630));
        facade.stopGame();

        // Lần tìm đầu: người chơi gõ con số đang thấy trên màn hình.
        Map<String, Object> first = Json.readObject(facade.scanSave(suiteId, 8630));
        check(Json.bool(first, "ok", false), "tìm được: " + Json.string(first, "error", ""));
        int found = Json.integer(first, "count", 0);
        check(found > 1, "một lần tìm chưa đủ — còn " + found + " chỗ mang số này");
        check(!Json.bool(first, "done", true), "và nó nói thẳng là chưa xong");

        // Chơi tiếp: tiêu mất 130 vàng, con số trên màn hình đổi.
        bank = start(facade, suiteId);
        call(facade, bank, "spend", "(I)V", Integer.valueOf(130));
        eq(8500, gold(facade, bank), "game tiêu tiền xong còn 8500");
        facade.stopGame();

        // Lần tìm thứ hai: chỗ nào đổi theo đúng như vậy.
        Map<String, Object> second = Json.readObject(facade.narrowSave(suiteId, 8500));
        int left = Json.integer(second, "count", 0);
        check(left < found, "lần hai lọc bớt: " + found + " còn " + left);
        check(left >= 1, "và vẫn giữ được chỗ thật");

        // Sửa: đặt số vàng mới vào chỗ đầu tiên còn lại.
        List<Object> hits = Json.array(second, "hits");
        Map<String, Object> hit = (Map<String, Object>) hits.get(0);
        check(Json.string(hit, "encodingName", "").length() > 0,
                "mỗi chỗ nói rõ nó được ghi kiểu gì: " + Json.string(hit, "encodingName", ""));

        // Còn vài chỗ thì đặt cùng một số vào tất cả: game giữ số vàng ở
        // nhiều chỗ thật — một bản nhị phân, một bản viết thành chữ — và sửa
        // mỗi một chỗ là để lại một phần lưu tự mâu thuẫn.
        Map<String, Object> written = Json.readObject(
                facade.setAllSaveValues(suiteId, 999999));
        check(Json.bool(written, "ok", false),
                "sửa được: " + Json.string(written, "error", ""));
        check(Json.integer(written, "written", 0) >= 1, "và nói rõ sửa mấy chỗ");

        // Câu hỏi duy nhất đáng hỏi: game đọc ra gì?
        bank = start(facade, suiteId);
        call(facade, bank, "reload", "()V", null);
        eq(999999, ((Integer) bank.get("reloaded")).intValue(),
                "game mở lại và đọc thấy đúng số vàng vừa đặt");
        facade.stopGame();

        // Con số không vừa ô thì phải bị từ chối, chứ không lặng lẽ cắt cụt:
        // nhét 70000 vào một ô hai byte thì game đọc ra 4464.
        facade.scanSave(suiteId, 3);
        List<Object> small = Json.array(Json.readObject(facade.narrowSave(suiteId, 3)), "hits");
        int narrow = -1;
        for (int i = 0; i < small.size(); i++) {
            Map<String, Object> row = (Map<String, Object>) small.get(i);
            if (Json.integer(row, "length", 0) <= 2) {
                narrow = i;
                break;
            }
        }
        check(narrow >= 0, "có một ô nhỏ để thử");
        Map<String, Object> refused = Json.readObject(
                facade.setSaveValue(suiteId, narrow, 70000));
        check(!Json.bool(refused, "ok", true), "số quá lớn so với ô thì bị từ chối");
        check(Json.string(refused, "error", "").indexOf("không vừa") >= 0,
                "và nói rõ vì sao: " + Json.string(refused, "error", ""));

        // Danh sách cũ thì không sửa mò được nữa.
        facade.clearSaveScan();
        check(!Json.bool(Json.readObject(facade.setSaveValue(suiteId, 0, 5)), "ok", true),
                "bỏ danh sách rồi thì không sửa theo số thứ tự cũ được");

        // Sao lưu trước khi ghi: sửa phần lưu là sửa thứ không dựng lại được.
        check(Json.array(Json.readObject(facade.savesJson(suiteId)), "backups").size() > 0,
                "trước khi sửa, phần lưu được sao lưu");
    }

    // ------------------------------------------------------------ tiện ích

    private VmObject start(MobiCoreFacade facade, String suiteId) throws Exception {
        Map<String, Object> started = Json.readObject(
                facade.startGame(suiteId, "demo.PiggyBank"));
        check(Json.bool(started, "ok", false),
                "game mở được: " + Json.string(started, "error", ""));
        return facade.session().context().midlet();
    }

    private void call(MobiCoreFacade facade, VmObject bank, String method, String signature,
                      Object argument) {
        EmulatorSession session = facade.session();
        if (argument == null) {
            session.vm().callVirtual(bank, method, signature);
        } else {
            session.vm().callVirtual(bank, method, signature, argument);
        }
    }

    private int gold(MobiCoreFacade facade, VmObject bank) {
        return ((Integer) bank.get("gold")).intValue();
    }
}
