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
        MidpMedia.install(vm, context);
        // Nokia sold most of the handsets, so most of the games target its
        // own additions — and one that extends FullCanvas does not load at
        // all without them.
        NokiaUi.install(vm, context);
    }
}
