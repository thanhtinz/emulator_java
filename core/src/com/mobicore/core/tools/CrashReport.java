package com.mobicore.core.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.net.NetworkMonitor;
import com.mobicore.core.storage.Json;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a crash into something a user can send and a developer can act on.
 *
 * <p>Deliberately self-contained and local: it carries the suite's own
 * metadata, the emulated stack trace and the recent log, and nothing about the
 * device or the person using it.</p>
 */
public final class CrashReport {

    private final String title;
    private final String detail;
    private final String stackTrace;
    private final String log;
    private final MidletSuiteInfo info;
    private final String deviceProfile;
    private final int networkExchanges;
    private final long timestamp;

    private CrashReport(String title, String detail, String stackTrace, String log,
                        MidletSuiteInfo info, String deviceProfile, int networkExchanges,
                        long timestamp) {
        this.title = title;
        this.detail = detail;
        this.stackTrace = stackTrace;
        this.log = log;
        this.info = info;
        this.deviceProfile = deviceProfile;
        this.networkExchanges = networkExchanges;
        this.timestamp = timestamp;
    }

    /** Builds a report from a session and the failure that ended it. */
    public static CrashReport from(EmulatorSession session, Throwable failure) {
        String title;
        String detail;
        if (failure instanceof VmThrow) {
            VmClass type = ((VmThrow) failure).type();
            title = type == null ? "Uncaught exception" : type.binaryName();
            detail = failure.getMessage() == null ? "" : failure.getMessage();
        } else if (failure != null) {
            title = failure.getClass().getName();
            detail = failure.getMessage() == null ? "" : failure.getMessage();
        } else {
            title = "Unknown failure";
            detail = "";
        }
        NetworkMonitor monitor = session.network().monitor();
        return new CrashReport(title, detail,
                session.vm().interpreter().stackTrace(),
                session.log().render(),
                session.info(),
                session.profile().device().toString(),
                monitor.size(),
                session.vm().host().currentTimeMillis());
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public String stackTrace() {
        return stackTrace;
    }

    /** Plain text form, suitable for a share sheet or a bug report. */
    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("MobiCore crash report\n");
        out.append("=====================\n");
        out.append("Suite      : ").append(info.title()).append(' ').append(info.version()).append('\n');
        out.append("Vendor     : ").append(info.vendor()).append('\n');
        out.append("Runtime    : ").append(info.configuration()).append(" / ")
                .append(info.profile()).append('\n');
        out.append("Device     : ").append(deviceProfile).append('\n');
        out.append("Timestamp  : ").append(timestamp).append('\n');
        out.append("Network    : ").append(networkExchanges).append(" recorded exchanges\n");
        out.append('\n');
        out.append(title);
        if (detail.length() > 0) {
            out.append(": ").append(detail);
        }
        out.append('\n');
        out.append(stackTrace);
        out.append("\n--- log ---\n");
        out.append(log);
        return out.toString();
    }

    public String toJson() {
        Map<String, Object> json = Json.object();
        json.put("title", title);
        json.put("detail", detail);
        json.put("suite", info.title());
        json.put("version", info.version());
        json.put("vendor", info.vendor());
        json.put("device", deviceProfile);
        json.put("timestamp", Long.valueOf(timestamp));
        json.put("networkExchanges", Integer.valueOf(networkExchanges));
        List<Object> frames = new ArrayList<Object>();
        for (String line : stackTrace.split("\n")) {
            if (line.trim().length() > 0) {
                frames.add(line.trim());
            }
        }
        json.put("stack", frames);
        return Json.write(json);
    }
}
