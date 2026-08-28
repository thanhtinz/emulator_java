package com.mobicore.tests.net;

import com.mobicore.core.net.NetworkTransport;
import com.mobicore.core.net.SocketTransport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves canned responses without leaving the device.
 *
 * <p>This is the local server testing the specification asks for, and the
 * first half of a server bridge: a game whose backend is gone can be pointed
 * here, and its traffic answered by a rule the user configured.</p>
 */
public final class LoopbackTransport implements NetworkTransport {

    /** One canned answer, matched by a substring of the URL. */
    public static final class Rule {

        final String urlContains;
        final Response response;

        Rule(String urlContains, Response response) {
            this.urlContains = urlContains;
            this.response = response;
        }
    }

    private final List<Rule> rules = new ArrayList<Rule>();
    private final List<Request> received = new ArrayList<Request>();
    private NetworkTransport fallback;

    /** Requests the game made, in order; the network monitor reads these too. */
    public List<Request> received() {
        return new ArrayList<Request>(received);
    }

    /** Where unmatched requests go; refused when unset. */
    public void setFallback(NetworkTransport fallback) {
        this.fallback = fallback;
    }

    public LoopbackTransport respond(String urlContains, int status, String body) {
        return respond(urlContains, status, "OK", body, "text/plain");
    }

    public LoopbackTransport respond(String urlContains, int status, String message,
                                     String body, String contentType) {
        byte[] payload;
        try {
            payload = body == null ? new byte[0] : body.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            payload = body == null ? new byte[0] : body.getBytes();
        }
        Response response = new Response(status, message, payload);
        response.headers.put("Content-Type", contentType);
        response.headers.put("Content-Length", String.valueOf(payload.length));
        rules.add(new Rule(urlContains, response));
        return this;
    }

    /**
     * A canned answer that is not text: a JAR, a picture, a save file.
     *
     * <p>Needed because some of what a device fetches is not a string, and
     * turning bytes into a string and back would not survive the trip.</p>
     */
    public LoopbackTransport respondBytes(String urlContains, int status, byte[] body,
                                          String contentType) {
        Response response = new Response(status, "OK", body);
        response.headers.put("Content-Type", contentType);
        response.headers.put("Content-Length", String.valueOf(body == null ? 0 : body.length));
        rules.add(new Rule(urlContains, response));
        return this;
    }

    @Override
    public Response execute(Request request) throws IOException {
        received.add(request);
        for (Rule rule : rules) {
            if (request.url.indexOf(rule.urlContains) >= 0) {
                Response source = rule.response;
                Response copy = new Response(source.status, source.message, source.body);
                for (Map.Entry<String, String> header
                        : new LinkedHashMap<String, String>(source.headers).entrySet()) {
                    copy.headers.put(header.getKey(), header.getValue());
                }
                return copy;
            }
        }
        if (fallback != null) {
            return fallback.execute(request);
        }
        throw new IOException("No local rule matches " + request.url);
    }
}
