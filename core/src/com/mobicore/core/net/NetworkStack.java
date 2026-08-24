package com.mobicore.core.net;

import java.io.IOException;
import java.util.Map;

/**
 * Policy, monitoring and transport in front of every connection a game opens.
 *
 * <p>Nothing reaches the network without passing through here, which is what
 * makes the "warn before a game connects" rule enforceable rather than
 * advisory.</p>
 */
public final class NetworkStack {

    /** Asked when the policy cannot decide on its own. */
    public interface PermissionPrompt {

        /**
         * @return true to allow this host; the answer is remembered
         */
        boolean allowHost(String host, String url);
    }

    private final NetworkPolicy policy;
    private final NetworkMonitor monitor = new NetworkMonitor();
    private NetworkTransport transport = new BlockedTransport();
    private PermissionPrompt prompt;
    private Clock clock = new Clock() {
        public long now() {
            return System.currentTimeMillis();
        }
    };

    /** Injectable so tests and screenshots are reproducible. */
    public interface Clock {
        long now();
    }

    public NetworkStack(NetworkPolicy policy) {
        this.policy = policy == null ? new NetworkPolicy() : policy;
    }

    public NetworkPolicy policy() {
        return policy;
    }

    public NetworkMonitor monitor() {
        return monitor;
    }

    public NetworkTransport transport() {
        return transport;
    }

    public void setTransport(NetworkTransport transport) {
        this.transport = transport == null ? new BlockedTransport() : transport;
    }

    public void setPrompt(PermissionPrompt prompt) {
        this.prompt = prompt;
    }

    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    /**
     * Runs a request through the policy, records it, and returns the response.
     *
     * @throws IOException when the host is refused or the transport fails
     */
    public NetworkTransport.Response perform(NetworkTransport.Request request) throws IOException {
        NetworkMonitor.Exchange exchange = monitor.begin(request.url, request.method, clock.now());
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            exchange.addRequestHeader(header.getKey(), header.getValue());
        }
        exchange.recordRequestBody(request.body, policy.logBodies(), policy.maxBodyBytes());

        int decision = policy.decide(request.url);
        String host = NetworkPolicy.hostOf(request.url);
        if (decision == NetworkPolicy.ASK) {
            if (prompt == null) {
                // Nobody can answer right now. Refuse this attempt, but do not
                // record a decision: the user has not said no, and a game must
                // not be permanently cut off because a prompt was unavailable.
                decision = NetworkPolicy.DENY;
            } else if (prompt.allowHost(host, request.url)) {
                policy.allowHost(host);
                decision = NetworkPolicy.ALLOW;
            } else {
                policy.denyHost(host);
                decision = NetworkPolicy.DENY;
            }
        }
        exchange.setDecision(decision);

        if (decision != NetworkPolicy.ALLOW) {
            exchange.complete(0, "blocked", clock.now());
            throw new IOException("MobiCore blocked a connection to " + host);
        }

        try {
            NetworkTransport.Response response = transport.execute(request);
            for (Map.Entry<String, String> header : response.headers.entrySet()) {
                exchange.addResponseHeader(header.getKey(), header.getValue());
            }
            exchange.recordResponseBody(response.body, policy.logBodies(), policy.maxBodyBytes());
            exchange.complete(response.status, "ok", clock.now());
            return response;
        } catch (IOException e) {
            exchange.complete(0, "failed: " + e.getMessage(), clock.now());
            throw e;
        }
    }
}
