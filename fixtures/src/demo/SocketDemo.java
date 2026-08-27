package demo;

import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.UDPDatagramConnection;
import javax.microedition.midlet.MIDlet;

/**
 * A MIDlet that plays over a socket, the way a multiplayer game of the era did.
 *
 * <p>Three shapes in one, because games used all three: a line held open to a
 * lobby server, a port opened so the handset next to you can dial in, and a
 * packet fired off without waiting to hear back. Compiled to real bytecode and
 * run by the interpreter in the test suite, so what is proved is that a game
 * can hold a conversation — not that a class was registered.</p>
 */
public final class SocketDemo extends MIDlet {

    /** What the echo server sent back. */
    public String echoed = "";
    /** The far end of the client connection, as the game sees it. */
    public String peer = "";
    /** The port the game was given for its own end of that connection. */
    public int localPort;
    /** What arrived on the port the game opened for others to dial in to. */
    public String accepted = "";
    /** What came back in the packet the game sent itself. */
    public String packet = "";
    /** Who the packet said it came from. */
    public String packetFrom = "";
    /** The message when opening something the emulator does not carry. */
    public String refused = "";

    protected void startApp() {
        try {
            talkToServer();
            listenForAPeer();
            sendAPacket();
            tryTheUnsupported();
        } catch (Exception e) {
            echoed = "failed: " + e;
        }
    }

    /** A line held open to a server: write a word, read the answer. */
    private void talkToServer() throws Exception {
        SocketConnection connection =
                (SocketConnection) Connector.open("socket://lobby.test:7000");
        try {
            peer = connection.getAddress() + ":" + connection.getPort();
            localPort = connection.getLocalPort();
            OutputStream out = connection.openOutputStream();
            out.write(new byte[]{'P', 'I', 'N', 'G'}, 0, 4);
            out.flush();
            echoed = readExactly(connection.openInputStream(), 4);
        } finally {
            connection.close();
        }
    }

    /** A port of the game's own, and somebody dialling in to it. */
    private void listenForAPeer() throws Exception {
        ServerSocketConnection server =
                (ServerSocketConnection) Connector.open("socket://:7200");
        try {
            SocketConnection caller =
                    (SocketConnection) Connector.open("socket://127.0.0.1:7200");
            try {
                OutputStream out = caller.openOutputStream();
                out.write(new byte[]{'J', 'O', 'I', 'N'}, 0, 4);
                out.flush();
                StreamConnection incoming = server.acceptAndOpen();
                try {
                    accepted = readExactly(incoming.openInputStream(), 4);
                } finally {
                    incoming.close();
                }
            } finally {
                caller.close();
            }
        } finally {
            server.close();
        }
    }

    /** One packet, fired off and caught again. */
    private void sendAPacket() throws Exception {
        UDPDatagramConnection port =
                (UDPDatagramConnection) Connector.open("datagram://:7100");
        try {
            Datagram outgoing = port.newDatagram(64, "datagram://127.0.0.1:7100");
            outgoing.writeUTF("MOVE 3 4");
            port.send(outgoing);

            Datagram incoming = port.newDatagram(64);
            port.receive(incoming);
            packet = incoming.readUTF();
            packetFrom = incoming.getAddress();
        } finally {
            port.close();
        }
    }

    /** A scheme the emulator does not carry has to say so, not pretend. */
    private void tryTheUnsupported() {
        try {
            Connector.open("comm://COM1:9600");
            refused = "opened";
        } catch (Exception e) {
            refused = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private static String readExactly(InputStream in, int count) throws Exception {
        byte[] buffer = new byte[count];
        int filled = 0;
        while (filled < count) {
            int read = in.read(buffer, filled, count - filled);
            if (read < 0) {
                break;
            }
            filled += read;
        }
        return new String(buffer, 0, filled);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
