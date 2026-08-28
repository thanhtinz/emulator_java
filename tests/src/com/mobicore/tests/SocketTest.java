package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.net.LoopbackSockets;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.net.RealSockets;
import com.mobicore.core.net.SocketTransport;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * {@code socket://} and {@code datagram://}: connections that stay open.
 *
 * <p>The fixture is a real MIDlet holding a real conversation — it writes to a
 * server and reads the answer, opens a port and accepts somebody dialling in,
 * and fires a packet off and catches it again. What it leaves in its fields is
 * what this checks, because a socket layer that opened nothing and returned
 * empty strings would pass a test that only looked for the absence of an
 * exception.</p>
 */
public final class SocketTest extends Test {

    private final String fixtureDir;

    public SocketTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Nối thẳng bằng socket";
    }

    @Override
    public void run() throws Exception {
        addresses();
        loopbackCarriesAConversation();
        thePolicyStandsInFront();
        packetsGoBothWays();
        stoppingHangsUp();
        addressesThatCannotWork();
        everyLineHasAName();
        if (!new File(fixtureDir, "demo/SocketDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        runsAsBytecode();
    }

    // -------------------------------------------------------------- parsing

    private void addresses() {
        eq("lobby.test", com.mobicore.core.midp.MidpSockets.hostPart("socket://lobby.test:7000"),
                "a host and port split apart");
        eq(7000, com.mobicore.core.midp.MidpSockets.portPart("socket://lobby.test:7000"),
                "and the port is a number");
        // socket://:7200 is not a connection to nowhere: it is the game asking
        // to be dialled in to, and the empty host is how it says so.
        eq("", com.mobicore.core.midp.MidpSockets.hostPart("socket://:7200"),
                "a URL with no host names no host");
        eq(7200, com.mobicore.core.midp.MidpSockets.portPart("socket://:7200"),
                "but still names a port");
        eq(NetworkPolicy.DENY, new NetworkPolicy().decideHost(null),
                "and a connection with no host to judge is refused");
    }

    // ------------------------------------------------------------- loopback

    private void loopbackCarriesAConversation() throws Exception {
        LoopbackSockets sockets = new LoopbackSockets().serve(7000, LoopbackSockets.echo());
        NetworkStack stack = allowAll(sockets);

        SocketTransport.Stream stream = stack.openSocket("lobby.test", 7000, 1000);
        OutputStream out = stream.output();
        out.write("HELLO".getBytes("UTF-8"));
        out.flush();
        eq("HELLO", read(stream.input(), 5), "what goes down the socket comes back");
        stream.close();

        eq("[lobby.test:7000]", sockets.dialled().toString(), "and the address dialled is recorded");

        NetworkMonitor.Exchange exchange = last(stack);
        eq("SOCKET", exchange.method(), "the monitor records it as a socket, not a request");
        eq("socket://lobby.test:7000", exchange.url(), "with the address the game asked for");
        // A socket has no single request body: these counters have to add up
        // over the life of the connection or they would always read zero.
        eq(5, exchange.requestBytes(), "counting every byte the game sent");
        eq(5, exchange.responseBytes(), "and every byte that came back");
        eq("HELLO", exchange.requestPreview(), "and keeping the opening of the conversation");
        eq("closed", exchange.outcome(), "and noting when the line was hung up");

        // Nobody is listening on 7001. A refusal is the answer, not a hang.
        try {
            stack.openSocket("lobby.test", 7001, 1000);
            fail("a port nobody is on should refuse the connection");
        } catch (IOException e) {
            check(e.getMessage().indexOf("refused") >= 0,
                    "a port nobody is on refuses: " + e.getMessage());
        }
    }

    private void thePolicyStandsInFront() throws Exception {
        LoopbackSockets sockets = new LoopbackSockets().serve(7000, LoopbackSockets.echo());
        NetworkPolicy policy = new NetworkPolicy();
        policy.setMode(GameProfile.NETWORK_BLOCKED);
        NetworkStack stack = new NetworkStack(policy);
        stack.setSocketTransport(sockets);

        try {
            stack.openSocket("lobby.test", 7000, 1000);
            fail("a blocked game should not reach a socket");
        } catch (IOException e) {
            check(e.getMessage().indexOf("blocked") >= 0,
                    "a blocked game is stopped before it dials: " + e.getMessage());
        }
        eq(0, sockets.dialled().size(), "and nothing was dialled at all");
        eq(NetworkPolicy.DENY, last(stack).decision(),
                "while the attempt is still on the record");

        // Listening has no host to name, so it is remembered against the
        // device itself — asked once, not every time the game opens a port.
        policy.setMode(GameProfile.NETWORK_ASK);
        policy.denyHost(NetworkPolicy.THIS_DEVICE);
        try {
            stack.openServer(7200);
            fail("listening should obey the same policy as dialling out");
        } catch (IOException e) {
            check(e.getMessage().indexOf("listening") >= 0,
                    "and opening a port is refused too: " + e.getMessage());
        }
    }

    private void packetsGoBothWays() throws Exception {
        LoopbackSockets sockets = new LoopbackSockets();
        NetworkStack stack = allowAll(sockets);

        SocketTransport.Datagrams alice = stack.openDatagrams(7100);
        SocketTransport.Datagrams bob = stack.openDatagrams(7101);
        alice.send("127.0.0.1", 7101, "MOVE".getBytes("UTF-8"), 0, 4);

        byte[] buffer = new byte[64];
        SocketTransport.Packet packet = bob.receive(buffer, 0, buffer.length);
        eq(4, packet.length, "the packet arrives whole");
        eq("MOVE", new String(buffer, 0, packet.length, "UTF-8"), "with the bytes that were sent");
        eq(7100, packet.port, "and says which port it came from, so a reply can go back");

        // A packet sent where nobody is listening is dropped, the way UDP
        // drops it. Raising here would teach a game the wrong lesson.
        alice.send("127.0.0.1", 7999, "LOST".getBytes("UTF-8"), 0, 4);
        alice.close();
        bob.close();
    }

    /**
     * A blocked read is the one place counting instructions cannot help.
     */
    private void stoppingHangsUp() throws Exception {
        LoopbackSockets sockets = new LoopbackSockets();
        final NetworkStack stack = allowAll(sockets);
        sockets.serve(7000, new LoopbackSockets.Peer() {
            public void talk(SocketTransport.Stream connection) throws IOException {
                // A server that accepts the connection and then says nothing:
                // exactly the shape of a lobby whose machine has gone away.
            }
        });
        final SocketTransport.Stream stream = stack.openSocket("lobby.test", 7000, 1000);
        Thread stopper = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                stack.closeAll();
            }
        });
        stopper.setDaemon(true);
        stopper.start();

        long started = System.currentTimeMillis();
        int value = -2;
        try {
            value = stream.input().read();
        } catch (IOException e) {
            value = -1;
        }
        long waited = System.currentTimeMillis() - started;
        check(value < 0, "a read on a hung-up connection ends instead of waiting");
        check(waited < 5000, "and it ends when the player stops the game, in " + waited + "ms");
    }

    // ------------------------------------------------------------- bytecode

    private void runsAsBytecode() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("MobiCore");
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        profile.setNetworkMode(GameProfile.NETWORK_ALLOWED);
        EmulatorSession session = EmulatorSession.create(suite, profile, vfs, layout, null);
        LoopbackSockets sockets = new LoopbackSockets().serve(7000, LoopbackSockets.echo());
        session.network().setSocketTransport(sockets);
        session.start("demo.SocketDemo");

        VmObject midlet = session.midlet();
        eq("PING", session.vm().stringOf(midlet.get("echoed")),
                "a real MIDlet writes to a server and reads the answer back");
        eq("lobby.test:7000", session.vm().stringOf(midlet.get("peer")),
                "and can say who it is talking to");
        check(((Integer) midlet.get("localPort")).intValue() > 0,
                "and which port its own end was given");
        eq("JOIN", session.vm().stringOf(midlet.get("accepted")),
                "a port the game opened accepts somebody dialling in");
        eq("MOVE 3 4", session.vm().stringOf(midlet.get("packet")),
                "and a packet written with writeUTF reads back with readUTF");
        eq("datagram://127.0.0.1:7100", session.vm().stringOf(midlet.get("packetFrom")),
                "carrying the sender, which is how a game answers a packet");
        // A scheme the emulator does not carry has to say so. Pretending the
        // connection opened would leave the game reading from nothing.
        check(session.vm().stringOf(midlet.get("refused")).indexOf("comm") >= 0,
                "and a scheme the emulator does not carry says which one: "
                        + session.vm().stringOf(midlet.get("refused")));

        List<NetworkMonitor.Exchange> exchanges = session.network().monitor().exchanges();
        check(exchanges.size() >= 4,
                "every connection the game opened is on the record, not just the first");
        session.destroy();
    }

    // ------------------------------------------------- địa chỉ không dùng được

    /**
     * Số cổng vô lý phải ra {@code IOException}, không phải một lỗi giết game.
     *
     * <p>Số cổng hiếm khi được viết cứng trong game: nó đến từ người chơi gõ
     * vào ô "địa chỉ máy chủ", từ một dòng máy chủ gửi về, từ một tệp cấu
     * hình. Một con số vô lý vì thế là chuyện thường ngày — và bên dưới,
     * {@code java.net} trả lời chuyện ấy bằng một lỗi <em>không ai bắt
     * được</em>. Game viết sẵn {@code try/catch (IOException)} quanh chỗ mở
     * kết nối thì tự xử lý được; còn lỗi kia thì cả khung hình chết theo.</p>
     */
    private void addressesThatCannotWork() throws Exception {
        // Thử trên cả hai đường truyền. Đường trong bộ nhớ thì hiền, còn
        // java.net mới là chỗ ném ra lỗi không ai bắt được — kiểm mỗi đường
        // hiền thì bài kiểm tra này chẳng canh được cái gì.
        cannotWork(allowAll(new LoopbackSockets()), true);
        cannotWork(allowAll(new RealSockets()), false);
    }

    /**
     * @param canSend đường truyền này mở được cổng gói tin để thử gửi hay không
     */
    private void cannotWork(NetworkStack stack, boolean canSend) throws Exception {
        int[] impossible = {-1, 0, 65536, 99999, Integer.MAX_VALUE};
        for (int i = 0; i < impossible.length; i++) {
            int port = impossible[i];
            check(refused(stack, port), "gọi ra cổng " + port + " thì báo lỗi bắt được");
        }

        // Cổng 0 khi mở cổng chờ lại hợp lệ: đó là cách nói "cổng nào trống
        // cũng được", và máy tự chọn một cổng thật.
        SocketTransport.Server any = stack.openServer(0);
        check(any.localPort() > 0, "mở cổng 0 nghĩa là để máy tự chọn, và nó chọn một cổng thật");
        any.close();

        int[] badBind = {-1, 65536, 99999};
        for (int i = 0; i < badBind.length; i++) {
            try {
                stack.openServer(badBind[i]);
                check(false, "mở cổng chờ ở " + badBind[i] + " phải báo lỗi");
            } catch (IOException expected) {
                check(expected.getMessage().indexOf(String.valueOf(badBind[i])) >= 0,
                        "và nói ra con số sai: " + expected.getMessage());
            }
            try {
                stack.openDatagrams(badBind[i]);
                check(false, "mở cổng gói tin ở " + badBind[i] + " phải báo lỗi");
            } catch (IOException expected) {
                check(expected.getMessage().length() > 0, "kèm lý do");
            }
        }

        if (!canSend) {
            return;
        }
        // Gửi một gói tới cổng vô lý cũng vậy: địa chỉ gói tin thường do game
        // tự ghép từ thứ nó vừa đọc được.
        SocketTransport.Datagrams port = stack.openDatagrams(0);
        byte[] payload = {1, 2, 3};
        for (int i = 0; i < impossible.length; i++) {
            try {
                port.send("127.0.0.1", impossible[i], payload, 0, payload.length);
                check(false, "gửi gói tới cổng " + impossible[i] + " phải báo lỗi");
            } catch (IOException expected) {
                check(expected.getMessage().length() > 0, "kèm lý do");
            }
        }
        port.close();

        // Và dùng lại một thứ đã đóng thì cũng là lỗi bắt được, không phải
        // một lỗi trống rỗng.
        try {
            port.send("127.0.0.1", 9000, payload, 0, payload.length);
            check(false, "gửi qua một cổng đã đóng phải báo lỗi");
        } catch (IOException expected) {
            check(expected.getMessage() != null, "và nói ra là đã đóng");
        }
    }

    /**
     * Bảng theo dõi phải gọi được tên chỗ game đang nói chuyện.
     *
     * <p>Mở một cổng trên chính máy đang chơi thì không có tên máy nào ở đầu
     * bên kia, và trước đây bảng theo dõi in ra đúng chữ "null" — một câu trả
     * lời không nói gì với người đọc.</p>
     */
    private void everyLineHasAName() throws Exception {
        NetworkStack stack = allowAll(new LoopbackSockets());
        SocketTransport.Server server = stack.openServer(7100);
        NetworkMonitor.Exchange listening = last(stack);
        eq(NetworkPolicy.THIS_DEVICE, listening.host(),
                "mở cổng chờ được ghi dưới tên chính máy này");
        eq("máy này", listening.hostLabel(), "và hiện lên màn hình bằng tiếng người");
        server.close();

        // Không cần ai nhấc máy: cái đang kiểm là dòng ghi lại, và một cuộc
        // gọi không ai nghe vẫn được ghi dưới tên máy nó gọi.
        try {
            stack.openSocket("lobby.test", 7000, 200).close();
        } catch (IOException refused) {
            // không sao
        }
        eq("lobby.test", last(stack).hostLabel(), "còn gọi ra ngoài thì gọi đúng tên máy kia");
    }

    /** True khi mở kết nối tới cổng ấy báo về một lỗi game bắt được. */
    private boolean refused(NetworkStack stack, int port) {
        try {
            stack.openSocket("127.0.0.1", port, 200);
            return false;
        } catch (IOException expected) {
            return true;
        } catch (RuntimeException escaped) {
            return false;
        }
    }

    // ------------------------------------------------------------- plumbing

    private static NetworkStack allowAll(SocketTransport sockets) {
        NetworkPolicy policy = new NetworkPolicy();
        policy.setMode(GameProfile.NETWORK_ALLOWED);
        NetworkStack stack = new NetworkStack(policy);
        stack.setSocketTransport(sockets);
        return stack;
    }

    private static NetworkMonitor.Exchange last(NetworkStack stack) {
        List<NetworkMonitor.Exchange> exchanges = stack.monitor().exchanges();
        return exchanges.get(exchanges.size() - 1);
    }

    private static String read(InputStream in, int count) throws IOException {
        byte[] buffer = new byte[count];
        int filled = 0;
        while (filled < count) {
            int read = in.read(buffer, filled, count - filled);
            if (read < 0) {
                break;
            }
            filled += read;
        }
        return new String(buffer, 0, filled, "UTF-8");
    }
}
