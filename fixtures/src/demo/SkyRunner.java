package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.LayerManager;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.game.TiledLayer;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordComparator;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordFilter;
import javax.microedition.rms.RecordStore;

/**
 * Demo MIDlet shipped with MobiCore.
 *
 * It is a real J2ME program — compiled to bytecode and executed by the
 * emulator, not drawn by the host — so it doubles as the compatibility probe
 * for Canvas, Graphics, Image, Font, Sprite, TiledLayer and LayerManager.
 */
public class SkyRunner extends MIDlet implements CommandListener {

    private static final String SCORE_STORE = "skyrunner-scores";

    private Scene scene;

    protected void startApp() {
        if (scene == null) {
            scene = new Scene();
            scene.setTitle("Sky Runner");
            scene.addCommand(new Command("Pause", Command.STOP, 1));
            scene.addCommand(new Command("Exit", Command.EXIT, 2));
            scene.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(scene);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /**
     * Persists the high score through RMS, which is what the emulator's save
     * management has to keep intact across sessions, backups and restores.
     */
    public int saveScore(int score) throws Exception {
        RecordStore store = RecordStore.openRecordStore(SCORE_STORE, true);
        try {
            byte[] payload = encode(score);
            return store.addRecord(payload, 0, payload.length);
        } finally {
            store.closeRecordStore();
        }
    }

    /** Highest score on record, or zero when nothing has been saved yet. */
    public int bestScore() throws Exception {
        RecordStore store = RecordStore.openRecordStore(SCORE_STORE, true);
        try {
            RecordEnumeration records = store.enumerateRecords(new PositiveScores(), new Descending(), false);
            if (!records.hasNextElement()) {
                return 0;
            }
            int best = decode(records.nextRecord());
            records.destroy();
            return best;
        } finally {
            store.closeRecordStore();
        }
    }

    public int savedScoreCount() throws Exception {
        RecordStore store = RecordStore.openRecordStore(SCORE_STORE, true);
        try {
            return store.getNumRecords();
        } finally {
            store.closeRecordStore();
        }
    }

    private static byte[] encode(int score) {
        return new byte[]{
                (byte) (score >>> 24), (byte) (score >>> 16), (byte) (score >>> 8), (byte) score};
    }

    private static int decode(byte[] data) {
        return ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
    }

    /** Rejects the zero-score placeholder some builds used to write. */
    static class PositiveScores implements RecordFilter {

        public boolean matches(byte[] candidate) {
            return candidate.length == 4 && decode(candidate) > 0;
        }
    }

    static class Descending implements RecordComparator {

        public int compare(byte[] left, byte[] right) {
            int a = decode(left);
            int b = decode(right);
            if (a == b) {
                return EQUIVALENT;
            }
            return a > b ? PRECEDES : FOLLOWS;
        }
    }

    /**
     * Posts a score to a leaderboard, the way a late J2ME game would. The
     * emulator's network layer decides whether this is allowed to leave the
     * device at all.
     */
    public String submitScore(String url, int score) throws Exception {
        HttpConnection connection = (HttpConnection) Connector.open(url);
        try {
            connection.setRequestMethod(HttpConnection.POST);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            java.io.OutputStream out = connection.openOutputStream();
            out.write(("score=" + score).getBytes());
            out.close();

            int status = connection.getResponseCode();
            java.io.InputStream in = connection.openInputStream();
            StringBuffer body = new StringBuffer();
            int b;
            while ((b = in.read()) >= 0) {
                body.append((char) b);
            }
            in.close();
            return status + " " + body.toString();
        } finally {
            connection.close();
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.EXIT) {
            notifyDestroyed();
        }
    }

    /** The playfield. */
    static class Scene extends Canvas {

        private static final int SKY_TOP = 0x1B3A63;
        private static final int SKY_BOTTOM = 0x7FB4D8;
        /** Drawn as the sprite background, then turned into transparency. */
        private static final int KEY_COLOR = 0xFF00FF;

        private final LayerManager layers = new LayerManager();
        private final Sprite runner;
        private final TiledLayer ground;

        private int playerX = 40;
        private int playerY;
        private int score;
        private int frame;
        private String lastKey = "—";

        Scene() {
            Image sheet = buildRunnerSheet();
            runner = new Sprite(sheet, 16, 16);
            runner.defineCollisionRectangle(2, 1, 12, 15);

            Image tiles = buildTileSheet();
            ground = new TiledLayer(16, 3, tiles, 16, 16);
            for (int column = 0; column < 16; column++) {
                ground.setCell(column, 0, 1);
                ground.setCell(column, 1, 2);
                ground.setCell(column, 2, 2);
            }
            playerY = 0;
            layers.append(runner);
            layers.append(ground);
        }

        /**
         * Two walk frames drawn procedurally, so no binary art is needed.
         * The frames are drawn onto a magenta key colour and then converted to
         * an ARGB image, which is how J2ME art gets its transparency.
         */
        private Image buildRunnerSheet() {
            Image sheet = Image.createImage(32, 16);
            Graphics g = sheet.getGraphics();
            g.setColor(KEY_COLOR);
            g.fillRect(0, 0, 32, 16);
            for (int frameIndex = 0; frameIndex < 2; frameIndex++) {
                int offset = frameIndex * 16;
                g.setColor(0xF2C078);
                g.fillArc(offset + 4, 0, 9, 9, 0, 360);
                g.setColor(0xE8453C);
                g.fillRect(offset + 4, 8, 9, 5);
                g.setColor(0xF2C078);
                g.fillRect(offset + 1, 8, 3, 4);
                g.fillRect(offset + 13, 8, 2, 4);
                g.setColor(0x2B3A55);
                if (frameIndex == 0) {
                    g.fillRect(offset + 5, 13, 3, 3);
                    g.fillRect(offset + 10, 13, 3, 3);
                } else {
                    g.fillRect(offset + 3, 13, 4, 3);
                    g.fillRect(offset + 11, 13, 3, 3);
                }
                g.setColor(0x1B1B1B);
                g.fillRect(offset + 6, 3, 2, 2);
                g.fillRect(offset + 10, 3, 2, 2);
            }
            int[] pixels = new int[32 * 16];
            sheet.getRGB(pixels, 0, 32, 0, 0, 32, 16);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] & 0xFFFFFF) == KEY_COLOR ? 0 : (0xFF000000 | pixels[i]);
            }
            return Image.createRGBImage(pixels, 32, 16, true);
        }

