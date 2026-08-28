package com.mobicore.core.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which emulated {@code Thread} object each host thread is running as.
 *
 * <p>Games run their loop on a thread of their own and then ask about it:
 * {@code Thread.currentThread().setPriority(...)}, a name in a log line, a
 * check that the loop is not calling itself. All of that needs
 * {@code currentThread()} to hand back <em>the same object</em> the game
 * started — a fresh one every call answers every question wrongly and never
 * says so.</p>
 *
 * <p>Keyed weakly on the host thread, so a finished game thread is not held
 * alive by this table.</p>
 */
public final class Threads {

    private final Map<Thread, VmObject> running = new WeakHashMap<Thread, VmObject>();
    /** Những luồng do chính game mở, tách khỏi luồng máy ảo mượn để chạy MIDlet. */
    private final Map<Thread, Boolean> ownStarted = new WeakHashMap<Thread, Boolean>();
    private int started;

    /** Binds a host thread the game itself started. */
    public void bind(Thread host, VmObject thread) {
        synchronized (running) {
            running.put(host, thread);
            ownStarted.put(host, Boolean.TRUE);
        }
    }

    /** Binds a thread of the emulator's own that a game has asked about. */
    public void adopt(Thread host, VmObject thread) {
        synchronized (running) {
            running.put(host, thread);
        }
    }

    /** True when the game started this thread itself. */
    public boolean startedByGame(Thread host) {
        synchronized (running) {
            return ownStarted.containsKey(host);
        }
    }

    /** Forgets a thread that has finished. */
    public void unbind(Thread host) {
        synchronized (running) {
            running.remove(host);
            ownStarted.remove(host);
        }
    }

    /** The emulated object for the calling thread, or null if it has none. */
    public VmObject current() {
        synchronized (running) {
            return running.get(Thread.currentThread());
        }
    }

    /**
     * How many emulated threads are alive.
     *
     * <p>Counted from the objects rather than from the host's thread group:
     * the host runs threads of its own — audio, the network — that are no part
     * of the game and that a game counting its own workers must not see.</p>
     */
    public int alive() {
        int count = 0;
        for (Object[] each : snapshot()) {
            Thread host = (Thread) each[0];
            if (host.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** Every bound thread, as {@code {host, emulated object}} pairs. */
    public List<Object[]> snapshot() {
        List<Object[]> out = new ArrayList<Object[]>();
        synchronized (running) {
            for (Map.Entry<Thread, VmObject> entry : running.entrySet()) {
                out.add(new Object[]{entry.getKey(), entry.getValue()});
            }
        }
        return out;
    }

    /** The next default name, as the JDK's own {@code Thread-N} sequence. */
    public synchronized String nextName() {
        return "Thread-" + (started++);
    }
}
