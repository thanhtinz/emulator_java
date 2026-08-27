package com.mobicore.core.emu;

import com.mobicore.core.vm.VmHost;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer of emulator and game output, shown by the log console and
 * attached to crash reports.
 *
 * <p>Bounded on purpose: a game that prints every frame must not be able to
 * grow the log until the device runs out of memory.</p>
 */
public final class EmulatorLog {

    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_GAME = 1;
    public static final int LEVEL_ERROR = 2;

    /** One recorded line. */
    public static final class Entry {

        public final int level;
        public final String text;
        public final long timestamp;

        Entry(int level, String text, long timestamp) {
            this.level = level;
            this.text = text;
            this.timestamp = timestamp;
        }

        public String levelName() {
            switch (level) {
                case LEVEL_GAME: return "game";
                case LEVEL_ERROR: return "error";
                default: return "info";
            }
        }

        @Override
        public String toString() {
            return "[" + levelName() + "] " + text;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final StringBuilder pending = new StringBuilder();
    private int limit = 500;
    private long clock;

    public void setLimit(int limit) {
        this.limit = Math.max(16, limit);
    }

    public synchronized List<Entry> entries() {
        return new ArrayList<Entry>(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        pending.setLength(0);
    }

    public void info(String text) {
        add(LEVEL_INFO, text);
    }

    public void error(String text) {
        add(LEVEL_ERROR, text);
    }

    public synchronized void add(int level, String text) {
        entries.add(new Entry(level, text, clock));
        while (entries.size() > limit) {
            entries.remove(0);
        }
    }

    /** Buffers partial console output until a newline completes a line. */
    private synchronized void write(boolean error, String text) {
        pending.append(text);
        int newline;
        while ((newline = pending.indexOf("\n")) >= 0) {
            String line = pending.substring(0, newline);
            pending.delete(0, newline + 1);
            add(error ? LEVEL_ERROR : LEVEL_GAME, line);
        }
    }

    /** Renders the log the way the console screen and crash reports show it. */
    public synchronized String render() {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            out.append(entry).append('\n');
        }
        return out.toString();
    }

    /**
     * Wraps a host so everything the game prints is captured, then forwarded.
     * The console needs the text, and a developer watching the terminal still
     * wants to see it live.
     */
    public VmHost hostBridge(final VmHost delegate) {
        return new VmHost() {

            @Override
            public long currentTimeMillis() {
                long now = delegate.currentTimeMillis();
                clock = now;
                return now;
            }

            @Override
            public void print(boolean error, String text) {
                write(error, text);
                delegate.print(error, text);
            }

            @Override
            public void exit(int code) {
                add(LEVEL_INFO, "The MIDlet requested exit(" + code + ")");
                delegate.exit(code);
            }

            @Override
            public String property(String name) {
                // Máy giả trả lời câu này (xem HandsetHost); ở đây chỉ chuyển
                // tiếp, vì nhật ký không phải chỗ quyết định game đang chạy
                // trên máy nào.
                return delegate.property(name);
            }

            @Override
            public void sleep(long millis) throws InterruptedException {
                delegate.sleep(millis);
            }
        };
    }
}
