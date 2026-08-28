package com.mobicore.core.vm;

/**
 * Object monitors for {@code monitorenter}, {@code monitorexit} and the
 * {@code Object.wait}/{@code notify} family.
 *
 * <p>Each emulated object borrows the host monitor of its own {@link VmObject},
 * so blocking maps onto real host blocking instead of a spin loop. Games run
 * their loop on a dedicated thread and synchronise against the UI thread, which
 * only works if these are genuine locks.</p>
 *
 * <p>Every phép ném ở đây là ngoại lệ <em>giả lập</em>, không phải lỗi của máy
 * chủ: một game khoá sai chỗ vẫn phải bắt được lỗi của chính nó, chứ không
 * được kéo sập cả máy ảo.</p>
 */
public final class Monitors {

    private Monitors() {
    }

    public static void enter(Vm vm, VmObject target) {
        if (target == null) {
            throw vm.raise("java/lang/NullPointerException", "monitorenter trên null");
        }
        synchronized (target) {
            Thread self = Thread.currentThread();
            while (target.monitorOwner != null && target.monitorOwner != self) {
                try {
                    target.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw vm.raise("java/lang/IllegalMonitorStateException",
                            "Bị ngắt khi đang chờ khoá");
                }
            }
            target.monitorOwner = self;
            target.monitorDepth++;
        }
    }

    public static void exit(Vm vm, VmObject target) {
        if (target == null) {
            throw vm.raise("java/lang/NullPointerException", "monitorexit trên null");
        }
        synchronized (target) {
            if (target.monitorOwner != Thread.currentThread()) {
                throw vm.raise("java/lang/IllegalMonitorStateException",
                        "Nhả một cái khoá không phải của mình");
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
     * <p>Nằm đợi ở hàng đợi riêng của đối tượng, không phải ở cái khoá máy chủ
     * dùng để giành quyền cầm khoá. Dùng chung một chỗ thì hễ có luồng nào nhả
     * khoá là mọi luồng đang {@code wait} đều tỉnh dậy như vừa được báo.</p>
     *
     * <p>Cầm hàng đợi <em>trước</em> khi nhả khoá, vì người báo cũng phải cầm
     * hàng đợi mới báo được: nhả trước thì lời báo lọt vào đúng khe giữa hai
     * việc và người đợi nằm đó mãi.</p>
     *
     * <p>Và <em>lấy lại khoá sau khi đã buông hàng đợi</em>. Lấy lại khi còn
     * cầm hàng đợi là khoá ngược thứ tự: luồng này chờ khoá của đối tượng
     * trong lúc giữ hàng đợi, còn luồng kia đang giữ khoá ấy lại chờ hàng đợi
     * để vào {@code wait} — hai bên đứng im nhìn nhau, và chó canh tám giây
     * cũng không sủa được vì không luồng nào chạy lệnh nào.</p>
     */
    public static void await(Vm vm, VmObject target, long millis) throws InterruptedException {
        if (target == null) {
            throw vm.raise("java/lang/NullPointerException", "wait() trên null");
        }
        Object waitSet = target.waitSet();
        int depth;
        synchronized (target) {
            if (target.monitorOwner != Thread.currentThread()) {
                throw vm.raise("java/lang/IllegalMonitorStateException",
                        "Gọi wait() ngoài khối synchronized");
            }
            depth = target.monitorDepth;
        }
        boolean released = false;
        try {
            synchronized (waitSet) {
                // Nhả khoá khi đã cầm hàng đợi, để lời báo không lọt vào khe
                // giữa "nhả" và "nằm xuống".
                synchronized (target) {
                    target.monitorDepth = 0;
                    target.monitorOwner = null;
                    target.notifyAll();
                }
                released = true;
                waitSet.wait(millis);
            }
        } finally {
            if (released) {
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

    public static void signal(Vm vm, VmObject target, boolean all) {
        if (target == null) {
            throw vm.raise("java/lang/NullPointerException", "notify() trên null");
        }
        synchronized (target) {
            if (target.monitorOwner != Thread.currentThread()) {
                throw vm.raise("java/lang/IllegalMonitorStateException",
                        "Gọi notify() ngoài khối synchronized");
            }
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
