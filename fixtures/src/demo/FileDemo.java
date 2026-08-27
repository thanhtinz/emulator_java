package demo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.midlet.MIDlet;

/**
 * A MIDlet that keeps its own files, the way a game with a level editor did.
 *
 * <p>Record stores hold a few hundred bytes; a saved level, a downloaded
 * track or a photo went to JSR-75 instead. Compiled to real bytecode and run
 * by the interpreter in the test suite, because what this guards against is
 * the class loader giving up on {@code FileConnection} before a single byte
 * is written.</p>
 */
public final class FileDemo extends MIDlet {

    /** Set as each step succeeds, so a test can tell how far it got. */
    public int steps;
    /** What was read back, so a test can compare it with what went in. */
    public String readBack = "";
    /** How many entries the directory listing found. */
    public int listed;
    /** How big the file was after appending to it. */
    public int size;
    /** The root the handset says it has. */
    public String root = "";
    /** Set when writing outside the game's own folder was refused. */
    public boolean escapeRefused;

    protected void startApp() {
        try {
            run();
        } catch (Exception e) {
            readBack = "failed: " + e;
        }
    }

    private void run() throws Exception {
        Enumeration roots = FileSystemRegistry.listRoots();
        if (roots != null && roots.hasMoreElements()) {
            root = String.valueOf(roots.nextElement());
            steps++;
        }

        // A game hard-codes whatever root its target handset had; this one is
        // a Series 40 game, so it asks for c:/.
        FileConnection levels = (FileConnection) Connector.open(
                "file:///c:/levels/", Connector.READ_WRITE);
        if (!levels.exists()) {
            levels.mkdir();
        }
        steps++;

        FileConnection level = (FileConnection) Connector.open(
                "file:///c:/levels/level1.dat", Connector.READ_WRITE);
        if (!level.exists()) {
            level.create();
        }
        DataOutputStream out = level.openDataOutputStream();
        out.writeUTF("xin chào");
        out.writeInt(1234);
        out.close();
        steps++;

        DataInputStream in = level.openDataInputStream();
        readBack = in.readUTF() + ":" + in.readInt();
        in.close();
        steps++;

        // Appending, which is how a game adds a lap time to a file it has.
        OutputStream more = level.openOutputStream(level.fileSize());
        more.write(new byte[]{'!', '!'});
        more.close();
        size = (int) level.fileSize();
        steps++;

        Enumeration names = levels.list();
        while (names.hasMoreElements()) {
            names.nextElement();
            listed++;
        }
        level.close();
        levels.close();

        // Reading is reading: a connection opened READ must refuse to write.
        FileConnection readOnly = (FileConnection) Connector.open(
                "file:///c:/levels/level1.dat", Connector.READ);
        InputStream stream = readOnly.openInputStream();
        stream.close();
        try {
            readOnly.delete();
        } catch (Exception refused) {
            steps++;
        }
        readOnly.close();

        // And the whole point of the sandbox: a path that climbs out is
        // refused rather than quietly landing somewhere else.
        try {
            FileConnection escape = (FileConnection) Connector.open(
                    "file:///c:/../../library.json", Connector.READ_WRITE);
            escape.create();
        } catch (Exception refused) {
            escapeRefused = true;
        }
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
