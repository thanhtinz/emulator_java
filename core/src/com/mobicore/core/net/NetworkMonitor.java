package com.mobicore.core.net;

import com.mobicore.core.storage.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records every connection a game attempts, for the network monitor screen.
 *
 * <p>Being able to see exactly what a twenty-year-old game sends is the point:
 * it is how a user decides whether to allow a host, and how a developer
 * migrates a dead backend to a new one.</p>
 */
public final class NetworkMonitor {

    /** One attempted connection and what came back. */
    public static final class Exchange {

        private final String url;
        private final String method;
        private final long startedAt;
        private final Map<String, String> requestHeaders = new LinkedHashMap<String, String>();
        private final Map<String, String> responseHeaders = new LinkedHashMap<String, String>();

        private int decision = NetworkPolicy.ASK;
        private int status;
        private String outcome = "pending";
        private long finishedAt;
        private int requestBytes;
        private int responseBytes;
        private String requestPreview;
        private String responsePreview;
        private byte[] requestSample = new byte[0];
        private byte[] responseSample = new byte[0];

        Exchange(String url, String method, long startedAt) {
            this.url = url;
            this.method = method;
            this.startedAt = startedAt;
        }

        public String url() {
            return url;
        }

        public String host() {
            return NetworkPolicy.hostOf(url);
        }

        public String method() {
            return method;
        }

        public long startedAt() {
            return startedAt;
        }

        public long durationMs() {
            return finishedAt == 0 ? 0 : finishedAt - startedAt;
        }

        public int decision() {
            return decision;
        }

        public int status() {
            return status;
        }

        public String outcome() {
            return outcome;
        }

        public int requestBytes() {
            return requestBytes;
        }

        public int responseBytes() {
            return responseBytes;
        }

        public String requestPreview() {
            return requestPreview;
        }

        public String responsePreview() {
            return responsePreview;
        }

        public Map<String, String> requestHeaders() {
            return Collections.unmodifiableMap(requestHeaders);
        }

        public Map<String, String> responseHeaders() {
            return Collections.unmodifiableMap(responseHeaders);
        }

        public void addRequestHeader(String name, String value) {
            requestHeaders.put(name, value);
        }

        public void addResponseHeader(String name, String value) {
            responseHeaders.put(name, value);
        }

        public void setDecision(int decision) {
            this.decision = decision;
        }

        public void complete(int status, String outcome, long finishedAt) {
            this.status = status;
            this.outcome = outcome;
            this.finishedAt = finishedAt;
        }

        public void recordRequestBody(byte[] body, boolean keepPreview, int limit) {
            requestBytes = body == null ? 0 : body.length;
            if (keepPreview && body != null) {
                requestPreview = preview(body, limit);
            }
        }

        /**
         * Counts bytes a still-open connection has sent.
         *
         * <p>A socket has no single request body: it carries whatever the game
         * types for as long as the connection lives. So the counters add up
         * rather than being set, and the preview keeps only the opening of the
         * conversation — which is the part that says what protocol this is.</p>
         */
        public void addRequestBytes(byte[] data, int offset, int length,
                                    boolean keepPreview, int limit) {
            requestBytes += Math.max(0, length);
            if (keepPreview && data != null && length > 0) {
                requestSample = append(requestSample, data, offset, length, limit);
                requestPreview = preview(requestSample, limit);
            }
        }

        /** Counts bytes a still-open connection has received. */
        public void addResponseBytes(byte[] data, int offset, int length,
                                     boolean keepPreview, int limit) {
            responseBytes += Math.max(0, length);
            if (keepPreview && data != null && length > 0) {
                responseSample = append(responseSample, data, offset, length, limit);
                responsePreview = preview(responseSample, limit);
            }
        }

        private static byte[] append(byte[] sample, byte[] data, int offset, int length,
                                     int limit) {
            int room = limit - sample.length;
            if (room <= 0) {
                return sample;
            }
            int take = Math.min(room, length);
            byte[] grown = new byte[sample.length + take];
            System.arraycopy(sample, 0, grown, 0, sample.length);
            System.arraycopy(data, offset, grown, sample.length, take);
            return grown;
        }

        public void recordResponseBody(byte[] body, boolean keepPreview, int limit) {
            responseBytes = body == null ? 0 : body.length;
            if (keepPreview && body != null) {
                responsePreview = preview(body, limit);
            }
        }

        /** Printable preview; binary payloads are summarised, not dumped. */
        private static String preview(byte[] body, int limit) {
            int length = Math.min(body.length, limit);
            int printable = 0;
            for (int i = 0; i < length; i++) {
                int b = body[i] & 0xFF;
                if (b == '\n' || b == '\r' || b == '\t' || (b >= 0x20 && b < 0x7F)) {
                    printable++;
                }
            }
            if (length == 0) {
                return "";
            }
            if (printable * 10 < length * 8) {
                return "<" + body.length + " bytes of binary data>";
            }
            try {
                return new String(body, 0, length, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                return new String(body, 0, length);
            }
        }

        public Map<String, Object> toJson() {
            Map<String, Object> json = Json.object();
            json.put("url", url);
            json.put("host", host());
            json.put("method", method);
            json.put("decision", Integer.valueOf(decision));
            json.put("status", Integer.valueOf(status));
            json.put("outcome", outcome);
            json.put("durationMs", Long.valueOf(durationMs()));
            json.put("requestBytes", Integer.valueOf(requestBytes));
            json.put("responseBytes", Integer.valueOf(responseBytes));
            if (requestPreview != null) {
                json.put("requestPreview", requestPreview);
            }
            if (responsePreview != null) {
                json.put("responsePreview", responsePreview);
            }
            return json;
        }
    }

    private final List<Exchange> exchanges = new ArrayList<Exchange>();
    private int limit = 200;

    public void setLimit(int limit) {
        this.limit = Math.max(8, limit);
    }

    public synchronized Exchange begin(String url, String method, long now) {
        Exchange exchange = new Exchange(url, method, now);
        exchanges.add(exchange);
        while (exchanges.size() > limit) {
            exchanges.remove(0);
        }
        return exchange;
    }

    public synchronized List<Exchange> exchanges() {
        return new ArrayList<Exchange>(exchanges);
    }

    public synchronized int size() {
        return exchanges.size();
    }

    public synchronized void clear() {
        exchanges.clear();
    }

    /** Bytes sent and received across every recorded exchange. */
    public synchronized int[] totals() {
        int sent = 0;
        int received = 0;
        for (Exchange exchange : exchanges) {
            sent += exchange.requestBytes();
            received += exchange.responseBytes();
        }
        return new int[]{sent, received};
    }

    public synchronized String toJson() {
        Map<String, Object> root = Json.object();
        List<Object> list = new ArrayList<Object>();
        for (Exchange exchange : exchanges) {
            list.add(exchange.toJson());
        }
        root.put("exchanges", list);
        int[] totals = totals();
        root.put("bytesSent", Integer.valueOf(totals[0]));
        root.put("bytesReceived", Integer.valueOf(totals[1]));
        return Json.write(root);
    }
}