        /** Tile 1 is grass, tile 2 is soil. */
        private Image buildTileSheet() {
            Image tiles = Image.createImage(32, 16);
            Graphics g = tiles.getGraphics();
            g.setColor(0x3E8948);
            g.fillRect(0, 0, 16, 16);
            g.setColor(0x63C74D);
            g.fillRect(0, 0, 16, 5);
            for (int i = 0; i < 16; i += 4) {
                g.setColor(0x2F6B37);
                g.drawLine(i, 5, i + 2, 8);
            }
            g.setColor(0x6B4B2A);
            g.fillRect(16, 0, 16, 16);
            g.setColor(0x53381F);
            g.fillRect(18, 4, 4, 3);
            g.fillRect(25, 9, 5, 3);
            return Image.createImage(tiles);
        }

        protected void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();
            paintSky(g, width, height);
            paintMountains(g, width, height);
            paintClouds(g, width);

            int groundTop = height - 48;
            ground.setPosition(-(frame * 2) % 32, groundTop);
            runner.setPosition(playerX, groundTop - 16 - playerY);
            runner.setFrame((frame / 6) % 2);
            layers.paint(g, 0, 0);

            paintHud(g, width);
        }

        private void paintSky(Graphics g, int width, int height) {
            int bands = 16;
            for (int i = 0; i < bands; i++) {
                g.setColor(blend(SKY_TOP, SKY_BOTTOM, i, bands));
                g.fillRect(0, i * height / bands, width, height / bands + 1);
            }
            g.setColor(0xFFE9A8);
            g.fillArc(width - 58, 14, 34, 34, 0, 360);
            g.setColor(0xFFF6D8);
            g.fillArc(width - 52, 20, 22, 22, 0, 360);
        }

        private void paintMountains(Graphics g, int width, int height) {
            int base = height - 48;
            g.setColor(0x2E4A6B);
            g.fillTriangle(-10, base, 60, base - 74, 130, base);
            g.setColor(0x395A80);
            g.fillTriangle(80, base, 150, base - 58, 220, base);
            g.setColor(0xD8E8F4);
            g.fillTriangle(40, base - 42, 60, base - 74, 80, base - 42);
        }

        private void paintClouds(Graphics g, int width) {
            int drift = (frame / 3) % (width + 80) - 40;
            g.setColor(0xFFFFFF);
            puff(g, drift, 44);
            puff(g, (drift + 120) % (width + 80) - 20, 70);
        }

        private void puff(Graphics g, int x, int y) {
            g.fillRoundRect(x, y, 44, 14, 14, 14);
            g.fillArc(x + 8, y - 8, 18, 18, 0, 360);
            g.fillArc(x + 22, y - 6, 14, 14, 0, 360);
        }

        private void paintHud(Graphics g, int width) {
            g.setColor(0x000000);
            g.fillRect(0, 0, width, 18);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
            g.setColor(0xFFFFFF);
            g.drawString("ĐIỂM " + score, 4, 2, Graphics.TOP | Graphics.LEFT);
            g.drawString("F" + frame, width - 4, 2, Graphics.TOP | Graphics.RIGHT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.setColor(0x9FD3FF);
            g.drawString("PHÍM " + lastKey, width / 2, 2, Graphics.TOP | Graphics.HCENTER);
        }

        private static int blend(int from, int to, int step, int steps) {
            int r = channel(from, 16) + (channel(to, 16) - channel(from, 16)) * step / steps;
            int g = channel(from, 8) + (channel(to, 8) - channel(from, 8)) * step / steps;
            int b = channel(from, 0) + (channel(to, 0) - channel(from, 0)) * step / steps;
            return (r << 16) | (g << 8) | b;
        }

        private static int channel(int color, int shift) {
            return (color >> shift) & 0xFF;
        }

        /** Advances the simulation; the emulator calls this once per frame. */
        public void tick() {
            frame++;
            score += 1;
            if (playerY > 0) {
                playerY -= 4;
                if (playerY < 0) {
                    playerY = 0;
                }
            }
            repaint();
        }

        protected void keyPressed(int keyCode) {
            lastKey = getKeyName(keyCode);
            int action = getGameAction(keyCode);
            if (action == LEFT) {
                playerX = Math.max(0, playerX - 8);
            } else if (action == RIGHT) {
                playerX = Math.min(getWidth() - 16, playerX + 8);
            } else if (action == FIRE || action == UP) {
                playerY = 28;
                score += 10;
            }
            repaint();
        }

        public int score() {
            return score;
        }

        public int playerX() {
            return playerX;
        }
    }
}
