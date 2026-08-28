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

    /**
     * Implements {@code Object.wait}, releasing the monitor while blocked.
     *
     * <p>Nằm đợi ở hàng đợi riêng của đối tượng, không phải ở cái khoá máy
     * chủ dùng để giành quyền cầm khoá. Trước đây dùng chung, và hậu quả là
     * hễ có luồng nào nhả khoá thì mọi luồng đang {@code wait} đều tỉnh dậy
     * như thể vừa được báo — một vòng lặp game viết theo lối đợi-báo, tức là
     * gần như mọi vòng lặp game, chạy loạn hết cả.</p>
     *
     * <p>Cầm hàng đợi <em>trước</em> khi nhả khoá, vì người báo cũng phải cầm
     * hàng đợi mới báo được: nhả trước thì lời báo có thể lọt vào đúng khe
     * giữa hai việc và người đợi nằm đó mãi.</p>
     */
    public static void await(VmObject target, long millis) throws InterruptedException {
        if (target == null) {
            throw new VmError("wait on null");
        }
        Object waitSet = target.waitSet();
        int depth;
        synchronized (waitSet) {
            synchronized (target) {
                if (target.monitorOwner != Thread.currentThread()) {
                    throw new VmError("Gọi wait() ngoài khối synchronized");
                }
                depth = target.monitorDepth;
                target.monitorDepth = 0;
                target.monitorOwner = null;
                target.notifyAll();
            }
            try {
                waitSet.wait(millis);
            } finally {
                reenter(target, depth);
            }
        }
    }

    /** Lấy lại khoá với đúng độ sâu đã nhả, sau khi thôi đợi. */
    private static void reenter(VmObject target, int depth) throws InterruptedException {
        synchronized (target) {
            while (target.monitorOwner != null && target.monitorOwner != Thread.currentThread()) {
                target.wait();
            }
            target.monitorOwner = Thread.currentThread();
            target.monitorDepth = depth;
        }
    }

    public static void signal(VmObject target, boolean all) {
        if (target == null) {
            throw new VmError("notify on null");
        }
        Object waitSet = target.waitSet();
        synchronized (waitSet) {
            if (all) {
                waitSet.notifyAll();
            } else {
                waitSet.notify();
            }
        }
    }
}
