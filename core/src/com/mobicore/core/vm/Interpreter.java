package com.mobicore.core.vm;

import java.util.ArrayList;
import java.util.List;

/**
 * Bytecode execution engine.
 *
 * <p>A straightforward switch-threaded interpreter. Emulated calls recurse onto
 * the host stack, which keeps native and interpreted frames interleaved in one
 * call chain — a native MIDP method can call back into the game's paint routine
 * without any trampolining — at the cost of a frame limit, enforced by
 * {@link Vm#maxFrames()}.</p>
 */
public final class Interpreter {

    private final Vm vm;

    /**
     * Sổ riêng của một luồng: ngăn xếp của nó, lúc lời gọi ngoài cùng bắt đầu,
     * và số lệnh nó đã chạy.
     *
     * <p>Ba thứ này trước đây dùng chung cho mọi luồng, và điều đó làm hỏng
     * đúng cái quan trọng nhất: game nào cũng chạy vòng lặp trên một luồng
     * riêng, nên chỉ cần luồng phụ gọi vào máy ảo là đồng hồ "đợi bao lâu" bị
     * đặt lại — luồng chính treo mãi mà không ai bắt được.</p>
     */
    private static final class Watch {
        final java.lang.ref.WeakReference<Thread> owner =
                new java.lang.ref.WeakReference<Thread>(Thread.currentThread());
        final List<Frame> stack = new ArrayList<Frame>();
        /** Giờ thật lúc lời gọi ngoài cùng bắt đầu; 0 khi luồng đang rỗi. */
        long start;
        long executed;
    }

    private final ThreadLocal<Watch> watches = new ThreadLocal<Watch>() {
        @Override
        protected Watch initialValue() {
            Watch made = new Watch();
            synchronized (living) {
                living.add(made);
            }
            return made;
        }
    };

    /** Sổ của mọi luồng còn sống, để cộng lại thành tổng số lệnh đã chạy. */
    private final List<Watch> living = new ArrayList<Watch>();
    /** Số lệnh của những luồng đã tắt, gộp lại để tổng không tụt xuống. */
    private long retired;

    Interpreter(Vm vm) {
        this.vm = vm;
    }

    /**
     * Bytecodes executed since the last {@link #resetCounter()}, over every
     * thread the game runs.
     *
     * <p>Sổ của luồng đã tắt được gộp vào tổng ngay tại đây rồi bỏ đi, nên
     * một game mở rồi đóng nhiều luồng không làm bảng đếm tụt xuống, cũng
     * không để lại một đống sổ cũ.</p>
     */
    public long executed() {
        long total;
        synchronized (living) {
            for (int i = living.size() - 1; i >= 0; i--) {
                Watch watch = living.get(i);
                Thread owner = watch.owner.get();
                if (owner == null || !owner.isAlive()) {
                    retired += watch.executed;
                    living.remove(i);
                }
            }
            total = retired;
            for (int i = 0; i < living.size(); i++) {
                total += living.get(i).executed;
            }
        }
        return total;
    }

    public void resetCounter() {
        synchronized (living) {
            retired = 0;
            for (int i = 0; i < living.size(); i++) {
                living.get(i).executed = 0;
            }
        }
    }

    /**
     * Hàm trên cùng của một luồng khác, hoặc rỗng khi nó đang không chạy gì.
     *
     * <p>Đọc trộm ngăn xếp của luồng khác trong lúc nó đang chạy, nên chỉ đọc
     * một phần tử và bỏ qua nếu vừa lúc ấy nó đổi: đây là thứ để nhìn, không
     * phải thứ để dựa vào.</p>
     */
    public String topFrameOf(Thread host) {
        Watch watch = null;
        synchronized (living) {
            for (int i = 0; i < living.size(); i++) {
                if (living.get(i).owner.get() == host) {
                    watch = living.get(i);
                    break;
                }
            }
        }
        if (watch == null) {
            return "";
        }
        try {
            List<Frame> stack = watch.stack;
            int size = stack.size();
            return size == 0 ? "" : String.valueOf(stack.get(size - 1));
        } catch (RuntimeException racing) {
            return "";
        }
    }

    /** Current call stack of the calling thread, innermost frame last. */
    public List<Frame> callStack() {
        return watches.get().stack;
    }

    /**
     * Ngăn xếp của lần ném không ai bắt gần nhất.
     *
     * <p>Chụp lại ngay lúc ngoại lệ rời khung ngoài cùng, vì sau đó ngăn xếp
     * đã bị gỡ sạch: chỗ hỏi "game chết ở đâu" — cái bắt được ngoại lệ — lại
     * là chỗ không còn gì để đọc.</p>
     */
    private volatile String lastTrace = "";

    /** Ngăn xếp để kể lại một lần hỏng: đang chạy thì lấy ngay, xong rồi thì lấy lần chót. */
    public String crashTrace() {
        String now = stackTrace();
        return now.length() > 0 ? now : lastTrace;
    }

    /** Formats the emulated call stack for crash reports. */
    public String stackTrace() {
        StringBuilder out = new StringBuilder();
        List<Frame> frames = watches.get().stack;
        for (int i = frames.size() - 1; i >= 0; i--) {
            out.append("    at ").append(frames.get(i)).append('\n');
        }
        return out.toString();
    }

    /**
     * Bao nhiêu lệnh thì ngó đồng hồ một lần.
     *
     * <p>Vòng lặp chính chạy hàng chục triệu lệnh mỗi giây, nên hỏi giờ ở mỗi
     * lệnh là tự làm chậm chính mình. Con số này đủ nhỏ để một game treo bị
     * bắt trong vài phần nghìn giây, và đủ lớn để phép so sánh thêm vào không
     * đo được.</p>
     */
    private static final long CHECK_EVERY = 65536L;

