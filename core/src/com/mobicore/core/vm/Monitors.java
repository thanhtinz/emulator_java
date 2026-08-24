package com.mobicore.core.vm;

/**
 * Object monitors for {@code monitorenter}, {@code monitorexit} and the
 * {@code Object.wait}/{@code notify} family.
 *
 * <p>Each emulated object borrows the host monitor of its own {@link VmObject},
 * so blocking maps onto real host blocking instead of a spin loop. Games run
 * their loop on a dedicated thread and synchronise against the UI thread, which
 * only works if these are genuine locks.</p>
 */
public final class Monitors {

    private Monitors() {
    }

    public static void enter(VmObject target) {
        if (target == null) {
            throw new VmError("monitorenter on null");
        }
        synchronized (target) {
            Thread self = Thread.currentThread();
            while (target.monitorOwner != null && target.monitorOwner != self) {
                try {
                    target.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new VmError("Interrupted while entering a monitor");
                }
            }
            target.monitorOwner = self;
            target.monitorDepth++;
        }
    }

    public static void exit(VmObject target) {
        if (target == null) {
            throw new VmError("monitorexit on null");
        }
        synchronized (target) {
            if (target.monitorOwner != Thread.currentThread()) {
                return;
            }
            if (--target.monitorDepth <= 0) {
                target.monitorDepth = 0;
                target.monitorOwner = null;
                target.notifyAll();
            }
        }
    }

    /** Implements {@code Object.wait}, releasing the monitor while blocked. */
    public static void await(VmObject target, long millis) throws InterruptedException {
        synchronized (target) {
            int depth = target.monitorDepth;
            target.monitorDepth = 0;
            target.monitorOwner = null;
            target.notifyAll();
            try {
                target.wait(millis);
            } finally {
                while (target.monitorOwner != null && target.monitorOwner != Thread.currentThread()) {
                    target.wait();
                }
                target.monitorOwner = Thread.currentThread();
                target.monitorDepth = depth;
            }
        }
    }

    public static void signal(VmObject target, boolean all) {
        synchronized (target) {
            if (all) {
                target.notifyAll();
            } else {
                target.notify();
            }
        }
    }
}
