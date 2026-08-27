package com.mobicore.core.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The virtual machine: class loader, heap and entry point for execution.
 *
 * <p>One instance serves one MIDlet suite. Keeping the VM per-suite is what
 * makes the sandboxing promise in the specification cheap to honour — static
 * state, interned strings and record stores all die with the instance when the
 * user closes a game.</p>
 */
public final class Vm {

    public static final String OBJECT = "java/lang/Object";
    public static final String STRING = "java/lang/String";
    public static final String CLASS = "java/lang/Class";
    public static final String THROWABLE = "java/lang/Throwable";

    private final Map<String, VmClass> classes = new HashMap<String, VmClass>();
    private final Map<String, VmObject> internedStrings = new HashMap<String, VmObject>();
    private final List<ClassSource> sources = new ArrayList<ClassSource>();
    private final Interpreter interpreter = new Interpreter(this);

    private VmHost host = VmHost.DEFAULT;
    private long instructionBudget = Long.MAX_VALUE;
    /** Bao lâu thì một lời gọi vào game bị coi là treo. */
    private long stuckAfterMs = 8000;
    private volatile boolean cancelled;
    private int maxFrames = 512;

    public Vm() {
    }

    public void addSource(ClassSource source) {
        sources.add(source);
    }

    public List<ClassSource> sources() {
        return sources;
    }

    public VmHost host() {
        return host;
    }

    public void setHost(VmHost host) {
        this.host = host == null ? VmHost.DEFAULT : host;
    }

    public Interpreter interpreter() {
        return interpreter;
    }

    /**
     * Caps how many bytecodes a single call may execute. A runaway game must
     * not be able to wedge the UI thread; the emulator screen sets a budget per
     * frame and reports the overrun instead of hanging.
     */
    public void setInstructionBudget(long budget) {
        this.instructionBudget = budget <= 0 ? Long.MAX_VALUE : budget;
    }

    public long instructionBudget() {
        return instructionBudget;
    }

    /**
     * Một lời gọi vào game chạy quá lâu thì bị cắt.
     *
     * <p>Game J2ME viết vòng lặp của chính nó, và một vòng lặp không có lối
     * ra là chuyện thường gặp trong đám game viết cho đúng một đời máy. Không
     * có cái hạn này thì luồng chạy game kẹt mãi: màn hình đứng im, không nút
     * nào bấm được, và cách duy nhất thoát ra là tắt hẳn ứng dụng.</p>
     *
     * <p>Tám giây là rộng tay có chủ ý: máy ảo dịch từng lệnh nên một màn mở
     * đầu nặng có thể chạy vài giây thật, và cắt nhầm một game đang chạy
     * đúng thì tệ hơn là đợi thêm.</p>
     *
     * @param millis 0 hoặc số âm thì bỏ hạn
     */
    public void setStuckAfterMs(long millis) {
        this.stuckAfterMs = millis <= 0 ? Long.MAX_VALUE : millis;
    }

    public long stuckAfterMs() {
        return stuckAfterMs;
    }

    /**
     * Bảo game dừng lại ngay, dù nó đang ở giữa một khung hình.
     *
     * <p>Đặt từ luồng khác — người chơi bấm thoát trong lúc luồng game còn
     * đang chạy — nên là {@code volatile}: luồng game phải thấy được ngay
     * chứ không phải ở lần đọc bộ nhớ nào đó về sau.</p>
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int maxFrames() {
        return maxFrames;
    }

    public void setMaxFrames(int maxFrames) {
        this.maxFrames = maxFrames;
    }

    // ---------------------------------------------------------- class space

    public void registerClass(VmClass type) {
        classes.put(type.name(), type);
    }

    public VmClass findLoaded(String internalName) {
        return classes.get(internalName);
    }

    public List<VmClass> loadedClasses() {
        return new ArrayList<VmClass>(classes.values());
    }

    /** Loads and links a class, without running {@code <clinit>}. */
    public VmClass loadClass(String internalName) {
        VmClass loaded = classes.get(internalName);
        if (loaded != null) {
            // Builtins are registered without being linked, so link on first
            // lookup; link() is idempotent for classes already through it.
            link(loaded);
            return loaded;
        }
        if (internalName.startsWith("[")) {
            return defineArrayClass(internalName);
        }
        byte[] bytes = null;
        for (int i = sources.size() - 1; i >= 0 && bytes == null; i--) {
            bytes = sources.get(i).classBytes(internalName);
        }
        if (bytes == null) {
            throw new VmThrow(newThrowable("java/lang/NoClassDefFoundError",
                    Descriptors.toBinaryName(internalName)), internalName);
        }
        VmClass type = ClassFileParser.parse(bytes);
        classes.put(type.name(), type);
        link(type);
        return type;
    }

