package com.mobicore.core.rt;

import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The {@code java.util} subset CLDC defines.
 *
 * <p>Collections are backed by host collections. Keys go through {@link Key},
 * which forwards equality to the value a wrapper or string instance holds —
 * without it, two equal emulated strings would land in different hash buckets
 * and a game's {@code Hashtable} lookups would silently miss.</p>
 */
public final class UtilClasses {

    private UtilClasses() {
    }

    /** Hash key that respects emulated value equality. */
    static final class Key {

        final VmObject value;

        Key(VmObject value) {
            this.value = value;
        }

        private Object payload() {
            Object host = value == null ? null : value.host;
            boolean comparable = host instanceof String || host instanceof Number
                    || host instanceof Boolean || host instanceof Character;
            return comparable ? host : null;
        }

        @Override
        public int hashCode() {
            Object payload = payload();
            return payload == null ? System.identityHashCode(value) : payload.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            Object mine = payload();
            Object theirs = that.payload();
            if (mine == null || theirs == null) {
                return value == that.value;
            }
            return mine.equals(theirs);
        }
    }

    public static void install(final Vm vm) {
        enumeration(vm);
        vector(vm);
        stack(vm);
        hashtable(vm);
        random(vm);
        date(vm);
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(VmObject self) {
        return (List<Object>) self.host;
    }

    @SuppressWarnings("unchecked")
    static Hashtable<Key, Object> table(VmObject self) {
        return (Hashtable<Key, Object>) self.host;
    }

    private static VmObject newEnumeration(Vm vm, Iterator<Object> iterator) {
        VmObject instance = vm.newInstance("java/util/EnumerationImpl");
        instance.host = iterator;
        return instance;
    }

    private static void enumeration(final Vm vm) {
        vm.builtin("java/util/Enumeration", Vm.OBJECT, new String[0], true)
                .abstractMethod("hasMoreElements", "()Z")
                .abstractMethod("nextElement", "()Ljava/lang/Object;")
                .define();

        vm.builtin("java/util/EnumerationImpl", Vm.OBJECT, new String[]{"java/util/Enumeration"}, false)
                .method("hasMoreElements", "()Z", new NativeMethod() {
                    @SuppressWarnings("unchecked")
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(((Iterator<Object>) self.host).hasNext());
                    }
                })
                .method("nextElement", "()Ljava/lang/Object;", new NativeMethod() {
                    @SuppressWarnings("unchecked")
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Iterator<Object> iterator = (Iterator<Object>) self.host;
                        if (!iterator.hasNext()) {
                            throw vm.raise("java/util/NoSuchElementException", "Enumeration exhausted");
                        }
                        return iterator.next();
                    }
                })
                .define();
    }

    private static void vector(final Vm vm) {
        vm.builtin("java/util/Vector", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ArrayList<Object>();
                        return null;
                    }
                })
                .method("<init>", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ArrayList<Object>(Math.max(1, Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("addElement", "(Ljava/lang/Object;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(self).add(args[0]);
                        return null;
                    }
                })
                .method("insertElementAt", "(Ljava/lang/Object;I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(self).add(checkIndex(vm, self, Rt.i(args, 1), true), args[0]);
                        return null;
                    }
                })
                .method("elementAt", "(I)Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return list(self).get(checkIndex(vm, self, Rt.i(args, 0), false));
                    }
                })
                .method("setElementAt", "(Ljava/lang/Object;I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(self).set(checkIndex(vm, self, Rt.i(args, 1), false), args[0]);
                        return null;
                    }
                })
                .method("removeElementAt", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(self).remove(checkIndex(vm, self, Rt.i(args, 0), false));
                        return null;
                    }
                })
                .method("removeElement", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int index = indexOf(self, Rt.obj(args, 0));
                        if (index < 0) {
                            return Rt.box(false);
                        }
                        list(self).remove(index);
                        return Rt.box(true);
                    }
                })
                .method("removeAllElements", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(self).clear();
                        return null;
                    }
                })
                .method("contains", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(indexOf(self, Rt.obj(args, 0)) >= 0);
                    }
                })
                .method("indexOf", "(Ljava/lang/Object;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(indexOf(self, Rt.obj(args, 0)));
                    }
                })
                .method("size", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(list(self).size());
                    }
                })
                .method("isEmpty", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(list(self).isEmpty());
                    }
                })
                .method("firstElement", "()Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return list(self).isEmpty() ? failEmpty(vm) : list(self).get(0);
                    }
                })
                .method("lastElement", "()Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<Object> items = list(self);
                        return items.isEmpty() ? failEmpty(vm) : items.get(items.size() - 1);
                    }
                })
                .method("elements", "()Ljava/util/Enumeration;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return newEnumeration(vm, new ArrayList<Object>(list(self)).iterator());
                    }
                })
                .method("copyInto", "([Ljava/lang/Object;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Object[] target = Rt.array(args, 0).objects();
                        List<Object> items = list(self);
                        for (int i = 0; i < items.size() && i < target.length; i++) {
                            target[i] = items.get(i);
                        }
                        return null;
                    }
                })
                .define();
    }

    private static Object failEmpty(Vm vm) {
        throw vm.raise("java/util/NoSuchElementException", "The vector is empty");
    }

    private static int checkIndex(Vm vm, VmObject self, int index, boolean allowEnd) {
        int size = list(self).size();
        if (index < 0 || index > size || (!allowEnd && index == size)) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", String.valueOf(index));
        }
        return index;
    }

    /** Element search using the same value equality as {@link Key}. */
    private static int indexOf(VmObject self, VmObject target) {
        List<Object> items = list(self);
        Key key = new Key(target);
        for (int i = 0; i < items.size(); i++) {
            if (new Key((VmObject) items.get(i)).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static void stack(final Vm vm) {
        vm.builtin("java/util/Stack", "java/util/Vector")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ArrayList<Object>();
                        return null;
                    }
                })
                .method("push", "(Ljava/lang/Object;)Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        list(self).add(args[0]);
                        return args[0];
                    }
                })
                .method("pop", "()Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<Object> items = list(self);
                        if (items.isEmpty()) {
                            throw vm.raise("java/util/EmptyStackException", "The stack is empty");
                        }
                        return items.remove(items.size() - 1);
                    }
                })
                .method("peek", "()Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<Object> items = list(self);
                        if (items.isEmpty()) {
                            throw vm.raise("java/util/EmptyStackException", "The stack is empty");
                        }
                        return items.get(items.size() - 1);
                    }
                })
                .method("empty", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(list(self).isEmpty());
                    }
                })
                .define();
    }

    private static void hashtable(final Vm vm) {
        vm.builtin("java/util/Hashtable", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new Hashtable<Key, Object>();
                        return null;
                    }
                })
                .method("<init>", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new Hashtable<Key, Object>(Math.max(1, Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (args[0] == null || args[1] == null) {
                            throw vm.nullPointer("Hashtable does not accept null keys or values");
                        }
                        return table(self).put(new Key(Rt.obj(args, 0)), args[1]);
                    }
                })
                .method("get", "(Ljava/lang/Object;)Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return table(self).get(new Key(Rt.obj(args, 0)));
                    }
                })
                .method("remove", "(Ljava/lang/Object;)Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return table(self).remove(new Key(Rt.obj(args, 0)));
                    }
                })
                .method("containsKey", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(table(self).containsKey(new Key(Rt.obj(args, 0))));
                    }
                })
                .method("size", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(table(self).size());
                    }
                })
                .method("isEmpty", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(table(self).isEmpty());
                    }
                })
                .method("clear", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        table(self).clear();
                        return null;
                    }
                })
                .method("keys", "()Ljava/util/Enumeration;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<Object> keys = new ArrayList<Object>();
                        for (Key key : table(self).keySet()) {
                            keys.add(key.value);
                        }
                        return newEnumeration(vm, keys.iterator());
                    }
                })
                .method("elements", "()Ljava/util/Enumeration;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return newEnumeration(vm, new ArrayList<Object>(table(self).values()).iterator());
                    }
                })
                .define();
    }

    private static void random(final Vm vm) {
        vm.builtin("java/util/Random", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new Random(vm.host().currentTimeMillis());
                        return null;
                    }
                })
                .method("<init>", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new Random(Rt.l(args, 0));
                        return null;
                    }
                })
                .method("setSeed", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        ((Random) self.host).setSeed(Rt.l(args, 0));
                        return null;
                    }
                })
                .method("nextInt", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(((Random) self.host).nextInt());
                    }
                })
                .method("nextInt", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int bound = Rt.i(args, 0);
                        if (bound <= 0) {
                            throw vm.raise("java/lang/IllegalArgumentException", "bound must be positive");
                        }
                        return Integer.valueOf(((Random) self.host).nextInt(bound));
                    }
                })
                .method("nextLong", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(((Random) self.host).nextLong());
                    }
                })
                .method("nextBoolean", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(((Random) self.host).nextBoolean());
                    }
                })
                .method("nextFloat", "()F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Float.valueOf(((Random) self.host).nextFloat());
                    }
                })
                .method("nextDouble", "()D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(((Random) self.host).nextDouble());
                    }
                })
                .define();
    }

    private static void date(final Vm vm) {
        vm.builtin("java/util/Date", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Long.valueOf(vm.host().currentTimeMillis());
                        return null;
                    }
                })
                .method("<init>", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Long.valueOf(Rt.l(args, 0));
                        return null;
                    }
                })
                .method("getTime", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.host;
                    }
                })
                .method("setTime", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Long.valueOf(Rt.l(args, 0));
                        return null;
                    }
                })
                .define();
    }
}
