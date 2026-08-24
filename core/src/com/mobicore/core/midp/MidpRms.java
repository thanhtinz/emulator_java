package com.mobicore.core.midp;

import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * {@code javax.microedition.rms}, bridged to the on-device record stores.
 *
 * <p>Writes are flushed as they happen rather than only on close: phones are
 * killed mid-game all the time, and a save the player earned must survive
 * that.</p>
 */
public final class MidpRms {

    public static final String RECORD_STORE = "javax/microedition/rms/RecordStore";
    public static final String ENUMERATION = "javax/microedition/rms/RecordEnumeration";
    public static final String ENUMERATION_IMPL = "javax/microedition/rms/RecordEnumerationImpl";
    public static final String FILTER = "javax/microedition/rms/RecordFilter";
    public static final String COMPARATOR = "javax/microedition/rms/RecordComparator";

    private MidpRms() {
    }

    /** Iterator state for a {@code RecordEnumeration}. */
    static final class EnumerationState {

        RecordStoreManager.Store store;
        List<Integer> ids = new ArrayList<Integer>();
        int cursor;
    }

    public static void install(final Vm vm, final RecordStoreManager manager, final MidpContext context) {
        exceptions(vm);
        callbacks(vm);
        recordStore(vm, manager, context);
        enumeration(vm);
    }

    private static void exceptions(final Vm vm) {
        define(vm, "javax/microedition/rms/RecordStoreException", "java/lang/Exception");
        define(vm, "javax/microedition/rms/RecordStoreNotOpenException",
                "javax/microedition/rms/RecordStoreException");
        define(vm, "javax/microedition/rms/RecordStoreNotFoundException",
                "javax/microedition/rms/RecordStoreException");
        define(vm, "javax/microedition/rms/RecordStoreFullException",
                "javax/microedition/rms/RecordStoreException");
        define(vm, "javax/microedition/rms/InvalidRecordIDException",
                "javax/microedition/rms/RecordStoreException");
    }

