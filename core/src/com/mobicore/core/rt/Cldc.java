package com.mobicore.core.rt;

import com.mobicore.core.vm.Vm;

/** Installs the whole CLDC runtime library into a virtual machine. */
public final class Cldc {

    private Cldc() {
    }

    public static void install(Vm vm) {
        LangClasses.install(vm);
        IoClasses.install(vm);
        UtilClasses.install(vm);
    }
}
