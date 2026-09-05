package demo;

import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordComparator;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordFilter;
import javax.microedition.rms.RecordListener;
import javax.microedition.rms.RecordStore;

/**
 * A high-score table, which is what the rest of {@code RecordEnumeration} is
 * for.
 *
 * <p>Reading a store front to back needs four methods and the emulator had
 * them. Everything a table actually does needs the other seven: scrolling back
 * up ({@code previousRecord}), jumping to a row ({@code getRecordId}), and
 * staying right when a score is added while the table is open
 * ({@code rebuild}, {@code keepUpdated}) — plus hearing about the change at
 * all ({@code RecordListener}).</p>
 */
public final class ScoreBoard extends MIDlet {

    private static final String STORE = "scores";

    /** Counted by the listener, so the test can see it really fired. */
    public int added;
    public int changed;
    public int deleted;
    /** The record id the last callback carried. */
    public int lastId;

    protected void startApp() {
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    private RecordStore store;

    /** Opens a fresh table holding the given scores, in the order given. */
    public void begin(String scores) throws Exception {
        try {
            RecordStore.deleteRecordStore(STORE);
        } catch (Exception ignored) {
            // No table yet, which is the normal case the first time.
        }
        store = RecordStore.openRecordStore(STORE, true);
        added = 0;
        changed = 0;
        deleted = 0;
        lastId = 0;
        if (scores.length() > 0) {
            String[] parts = split(scores);
            for (int i = 0; i < parts.length; i++) {
                byte[] row = parts[i].getBytes();
                store.addRecord(row, 0, row.length);
            }
        }
    }

    public void listen() {
        store.addRecordListener(new RecordListener() {
            public void recordAdded(RecordStore which, int recordId) {
                added++;
                lastId = recordId;
            }

            public void recordChanged(RecordStore which, int recordId) {
                changed++;
                lastId = recordId;
            }

            public void recordDeleted(RecordStore which, int recordId) {
                deleted++;
                lastId = recordId;
            }
        });
    }

    public void add(String score) throws Exception {
        byte[] row = score.getBytes();
        store.addRecord(row, 0, row.length);
    }

    public void change(int recordId, String score) throws Exception {
        byte[] row = score.getBytes();
        store.setRecord(recordId, row, 0, row.length);
    }

    public void remove(int recordId) throws Exception {
        store.deleteRecord(recordId);
    }

    /** Walks the whole table forwards, then all the way back. */
    public String bothWays() throws Exception {
        RecordEnumeration rows = store.enumerateRecords(null, null, false);
        StringBuffer out = new StringBuffer();
        while (rows.hasNextElement()) {
            out.append(new String(rows.nextRecord()));
        }
        out.append('|');
        while (rows.hasPreviousElement()) {
            out.append(new String(rows.previousRecord()));
        }
        rows.destroy();
        return out.toString();
    }

    /** The row at that index, without walking to it. */
    public String rowAt(int index) throws Exception {
        RecordEnumeration rows = store.enumerateRecords(null, null, false);
        String answer = new String(store.getRecord(rows.getRecordId(index)));
        rows.destroy();
        return answer;
    }

    /**
     * An open table, a score added, and what the table says afterwards.
     *
     * @param keptUpdated whether the table was asked to look after itself
     * @param rebuilt whether the game asks it to catch up by hand
     */
    public String whileOpen(boolean keptUpdated, boolean rebuilt, String score) throws Exception {
        RecordEnumeration rows = store.enumerateRecords(null, null, false);
        rows.keepUpdated(keptUpdated);
        add(score);
        if (rebuilt) {
            rows.rebuild();
        }
        StringBuffer out = new StringBuffer();
        out.append(rows.numRecords());
        out.append(rows.isKeptUpdated() ? "|giữ" : "|không");
        rows.destroy();
        return out.toString();
    }

    /** Only the scores that begin with the given digit, largest first. */
    public String filtered(final String digit) throws Exception {
        RecordEnumeration rows = store.enumerateRecords(new RecordFilter() {
            public boolean matches(byte[] candidate) {
                return new String(candidate).startsWith(digit);
            }
        }, new RecordComparator() {
            public int compare(byte[] left, byte[] right) {
                String a = new String(left);
                String b = new String(right);
                return a.compareTo(b) > 0 ? -1 : (a.equals(b) ? 0 : 1);
            }
        }, false);
        StringBuffer out = new StringBuffer();
        while (rows.hasNextElement()) {
            out.append(new String(rows.nextRecord()));
        }
        rows.destroy();
        return out.toString();
    }

    /** What the listener counted, as one string. */
    public String heard() {
        return added + "," + changed + "," + deleted + "," + lastId;
    }

    public void stopListening() {
        // Nothing to hand back: removing an unknown listener is a no-op, so
        // this proves the emulator does not mind either.
        store.removeRecordListener(null);
    }

    /** CLDC has no String.split, so the fixture brings its own. */
    private static String[] split(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ',') {
                count++;
            }
        }
        String[] parts = new String[count];
        int at = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ',') {
                parts[at++] = text.substring(start, i);
                start = i + 1;
            }
        }
        return parts;
    }
}