    /**
     * Chỗ ngó ra ngoài: hết giờ chưa, có ai bảo dừng chưa.
     *
     * @return mốc lệnh của lần ngó tiếp theo
     */
    private long checkIn(Watch watch, Frame frame) {
        if (vm.isCancelled()) {
            throw new VmCancelled("Người chơi dừng game");
        }
        long limit = vm.stuckAfterMs();
        if (limit != Long.MAX_VALUE && watch.start > 0) {
            // Giờ thật, không phải giờ của game: điều khiển tốc độ làm đồng hồ
            // của game chạy nhanh chậm khác đi, còn "người ngồi đợi bao lâu"
            // thì không.
            long waited = System.currentTimeMillis() - watch.start;
            if (waited > limit) {
                throw new VmError("Game không phản hồi sau " + describe(waited)
                        + ", đang kẹt trong " + frame.method.key() + threadNote());
            }
        }
        if (watch.executed > vm.instructionBudget()) {
            throw new VmError("Instruction budget exhausted in " + frame.method.key());
        }
        return watch.executed + CHECK_EVERY;
    }

    /**
     * Tên luồng đang kẹt, khi đó không phải luồng chính.
     *
     * <p>Game treo trên luồng riêng của nó thì câu "kẹt trong hàm X" chưa đủ:
     * hàm ấy có thể vẫn đang chạy bình thường ở luồng chính. Nói rõ luồng nào
     * thì người đọc biết ngay phải ngó vào đâu.</p>
     */
    private String threadNote() {
        VmObject thread = vm.threads().current();
        if (thread == null) {
            return "";
        }
        Object name = thread.get("name");
        String label = name == null ? "" : vm.stringOf(name);
        return label.length() == 0 || "chính".equals(label) ? "" : " (luồng \"" + label + "\")";
    }

    /** Khoảng thời gian, nói bằng đơn vị nó đáng được nói. */
    private static String describe(long millis) {
        // Chia lấy nguyên cho 1000 biến một phần tư giây thành "0 giây", tức
        // là một câu nói rằng chẳng có gì xảy ra cả.
        return millis >= 1000 ? (millis / 1000) + " giây" : millis + " mili giây";
    }

    public Object invoke(VmMethod method, VmObject self, Object[] args) {
        if (method.nativeImpl() != null) {
            return method.nativeImpl().invoke(vm, self, args);
        }
        if (method.isAbstract() || method.code() == null) {
            throw vm.raise("java/lang/AbstractMethodError", method.key());
        }
        Watch watch = watches.get();
        List<Frame> stack = watch.stack;
        if (stack.size() >= vm.maxFrames()) {
            throw vm.raise("java/lang/StackOverflowError", method.key());
        }

        Frame frame = new Frame(method);
        int slot = 0;
        if (!method.isStatic()) {
            frame.setLocalRef(slot++, self);
        }
        // Parsed when the method was loaded, not per call: this runs on
        // every invocation a game makes.
        char[] parameters = method.argumentKinds();
        for (int i = 0; i < parameters.length; i++) {
            Object value = args != null && i < args.length ? args[i] : null;
            slot += store(frame, slot, parameters[i], value);
        }

        if (method.isSynchronized()) {
            frame.monitor = method.isStatic() ? vm.mirrorOf(method.owner()) : self;
            Monitors.enter(vm, frame.monitor);
        }
        if (stack.isEmpty()) {
            // Lời gọi ngoài cùng: đồng hồ đo "người chơi đã đợi bao lâu" bắt
            // đầu chạy từ đây, chứ không phải từ lúc mở game. Đồng hồ này của
            // riêng luồng, nên luồng phụ chạy bận không đặt lại đồng hồ của
            // luồng đang treo.
            watch.start = System.currentTimeMillis();
        }
        stack.add(frame);
        try {
            return execute(watch, frame);
        } catch (RuntimeException e) {
            if (stack.size() == 1) {
                // Khung ngoài cùng: ngoại lệ này không ai trong game bắt, và
                // đây là lần cuối ngăn xếp còn đầy đủ.
                lastTrace = stackTrace();
            }
            throw e;
        } finally {
            stack.remove(stack.size() - 1);
            if (stack.isEmpty()) {
                watch.start = 0;
            }
            if (frame.monitor != null) {
                Monitors.exit(vm, frame.monitor);
            }
        }
    }

    private int store(Frame frame, int slot, char kind, Object value) {
        switch (kind) {
            case 'J':
                frame.setLocalLong(slot, value == null ? 0L : ((Number) value).longValue());
                return 2;
            case 'D':
                frame.setLocalLong(slot, Double.doubleToRawLongBits(
                        value == null ? 0d : ((Number) value).doubleValue()));
                return 2;
            case 'F':
                frame.setLocal(slot, Float.floatToRawIntBits(
                        value == null ? 0f : ((Number) value).floatValue()));
                return 1;
            case 'L':
            case '[':
                frame.setLocalRef(slot, value);
                return 1;
            default:
                frame.setLocal(slot, value == null ? 0 : ((Number) value).intValue());
                return 1;
        }
    }

    // ------------------------------------------------------------- main loop

