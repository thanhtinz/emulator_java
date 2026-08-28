package demo;

import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * A game that keeps a purse, the way an offline J2ME game did.
 *
 * <p>Nothing in the save says "gold": it is a run of bytes, and the game alone
 * knows which four of them are money. This one writes it the two ways games
 * actually did — a binary integer in one record, the same number as digits in
 * another — and surrounds it with decoys of the same value so a search that
 * only looks once cannot tell them apart.</p>
 */
public final class PiggyBank extends MIDlet {

    private static final String STORE = "bank";

    /** What the player currently has, as the game itself reports it. */
    public int gold;
    /** A decoy: equal to the purse at the start, and then it stops moving. */
    private int score;
    /** Read back from the save, to prove an edit really lands. */
    public int reloaded;

    protected void startApp() {
        try {
            load();
        } catch (Exception e) {
            gold = -1;
        }
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /** Starts a purse with a known amount, the way a new game does. */
    public void newGame(int amount) throws Exception {
        RecordStore.deleteRecordStore(STORE);
        gold = amount;
        // Điểm bằng đúng số vàng lúc bắt đầu, rồi đứng yên: đây chính là kiểu
        // trùng số mà một lần tìm không phân biệt được, và lần tìm thứ hai
        // sinh ra để loại.
        score = amount;
        save();
    }

    /** Spends some, which is how the player makes the number move. */
    public void spend(int amount) throws Exception {
        gold -= amount;
        save();
    }

    /** Reads the purse back out of the save, ignoring what is in memory. */
    public void reload() throws Exception {
        gold = 0;
        load();
        reloaded = gold;
    }

    private void save() throws Exception {
        RecordStore store = RecordStore.openRecordStore(STORE, true);
        try {
            // Bản ghi 1: mấy con số của ván chơi, vàng nằm giữa. Số điểm và
            // số của màn kế tiếp cố tình bằng đúng số vàng, để một lần tìm
            // không phân biệt được.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(score);       // điểm, tình cờ bằng số vàng lúc đầu
            out.writeInt(gold);        // vàng
            out.writeShort(3);         // màn đang chơi
            out.flush();
            byte[] record = bytes.toByteArray();
            if (store.getNumRecords() == 0) {
                store.addRecord(record, 0, record.length);
                byte[] name = ("player=Tin;gold=" + gold + ";").getBytes("UTF-8");
                store.addRecord(name, 0, name.length);
            } else {
                store.setRecord(1, record, 0, record.length);
                byte[] name = ("player=Tin;gold=" + gold + ";").getBytes("UTF-8");
                store.setRecord(2, name, 0, name.length);
            }
        } finally {
            store.closeRecordStore();
        }
    }

    private void load() throws Exception {
        RecordStore store = RecordStore.openRecordStore(STORE, true);
        try {
            if (store.getNumRecords() == 0) {
                gold = 0;
                return;
            }
            byte[] record = store.getRecord(1);
            // Bốn byte thứ hai là số vàng.
            gold = ((record[4] & 0xFF) << 24) | ((record[5] & 0xFF) << 16)
                    | ((record[6] & 0xFF) << 8) | (record[7] & 0xFF);
        } finally {
            store.closeRecordStore();
        }
    }
}
