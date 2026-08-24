package com.mobicore.core.rt;

import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code java.util.Timer} and {@code java.util.TimerTask}.
 *
 * <p>A great many MIDlets have no game loop of their own: they schedule a
 * {@code TimerTask} every fifty milliseconds and let it drive everything.
 * Without these classes such a game does not run slowly or oddly — the class
 * loader fails and it does not start.</p>
 *
 * <p>Nothing here starts a thread. Tasks are held in a queue and run by the
 * emulator between frames, on the one thread the MIDlet already runs on. That
 * is a deliberate difference from a real device, and it is the safer one: a
 * game's timer callback almost always touches the screen or its own state,
 * and the games of the era were written on the assumption that this happened
 * while nothing else did.</p>
 */
public final class TimerClasses {

    public static final String TIMER = "java/util/Timer";
    public static final String TIMER_TASK = "java/util/TimerTask";

    private TimerClasses() {
    }

    /** One scheduled task, host side. */
    public static final class Scheduled {

        final VmObject task;
        /** When it should next run, on the VM's clock. */
        long dueAt;
        /** Zero for a one-shot; otherwise the gap between runs. */
        final long period;
        /** True while the period counts from the end of the last run. */
        final boolean fixedDelay;
        boolean cancelled;

        Scheduled(VmObject task, long dueAt, long period, boolean fixedDelay) {
            this.task = task;
            this.dueAt = dueAt;
            this.period = period;
            this.fixedDelay = fixedDelay;
        }
    }

    /** The queue shared by every timer in one emulator session. */
    public static final class Queue {

        private final List<Scheduled> scheduled = new ArrayList<Scheduled>();

        void add(Scheduled entry) {
            scheduled.add(entry);
        }

        /** Cancels everything a timer scheduled. */
        void cancelAll(VmObject timer) {
            for (int i = scheduled.size() - 1; i >= 0; i--) {
                if (scheduled.get(i).task.get("timer") == timer) {
                    scheduled.remove(i);
                }
            }
        }

        public int size() {
            return scheduled.size();
        }

        /**
         * Runs everything that has come due.
         *
         * <p>A task that is late is run once, not once per missed period: a
         * game whose window was in the background for a minute should carry
         * on, not spend the next minute catching up on a loop that no longer
         * means anything.</p>
         *
         * @return how many tasks ran
         */
        public int runDue(Vm vm, long now) {
            int ran = 0;
            // Copied because a task may schedule another, and a queue being
            // appended to while it is walked is a bug waiting to happen.
            List<Scheduled> due = new ArrayList<Scheduled>();
            for (int i = scheduled.size() - 1; i >= 0; i--) {
                Scheduled entry = scheduled.get(i);
                if (entry.cancelled || cancelledInVm(entry)) {
                    scheduled.remove(i);
                } else if (entry.dueAt <= now) {
                    due.add(entry);
                }
            }
            for (int i = due.size() - 1; i >= 0; i--) {
                Scheduled entry = due.get(i);
                if (entry.period <= 0) {
                    scheduled.remove(entry);
                } else if (entry.fixedDelay) {
                    entry.dueAt = now + entry.period;
                } else {
                    entry.dueAt += entry.period;
                    if (entry.dueAt <= now) {
                        entry.dueAt = now + entry.period;
                    }
                }
                entry.task.set("lastRun", Long.valueOf(now));
                vm.callVirtual(entry.task, "run", "()V");
                ran++;
            }
            return ran;
        }

        private boolean cancelledInVm(Scheduled entry) {
            Object flag = entry.task.get("cancelled");
            return flag instanceof Integer && ((Integer) flag).intValue() != 0;
        }
    }

    public static Queue install(final Vm vm) {
        final Queue queue = new Queue();

        vm.builtin(TIMER_TASK, Vm.OBJECT)
                .field("cancelled", "I")
                .field("timer", "Ljava/util/Timer;")
                .field("lastRun", "J")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .abstractMethod("run", "()V")
                .method("cancel", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        boolean wasScheduled = self.get("timer") != null;
                        self.set("cancelled", Integer.valueOf(1));
                        return Rt.box(wasScheduled);
                    }
                })
                .method("scheduledExecutionTime", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Object last = self.get("lastRun");
                        return last instanceof Long ? last : Long.valueOf(0L);
                    }
                })
                .define();

        vm.builtin(TIMER, Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("schedule", "(Ljava/util/TimerTask;J)V",
                        schedule(queue, false, false))
                .method("schedule", "(Ljava/util/TimerTask;JJ)V",
                        schedule(queue, true, true))
                .method("scheduleAtFixedRate", "(Ljava/util/TimerTask;JJ)V",
                        schedule(queue, true, false))
                .method("cancel", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        queue.cancelAll(self);
                        return null;
                    }
                })
                .define();

        return queue;
    }

    /**
     * @param repeating whether the call carries a period
     * @param fixedDelay {@code schedule} spaces runs from the end of the last
     *     one; {@code scheduleAtFixedRate} keeps to the original cadence
     */
    private static NativeMethod schedule(final Queue queue, final boolean repeating,
                                         final boolean fixedDelay) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                VmObject task = Rt.obj(args, 0);
                if (task == null) {
                    throw vm.nullPointer("A timer needs a task to run");
                }
                long delay = Rt.l(args, 1);
                long period = repeating ? Rt.l(args, 2) : 0L;
                if (delay < 0 || period < 0) {
                    throw vm.raise("java/lang/IllegalArgumentException",
                            "A timer cannot be scheduled in the past");
                }
                if (task.get("timer") != null) {
                    throw vm.raise("java/lang/IllegalStateException",
                            "This task is already scheduled");
                }
                task.set("timer", self);
                task.set("cancelled", Integer.valueOf(0));
                queue.add(new Scheduled(task, vm.host().currentTimeMillis() + delay,
                        period, fixedDelay));
                return null;
            }
        };
    }
}
