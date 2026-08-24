package demo;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

/**
 * Exercises the bytecode the emulator must execute.
 *
 * Compiled by the build script and run through the interpreter by the test
 * suite, so a regression in any opcode shows up as a failing assertion rather
 * than as a mysteriously broken game.
 */
public class VmProbe {

    static int counter;
    static final int CONSTANT = 42;
    static String greeting;

    static {
        counter = 7;
        greeting = "hello";
    }

    public static int arithmetic(int a, int b) {
        int sum = a + b;
        int difference = a - b;
        int product = a * b;
        int quotient = a / b;
        int remainder = a % b;
        return sum + difference * 2 + product / 3 + quotient - remainder;
    }

    public static int bitwise(int a, int b) {
        return (a & b) + (a | b) + (a ^ b) + (a << 2) + (a >> 1) + (a >>> 1) + (~a);
    }

    public static long longMath(long a, long b) {
        long value = a * b + (a << 8) - (b >> 2);
        return value % 1000003L;
    }

    public static double floatMath(double a, double b) {
        double value = a * b + Math.sqrt(a) - Math.floor(b);
        return value + (float) (a / b);
    }

    public static int loops(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += i;
        }
        int j = n;
        while (j > 0) {
            total -= 1;
            j--;
        }
        do {
            total += 2;
        } while (total < 0);
        return total;
    }

    public static int arrays(int n) {
        int[] values = new int[n];
        for (int i = 0; i < n; i++) {
            values[i] = i * i;
        }
        int[][] grid = new int[3][4];
        grid[2][3] = 99;
        byte[] bytes = new byte[]{1, 2, 3};
        char[] chars = new char[]{'a', 'b'};
        long[] longs = new long[]{5L, 6L};
        int total = grid[2][3] + bytes[2] + chars[1] + (int) longs[1] + values.length;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
        }
        return total;
    }

    public static String strings(String name) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[").append(name.toUpperCase()).append("]");
        buffer.append(name.length()).append(':').append(true);
        String joined = buffer.toString() + "-" + CONSTANT;
        return joined.substring(1) + greeting.charAt(0) + name.indexOf('o');
    }

    public static int switches(int key) {
        int result;
        switch (key) {
            case 1: result = 10; break;
            case 2: result = 20; break;
            case 7: result = 70; break;
            default: result = -1; break;
        }
        switch (key) {
            case 100: result += 1; break;
            case 200: result += 2; break;
            case 300: result += 3; break;
            default: result += 5; break;
        }
        return result;
    }

    public static int exceptions(int mode) {
        int result = 0;
        try {
            if (mode == 0) {
                result = 10 / mode;
            }
            if (mode == 1) {
                int[] tiny = new int[1];
                result = tiny[5];
            }
            if (mode == 2) {
                throw new IllegalStateException("custom");
            }
            if (mode == 3) {
                Object value = greeting;
                result = ((Integer) value).intValue();
            }
            result = 1;
        } catch (ArithmeticException e) {
            result = 100;
        } catch (ArrayIndexOutOfBoundsException e) {
            result = 200;
        } catch (IllegalStateException e) {
            result = 300 + e.getMessage().length();
        } catch (ClassCastException e) {
            result = 400;
        } finally {
            result += 1;
        }
        return result;
    }

    public static int recursion(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * recursion(n - 1);
    }

    public static int polymorphism() {
        Shape[] shapes = new Shape[]{new Square(4), new Circle(3), new Square(2)};
        int total = 0;
        for (int i = 0; i < shapes.length; i++) {
            total += shapes[i].area();
            if (shapes[i] instanceof Square) {
                total += ((Square) shapes[i]).sides();
            }
        }
        return total;
    }

    public static int collections() {
        Vector items = new Vector();
        items.addElement("alpha");
        items.addElement("beta");
        items.insertElementAt("gamma", 1);
        items.removeElement("beta");

        Hashtable table = new Hashtable();
        table.put("one", new Integer(1));
        table.put("two", new Integer(2));
        table.put(new String("one"), new Integer(11));

        int total = items.size() * 100 + table.size() * 10;
        total += ((Integer) table.get("one")).intValue();
        total += items.contains("gamma") ? 1000 : 0;
        return total;
    }

    public static int streams() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(1234);
        out.writeUTF("save");
        out.writeBoolean(true);
        out.flush();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        int number = in.readInt();
        String text = in.readUTF();
        boolean flag = in.readBoolean();
        return number + text.length() + (flag ? 1 : 0);
    }

    public static int randomSeeded() {
        Random random = new Random(1234L);
        return random.nextInt(1000) + random.nextInt(1000);
    }

    public static int staticState() {
        counter += 3;
        return counter;
    }

    public static int threading() throws Exception {
        final Counter shared = new Counter();
        Thread worker = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 100; i++) {
                    shared.bump();
                }
            }
        });
        worker.start();
        worker.join();
        return shared.value();
    }

    public static void printBanner() {
        System.out.println("MobiCore VM ready - " + greeting + " x" + counter);
        System.out.println("platform=" + System.getProperty("microedition.platform"));
    }

    static class Counter {
        private int value;

        synchronized void bump() {
            value++;
        }

        synchronized int value() {
            return value;
        }
    }

    interface Shape {
        int area();
    }

    static class Square implements Shape {
        private final int side;

        Square(int side) {
            this.side = side;
        }

        public int area() {
            return side * side;
        }

        int sides() {
            return 4;
        }
    }

    static class Circle implements Shape {
        private final int radius;

        Circle(int radius) {
            this.radius = radius;
        }

        public int area() {
            return radius * radius * 3;
        }
    }
}