    private VmClass defineArrayClass(String descriptor) {
        VmClass type = new VmClass(descriptor, VmClass.ACC_ABSTRACT, null, OBJECT, null,
                descriptor.substring(1));
        classes.put(descriptor, type);
        type.setSuperClass(loadClass(OBJECT));
        type.setState(VmClass.STATE_INITIALISED);
        return type;
    }

    /** Resolves the supertypes and assigns field slots. */
    public void link(VmClass type) {
        if (type.state() >= VmClass.STATE_LINKED) {
            return;
        }
        type.setState(VmClass.STATE_LINKED);
        if (type.superName() != null && type.superClass() == null) {
            type.setSuperClass(loadClass(type.superName()));
        }
        String[] interfaceNames = type.interfaceNames();
        VmClass[] interfaces = new VmClass[interfaceNames.length];
        for (int i = 0; i < interfaceNames.length; i++) {
            interfaces[i] = loadClass(interfaceNames[i]);
        }
        type.setInterfaces(interfaces);

        int instanceSlot = type.superClass() == null ? 0 : type.superClass().instanceSlots();
        int staticSlot = 0;
        for (VmField field : type.fields()) {
            int width = field.isWide() ? 2 : 1;
            if (field.isStatic()) {
                field.setSlot(staticSlot);
                staticSlot += width;
            } else {
                field.setSlot(instanceSlot);
                instanceSlot += width;
            }
        }
        type.setInstanceSlots(instanceSlot);
        type.allocateStatics(staticSlot);
    }

    /** Runs {@code <clinit>} once, following the superclass chain first. */
    public void initialize(VmClass type) {
        if (type.state() >= VmClass.STATE_INITIALISING) {
            return;
        }
        link(type);
        type.setState(VmClass.STATE_INITIALISING);
        if (type.superClass() != null) {
            initialize(type.superClass());
        }
        for (VmField field : type.fields()) {
            Object constant = field.constantValue();
            if (constant == null || !field.isStatic()) {
                continue;
            }
            applyStaticConstant(type, field, constant);
        }
        VmMethod clinit = type.declaredMethod("<clinit>", "()V");
        if (clinit != null) {
            interpreter.invoke(clinit, null, new Object[0]);
        }
        type.setState(VmClass.STATE_INITIALISED);
    }

    private void applyStaticConstant(VmClass type, VmField field, Object constant) {
        int slot = field.slot();
        if (constant instanceof String) {
            type.staticRefs()[slot] = newString((String) constant);
        } else if (constant instanceof Long) {
            type.setStaticLong(slot, ((Long) constant).longValue());
        } else if (constant instanceof Double) {
            type.setStaticLong(slot, Double.doubleToRawLongBits(((Double) constant).doubleValue()));
        } else if (constant instanceof Float) {
            type.staticInts()[slot] = Float.floatToRawIntBits(((Float) constant).floatValue());
        } else if (constant instanceof Integer) {
            type.staticInts()[slot] = ((Integer) constant).intValue();
        }
    }

    public BuiltinBuilder builtin(String internalName, String superName) {
        return builtin(internalName, superName, new String[0], false);
    }

    public BuiltinBuilder builtin(String internalName, String superName, String[] interfaces, boolean isInterface) {
        VmClass type = new VmClass(internalName, isInterface ? VmClass.ACC_INTERFACE : 0,
                null, superName, interfaces, null);
        return new BuiltinBuilder(this, type);
    }

    // ------------------------------------------------------------------ heap

    public VmObject newInstance(VmClass type) {
        initialize(type);
        return new VmObject(type);
    }

