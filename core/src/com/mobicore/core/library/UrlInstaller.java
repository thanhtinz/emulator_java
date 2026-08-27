package com.mobicore.core.library;

import com.mobicore.core.jar.AttributeSet;
import com.mobicore.core.net.NetworkTransport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs a game from a link.
 *
 * <p>These games live on the web — an archive site, a forum post, a friend's
 * folder — and every one of them arrives as a link before it arrives as a
 * file. Making the player fetch it in a browser, find it in Downloads and then
 * pick it out of a file chooser is three steps for something the emulator can
 * do in one.</p>
 *
 * <p>Two kinds of link. A <strong>.jar</strong> is the game itself and is
 * enough on its own. A <strong>.jad</strong> is the descriptor a handset was
 * meant to be given first: it names the JAR in {@code MIDlet-Jar-URL}, so that
 * is fetched too — relative to the descriptor, because a JAD on a web page
 * almost always names its JAR as a bare file name beside it.</p>
 *
 * <p>What comes back is checked before it is installed. A link that is wrong,
 * expired, or points at a login page returns a web page, and a web page
 * installed as a game is a game that fails later and less clearly — so the
 * bytes are looked at: a JAR starts with {@code PK}, and a descriptor has to
 * parse and name a MIDlet.</p>
 */
public final class UrlInstaller {

    /** How large a download may be before it is refused. */
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    /** What was fetched, and what it turned out to be. */
    public static final class Download {

        private final byte[] jar;
        private final byte[] jad;
        private final String jarUrl;
        private final List<String> notes;

        Download(byte[] jar, byte[] jad, String jarUrl, List<String> notes) {
            this.jar = jar;
            this.jad = jad;
            this.jarUrl = jarUrl;
            this.notes = notes;
        }

        public byte[] jar() {
            return jar;
        }

        /** The descriptor, or null when the link went straight to a JAR. */
        public byte[] jad() {
            return jad;
        }

        /** Where the JAR itself came from, which may not be the link given. */
        public String jarUrl() {
            return jarUrl;
        }

        /** What happened, in words the player can read. */
        public List<String> notes() {
            return notes;
        }
    }

    /** Fetches one URL; the caller decides what is allowed to be fetched. */
    public interface Fetcher {
        NetworkTransport.Response fetch(String url) throws IOException;
    }

    private UrlInstaller() {
    }

    /**
     * Fetches whatever the link points at and returns something installable.
     *
     * @throws IOException with a message meant for the player, not a log
     */
    public static Download fetch(Fetcher fetcher, String url) throws IOException {
        String link = url == null ? "" : url.trim();
        if (link.length() == 0) {
            throw new IOException("Chưa có liên kết nào");
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            throw new IOException("Chỉ tải được liên kết http hoặc https");
        }
        List<String> notes = new ArrayList<String>();
        byte[] body = body(fetcher, link, notes);

        if (isJar(body)) {
            notes.add("Tải được tệp .jar — " + kilobytes(body.length));
            return new Download(body, null, link, notes);
        }

        // Not a JAR, so it had better be a descriptor. A descriptor is text
        // and names the JAR beside it.
        AttributeSet descriptor = AttributeSet.parse(text(body));
        String jarUrl = descriptor.get("MIDlet-Jar-URL");
        if (jarUrl == null || jarUrl.trim().length() == 0) {
            throw new IOException("Liên kết này không phải tệp game "
                    + "(.jar hoặc .jad): " + describe(body));
        }
        notes.add("Tải được tệp .jad của " + name(descriptor));

        String resolved = resolve(link, jarUrl.trim());
        notes.add("Tệp .jar nằm ở " + resolved);
        byte[] jar = body(fetcher, resolved, notes);
        if (!isJar(jar)) {
            throw new IOException("Tệp .jad chỉ tới một thứ không phải .jar: " + describe(jar));
        }
        notes.add("Tải được tệp .jar — " + kilobytes(jar.length));
        return new Download(jar, body, resolved, notes);
    }

    private static byte[] body(Fetcher fetcher, String url, List<String> notes)
            throws IOException {
        NetworkTransport.Response response;
        try {
            response = fetcher.fetch(url);
        } catch (IOException e) {
            // Whatever the transport says about a failed connection is written
            // for a log — a host name, a socket, a stack. The player needs the
            // one thing that helps: which address did not answer.
            throw new IOException("Không kết nối được tới " + hostOf(url));
        }
        if (response.status == 404) {
            throw new IOException("Không có gì ở liên kết này (404)");
        }
        if (response.status >= 400) {
            throw new IOException("Máy chủ trả về lỗi " + response.status
                    + (response.message.length() > 0 ? " — " + response.message : ""));
        }
        if (response.body.length == 0) {
            throw new IOException("Liên kết trả về tệp rỗng");
        }
        if (response.body.length > MAX_BYTES) {
            // A J2ME game is a few hundred kilobytes; anything this large is
            // not one, and a phone should not spend its memory finding out.
            throw new IOException("Tệp quá lớn (" + kilobytes(response.body.length) + ")");
        }
        return response.body;
    }

    /**
     * Turns the JAR URL in a descriptor into one that can be fetched.
     *
     * <p>A JAD names its JAR however the person who wrote it felt like: a full
     * address, a path from the site's root, or — most often — just the file
     * name, meaning "beside this descriptor".</p>
     */
    public static String resolve(String jadUrl, String jarUrl) {
        if (jarUrl.startsWith("http://") || jarUrl.startsWith("https://")) {
            return jarUrl;
        }
        int scheme = jadUrl.indexOf("://");
        int rootSlash = scheme < 0 ? -1 : jadUrl.indexOf('/', scheme + 3);
        String origin = rootSlash < 0 ? jadUrl : jadUrl.substring(0, rootSlash);
        if (jarUrl.startsWith("/")) {
            return origin + jarUrl;
        }
        int lastSlash = jadUrl.lastIndexOf('/');
        String folder = lastSlash > scheme + 2 ? jadUrl.substring(0, lastSlash + 1) : origin + "/";
        return folder + jarUrl;
    }

    /** The host part of a URL, for a message about a connection that failed. */
    private static String hostOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url.substring(scheme + 3) : url.substring(scheme + 3, slash);
    }

    /** A JAR is a zip, and every zip starts the same two bytes. */
    public static boolean isJar(byte[] body) {
        return body != null && body.length > 4 && body[0] == 'P' && body[1] == 'K'
                && (body[2] == 3 || body[2] == 5 || body[2] == 7);
    }

    private static String name(AttributeSet descriptor) {
        String name = descriptor.get("MIDlet-Name");
        return name == null || name.length() == 0 ? "một game không tên" : name;
    }

    /**
     * What arrived, said plainly.
     *
     * <p>"Không phải tệp game" on its own leaves the player guessing; naming a
     * web page tells them the link needs opening in a browser first, which is
     * usually exactly what happened.</p>
     */
    private static String describe(byte[] body) {
        String head = text(body).trim().toLowerCase();
        if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
            return "đây là một trang web, không phải tệp game";
        }
        if (body.length > 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B) {
            return "đây là tệp nén .gz";
        }
        return kilobytes(body.length) + " dữ liệu không đọc được";
    }

    private static String text(byte[] body) {
        try {
            int length = Math.min(body.length, 64 * 1024);
            return new String(body, 0, length, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String kilobytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + " KB";
    }
}
