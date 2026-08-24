package com.mobicore.tests;

import com.mobicore.core.rt.Cldc;
import com.mobicore.core.vm.Vm;
import com.mobicore.tools.DirectoryClassSource;
import com.mobicore.core.vm.VmObject;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Runs the fixture program twice — once on the host JVM, once inside the
 * emulator — and compares the results.
 *
 * <p>Differential testing against the real JVM is what makes this suite
 * trustworthy: nobody has to hand-compute the expected value of a bitwise
 * expression, and any opcode the interpreter gets subtly wrong shows up as a
 * mismatch.</p>
 */
public final class VmTest extends Test {

    private static final String PROBE = "demo/VmProbe";

    private final String fixtureDir;
    private Vm vm;
    private Class<?> hostProbe;

    public VmTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Bytecode interpreter";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/VmProbe.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        vm = new Vm();
        Cldc.install(vm);
        vm.addSource(new DirectoryClassSource(fixtureDir));

        URLClassLoader loader = new URLClassLoader(new URL[]{new File(fixtureDir).toURI().toURL()},
                Vm.class.getClassLoader());
        hostProbe = loader.loadClass("demo.VmProbe");

        compareInt("arithmetic", new Class[]{int.class, int.class}, Integer.valueOf(37), Integer.valueOf(5));
        compareInt("arithmetic", new Class[]{int.class, int.class}, Integer.valueOf(-9), Integer.valueOf(4));
        compareInt("bitwise", new Class[]{int.class, int.class}, Integer.valueOf(0xF0F0), Integer.valueOf(0x0FF0));
        compareInt("loops", new Class[]{int.class}, Integer.valueOf(25));
        compareInt("arrays", new Class[]{int.class}, Integer.valueOf(6));
        compareInt("switches", new Class[]{int.class}, Integer.valueOf(2));
        compareInt("switches", new Class[]{int.class}, Integer.valueOf(7));
        compareInt("switches", new Class[]{int.class}, Integer.valueOf(300));
        compareInt("exceptions", new Class[]{int.class}, Integer.valueOf(0));
        compareInt("exceptions", new Class[]{int.class}, Integer.valueOf(1));
        compareInt("exceptions", new Class[]{int.class}, Integer.valueOf(2));
        compareInt("exceptions", new Class[]{int.class}, Integer.valueOf(3));
        compareInt("exceptions", new Class[]{int.class}, Integer.valueOf(9));
        compareInt("recursion", new Class[]{int.class}, Integer.valueOf(10));
        compareInt("polymorphism", new Class[0]);
        compareInt("collections", new Class[0]);
        compareInt("streams", new Class[0]);
        compareInt("randomSeeded", new Class[0]);
        compareInt("staticState", new Class[0]);
        compareInt("threading", new Class[0]);

        compareLong("longMath", new Class[]{long.class, long.class}, Long.valueOf(123456789L), Long.valueOf(987L));
        compareDouble("floatMath", new Class[]{double.class, double.class},
                Double.valueOf(9.5d), Double.valueOf(2.25d));
        compareString("strings", new Class[]{String.class}, "world");

        // Static initialisers must run exactly once, so the second call to a
        // method that mutates static state continues from the first.
        eq(13, ((Integer) callVm("staticState", "()I")).intValue(), "static state persists across calls");

        check(vm.interpreter().executed() > 1000, "the interpreter actually executed bytecode");
        check(vm.loadedClasses().size() > 30, "the CLDC library and fixtures are both loaded");
    }

    private void compareInt(String method, Class<?>[] types, Object... args) throws Exception {
        Object expected = callHost(method, types, args);
        Object actual = callVm(method, descriptor(types, "I"), args);
        eq(expected, actual, method + descriptor(types, "I"));
    }

    private void compareLong(String method, Class<?>[] types, Object... args) throws Exception {
        Object expected = callHost(method, types, args);
        Object actual = callVm(method, descriptor(types, "J"), args);
        eq(expected, actual, method + " long result");
    }

    private void compareDouble(String method, Class<?>[] types, Object... args) throws Exception {
        double expected = ((Double) callHost(method, types, args)).doubleValue();
        double actual = ((Double) callVm(method, descriptor(types, "D"), args)).doubleValue();
        check(Math.abs(expected - actual) < 1e-9, method + " double result: expected " + expected
                + " but was " + actual);
    }

    private void compareString(String method, Class<?>[] types, Object... args) throws Exception {
        String expected = (String) callHost(method, types, args);
        Object[] vmArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            vmArgs[i] = vm.newString((String) args[i]);
        }
        Object result = vm.callStatic(PROBE, method, "(Ljava/lang/String;)Ljava/lang/String;", vmArgs);
        eq(expected, vm.stringOf((VmObject) result), method + " string result");
    }

    private Object callHost(String method, Class<?>[] types, Object[] args) throws Exception {
        Method target = hostProbe.getDeclaredMethod(method, types);
        target.setAccessible(true);
        return target.invoke(null, args);
    }

    private Object callVm(String method, String descriptor, Object... args) {
        return vm.callStatic(PROBE, method, descriptor, args);
    }

    private static String descriptor(Class<?>[] types, String returnType) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> type : types) {
            if (type == int.class) {
                out.append('I');
            } else if (type == long.class) {
                out.append('J');
            } else if (type == double.class) {
                out.append('D');
            } else {
                out.append("Ljava/lang/String;");
            }
        }
        return out.append(')').append(returnType).toString();
    }
}
