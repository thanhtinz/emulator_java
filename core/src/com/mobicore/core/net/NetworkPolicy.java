package com.mobicore.core.net;

import com.mobicore.core.model.GameProfile;
import com.mobicore.core.storage.Json;
import com.mobicore.core.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a game may open a connection.
 *
 * <p>Old J2ME games phone home to servers that no longer exist, or to ones the
 * player has no relationship with. The specification requires warning before a
 * game reaches the network, so the default is to ask, and every decision is
 * recorded per host rather than granted blanket.</p>
 */
public final class NetworkPolicy {

    public static final int ALLOW = 0;
    public static final int DENY = 1;
    public static final int ASK = 2;

    /**
     * Stands in for the far end when a game opens a port on this device.
     *
     * <p>Listening has no host to name, but it is still a decision worth
     * remembering: the player is allowing this game to accept connections,
     * and they should be asked once rather than every time it listens.</p>
     */
    public static final String THIS_DEVICE = "this-device";

    private final List<String> allowed = new ArrayList<String>();
    private final List<String> denied = new ArrayList<String>();
    private int mode = GameProfile.NETWORK_ASK;
    private boolean logBodies = true;
    private int maxBodyBytes = 8192;

    public int mode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public boolean logBodies() {
        return logBodies;
    }

    public void setLogBodies(boolean logBodies) {
        this.logBodies = logBodies;
    }

    public int maxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = Math.max(0, maxBodyBytes);
    }

    /** Adds a host the game may reach without prompting again. */
    public void allowHost(String host) {
        String key = normalise(host);
        if (key != null && !allowed.contains(key)) {
            allowed.add(key);
            denied.remove(key);
        }
    }

    public void denyHost(String host) {
        String key = normalise(host);
        if (key != null && !denied.contains(key)) {
            denied.add(key);
            allowed.remove(key);
        }
    }

    public void forget(String host) {
        String key = normalise(host);
        allowed.remove(key);
        denied.remove(key);
    }

    public List<String> allowedHosts() {
        return Collections.unmodifiableList(allowed);
    }

    public List<String> deniedHosts() {
        return Collections.unmodifiableList(denied);
    }

    /**
     * @return {@link #ALLOW}, {@link #DENY} or {@link #ASK} for a URL
     */
    public int decide(String url) {
        return decideHost(hostOf(url));
    }

    /**
     * @return {@link #ALLOW}, {@link #DENY} or {@link #ASK} for a named host
     */
    public int decideHost(String host) {
        if (host == null) {
            return DENY;
        }
        if (denied.contains(host)) {
            return DENY;
        }
        if (allowed.contains(host)) {
            return ALLOW;
        }
        switch (mode) {
            case GameProfile.NETWORK_ALLOWED: return ALLOW;
            case GameProfile.NETWORK_BLOCKED: return DENY;
            default: return ASK;
        }
    }

    /** Host part of a connection URL, lower-cased and without the port. */
    public static String hostOf(String url) {
        if (Text.isEmpty(url)) {
            return null;
        }
        int schemeEnd = url.indexOf("://");
        String rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
        if (rest.startsWith("//")) {
            rest = rest.substring(2);
        }
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int at = rest.indexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        int colon = rest.indexOf(':');
        if (colon >= 0) {
            rest = rest.substring(0, colon);
        }
        return normalise(rest);
    }

    /** Scheme of a Generic Connection Framework URL, e.g. {@code http}. */
    public static String schemeOf(String url) {
        if (url == null) {
            return null;
        }
        int colon = url.indexOf(':');
        return colon <= 0 ? null : url.substring(0, colon).toLowerCase();
    }

    private static String normalise(String host) {
        String value = Text.trimOrNull(host);
        return value == null ? null : value.toLowerCase();
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("mode", Integer.valueOf(mode));
        json.put("logBodies", Boolean.valueOf(logBodies));
        json.put("maxBodyBytes", Integer.valueOf(maxBodyBytes));
        json.put("allowed", new ArrayList<Object>(allowed));
        json.put("denied", new ArrayList<Object>(denied));
        return json;
    }

    public static NetworkPolicy fromJson(Map<String, Object> json) {
        NetworkPolicy policy = new NetworkPolicy();
        policy.mode = Json.integer(json, "mode", GameProfile.NETWORK_ASK);
        policy.logBodies = Json.bool(json, "logBodies", true);
        policy.maxBodyBytes = Json.integer(json, "maxBodyBytes", 8192);
        for (Object host : Json.array(json, "allowed")) {
            policy.allowHost(String.valueOf(host));
        }
        for (Object host : Json.array(json, "denied")) {
            policy.denyHost(String.valueOf(host));
        }
        return policy;
    }
}
