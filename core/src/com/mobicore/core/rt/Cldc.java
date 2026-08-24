package com.mobicore.core.rt;

import com.mobicore.core.vm.Vm;

/** Installs the whole CLDC runtime library into a virtual machine. */
public final class Cldc {

    private Cldc() {
    }

    /**
     * @return the timer queue, which the emulator must pump between frames —
     *     nothing here runs on a thread of its own
     */
    public static TimerClasses.Queue install(Vm vm) {
        LangClasses.install(vm);
        IoClasses.install(vm);
        UtilClasses.install(vm);
        return TimerClasses.install(vm);
    }
}
