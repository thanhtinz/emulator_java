package com.mobicore.core.net;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries one request out to the world.
 *
 * <p>The emulator never talks to a socket directly: routing through this
 * interface is what lets the policy layer sit in front, what lets a test serve
 * canned responses, and what lets a dead backend be redirected to a modern one
 * without patching the game.</p>
 */
public interface NetworkTransport {

    /** An outgoing request. */
    final class Request {

        public final String url;
        public String method = "GET";
        public final Map<String, String> headers = new LinkedHashMap<String, String>();
        public byte[] body;
        public int timeoutMs = 15000;

        public Request(String url) {
            this.url = url;
        }
    }

    /** What came back. */
    final class Response {

        public final int status;
        public final String message;
        public final Map<String, String> headers = new LinkedHashMap<String, String>();
        public final byte[] body;

        public Response(int status, String message, byte[] body) {
            this.status = status;
            this.message = message == null ? "" : message;
            this.body = body == null ? new byte[0] : body;
        }
    }

    Response execute(Request request) throws IOException;
}
