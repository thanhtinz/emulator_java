package demo;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.midlet.MIDlet;

import java.io.ByteArrayInputStream;

/**
 * A MIDlet that makes noise the way a J2ME game made noise.
 *
 * <p>Compiled to real bytecode and run by the interpreter in the test suite,
 * so every path here is exercised as a game would exercise it: a beep on a
 * key press, a tune built as a tone sequence with a repeated block, a WAV
 * sound effect, and
 * a MIDI tune, which is what half the games of the era shipped their music
 * as.</p>
 */
public final class SoundDemo extends MIDlet implements CommandListener {

    private final Screen screen = new Screen();

    protected void startApp() {
        Display display = Display.getDisplay(this);
        screen.addCommand(new Command("Thoát", Command.EXIT, 2));
        screen.setCommandListener(this);
        display.setCurrent(screen);
        screen.everything();
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
        screen.release();
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.EXIT) {
            notifyDestroyed();
        }
    }

    /** Keeps what happened on screen, so a screenshot shows the outcome. */
    static final class Screen extends Canvas implements PlayerListener {

        private final String[] lines = new String[8];
        private int count;
        private Player tune;
        private Player effect;
        private int updates;

        void say(String line) {
            if (count < lines.length) {
                lines[count++] = line;
            }
        }

        void everything() {
            beep();
            tune();
            effect();
            music();
            say("Sự kiện: " + updates);
            repaint();
        }

        /** The single tone a game plays when something happens. */
        void beep() {
            try {
                Manager.playTone(69, 120, 80);
                say("Bíp: nốt La, 120ms");
            } catch (MediaException e) {
                say("Bíp lỗi: " + e.getMessage());
            }
        }

        /**
         * A tune, as a tone sequence: two bars defined once as a block and
         * played twice. This is how a game shipped music in eighty bytes.
         */
        void tune() {
            try {
                tune = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
                tune.realize();
                ToneControl control = (ToneControl) tune.getControl("ToneControl");
                byte tempo = 30;
                byte c = ToneControl.C4;
                byte d = (byte) (c + 2);
                byte e = (byte) (c + 4);
                byte g = (byte) (c + 7);
                byte[] sequence = {
                        ToneControl.VERSION, 1,
                        ToneControl.TEMPO, tempo,
                        ToneControl.BLOCK_START, 0,
                        c, 8, d, 8, e, 8, g, 16,
                        ToneControl.BLOCK_END, 0,
                        ToneControl.PLAY_BLOCK, 0,
                        ToneControl.SILENCE, 4,
                        ToneControl.PLAY_BLOCK, 0,
                };
                control.setSequence(sequence);
                tune.addPlayerListener(this);
                tune.setLoopCount(2);
                tune.start();
                say("Nhạc: " + (tune.getDuration() / 1000) + "ms x2");
            } catch (Exception e) {
                say("Nhạc lỗi: " + e.getMessage());
            }
        }

        /** A sound effect from a file in the JAR, as a game would load one. */
        void effect() {
            try {
                effect = Manager.createPlayer(new ByteArrayInputStream(wav()), "audio/x-wav");
                effect.prefetch();
                VolumeControl volume = (VolumeControl) effect.getControl("VolumeControl");
                volume.setLevel(60);
                effect.start();
                say("Hiệu ứng: WAV " + volume.getLevel() + "%");
            } catch (Exception e) {
                say("Hiệu ứng lỗi: " + e.getMessage());
            }
        }

        /**
         * MIDI: what a J2ME game's music nearly always was.
         *
         * <p>Two notes and a tempo, which is the smallest file that proves
         * the emulator read one. A game whose music will not play carries on
         * silently, so this also has to survive being refused.</p>
         */
        void music() {
            try {
                Player player = Manager.createPlayer(
                        new ByteArrayInputStream(midiFile()), "audio/midi");
                player.realize();
                player.start();
                say("MIDI: phát được");
            } catch (MediaException e) {
                say("MIDI: bị từ chối, game vẫn chạy");
            } catch (Exception e) {
                say("MIDI: " + e.getMessage());
            }
        }

        /** One bar: a tempo, a note on, a note off, end of track. */
        private byte[] midiFile() {
            byte[] track = {
                    0, (byte) 0xFF, 0x51, 3, 0x07, (byte) 0xA1, 0x20,
                    0, (byte) 0x90, 60, 100,
                    96, (byte) 0x80, 60, 0,
                    0, (byte) 0xFF, 0x2F, 0,
            };
            byte[] file = new byte[22 + track.length];
            file[0] = 'M';
            file[1] = 'T';
            file[2] = 'h';
            file[3] = 'd';
            file[7] = 6;
            file[11] = 1;
            file[13] = 96;
            file[14] = 'M';
            file[15] = 'T';
            file[16] = 'r';
            file[17] = 'k';
            file[21] = (byte) track.length;
            System.arraycopy(track, 0, file, 22, track.length);
            return file;
        }

        public void playerUpdate(Player player, String event, Object data) {
            updates++;
        }

        void release() {
            if (tune != null) {
                tune.close();
            }
            if (effect != null) {
                effect.close();
            }
        }

        /** A short square wave, built here so the fixture needs no asset. */
        private byte[] wav() {
            int rate = 8000;
            int frames = rate / 10;
            byte[] out = new byte[44 + frames * 2];
            header(out, rate, frames);
            for (int i = 0; i < frames; i++) {
                int value = (i / 20) % 2 == 0 ? 6000 : -6000;
                out[44 + i * 2] = (byte) (value & 0xFF);
                out[44 + i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
            }
            return out;
        }

        private void header(byte[] out, int rate, int frames) {
            ascii(out, 0, "RIFF");
            int32(out, 4, 36 + frames * 2);
            ascii(out, 8, "WAVE");
            ascii(out, 12, "fmt ");
            int32(out, 16, 16);
            int16(out, 20, 1);
            int16(out, 22, 1);
            int32(out, 24, rate);
            int32(out, 28, rate * 2);
            int16(out, 32, 2);
            int16(out, 34, 16);
            ascii(out, 36, "data");
            int32(out, 40, frames * 2);
        }

        private void ascii(byte[] out, int at, String text) {
            for (int i = 0; i < text.length(); i++) {
                out[at + i] = (byte) text.charAt(i);
            }
        }

        private void int32(byte[] out, int at, int value) {
            out[at] = (byte) value;
            out[at + 1] = (byte) (value >> 8);
            out[at + 2] = (byte) (value >> 16);
            out[at + 3] = (byte) (value >> 24);
        }

        private void int16(byte[] out, int at, int value) {
            out[at] = (byte) value;
            out[at + 1] = (byte) (value >> 8);
        }

        protected void paint(Graphics g) {
            g.setColor(0x0E1621);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0x8ECBFF);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
            g.drawString("ÂM THANH", 8, 6, Graphics.TOP | Graphics.LEFT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.setColor(0xE6EDF5);
            int y = 30;
            for (int i = 0; i < count; i++) {
                g.drawString(lines[i], 8, y, Graphics.TOP | Graphics.LEFT);
                y += 18;
            }
        }

        protected void keyPressed(int keyCode) {
            beep();
            repaint();
        }
    }
}