    private static void define(Vm vm, String name, String superName) {
        vm.builtin(name, superName)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("message", args[0]);
                        return null;
                    }
                })
                .define();
    }

    private static void callbacks(final Vm vm) {
        vm.builtin(FILTER, Vm.OBJECT, new String[0], true)
                .abstractMethod("matches", "([B)Z")
                .define();
        vm.builtin(COMPARATOR, Vm.OBJECT, new String[0], true)
                .abstractMethod("compare", "([B[B)I")
                .define();
    }

    static RecordStoreManager.Store store(Vm vm, VmObject self) {
        if (!(self.host instanceof RecordStoreManager.Store)) {
            throw vm.raise("javax/microedition/rms/RecordStoreNotOpenException",
                    "The record store is closed");
        }
        return (RecordStoreManager.Store) self.host;
    }

    private static void recordStore(final Vm vm, final RecordStoreManager manager,
                                    final MidpContext context) {
        vm.builtin(RECORD_STORE, Vm.OBJECT)
                .staticMethod("openRecordStore", "(Ljava/lang/String;Z)Ljavax/microedition/rms/RecordStore;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                String name = Rt.s(vm, args, 0);
                                boolean create = Rt.bool(args, 1);
                                if (name == null || name.length() == 0 || name.length() > 32) {
                                    throw vm.raise("java/lang/IllegalArgumentException",
                                            "Record store names are 1 to 32 characters");
                                }
                                try {
                                    RecordStoreManager.Store opened = manager.openStore(name, create);
                                    if (opened == null) {
                                        throw vm.raise("javax/microedition/rms/RecordStoreNotFoundException", name);
                                    }
                                    VmObject instance = vm.newInstance(RECORD_STORE);
                                    instance.host = opened;
                                    return instance;
                                } catch (IOException e) {
                                    throw vm.raise("javax/microedition/rms/RecordStoreException",
                                            e.getMessage());
                                }
                            }
                        })
                .staticMethod("deleteRecordStore", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String name = Rt.s(vm, args, 0);
                        try {
                            if (!manager.deleteStore(name)) {
                                throw vm.raise("javax/microedition/rms/RecordStoreNotFoundException", name);
                            }
                        } catch (IOException e) {
                            throw vm.raise("javax/microedition/rms/RecordStoreException", e.getMessage());
                        }
                        return null;
                    }
                })
                .staticMethod("listRecordStores", "()[Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<String> names = manager.listStoreNames();
                        if (names.isEmpty()) {
                            // The specification asks for null, not an empty array.
                            return null;
                        }
                        VmArray array = vm.newArray("Ljava/lang/String;", names.size());
                        for (int i = 0; i < names.size(); i++) {
                            array.objects()[i] = vm.newString(names.get(i));
                        }
                        return array;
                    }
                })
                .method("closeRecordStore", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        RecordStoreManager.Store target = store(vm, self);
                        try {
                            manager.close(target.name());
                        } catch (IOException e) {
                            throw vm.raise("javax/microedition/rms/RecordStoreException", e.getMessage());
                        }
                        self.host = null;
                        return null;
                    }
                })
                .method("getName", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(store(vm, self).name());
                    }
                })
                .method("getVersion", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(store(vm, self).version());
                    }
                })
                .method("getLastModified", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(store(vm, self).lastModified());
                    }
                })
                .method("getNumRecords", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(store(vm, self).size());
                    }
                })
                .method("getSize", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(store(vm, self).byteSize());
                    }
                })
                .method("getSizeAvailable", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Modern devices have far more room than a MIDlet can
                        // sensibly use; report a generous but finite budget.
                        return Integer.valueOf(4 * 1024 * 1024 - store(vm, self).byteSize());
                    }
                })
                .method("getNextRecordID", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(store(vm, self).nextRecordId());
                    }
                })
                .method("addRecord", "([BII)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        RecordStoreManager.Store target = store(vm, self);
                        byte[] data = slice(vm, Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                        int id = target.add(data, context.vm().host().currentTimeMillis());
                        persist(vm, manager, target);
                        return Integer.valueOf(id);
                    }
                })
                .method("setRecord", "(I[BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        RecordStoreManager.Store target = store(vm, self);
                        byte[] data = slice(vm, Rt.array(args, 1), Rt.i(args, 2), Rt.i(args, 3));
                        if (!target.set(Rt.i(args, 0), data, context.vm().host().currentTimeMillis())) {
                            throw vm.raise("javax/microedition/rms/InvalidRecordIDException",
                                    "No record " + Rt.i(args, 0));
                        }
                        persist(vm, manager, target);
                        return null;
                    }
                })
                .method("getRecord", "(I)[B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        byte[] data = record(vm, store(vm, self), Rt.i(args, 0));
                        return vm.wrapArray("B", data.clone(), data.length);
                    }
                })
                .method("getRecord", "(I[BI)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        byte[] data = record(vm, store(vm, self), Rt.i(args, 0));
                        VmArray target = Rt.array(args, 1);
                        int offset = Rt.i(args, 2);
                        if (offset < 0 || offset + data.length > target.length()) {
                            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException",
                                    "The buffer is too small for record " + Rt.i(args, 0));
                        }
                        System.arraycopy(data, 0, target.bytes(), offset, data.length);
                        return Integer.valueOf(data.length);
                    }
                })
                .method("getRecordSize", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(record(vm, store(vm, self), Rt.i(args, 0)).length);
                    }
                })
                .method("deleteRecord", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        RecordStoreManager.Store target = store(vm, self);
                        if (!target.delete(Rt.i(args, 0), context.vm().host().currentTimeMillis())) {
                            throw vm.raise("javax/microedition/rms/InvalidRecordIDException",
                                    "No record " + Rt.i(args, 0));
                        }
                        persist(vm, manager, target);
                        return null;
                    }
                })
                .method("enumerateRecords",
                        "(Ljavax/microedition/rms/RecordFilter;Ljavax/microedition/rms/RecordComparator;Z)"
                                + "Ljavax/microedition/rms/RecordEnumeration;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return buildEnumeration(vm, store(vm, self), Rt.obj(args, 0), Rt.obj(args, 1));
                            }
                        })
                .define();
    }

    private static void persist(Vm vm, RecordStoreManager manager, RecordStoreManager.Store store) {
        try {
            manager.flush(store.name());
        } catch (IOException e) {
            throw vm.raise("javax/microedition/rms/RecordStoreException",
                    "Cannot write the record store: " + e.getMessage());
        }
    }

    private static byte[] record(Vm vm, RecordStoreManager.Store store, int recordId) {
        byte[] data = store.get(recordId);
        if (data == null) {
            throw vm.raise("javax/microedition/rms/InvalidRecordIDException", "No record " + recordId);
        }
        return data;
    }

    private static byte[] slice(Vm vm, VmArray array, int offset, int length) {
        if (array == null) {
            return new byte[0];
        }
        if (offset < 0 || length < 0 || offset + length > array.length()) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", "Record range is outside the array");
        }
        byte[] data = new byte[length];
        System.arraycopy(array.bytes(), offset, data, 0, length);
        return data;
    }

    /** Applies the game's filter and comparator, both of which run as bytecode. */
    private static VmObject buildEnumeration(final Vm vm, final RecordStoreManager.Store store,
                                             final VmObject filter, final VmObject comparator) {
        EnumerationState state = new EnumerationState();
        state.store = store;
        for (Integer id : store.recordIds()) {
            if (filter != null) {
                byte[] data = store.get(id.intValue());
                Object matches = vm.callVirtual(filter, "matches", "([B)Z",
                        vm.wrapArray("B", data.clone(), data.length));
                if (((Integer) matches).intValue() == 0) {
                    continue;
                }
            }
            state.ids.add(id);
        }
        if (comparator != null) {
            Collections.sort(state.ids, new Comparator<Integer>() {
                public int compare(Integer left, Integer right) {
                    byte[] a = store.get(left.intValue());
                    byte[] b = store.get(right.intValue());
                    Object result = vm.callVirtual(comparator, "compare", "([B[B)I",
                            vm.wrapArray("B", a.clone(), a.length),
                            vm.wrapArray("B", b.clone(), b.length));
                    int order = ((Integer) result).intValue();
                    // RecordComparator returns PRECEDES(-1), EQUIVALENT(0) or
                    // FOLLOWS(1), which already matches Comparator's contract.
                    return order;
                }
            });
        }
        VmObject instance = vm.newInstance(ENUMERATION_IMPL);
        instance.host = state;
        return instance;
    }

    private static void enumeration(final Vm vm) {
        vm.builtin(ENUMERATION, Vm.OBJECT, new String[0], true)
                .abstractMethod("numRecords", "()I")
                .abstractMethod("hasNextElement", "()Z")
                .abstractMethod("nextRecordId", "()I")
                .abstractMethod("nextRecord", "()[B")
                .abstractMethod("reset", "()V")
                .abstractMethod("destroy", "()V")
                .define();

        vm.builtin(ENUMERATION_IMPL, Vm.OBJECT, new String[]{ENUMERATION}, false)
                .method("numRecords", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(state(vm, self).ids.size());
                    }
                })
                .method("hasNextElement", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        EnumerationState state = state(vm, self);
                        return Rt.box(state.cursor < state.ids.size());
                    }
                })
                .method("nextRecordId", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(advance(vm, state(vm, self)));
                    }
                })
                .method("nextRecord", "()[B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        EnumerationState state = state(vm, self);
                        byte[] data = record(vm, state.store, advance(vm, state));
                        return vm.wrapArray("B", data.clone(), data.length);
                    }
                })
                .method("reset", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        state(vm, self).cursor = 0;
                        return null;
                    }
                })
                .method("destroy", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = null;
                        return null;
                    }
                })
                .define();
    }

    private static EnumerationState state(Vm vm, VmObject self) {
        if (!(self.host instanceof EnumerationState)) {
            throw vm.raise("java/lang/IllegalStateException", "The enumeration was destroyed");
        }
        return (EnumerationState) self.host;
    }

    private static int advance(Vm vm, EnumerationState state) {
        if (state.cursor >= state.ids.size()) {
            throw vm.raise("javax/microedition/rms/InvalidRecordIDException",
                    "The enumeration has no more records");
        }
        return state.ids.get(state.cursor++).intValue();
    }
}