    private Object execute(Watch watch, Frame frame) {
        byte[] code = frame.method.code();
        VmClass owner = frame.method.owner();
        long checkpoint = watch.executed + CHECK_EVERY;

        while (true) {
            try {
                while (true) {
                    if (++watch.executed > checkpoint) {
                        checkpoint = checkIn(watch, frame);
                    }
                    int pc = frame.pc;
                    int op = code[pc] & 0xFF;
                    frame.pc = pc + 1;

                    switch (op) {
                        case Opcodes.NOP:
                            break;
                        case Opcodes.ACONST_NULL:
                            frame.pushRef(null);
                            break;
                        case Opcodes.ICONST_M1: case Opcodes.ICONST_0: case Opcodes.ICONST_1:
                        case Opcodes.ICONST_2: case Opcodes.ICONST_3: case Opcodes.ICONST_4:
                        case Opcodes.ICONST_5:
                            frame.push(op - Opcodes.ICONST_0);
                            break;
                        case Opcodes.LCONST_0: case Opcodes.LCONST_1:
                            frame.pushLong(op - Opcodes.LCONST_0);
                            break;
                        case Opcodes.FCONST_0: case Opcodes.FCONST_1: case Opcodes.FCONST_2:
                            frame.pushFloat(op - Opcodes.FCONST_0);
                            break;
                        case Opcodes.DCONST_0: case Opcodes.DCONST_1:
                            frame.pushDouble(op - Opcodes.DCONST_0);
                            break;
                        case Opcodes.BIPUSH:
                            frame.push(code[frame.pc++]);
                            break;
                        case Opcodes.SIPUSH:
                            frame.push((short) readU2(code, frame));
                            break;
                        case Opcodes.LDC:
                            loadConstant(frame, owner, code[frame.pc++] & 0xFF, false);
                            break;
                        case Opcodes.LDC_W:
                            loadConstant(frame, owner, readU2(code, frame), false);
                            break;
                        case Opcodes.LDC2_W:
                            loadConstant(frame, owner, readU2(code, frame), true);
                            break;

                        case Opcodes.ILOAD: case Opcodes.FLOAD:
                            frame.push(frame.local(code[frame.pc++] & 0xFF));
                            break;
                        case Opcodes.LLOAD: case Opcodes.DLOAD:
                            frame.pushLong(frame.localLong(code[frame.pc++] & 0xFF));
                            break;
                        case Opcodes.ALOAD:
                            frame.pushRef(frame.localRef(code[frame.pc++] & 0xFF));
                            break;
                        case 0x1A: case 0x1B: case 0x1C: case 0x1D:
                            frame.push(frame.local(op - Opcodes.ILOAD_0));
                            break;
                        case 0x1E: case 0x1F: case 0x20: case 0x21:
                            frame.pushLong(frame.localLong(op - Opcodes.LLOAD_0));
                            break;
                        case 0x22: case 0x23: case 0x24: case 0x25:
                            frame.push(frame.local(op - Opcodes.FLOAD_0));
                            break;
                        case 0x26: case 0x27: case 0x28: case 0x29:
                            frame.pushLong(frame.localLong(op - Opcodes.DLOAD_0));
                            break;
                        case 0x2A: case 0x2B: case 0x2C: case 0x2D:
                            frame.pushRef(frame.localRef(op - Opcodes.ALOAD_0));
                            break;

                        case Opcodes.ISTORE: case Opcodes.FSTORE:
                            frame.setLocal(code[frame.pc++] & 0xFF, frame.pop());
                            break;
                        case Opcodes.LSTORE: case Opcodes.DSTORE:
                            frame.setLocalLong(code[frame.pc++] & 0xFF, frame.popLong());
                            break;
                        case Opcodes.ASTORE:
                            frame.setLocalRef(code[frame.pc++] & 0xFF, frame.popRef());
                            break;
                        case 0x3B: case 0x3C: case 0x3D: case 0x3E:
                            frame.setLocal(op - Opcodes.ISTORE_0, frame.pop());
                            break;
                        case 0x3F: case 0x40: case 0x41: case 0x42:
                            frame.setLocalLong(op - Opcodes.LSTORE_0, frame.popLong());
                            break;
                        case 0x43: case 0x44: case 0x45: case 0x46:
                            frame.setLocal(op - Opcodes.FSTORE_0, frame.pop());
                            break;
                        case 0x47: case 0x48: case 0x49: case 0x4A:
                            frame.setLocalLong(op - Opcodes.DSTORE_0, frame.popLong());
                            break;
                        case 0x4B: case 0x4C: case 0x4D: case 0x4E:
                            frame.setLocalRef(op - Opcodes.ASTORE_0, frame.popRef());
                            break;

                        case Opcodes.IALOAD: case Opcodes.FALOAD: case Opcodes.BALOAD:
                        case Opcodes.CALOAD: case Opcodes.SALOAD: case Opcodes.LALOAD:
                        case Opcodes.DALOAD: case Opcodes.AALOAD:
                            arrayLoad(frame, op);
                            break;
                        case Opcodes.IASTORE: case Opcodes.FASTORE: case Opcodes.BASTORE:
                        case Opcodes.CASTORE: case Opcodes.SASTORE: case Opcodes.LASTORE:
                        case Opcodes.DASTORE: case Opcodes.AASTORE:
                            arrayStore(frame, op);
                            break;

                        case Opcodes.POP:
                            frame.sp--;
                            frame.stackRefs[frame.sp] = null;
                            break;
                        case Opcodes.POP2:
                            frame.stackRefs[--frame.sp] = null;
                            frame.stackRefs[--frame.sp] = null;
                            break;
                        case Opcodes.DUP:
                            copySlot(frame, frame.sp - 1, frame.sp);
                            frame.sp++;
                            break;
                        case Opcodes.DUP_X1:
                            insert(frame, 1, 1);
                            break;
                        case Opcodes.DUP_X2:
                            insert(frame, 1, 2);
                            break;
                        case Opcodes.DUP2:
                            insert(frame, 2, 0);
                            break;
                        case Opcodes.DUP2_X1:
                            insert(frame, 2, 1);
                            break;
                        case Opcodes.DUP2_X2:
                            insert(frame, 2, 2);
                            break;
                        case Opcodes.SWAP: {
                            int a = frame.stack[frame.sp - 1];
                            Object ar = frame.stackRefs[frame.sp - 1];
                            frame.stack[frame.sp - 1] = frame.stack[frame.sp - 2];
                            frame.stackRefs[frame.sp - 1] = frame.stackRefs[frame.sp - 2];
                            frame.stack[frame.sp - 2] = a;
                            frame.stackRefs[frame.sp - 2] = ar;
                            break;
                        }

                        case Opcodes.IADD: frame.push(frame.pop() + frame.pop()); break;
                        case Opcodes.ISUB: { int b = frame.pop(); frame.push(frame.pop() - b); break; }
                        case Opcodes.IMUL: frame.push(frame.pop() * frame.pop()); break;
                        case Opcodes.IDIV: {
                            int b = frame.pop();
                            if (b == 0) {
                                throw vm.raise("java/lang/ArithmeticException", "/ by zero");
                            }
                            frame.push(frame.pop() / b);
                            break;
                        }
                        case Opcodes.IREM: {
                            int b = frame.pop();
                            if (b == 0) {
                                throw vm.raise("java/lang/ArithmeticException", "/ by zero");
                            }
                            frame.push(frame.pop() % b);
                            break;
                        }
                        case Opcodes.INEG: frame.push(-frame.pop()); break;
                        case Opcodes.ISHL: { int b = frame.pop(); frame.push(frame.pop() << (b & 31)); break; }
                        case Opcodes.ISHR: { int b = frame.pop(); frame.push(frame.pop() >> (b & 31)); break; }
                        case Opcodes.IUSHR: { int b = frame.pop(); frame.push(frame.pop() >>> (b & 31)); break; }
                        case Opcodes.IAND: frame.push(frame.pop() & frame.pop()); break;
                        case Opcodes.IOR: frame.push(frame.pop() | frame.pop()); break;
                        case Opcodes.IXOR: frame.push(frame.pop() ^ frame.pop()); break;

                        case Opcodes.LADD: frame.pushLong(frame.popLong() + frame.popLong()); break;
                        case Opcodes.LSUB: { long b = frame.popLong(); frame.pushLong(frame.popLong() - b); break; }
                        case Opcodes.LMUL: frame.pushLong(frame.popLong() * frame.popLong()); break;
                        case Opcodes.LDIV: {
                            long b = frame.popLong();
                            if (b == 0) {
                                throw vm.raise("java/lang/ArithmeticException", "/ by zero");
                            }
                            frame.pushLong(frame.popLong() / b);
                            break;
                        }
                        case Opcodes.LREM: {
                            long b = frame.popLong();
                            if (b == 0) {
                                throw vm.raise("java/lang/ArithmeticException", "/ by zero");
                            }
                            frame.pushLong(frame.popLong() % b);
                            break;
                        }
                        case Opcodes.LNEG: frame.pushLong(-frame.popLong()); break;
                        case Opcodes.LSHL: { int b = frame.pop(); frame.pushLong(frame.popLong() << (b & 63)); break; }
                        case Opcodes.LSHR: { int b = frame.pop(); frame.pushLong(frame.popLong() >> (b & 63)); break; }
                        case Opcodes.LUSHR: { int b = frame.pop(); frame.pushLong(frame.popLong() >>> (b & 63)); break; }
                        case Opcodes.LAND: frame.pushLong(frame.popLong() & frame.popLong()); break;
                        case Opcodes.LOR: frame.pushLong(frame.popLong() | frame.popLong()); break;
                        case Opcodes.LXOR: frame.pushLong(frame.popLong() ^ frame.popLong()); break;
                        case Opcodes.LCMP: {
                            long b = frame.popLong();
                            long a = frame.popLong();
                            frame.push(a < b ? -1 : (a == b ? 0 : 1));
                            break;
                        }

                        case Opcodes.FADD: frame.pushFloat(frame.popFloat() + frame.popFloat()); break;
                        case Opcodes.FSUB: { float b = frame.popFloat(); frame.pushFloat(frame.popFloat() - b); break; }
                        case Opcodes.FMUL: frame.pushFloat(frame.popFloat() * frame.popFloat()); break;
                        case Opcodes.FDIV: { float b = frame.popFloat(); frame.pushFloat(frame.popFloat() / b); break; }
                        case Opcodes.FREM: { float b = frame.popFloat(); frame.pushFloat(frame.popFloat() % b); break; }
                        case Opcodes.FNEG: frame.pushFloat(-frame.popFloat()); break;
                        case Opcodes.FCMPL: case Opcodes.FCMPG: {
                            float b = frame.popFloat();
                            float a = frame.popFloat();
                            if (Float.isNaN(a) || Float.isNaN(b)) {
                                frame.push(op == Opcodes.FCMPG ? 1 : -1);
                            } else {
                                frame.push(a < b ? -1 : (a == b ? 0 : 1));
                            }
                            break;
                        }

                        case Opcodes.DADD: frame.pushDouble(frame.popDouble() + frame.popDouble()); break;
                        case Opcodes.DSUB: { double b = frame.popDouble(); frame.pushDouble(frame.popDouble() - b); break; }
                        case Opcodes.DMUL: frame.pushDouble(frame.popDouble() * frame.popDouble()); break;
                        case Opcodes.DDIV: { double b = frame.popDouble(); frame.pushDouble(frame.popDouble() / b); break; }
                        case Opcodes.DREM: { double b = frame.popDouble(); frame.pushDouble(frame.popDouble() % b); break; }
                        case Opcodes.DNEG: frame.pushDouble(-frame.popDouble()); break;
                        case Opcodes.DCMPL: case Opcodes.DCMPG: {
                            double b = frame.popDouble();
                            double a = frame.popDouble();
                            if (Double.isNaN(a) || Double.isNaN(b)) {
                                frame.push(op == Opcodes.DCMPG ? 1 : -1);
                            } else {
                                frame.push(a < b ? -1 : (a == b ? 0 : 1));
                            }
                            break;
                        }

                        case Opcodes.IINC: {
                            int index = code[frame.pc++] & 0xFF;
                            frame.setLocal(index, frame.local(index) + code[frame.pc++]);
                            break;
                        }

                        case Opcodes.I2L: frame.pushLong(frame.pop()); break;
                        case Opcodes.I2F: frame.pushFloat(frame.pop()); break;
                        case Opcodes.I2D: frame.pushDouble(frame.pop()); break;
                        case Opcodes.L2I: frame.push((int) frame.popLong()); break;
                        case Opcodes.L2F: frame.pushFloat(frame.popLong()); break;
                        case Opcodes.L2D: frame.pushDouble(frame.popLong()); break;
                        case Opcodes.F2I: frame.push((int) frame.popFloat()); break;
                        case Opcodes.F2L: frame.pushLong((long) frame.popFloat()); break;
                        case Opcodes.F2D: frame.pushDouble(frame.popFloat()); break;
                        case Opcodes.D2I: frame.push((int) frame.popDouble()); break;
                        case Opcodes.D2L: frame.pushLong((long) frame.popDouble()); break;
                        case Opcodes.D2F: frame.pushFloat((float) frame.popDouble()); break;
                        case Opcodes.I2B: frame.push((byte) frame.pop()); break;
                        case Opcodes.I2C: frame.push((char) frame.pop()); break;
                        case Opcodes.I2S: frame.push((short) frame.pop()); break;

                        case Opcodes.IFEQ: branchIf(frame, code, pc, frame.pop() == 0); break;
                        case Opcodes.IFNE: branchIf(frame, code, pc, frame.pop() != 0); break;
                        case Opcodes.IFLT: branchIf(frame, code, pc, frame.pop() < 0); break;
                        case Opcodes.IFGE: branchIf(frame, code, pc, frame.pop() >= 0); break;
                        case Opcodes.IFGT: branchIf(frame, code, pc, frame.pop() > 0); break;
                        case Opcodes.IFLE: branchIf(frame, code, pc, frame.pop() <= 0); break;
                        case Opcodes.IF_ICMPEQ: { int b = frame.pop(); branchIf(frame, code, pc, frame.pop() == b); break; }
                        case Opcodes.IF_ICMPNE: { int b = frame.pop(); branchIf(frame, code, pc, frame.pop() != b); break; }
                        case Opcodes.IF_ICMPLT: { int b = frame.pop(); branchIf(frame, code, pc, frame.pop() < b); break; }
                        case Opcodes.IF_ICMPGE: { int b = frame.pop(); branchIf(frame, code, pc, frame.pop() >= b); break; }
                        case Opcodes.IF_ICMPGT: { int b = frame.pop(); branchIf(frame, code, pc, frame.pop() > b); break; }
                        case Opcodes.IF_ICMPLE: { int b = frame.pop(); branchIf(frame, code, pc, frame.pop() <= b); break; }
                        case Opcodes.IF_ACMPEQ: { Object b = frame.popRef(); branchIf(frame, code, pc, frame.popRef() == b); break; }
                        case Opcodes.IF_ACMPNE: { Object b = frame.popRef(); branchIf(frame, code, pc, frame.popRef() != b); break; }
                        case Opcodes.IFNULL: branchIf(frame, code, pc, frame.popRef() == null); break;
                        case Opcodes.IFNONNULL: branchIf(frame, code, pc, frame.popRef() != null); break;
                        case Opcodes.GOTO: frame.pc = pc + (short) readU2(code, frame); break;
                        case Opcodes.GOTO_W: frame.pc = pc + readS4(code, frame); break;

                        case Opcodes.JSR:
                            frame.push(frame.pc + 2);
                            frame.pc = pc + (short) readU2(code, frame);
                            break;
                        case Opcodes.JSR_W:
                            frame.push(frame.pc + 4);
                            frame.pc = pc + readS4(code, frame);
                            break;
                        case Opcodes.RET:
                            frame.pc = frame.local(code[frame.pc] & 0xFF);
                            break;

                        case Opcodes.TABLESWITCH: tableSwitch(frame, code, pc); break;
                        case Opcodes.LOOKUPSWITCH: lookupSwitch(frame, code, pc); break;

                        // Return values use one boxed type per JVM type, so a
                        // value crossing native and interpreted frames is never
                        // reinterpreted as raw bits.
                        case Opcodes.IRETURN:
                            return Integer.valueOf(frame.pop());
                        case Opcodes.FRETURN:
                            return Float.valueOf(frame.popFloat());
                        case Opcodes.LRETURN:
                            return Long.valueOf(frame.popLong());
                        case Opcodes.DRETURN:
                            return Double.valueOf(frame.popDouble());
                        case Opcodes.ARETURN:
                            return frame.popRef();
                        case Opcodes.RETURN:
                            return null;

                        case Opcodes.GETSTATIC: getStatic(frame, owner, readU2(code, frame)); break;
                        case Opcodes.PUTSTATIC: putStatic(frame, owner, readU2(code, frame)); break;
                        case Opcodes.GETFIELD: getField(frame, owner, readU2(code, frame)); break;
                        case Opcodes.PUTFIELD: putField(frame, owner, readU2(code, frame)); break;

                        case Opcodes.INVOKEVIRTUAL: invokeVirtual(frame, owner, readU2(code, frame), false); break;
                        case Opcodes.INVOKEINTERFACE: {
                            int index = readU2(code, frame);
                            frame.pc += 2; // count and a reserved zero byte
                            invokeVirtual(frame, owner, index, true);
                            break;
                        }
                        case Opcodes.INVOKESPECIAL: invokeSpecial(frame, owner, readU2(code, frame)); break;
                        case Opcodes.INVOKESTATIC: invokeStatic(frame, owner, readU2(code, frame)); break;
                        case Opcodes.INVOKEDYNAMIC:
                            throw new VmError("invokedynamic is not part of CLDC and is unsupported");

                        case Opcodes.NEW: {
                            VmClass type = resolveClass(owner, readU2(code, frame));
                            frame.pushRef(vm.newInstance(type));
                            break;
                        }
                        case Opcodes.NEWARRAY:
                            frame.pushRef(vm.newArray(Opcodes.arrayTypeDescriptor(code[frame.pc++] & 0xFF), frame.pop()));
                            break;
                        case Opcodes.ANEWARRAY: {
                            VmClass type = resolveClass(owner, readU2(code, frame));
                            String component = type.isArray() ? type.name() : "L" + type.name() + ";";
                            frame.pushRef(vm.newArray(component, frame.pop()));
                            break;
                        }
                        case Opcodes.MULTIANEWARRAY: {
                            VmClass type = resolveClass(owner, readU2(code, frame));
                            int dimensions = code[frame.pc++] & 0xFF;
                            int[] sizes = new int[dimensions];
                            for (int i = dimensions - 1; i >= 0; i--) {
                                sizes[i] = frame.pop();
                            }
                            frame.pushRef(newMultiArray(type.name(), sizes, 0));
                            break;
                        }
                        case Opcodes.ARRAYLENGTH: {
                            Object array = frame.popRef();
                            if (array == null) {
                                throw vm.nullPointer("arraylength on null");
                            }
                            frame.push(((VmArray) array).length());
                            break;
                        }
                        case Opcodes.ATHROW: {
                            VmObject thrown = (VmObject) frame.popRef();
                            if (thrown == null) {
                                throw vm.nullPointer("athrow of null");
                            }
                            throw new VmThrow(thrown, messageOf(thrown));
                        }
                        case Opcodes.CHECKCAST: {
                            VmClass type = resolveClass(owner, readU2(code, frame));
                            Object value = frame.peekRef(0);
                            if (value != null && !((VmObject) value).type().isAssignableTo(type)) {
                                throw vm.raise("java/lang/ClassCastException",
                                        ((VmObject) value).type().binaryName() + " cannot be cast to "
                                                + type.binaryName());
                            }
                            break;
                        }
                        case Opcodes.INSTANCEOF: {
                            VmClass type = resolveClass(owner, readU2(code, frame));
                            Object value = frame.popRef();
                            frame.push(value != null && ((VmObject) value).type().isAssignableTo(type) ? 1 : 0);
                            break;
                        }
                        case Opcodes.MONITORENTER:
                            Monitors.enter(vm, (VmObject) frame.popRef());
                            break;
                        case Opcodes.MONITOREXIT:
                            Monitors.exit(vm, (VmObject) frame.popRef());
                            break;
                        case Opcodes.WIDE:
                            wide(frame, code);
                            break;
                        default:
                            throw new VmError("Unsupported opcode 0x" + Integer.toHexString(op)
                                    + " in " + frame.method.key());
                    }
                }
            } catch (VmThrow thrown) {
                int handler = findHandler(frame, thrown);
                if (handler < 0) {
                    throw thrown;
                }
                frame.sp = 0;
                frame.pushRef(thrown.throwable());
                frame.pc = handler;
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private String messageOf(VmObject throwable) {
        VmField field = throwable.type().findField("message");
        if (field == null) {
            return null;
        }
        return vm.stringOf(throwable.getRef(field.slot()));
    }

    private int findHandler(Frame frame, VmThrow thrown) {
        int[] table = frame.method.exceptionTable();
        // frame.pc has already advanced past the operands of the failing
        // instruction, so match against the instruction that raised.
        int pc = frame.pc - 1;
        for (int i = 0; i + 3 < table.length; i += 4) {
            if (pc < table[i] || pc >= table[i + 1]) {
                continue;
            }
            int catchType = table[i + 3];
            if (catchType == 0) {
                return table[i + 2];
            }
            VmClass expected = resolveClass(frame.method.owner(), catchType);
            if (thrown.type() != null && thrown.type().isAssignableTo(expected)) {
                return table[i + 2];
            }
        }
        return -1;
    }

    private static int readU2(byte[] code, Frame frame) {
        int value = ((code[frame.pc] & 0xFF) << 8) | (code[frame.pc + 1] & 0xFF);
        frame.pc += 2;
        return value;
    }

    private static int readS4(byte[] code, Frame frame) {
        int value = ((code[frame.pc] & 0xFF) << 24) | ((code[frame.pc + 1] & 0xFF) << 16)
                | ((code[frame.pc + 2] & 0xFF) << 8) | (code[frame.pc + 3] & 0xFF);
        frame.pc += 4;
        return value;
    }

    private static void branchIf(Frame frame, byte[] code, int opcodePc, boolean condition) {
        int offset = (short) readU2(code, frame);
        if (condition) {
            frame.pc = opcodePc + offset;
        }
    }

    private static void copySlot(Frame frame, int from, int to) {
        frame.stack[to] = frame.stack[from];
        frame.stackRefs[to] = frame.stackRefs[from];
    }

    /**
     * Implements the dup family: duplicates the top {@code count} slots and
     * reinserts them {@code below} slots further down.
     */
    private static void insert(Frame frame, int count, int below) {
        int total = count + below;
        int[] values = new int[total];
        Object[] refs = new Object[total];
        for (int i = 0; i < total; i++) {
            values[i] = frame.stack[frame.sp - total + i];
            refs[i] = frame.stackRefs[frame.sp - total + i];
        }
        frame.sp -= total;
        for (int i = 0; i < count; i++) {
            frame.stack[frame.sp] = values[below + i];
            frame.stackRefs[frame.sp] = refs[below + i];
            frame.sp++;
        }
        for (int i = 0; i < total; i++) {
            frame.stack[frame.sp] = values[i];
            frame.stackRefs[frame.sp] = refs[i];
            frame.sp++;
        }
    }

    private void wide(Frame frame, byte[] code) {
        int op = code[frame.pc++] & 0xFF;
        int index = readU2(code, frame);
        switch (op) {
            case Opcodes.ILOAD: case Opcodes.FLOAD: frame.push(frame.local(index)); break;
            case Opcodes.LLOAD: case Opcodes.DLOAD: frame.pushLong(frame.localLong(index)); break;
            case Opcodes.ALOAD: frame.pushRef(frame.localRef(index)); break;
            case Opcodes.ISTORE: case Opcodes.FSTORE: frame.setLocal(index, frame.pop()); break;
            case Opcodes.LSTORE: case Opcodes.DSTORE: frame.setLocalLong(index, frame.popLong()); break;
            case Opcodes.ASTORE: frame.setLocalRef(index, frame.popRef()); break;
            case Opcodes.RET: frame.pc = frame.local(index); break;
            case Opcodes.IINC: frame.setLocal(index, frame.local(index) + (short) readU2(code, frame)); break;
            default: throw new VmError("Unsupported wide opcode 0x" + Integer.toHexString(op));
        }
    }

    private void tableSwitch(Frame frame, byte[] code, int opcodePc) {
        frame.pc = (opcodePc + 4) & ~3;
        int defaultOffset = readS4(code, frame);
        int low = readS4(code, frame);
        int high = readS4(code, frame);
        int key = frame.pop();
        if (key < low || key > high) {
            frame.pc = opcodePc + defaultOffset;
            return;
        }
        frame.pc += (key - low) * 4;
        frame.pc = opcodePc + readS4(code, frame);
    }

    private void lookupSwitch(Frame frame, byte[] code, int opcodePc) {
        frame.pc = (opcodePc + 4) & ~3;
        int defaultOffset = readS4(code, frame);
        int pairs = readS4(code, frame);
        int key = frame.pop();
        for (int i = 0; i < pairs; i++) {
            int match = readS4(code, frame);
            int offset = readS4(code, frame);
            if (match == key) {
                frame.pc = opcodePc + offset;
                return;
            }
        }
        frame.pc = opcodePc + defaultOffset;
    }

    private VmArray newMultiArray(String descriptor, int[] sizes, int dimension) {
        String component = descriptor.substring(1);
        VmArray array = vm.newArray(component, sizes[dimension]);
        if (dimension + 1 < sizes.length) {
            Object[] slots = array.objects();
            for (int i = 0; i < sizes[dimension]; i++) {
                slots[i] = newMultiArray(component, sizes, dimension + 1);
            }
        }
        return array;
    }

    private void arrayLoad(Frame frame, int op) {
        int index = frame.pop();
        VmArray array = checkArray(frame.popRef(), index);
        switch (op) {
            case Opcodes.IALOAD: frame.push(array.ints()[index]); break;
            case Opcodes.FALOAD: frame.push(Float.floatToRawIntBits(array.floats()[index])); break;
            case Opcodes.BALOAD: frame.push(array.componentKind() == 'Z'
                    ? array.bytes()[index] & 1 : array.bytes()[index]); break;
            case Opcodes.CALOAD: frame.push(array.chars()[index]); break;
            case Opcodes.SALOAD: frame.push(array.shorts()[index]); break;
            case Opcodes.LALOAD: frame.pushLong(array.longs()[index]); break;
            case Opcodes.DALOAD: frame.pushDouble(array.doubles()[index]); break;
            default: frame.pushRef(array.objects()[index]); break;
        }
    }

    private void arrayStore(Frame frame, int op) {
        if (op == Opcodes.AASTORE) {
            Object value = frame.popRef();
            int index = frame.pop();
            checkArray(frame.popRef(), index).objects()[index] = value;
            return;
        }
        if (op == Opcodes.LASTORE || op == Opcodes.DASTORE) {
            long value = frame.popLong();
            int index = frame.pop();
            VmArray array = checkArray(frame.popRef(), index);
            if (op == Opcodes.LASTORE) {
                array.longs()[index] = value;
            } else {
                array.doubles()[index] = Double.longBitsToDouble(value);
            }
            return;
        }
        int value = frame.pop();
        int index = frame.pop();
        VmArray array = checkArray(frame.popRef(), index);
        switch (op) {
            case Opcodes.IASTORE: array.ints()[index] = value; break;
            case Opcodes.FASTORE: array.floats()[index] = Float.intBitsToFloat(value); break;
            case Opcodes.BASTORE: array.bytes()[index] = (byte) value; break;
            case Opcodes.CASTORE: array.chars()[index] = (char) value; break;
            default: array.shorts()[index] = (short) value; break;
        }
    }

    private VmArray checkArray(Object reference, int index) {
        if (reference == null) {
            throw vm.nullPointer("array access on null");
        }
        VmArray array = (VmArray) reference;
        if (index < 0 || index >= array.length()) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException",
                    index + " is outside 0.." + (array.length() - 1));
        }
        return array;
    }

    private void loadConstant(Frame frame, VmClass owner, int index, boolean wide) {
        ConstantPool pool = owner.constantPool();
        switch (pool.tag(index)) {
            case ConstantPool.INTEGER: frame.push(pool.intValue(index)); break;
            case ConstantPool.FLOAT: frame.push(pool.intValue(index)); break;
            case ConstantPool.LONG: frame.pushLong(pool.longValue(index)); break;
            case ConstantPool.DOUBLE: frame.pushLong(pool.longValue(index)); break;
            case ConstantPool.STRING: {
                Object cached = owner.resolved(index);
                if (cached == null) {
                    cached = vm.internString(pool.stringValue(index));
                    owner.setResolved(index, cached);
                }
                frame.pushRef(cached);
                break;
            }
            case ConstantPool.CLASS:
                frame.pushRef(vm.mirrorOf(resolveClass(owner, index)));
                break;
            default:
                throw new VmError("ldc" + (wide ? "2_w" : "") + " on unsupported constant " + index);
        }
    }

    VmClass resolveClass(VmClass owner, int index) {
        Object cached = owner.resolved(index);
        if (cached instanceof VmClass) {
            return (VmClass) cached;
        }
        VmClass type = vm.loadClass(owner.constantPool().className(index));
        owner.setResolved(index, type);
        return type;
    }

    private VmField resolveField(VmClass owner, int index) {
        Object cached = owner.resolved(index);
        if (cached instanceof VmField) {
            return (VmField) cached;
        }
        ConstantPool pool = owner.constantPool();
        VmClass declaring = vm.loadClass(pool.refClass(index));
        vm.initialize(declaring);
        VmField field = declaring.findField(pool.refName(index));
        if (field == null) {
            throw new VmError("No field " + pool.refClass(index) + "." + pool.refName(index));
        }
        owner.setResolved(index, field);
        return field;
    }

    private VmMethod resolveMethod(VmClass owner, int index) {
        Object cached = owner.resolved(index);
        if (cached instanceof VmMethod) {
            return (VmMethod) cached;
        }
        ConstantPool pool = owner.constantPool();
        VmClass declaring = vm.loadClass(pool.refClass(index));
        VmMethod method = declaring.findMethod(pool.refName(index), pool.refDescriptor(index));
        if (method == null) {
            throw vm.raise("java/lang/NoSuchMethodError",
                    Descriptors.toBinaryName(pool.refClass(index)) + "." + pool.refName(index)
                            + pool.refDescriptor(index));
        }
        owner.setResolved(index, method);
        return method;
    }

    private void getStatic(Frame frame, VmClass owner, int index) {
        VmField field = resolveField(owner, index);
        VmClass declaring = field.owner();
        if (field.isReference()) {
            frame.pushRef(declaring.staticRefs()[field.slot()]);
        } else if (field.isWide()) {
            frame.pushLong(declaring.getStaticLong(field.slot()));
        } else {
            frame.push(declaring.staticInts()[field.slot()]);
        }
    }

    private void putStatic(Frame frame, VmClass owner, int index) {
        VmField field = resolveField(owner, index);
        VmClass declaring = field.owner();
        if (field.isReference()) {
            declaring.staticRefs()[field.slot()] = frame.popRef();
        } else if (field.isWide()) {
            declaring.setStaticLong(field.slot(), frame.popLong());
        } else {
            declaring.staticInts()[field.slot()] = frame.pop();
        }
    }

    private void getField(Frame frame, VmClass owner, int index) {
        VmField field = resolveField(owner, index);
        VmObject target = (VmObject) frame.popRef();
        if (target == null) {
            throw vm.nullPointer("read of " + field.name() + " on null");
        }
        if (field.isReference()) {
            frame.pushRef(target.getRef(field.slot()));
        } else if (field.isWide()) {
            frame.pushLong(target.getLong(field.slot()));
        } else {
            frame.push(target.getInt(field.slot()));
        }
    }

    private void putField(Frame frame, VmClass owner, int index) {
        VmField field = resolveField(owner, index);
        if (field.isReference()) {
            Object value = frame.popRef();
            VmObject target = requireTarget(frame, field);
            target.setRef(field.slot(), value);
        } else if (field.isWide()) {
            long value = frame.popLong();
            requireTarget(frame, field).setLong(field.slot(), value);
        } else {
            int value = frame.pop();
            requireTarget(frame, field).setInt(field.slot(), value);
        }
    }

    private VmObject requireTarget(Frame frame, VmField field) {
        VmObject target = (VmObject) frame.popRef();
        if (target == null) {
            throw vm.nullPointer("write to " + field.name() + " on null");
        }
        return target;
    }

    /** Pops the declared arguments of a method, innermost last. */
    private Object[] popArguments(Frame frame, VmMethod method) {
        char[] kinds = method.argumentKinds();
        Object[] args = new Object[kinds.length];
        for (int i = kinds.length - 1; i >= 0; i--) {
            char kind = kinds[i];
            switch (kind) {
                case 'J': args[i] = Long.valueOf(frame.popLong()); break;
                case 'D': args[i] = Double.valueOf(frame.popDouble()); break;
                case 'F': args[i] = Float.valueOf(frame.popFloat()); break;
                case 'L': case '[': args[i] = frame.popRef(); break;
                default: args[i] = Integer.valueOf(frame.pop()); break;
            }
        }
        return args;
    }

    private void pushResult(Frame frame, VmMethod method, Object result) {
        switch (method.returnKind()) {
            case 'V':
                break;
            case 'J':
                frame.pushLong(result == null ? 0L : ((Number) result).longValue());
                break;
            case 'D':
                frame.pushDouble(result == null ? 0d : ((Number) result).doubleValue());
                break;
            case 'F':
                frame.pushFloat(result == null ? 0f : ((Number) result).floatValue());
                break;
            case 'L': case '[':
                frame.pushRef(result);
                break;
            default:
                frame.push(result == null ? 0 : ((Number) result).intValue());
                break;
        }
    }

    private void invokeVirtual(Frame frame, VmClass owner, int index, boolean isInterface) {
        VmMethod declared = resolveMethod(owner, index);
        Object[] args = popArguments(frame, declared);
        VmObject self = (VmObject) frame.popRef();
        if (self == null) {
            throw vm.nullPointer("call to " + declared.name() + " on null");
        }
        VmMethod target = self.type().resolveVirtual(declared);
        if (target == null || target.isAbstract()) {
            throw vm.raise("java/lang/AbstractMethodError",
                    self.type().binaryName() + "." + declared.name() + declared.descriptor());
        }
        pushResult(frame, declared, invoke(target, self, args));
    }

    private void invokeSpecial(Frame frame, VmClass owner, int index) {
        VmMethod target = resolveMethod(owner, index);
        Object[] args = popArguments(frame, target);
        VmObject self = (VmObject) frame.popRef();
        if (self == null) {
            throw vm.nullPointer("call to " + target.name() + " on null");
        }
        if (target.isAbstract()) {
            // A builtin superclass may declare a constructor abstractly; there
            // is nothing to run, and the subclass has already been allocated.
            pushResult(frame, target, null);
            return;
        }
        pushResult(frame, target, invoke(target, self, args));
    }

    private void invokeStatic(Frame frame, VmClass owner, int index) {
        VmMethod target = resolveMethod(owner, index);
        vm.initialize(target.owner());
        Object[] args = popArguments(frame, target);
        pushResult(frame, target, invoke(target, null, args));
    }
}
