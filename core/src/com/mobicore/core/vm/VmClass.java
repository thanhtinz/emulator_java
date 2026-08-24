package com.mobicore.core.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A loaded class, interface or array type.
 *
 * <p>Linking assigns field slots and resolves the superclass; initialisation
 * runs {@code <clinit>} on first active use. Both steps are lazy, which keeps
 * suite start-up fast: a game with two hundred classes only pays for the ones
 * it actually reaches.</p>
 */
public final class VmClass {

    public static final int ACC_INTERFACE = 0x0200;
    public static final int ACC_ABSTRACT = 0x0400;

    static final int STATE_LOADED = 0;
    static final int STATE_LINKED = 1;
    static final int STATE_INITIALISING = 2;
    static final int STATE_INITIALISED = 3;

    private final String name;
    private final int access;
    private final ConstantPool constantPool;
    private final String superName;
    private final String[] interfaceNames;

    private VmClass superClass;
    private VmClass[] interfaces = new VmClass[0];
    private VmField[] fields = new VmField[0];
    private VmMethod[] methods = new VmMethod[0];
    private final Map<String, VmMethod> methodIndex = new HashMap<String, VmMethod>();

    private int[] staticInts = new int[0];
    private Object[] staticRefs = new Object[0];
    private int instanceSlots;
    private int state = STATE_LOADED;
    private String sourceFile;

    /** Set for array types; {@code null} otherwise. */
    private final String componentType;
    /** Lazily created {@code java.lang.Class} mirror handed to the program. */
    private VmObject mirror;
    /**
     * Cache of resolved constant pool entries, indexed by pool index. Symbolic
     * references are resolved once and reused: an inner render loop can execute
     * the same {@code invokevirtual} thousands of times per second.
     */
    private Object[] resolvedCache;

    VmClass(String name, int access, ConstantPool constantPool, String superName, String[] interfaceNames,
            String componentType) {
        this.name = name;
        this.access = access;
        this.constantPool = constantPool;
        this.superName = superName;
        this.interfaceNames = interfaceNames == null ? new String[0] : interfaceNames;
        this.componentType = componentType;
    }

    public String name() {
        return name;
    }

    public String binaryName() {
        return Descriptors.toBinaryName(name);
    }

    public ConstantPool constantPool() {
        return constantPool;
    }

    Object resolved(int poolIndex) {
        return resolvedCache == null || poolIndex >= resolvedCache.length ? null : resolvedCache[poolIndex];
    }

    void setResolved(int poolIndex, Object value) {
        if (resolvedCache == null) {
            resolvedCache = new Object[constantPool == null ? 0 : constantPool.count()];
        }
        if (poolIndex < resolvedCache.length) {
            resolvedCache[poolIndex] = value;
        }
    }

    public String superName() {
        return superName;
    }

    public VmClass superClass() {
        return superClass;
    }

    void setSuperClass(VmClass superClass) {
        this.superClass = superClass;
    }

    public String[] interfaceNames() {
        return interfaceNames;
    }

    public VmClass[] interfaces() {
        return interfaces;
    }

    void setInterfaces(VmClass[] interfaces) {
        this.interfaces = interfaces;
    }

    public boolean isInterface() {
        return (access & ACC_INTERFACE) != 0;
    }

    public boolean isArray() {
        return componentType != null;
    }

    public String componentType() {
        return componentType;
    }

    public String sourceFile() {
        return sourceFile;
    }

    void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public VmField[] fields() {
        return fields;
    }

    void setFields(VmField[] fields) {
        this.fields = fields;
    }

    public VmMethod[] methods() {
        return methods;
    }

    void setMethods(VmMethod[] methods) {
        this.methods = methods;
        methodIndex.clear();
        for (VmMethod method : methods) {
            methodIndex.put(method.name() + method.descriptor(), method);
        }
    }

    int state() {
        return state;
    }

    void setState(int state) {
        this.state = state;
    }

    public int instanceSlots() {
        return instanceSlots;
    }

    void setInstanceSlots(int instanceSlots) {
        this.instanceSlots = instanceSlots;
    }

    void allocateStatics(int slots) {
        staticInts = new int[slots];
        staticRefs = new Object[slots];
    }

    public int[] staticInts() {
        return staticInts;
    }

    public Object[] staticRefs() {
        return staticRefs;
    }

    public long getStaticLong(int slot) {
        return ((long) staticInts[slot] << 32) | (staticInts[slot + 1] & 0xFFFFFFFFL);
    }

    public void setStaticLong(int slot, long value) {
        staticInts[slot] = (int) (value >>> 32);
        staticInts[slot + 1] = (int) value;
    }

    VmObject mirror() {
        return mirror;
    }

    void setMirror(VmObject mirror) {
        this.mirror = mirror;
    }

    // ------------------------------------------------------------- lookups

    /** Declared method, without walking the hierarchy. */
    public VmMethod declaredMethod(String name, String descriptor) {
        return methodIndex.get(name + descriptor);
    }

    /** Method resolution: this class, then superclasses, then interfaces. */
    public VmMethod findMethod(String name, String descriptor) {
        for (VmClass type = this; type != null; type = type.superClass) {
            VmMethod method = type.declaredMethod(name, descriptor);
            if (method != null) {
                return method;
            }
        }
        return findInterfaceMethod(name, descriptor);
    }

    private VmMethod findInterfaceMethod(String name, String descriptor) {
        for (VmClass type = this; type != null; type = type.superClass) {
            for (VmClass candidate : type.interfaces) {
                VmMethod method = candidate.declaredMethod(name, descriptor);
                if (method != null && !method.isAbstract()) {
                    return method;
                }
                VmMethod inherited = candidate.findInterfaceMethod(name, descriptor);
                if (inherited != null) {
                    return inherited;
                }
            }
        }
        return null;
    }

    public VmField declaredField(String name) {
        for (VmField field : fields) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }

    /** Field resolution across the hierarchy, interfaces included. */
    public VmField findField(String name) {
        for (VmClass type = this; type != null; type = type.superClass) {
            VmField field = type.declaredField(name);
            if (field != null) {
                return field;
            }
            for (VmClass candidate : type.interfaces) {
                VmField inherited = candidate.findField(name);
                if (inherited != null) {
                    return inherited;
                }
            }
        }
        return null;
    }

    public boolean isSubclassOf(VmClass other) {
        for (VmClass type = this; type != null; type = type.superClass) {
            if (type == other) {
                return true;
            }
        }
        return false;
    }

    /** True when a reference of this type can be stored in {@code target}. */
    public boolean isAssignableTo(VmClass target) {
        if (target == this) {
            return true;
        }
        if (target.isInterface() && implementsInterface(target)) {
            return true;
        }
        if (isArray() && target.isArray()) {
            // Arrays of references are covariant; primitive arrays are not.
            return componentType.equals(target.componentType);
        }
        return isSubclassOf(target);
    }

    private boolean implementsInterface(VmClass target) {
        for (VmClass type = this; type != null; type = type.superClass) {
            for (VmClass candidate : type.interfaces) {
                if (candidate == target || candidate.implementsInterface(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<VmMethod> methodsNamed(String name) {
        List<VmMethod> result = new ArrayList<VmMethod>();
        for (VmMethod method : methods) {
            if (method.name().equals(name)) {
                result.add(method);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return binaryName();
    }
}
