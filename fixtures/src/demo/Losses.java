package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.LayerManager;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

/**
 * The three things an emulator can lose without saying a word.
 *
 * <p>Nothing here stops a game. A save that never reaches storage, a canvas
 * left translated and clipped for the rest of the level: the game plays on,
 * and only the player notices, hours later, that something is wrong.</p>
 */
public final class Losses extends MIDlet {

    /** The store the {@code destroyApp} question is asked about. */
    public static final String ON_THE_WAY_OUT = "lucCuoi";

    protected void startApp() {
    }

    protected void pauseApp() {
    }

    /**
     * Opens a store it never writes to, then fails on the way out.
     *
     * <p>Opening a store that did not exist creates it in memory only: it
     * reaches storage when the session flushes. So this is a save that exists
     * and has not been written, at the exact moment the game falls over. It
     * must survive anyway.</p>
     */
    protected void destroyApp(boolean unconditional) {
        try {
            RecordStore.openRecordStore(ON_THE_WAY_OUT, true);
        } catch (Exception e) {
            // Nothing to be done about it here; the test will notice.
        }
        // A failure that is not the game's own exception: the emulator's
        // cleanup must not be its hostage either way.
        char[] room = new char[2];
        System.out.println(new String(room, 0, 9));
    }

    // ------------------------------------------------------------------ RMS

    private static final String STORE = "hai-tay-cam";

    /**
     * Two handles on one store, one of them closed, then a write.
     *
     * <p>MIDP hands both handles the same store, so closing one is not
     * closing the store. The write that follows still has to reach storage —
     * and the only way to prove it did is to put the store away completely
     * and read it back from nothing.</p>
     *
     * @return what the reopened store holds
     */
    public String writeAfterOneHandleCloses() throws Exception {
        try {
            RecordStore.deleteRecordStore(STORE);
        } catch (Exception ignored) {
            // Not there yet, which is the normal first time.
        }
        RecordStore first = RecordStore.openRecordStore(STORE, true);
        RecordStore second = RecordStore.openRecordStore(STORE, true);
        first.closeRecordStore();

        byte[] row = "9910".getBytes();
        second.addRecord(row, 0, row.length);
        second.closeRecordStore();

        RecordStore again = RecordStore.openRecordStore(STORE, false);
        StringBuffer out = new StringBuffer();
        int[] ids = new int[] { 1 };
        for (int i = 0; i < ids.length; i++) {
            out.append(new String(again.getRecord(ids[i])));
        }
        again.closeRecordStore();
        return out.toString();
    }

    /**
     * A third handle opened after the first was closed sees the same store.
     *
     * <p>If the emulator forgot the store the moment one handle closed, this
     * handle loads a second, stale copy from storage — and from then on the
     * two copies drift apart and whichever writes last wins.</p>
     */
    public String thirdHandleSeesTheSameStore() throws Exception {
        try {
            RecordStore.deleteRecordStore(STORE);
        } catch (Exception ignored) {
            // As above.
        }
        RecordStore first = RecordStore.openRecordStore(STORE, true);
        RecordStore second = RecordStore.openRecordStore(STORE, true);
        first.closeRecordStore();
        RecordStore third = RecordStore.openRecordStore(STORE, true);

        byte[] row = "7".getBytes();
        int id = second.addRecord(row, 0, row.length);
        String seen = third.getNumRecords() + ":" + new String(third.getRecord(id));
        second.closeRecordStore();
        third.closeRecordStore();
        return seen;
    }

    // -------------------------------------------------------------- touches

    private Board board;

    /** Puts up a canvas with more commands than softkeys, so a menu exists. */
    public void showBoard() {
        board = new Board();
        Display.getDisplay(this).setCurrent(board);
    }

    /** Every pointer event the canvas was given, in order. */
    public String touches() {
        return board.log.toString();
    }

    public void forgetTouches() {
        board.log = new StringBuffer();
    }

    /** Which menu command was run, if any. Empty when none was. */
    public String ranCommand() {
        return board.ran;
    }

    /** A canvas that writes down what it is told, and nothing else. */
    private static final class Board extends Canvas implements CommandListener {

        StringBuffer log = new StringBuffer();
        String ran = "";

        public void commandAction(Command command, Displayable screen) {
            ran = ran + command.getLabel();
        }

        Board() {
            setCommandListener(this);
            setTitle("Chạm");
            addCommand(new Command("Một", Command.ITEM, 1));
            addCommand(new Command("Hai", Command.ITEM, 2));
            addCommand(new Command("Ba", Command.ITEM, 3));
            addCommand(new Command("Bốn", Command.ITEM, 4));
        }

        protected void pointerPressed(int x, int y) {
            note("P", x, y);
        }

        protected void pointerDragged(int x, int y) {
            note("D", x, y);
        }

        protected void pointerReleased(int x, int y) {
            note("R", x, y);
        }

        private void note(String kind, int x, int y) {
            if (log.length() > 0) {
                log.append(' ');
            }
            log.append(kind).append(x).append(',').append(y);
        }

        protected void paint(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    // --------------------------------------------------------------- canvas

    /** A layer that fails half way through painting, as game code can. */
    private static final class Awkward extends Sprite {

        Awkward(Image image) {
            super(image);
        }

        static String inside = "chua-ve";

        public void paint(Graphics g) {
            inside = g.getTranslateX() + "," + g.getTranslateY();
            throw new RuntimeException("lớp này hỏng");
        }
    }

    /**
     * Paints a manager whose layer throws, then reports the canvas's state.
     *
     * @return the translation and clip after the failure, which must be the
     *         ones the game set before it
     */
    public String canvasAfterALayerFails() {
        Image sheet = Image.createImage(8, 8);
        Image target = Image.createImage(64, 64);
        Graphics g = target.getGraphics();
        g.translate(3, 5);
        g.setClip(1, 2, 20, 30);

        LayerManager layers = new LayerManager();
        layers.append(new Awkward(sheet));
        layers.setViewWindow(0, 0, 16, 16);
        String threw = "khong-nem";
        try {
            layers.paint(g, 9, 11);
        } catch (Throwable expected) {
            // Which is exactly what a game does with a bad level.
            threw = "nem:" + expected.getMessage();
        }
        return threw + " [" + Awkward.inside + "] " + g.getTranslateX() + "," + g.getTranslateY() + " "
                + g.getClipX() + "," + g.getClipY() + " "
                + g.getClipWidth() + "x" + g.getClipHeight();
    }
}
