package com.mobicore.core.vm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for classes implemented natively rather than loaded from
 * bytecode, used to define the CLDC and MIDP libraries.
 */
public final class BuiltinBuilder {

    private final Vm vm;
    private final VmClass type;
    private final List<VmField> fields = new ArrayList<VmField>();
    private final List<VmMethod> methods = new ArrayList<VmMethod>();

    BuiltinBuilder(Vm vm, VmClass type) {
        this.vm = vm;
        this.type = type;
    }

    public BuiltinBuilder field(String name, String descriptor) {
        fields.add(new VmField(type, name, descriptor, 0));
        return this;
    }

    public BuiltinBuilder staticField(String name, String descriptor) {
        fields.add(new VmField(type, name, descriptor, VmField.ACC_STATIC));
        return this;
    }

    public BuiltinBuilder method(String name, String descriptor, NativeMethod impl) {
        VmMethod method = new VmMethod(type, name, descriptor, VmMethod.ACC_PUBLIC | VmMethod.ACC_NATIVE);
        method.bindNative(impl);
        methods.add(method);
        return this;
    }

    public BuiltinBuilder staticMethod(String name, String descriptor, NativeMethod impl) {
        VmMethod method = new VmMethod(type, name, descriptor,
                VmMethod.ACC_PUBLIC | VmMethod.ACC_STATIC | VmMethod.ACC_NATIVE);
        method.bindNative(impl);
        methods.add(method);
        return this;
    }

    /** Declares an abstract method a subclass in the game is expected to override. */
    public BuiltinBuilder abstractMethod(String name, String descriptor) {
        methods.add(new VmMethod(type, name, descriptor, VmMethod.ACC_PUBLIC | VmMethod.ACC_ABSTRACT));
        return this;
    }

    public VmClass define() {
        type.setFields(fields.toArray(new VmField[fields.size()]));
        type.setMethods(methods.toArray(new VmMethod[methods.size()]));
        vm.registerClass(type);
        return type;
    }
}