    public VmObject newInstance(String internalName) {
        return newInstance(loadClass(internalName));
    }

    public VmArray newArray(String componentDescriptor, int length) {
        if (length < 0) {
            throw new VmThrow(newThrowable("java/lang/NegativeArraySizeException",
                    String.valueOf(length)), "negative array size");
        }
        VmClass arrayClass = loadClass("[" + componentDescriptor);
        char kind = componentDescriptor.charAt(0);
        Object data;
        switch (kind) {
            case 'I': data = new int[length]; break;
            case 'J': data = new long[length]; break;
            case 'F': data = new float[length]; break;
            case 'D': data = new double[length]; break;
            case 'B': case 'Z': data = new byte[length]; break;
            case 'C': data = new char[length]; break;
            case 'S': data = new short[length]; break;
            default: data = new Object[length]; break;
        }
        return new VmArray(arrayClass, kind, componentDescriptor, length, data);
    }

    /** Wraps an existing host array without copying it. */
    public VmArray wrapArray(String componentDescriptor, Object data, int length) {
        return new VmArray(loadClass("[" + componentDescriptor), componentDescriptor.charAt(0),
                componentDescriptor, length, data);
    }

    // --------------------------------------------------------------- strings

    /**
     * Emulated strings are host {@link String}s behind a {@code VmObject}
     * facade, which keeps every string operation a native call instead of an
     * interpreted char-array loop.
     */
    public VmObject newString(String value) {
        if (value == null) {
            return null;
        }
        VmObject instance = new VmObject(loadClass(STRING));
        instance.host = value;
        return instance;
    }

    public VmObject internString(String value) {
        VmObject existing = internedStrings.get(value);
        if (existing == null) {
            existing = newString(value);
            internedStrings.put(value, existing);
        }
        return existing;
    }

    public String stringOf(Object reference) {
        if (reference == null) {
            return null;
        }
        VmObject instance = (VmObject) reference;
        return instance.host instanceof String ? (String) instance.host : String.valueOf(instance.host);
    }

    /** The {@code java.lang.Class} mirror for a type, created on demand. */
    public VmObject mirrorOf(VmClass type) {
        VmObject mirror = type.mirror();
        if (mirror == null) {
            mirror = new VmObject(loadClass(CLASS));
            mirror.host = type;
            type.setMirror(mirror);
        }
        return mirror;
    }

    // ------------------------------------------------------------ exceptions

    public VmObject newThrowable(String internalName, String message) {
        VmClass type;
        try {
            type = loadClass(internalName);
        } catch (RuntimeException e) {
            // The throwable class itself is missing: fall back to Throwable so
            // the game still sees a catchable object instead of a VM crash.
            type = loadClass(THROWABLE);
        }
        VmObject instance = new VmObject(type);
        VmField messageField = type.findField("message");
        if (messageField != null && message != null) {
            instance.setRef(messageField.slot(), newString(message));
        }
        return instance;
    }

    /** Builds and returns a throwable ready to be raised by native code. */
    public VmThrow raise(String internalName, String message) {
        return new VmThrow(newThrowable(internalName, message), message);
    }

    public VmThrow nullPointer(String detail) {
        return raise("java/lang/NullPointerException", detail);
    }

    // ------------------------------------------------------------- execution

    public Object invoke(VmMethod method, VmObject self, Object[] args) {
        return interpreter.invoke(method, self, args);
    }

    /** Convenience entry point used by the runtime and the tools. */
    public Object callStatic(String internalName, String name, String descriptor, Object... args) {
        VmClass type = loadClass(internalName);
        initialize(type);
        VmMethod method = type.findMethod(name, descriptor);
        if (method == null) {
            throw new VmError("No such method " + internalName + "." + name + descriptor);
        }
        return interpreter.invoke(method, null, args);
    }

    public Object callVirtual(VmObject self, String name, String descriptor, Object... args) {
        if (self == null) {
            throw nullPointer("call on null receiver");
        }
        VmMethod method = self.type().findMethod(name, descriptor);
        if (method == null) {
            throw new VmError("No such method " + self.type().name() + "." + name + descriptor);
        }
        return interpreter.invoke(method, self, args);
    }
}
