package com.mobicore.core.midp;

import com.mobicore.core.vm.Vm;

/** Installs the whole MIDP profile into a virtual machine. */
public final class Midp {

    private Midp() {
    }

    public static void install(Vm vm, MidpContext context) {
        MidpGfx.install(vm);
        MidpUi.install(vm, context);
        MidpForms.install(vm, context);
        MidpGame.install(vm);
    }
}
