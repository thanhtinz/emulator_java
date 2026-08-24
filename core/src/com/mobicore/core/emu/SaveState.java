package com.mobicore.core.emu;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.midp.MidpGame;
import com.mobicore.core.rt.JavaRandom;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves a game where it stands, and puts it back.
 *
 * <p>Most J2ME games save nothing, or save only a high score. Quitting one
 * halfway through a level meant starting the level again, and on a phone —
 * where a call, a message or a flat battery ends the game for you — that is
 * the difference between a game being playable and being an errand.</p>
 *
 * <p>What is written is the emulated heap: every object reachable from the
 * classes' static fields, the MIDlet and the screen it is showing. Objects are
 * plain — a type, a row of integers and a row of references — so the walk is
 * simple. What is not plain is host state: a {@code String}'s characters, an
 * image's pixels, a random generator's seed. Each of those is written by hand
 * below, and anything not on that list stops the save with a message naming
 * it. A save state that quietly dropped a game's open file and restored a
 * broken game would be worse than no save state at all.</p>
 */
public final class SaveState {

    /** File magic and version, so a stale save is refused rather than misread. */
    private static final int MAGIC = 0x4D435353;
    private static final int VERSION = 1;

    /** Object kinds in the stream. */
    private static final int KIND_OBJECT = 1;
    private static final int KIND_ARRAY = 2;

    /** Host payload kinds. */
    private static final int HOST_NONE = 0;
    private static final int HOST_STRING = 1;
    private static final int HOST_BUILDER = 2;
    private static final int HOST_INTEGER = 3;
    private static final int HOST_LONG = 4;
    private static final int HOST_FLOAT = 5;
    private static final int HOST_DOUBLE = 6;
    private static final int HOST_BOOLEAN = 7;
    private static final int HOST_CHARACTER = 8;
    private static final int HOST_IMAGE = 9;
    private static final int HOST_RANDOM = 10;
    private static final int HOST_LIST = 11;
    /** Sprite, tiled layer and layer manager state; see {@link MidpGame}. */
    private static final int HOST_GAME = 12;

    /** Raised when the running game holds something that cannot be saved. */
    public static final class NotSavable extends Exception {

        public NotSavable(String message) {
            super(message);
        }
    }

    private SaveState() {
    }

    // ------------------------------------------------------------- capture

    public static byte[] capture(EmulatorSession session) throws NotSavable {
        if (session.state() != EmulatorSession.STATE_ACTIVE) {
            throw new NotSavable("Chỉ lưu được khi game đang chạy");
        }
        Writer writer = new Writer(session);
        try {
            return writer.write();
        } catch (IOException e) {
            throw new NotSavable(e.getMessage());
        }
    }

    private static final class Writer {

        private final EmulatorSession session;
        private final Vm vm;
        private final Map<Object, Integer> ids = new IdentityHashMap<Object, Integer>();
        private final List<Object> order = new ArrayList<Object>();

        Writer(EmulatorSession session) {
            this.session = session;
            this.vm = session.vm();
        }

        byte[] write() throws IOException, NotSavable {
            MidpContext context = session.context();

            // Roots first, so their ids are stable and the walk that follows
            // reaches everything they hold.
            List<VmClass> classes = new ArrayList<VmClass>(vm.loadedClasses());
            for (int i = 0; i < classes.size(); i++) {
                Object[] statics = classes.get(i).staticRefs();
                for (int slot = 0; slot < statics.length; slot++) {
                    idOf(statics[slot]);
                }
            }
            int midletId = idOf(context.midlet());
            int currentId = idOf(context.current());

            // Command registrations live in the context, not on the screen:
            // without them a restored game has blank softkeys and a canvas of
            // the wrong height, which is a different game to look at.
            List<VmObject> owners = context.commandOwners();
            for (int i = 0; i < owners.size(); i++) {
                idOf(owners.get(i));
                List<VmObject> registered = context.commandsOf(owners.get(i));
                for (int c = 0; c < registered.size(); c++) {
                    idOf(registered.get(c));
                }
            }

            // The walk itself: every object found adds the objects it holds.
            for (int i = 0; i < order.size(); i++) {
                Object item = order.get(i);
                if (item instanceof VmArray) {
                    VmArray array = (VmArray) item;
                    if (array.componentKind() == 'L' || array.componentKind() == '[') {
                        Object[] elements = array.objects();
                        for (int e = 0; e < elements.length; e++) {
                            idOf(elements[e]);
                        }
                    }
                } else if (item instanceof VmObject) {
                    VmObject object = (VmObject) item;
                    int slots = object.type() == null ? 0 : object.type().instanceSlots();
                    for (int slot = 0; slot < slots; slot++) {
                        idOf(object.getRef(slot));
                    }
                    walkHost(object);
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(session.info().suiteId());
            out.writeUTF(session.midletClass() == null ? "" : session.midletClass());
            out.writeInt(session.screen().width());
            out.writeInt(session.screen().height());

            out.writeInt(order.size());
            for (int i = 0; i < order.size(); i++) {
                writeObject(out, order.get(i));
            }

            out.writeInt(classes.size());
            for (int i = 0; i < classes.size(); i++) {
                VmClass type = classes.get(i);
                out.writeUTF(type.name());
                int[] ints = type.staticInts();
                Object[] refs = type.staticRefs();
                out.writeInt(ints.length);
                for (int slot = 0; slot < ints.length; slot++) {
                    out.writeInt(ints[slot]);
                }
                out.writeInt(refs.length);
                for (int slot = 0; slot < refs.length; slot++) {
                    out.writeInt(idOrNull(refs[slot]));
                }
            }

            out.writeInt(owners.size());
            for (int i = 0; i < owners.size(); i++) {
                VmObject owner = owners.get(i);
                out.writeInt(idOrNull(owner));
                List<VmObject> registered = context.commandsOf(owner);
                out.writeInt(registered.size());
                for (int c = 0; c < registered.size(); c++) {
                    out.writeInt(idOrNull(registered.get(c)));
                }
            }

            out.writeInt(midletId);
            out.writeInt(currentId);
            out.writeInt(context.keyStates());
            out.writeBoolean(context.isFullScreen());
            out.flush();
            return bytes.toByteArray();
        }

        /** References a host payload holds, which the walk must follow too. */
        private void walkHost(VmObject object) throws NotSavable {
            Object host = object.host;
            MidpGame.HostState game = MidpGame.capture(host);
            if (game != null) {
                for (int i = 0; i < game.refs.length; i++) {
                    if (game.refs[i] instanceof VmObject) {
                        idOf(game.refs[i]);
                    }
                }
                return;
            }
            if (host instanceof List) {
                List<?> list = (List<?>) host;
                for (int i = 0; i < list.size(); i++) {
                    Object element = list.get(i);
                    if (element instanceof VmObject) {
                        idOf(element);
                    }
                }
            }
        }

        private int idOf(Object value) {
            if (value == null) {
                return 0;
            }
            Integer existing = ids.get(value);
            if (existing != null) {
                return existing.intValue();
            }
            int id = order.size() + 1;
            ids.put(value, Integer.valueOf(id));
            order.add(value);
            return id;
        }

        private int idOrNull(Object value) {
            if (value == null) {
                return 0;
            }
            Integer existing = ids.get(value);
            return existing == null ? 0 : existing.intValue();
        }

        private void writeObject(DataOutputStream out, Object item)
                throws IOException, NotSavable {
            if (item instanceof VmArray) {
                VmArray array = (VmArray) item;
                out.writeByte(KIND_ARRAY);
                out.writeUTF(array.componentType());
                out.writeInt(array.length());
                writeArrayData(out, array);
                return;
            }
            VmObject object = (VmObject) item;
            out.writeByte(KIND_OBJECT);
            out.writeUTF(object.type().name());
            int slots = object.type().instanceSlots();
            out.writeInt(slots);
            for (int slot = 0; slot < slots; slot++) {
                out.writeInt(object.getInt(slot));
            }
            for (int slot = 0; slot < slots; slot++) {
                out.writeInt(idOrNull(object.getRef(slot)));
            }
            writeHost(out, object);
        }

        private void writeArrayData(DataOutputStream out, VmArray array) throws IOException {
            char kind = array.componentKind();
            int length = array.length();
            switch (kind) {
                case 'L': case '[': {
                    Object[] elements = array.objects();
                    for (int i = 0; i < length; i++) {
                        out.writeInt(idOrNull(elements[i]));
                    }
                    break;
                }
                case 'J': {
                    long[] values = array.longs();
                    for (int i = 0; i < length; i++) {
                        out.writeLong(values[i]);
                    }
                    break;
                }
                case 'D': {
                    double[] values = array.doubles();
                    for (int i = 0; i < length; i++) {
                        out.writeDouble(values[i]);
                    }
                    break;
                }
                case 'F': {
                    float[] values = array.floats();
                    for (int i = 0; i < length; i++) {
                        out.writeFloat(values[i]);
                    }
                    break;
                }
                case 'B': case 'Z': {
                    byte[] values = array.bytes();
                    out.write(values, 0, length);
                    break;
                }
                case 'C': {
                    char[] values = array.chars();
                    for (int i = 0; i < length; i++) {
                        out.writeChar(values[i]);
                    }
                    break;
                }
                case 'S': {
                    short[] values = array.shorts();
                    for (int i = 0; i < length; i++) {
                        out.writeShort(values[i]);
                    }
                    break;
                }
                default: {
                    int[] values = array.ints();
                    for (int i = 0; i < length; i++) {
                        out.writeInt(values[i]);
                    }
                    break;
                }
            }
        }

        /**
         * Host payloads, one kind at a time.
         *
         * <p>Anything not listed stops the save: an open connection, a record
         * store or a sound being played cannot be written down and put back,
         * and a save that pretended otherwise would restore a game that is
         * subtly broken instead of refusing honestly.</p>
         */
        private void writeHost(DataOutputStream out, VmObject object)
                throws IOException, NotSavable {
            Object host = object.host;
            if (host == null) {
                out.writeByte(HOST_NONE);
                return;
            }
            if (host instanceof String) {
                out.writeByte(HOST_STRING);
                out.writeUTF((String) host);
            } else if (host instanceof StringBuilder) {
                out.writeByte(HOST_BUILDER);
                out.writeUTF(host.toString());
            } else if (host instanceof Integer) {
                out.writeByte(HOST_INTEGER);
                out.writeInt(((Integer) host).intValue());
            } else if (host instanceof Long) {
                out.writeByte(HOST_LONG);
                out.writeLong(((Long) host).longValue());
            } else if (host instanceof Float) {
                out.writeByte(HOST_FLOAT);
                out.writeFloat(((Float) host).floatValue());
            } else if (host instanceof Double) {
                out.writeByte(HOST_DOUBLE);
                out.writeDouble(((Double) host).doubleValue());
            } else if (host instanceof Boolean) {
                out.writeByte(HOST_BOOLEAN);
                out.writeBoolean(((Boolean) host).booleanValue());
            } else if (host instanceof Character) {
                out.writeByte(HOST_CHARACTER);
                out.writeChar(((Character) host).charValue());
            } else if (host instanceof Framebuffer) {
                Framebuffer image = (Framebuffer) host;
                out.writeByte(HOST_IMAGE);
                out.writeInt(image.width());
                out.writeInt(image.height());
                int[] pixels = image.pixels();
                for (int i = 0; i < pixels.length; i++) {
                    out.writeInt(pixels[i]);
                }
            } else if (host instanceof JavaRandom) {
                out.writeByte(HOST_RANDOM);
                out.writeLong(((JavaRandom) host).rawSeed());
            } else if (host instanceof List) {
                List<?> list = (List<?>) host;
                out.writeByte(HOST_LIST);
                out.writeInt(list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object element = list.get(i);
                    if (element != null && !(element instanceof VmObject)) {
                        throw new NotSavable("Không lưu được nội dung của "
                                + object.type().binaryName());
                    }
                    out.writeInt(idOrNull(element));
                }
            } else {
                MidpGame.HostState game = MidpGame.capture(host);
                if (game == null) {
                    throw new NotSavable("Game đang mở " + describe(object)
                            + " — hãy lưu lúc đang chơi bình thường");
                }
                out.writeByte(HOST_GAME);
                out.writeUTF(game.kind);
                out.writeInt(game.numbers.length);
                for (int i = 0; i < game.numbers.length; i++) {
                    out.writeInt(game.numbers[i]);
                }
                out.writeInt(game.refs.length);
                for (int i = 0; i < game.refs.length; i++) {
                    Object ref = game.refs[i];
                    if (ref instanceof Framebuffer) {
                        // A sprite's sheet is pixels, not an object in the
                        // heap: it is written here rather than referenced.
                        Framebuffer image = (Framebuffer) ref;
                        out.writeByte(HOST_IMAGE);
                        out.writeInt(image.width());
                        out.writeInt(image.height());
                        int[] pixels = image.pixels();
                        for (int p = 0; p < pixels.length; p++) {
                            out.writeInt(pixels[p]);
                        }
                    } else {
                        out.writeByte(HOST_NONE);
                        out.writeInt(idOrNull(ref));
                    }
                }
            }
        }

        private String describe(VmObject object) {
            String name = object.type().binaryName();
            if (name.indexOf("Connection") >= 0 || name.indexOf("Connector") >= 0) {
                return "một kết nối mạng";
            }
            if (name.indexOf("RecordStore") >= 0) {
                return "một kho dữ liệu";
            }
            if (name.indexOf("Player") >= 0) {
                return "một bản nhạc";
            }
            if (name.indexOf("Stream") >= 0) {
                return "một tệp";
            }
            return name;
        }
    }

    // ------------------------------------------------------------- restore

    /**
     * Puts a captured heap back into a freshly started session.
     *
     * <p>The session must be running the same suite: it is started normally,
     * which builds a working game, and then its heap is replaced. Starting
     * first rather than restoring into an empty machine means every class is
     * loaded and every native class is installed before anything refers to
     * them.</p>
     */
    public static void restore(EmulatorSession session, byte[] blob) throws NotSavable {
        try {
            new Reader(session, blob).read();
        } catch (IOException e) {
            throw new NotSavable("Bản lưu bị hỏng: " + e.getMessage());
        }
    }

    /** The suite a save belongs to, without restoring it. */
    public static String suiteIdOf(byte[] blob) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob));
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return null;
            }
            return in.readUTF();
        } catch (IOException e) {
            return null;
        }
    }

    /** Game state waiting for the objects it refers to to exist. */
    private static final class PendingGame {

        final String kind;
        final int[] numbers;
        final Object[] refs;
        /** Ids for the reference slots that are objects, or null. */
        final int[] ids;

        PendingGame(String kind, int[] numbers, Object[] refs, int[] ids) {
            this.kind = kind;
            this.numbers = numbers;
            this.refs = refs;
            this.ids = ids;
        }
    }

    private static final class Reader {

        private final EmulatorSession session;
        private final DataInputStream in;
        private Object[] objects;
        private final Map<Integer, PendingGame> gameStates =
                new java.util.HashMap<Integer, PendingGame>();
        /** Id of the object being read, so its host state knows its owner. */
        private int currentId;

        Reader(EmulatorSession session, byte[] blob) {
            this.session = session;
            this.in = new DataInputStream(new ByteArrayInputStream(blob));
        }

        void read() throws IOException, NotSavable {
            Vm vm = session.vm();
            MidpContext context = session.context();

            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new NotSavable("Bản lưu này không phải của phiên bản hiện tại");
            }
            String suiteId = in.readUTF();
            if (!suiteId.equals(session.info().suiteId())) {
                throw new NotSavable("Bản lưu thuộc về một trò chơi khác");
            }
            in.readUTF();
            int width = in.readInt();
            int height = in.readInt();
            if (width != session.screen().width() || height != session.screen().height()) {
                throw new NotSavable("Bản lưu dùng màn hình " + width + "x" + height);
            }

            int count = in.readInt();
            objects = new Object[count + 1];
            int[][] refIds = new int[count + 1][];
            int[][] arrayRefIds = new int[count + 1][];
            int[] hostListIds = null;
            Map<Integer, int[]> hostLists = new java.util.HashMap<Integer, int[]>();

            // Two passes: every object is created before any reference is
            // filled in, because the heap is a graph and holds cycles.
            for (int id = 1; id <= count; id++) {
                int kind = in.readByte();
                if (kind == KIND_ARRAY) {
                    String component = in.readUTF();
                    int length = in.readInt();
                    VmArray array = vm.newArray(component, length);
                    objects[id] = array;
                    arrayRefIds[id] = readArrayData(array, length);
                } else {
                    VmClass type = vm.loadClass(in.readUTF());
                    VmObject object = vm.newInstance(type);
                    objects[id] = object;
                    currentId = id;
                    int slots = in.readInt();
                    for (int slot = 0; slot < slots; slot++) {
                        int value = in.readInt();
                        if (slot < type.instanceSlots()) {
                            object.setInt(slot, value);
                        }
                    }
                    int[] refs = new int[slots];
                    for (int slot = 0; slot < slots; slot++) {
                        refs[slot] = in.readInt();
                    }
                    refIds[id] = refs;
                    hostListIds = readHost(object);
                    if (hostListIds != null) {
                        hostLists.put(Integer.valueOf(id), hostListIds);
                    }
                }
            }

            for (int id = 1; id <= count; id++) {
                if (objects[id] instanceof VmObject && refIds[id] != null) {
                    VmObject object = (VmObject) objects[id];
                    int slots = Math.min(refIds[id].length, object.type().instanceSlots());
                    for (int slot = 0; slot < slots; slot++) {
                        object.setRef(slot, resolve(refIds[id][slot]));
                    }
                } else if (objects[id] instanceof VmArray && arrayRefIds[id] != null) {
                    Object[] elements = ((VmArray) objects[id]).objects();
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = resolve(arrayRefIds[id][i]);
                    }
                }
            }

            for (Map.Entry<Integer, PendingGame> entry : gameStates.entrySet()) {
                VmObject owner = (VmObject) objects[entry.getKey().intValue()];
                PendingGame pending = entry.getValue();
                if (pending.ids != null) {
                    for (int i = 0; i < pending.refs.length; i++) {
                        if (pending.refs[i] == null) {
                            pending.refs[i] = resolve(pending.ids[i]);
                        }
                    }
                }
                owner.host = MidpGame.restore(
                        new MidpGame.HostState(pending.kind, pending.numbers, pending.refs));
            }

            for (Map.Entry<Integer, int[]> entry : hostLists.entrySet()) {
                VmObject owner = (VmObject) objects[entry.getKey().intValue()];
                List<Object> list = new ArrayList<Object>();
                int[] elementIds = entry.getValue();
                for (int i = 0; i < elementIds.length; i++) {
                    list.add(resolve(elementIds[i]));
                }
                owner.host = list;
            }

            int classCount = in.readInt();
            for (int i = 0; i < classCount; i++) {
                VmClass type = vm.loadClass(in.readUTF());
                int intCount = in.readInt();
                int[] ints = type.staticInts();
                for (int slot = 0; slot < intCount; slot++) {
                    int value = in.readInt();
                    if (slot < ints.length) {
                        ints[slot] = value;
                    }
                }
                int refCount = in.readInt();
                Object[] refs = type.staticRefs();
                for (int slot = 0; slot < refCount; slot++) {
                    Object value = resolve(in.readInt());
                    if (slot < refs.length) {
                        refs[slot] = value;
                    }
                }
            }

            int ownerCount = in.readInt();
            for (int i = 0; i < ownerCount; i++) {
                VmObject owner = (VmObject) resolve(in.readInt());
                int commandCount = in.readInt();
                for (int c = 0; c < commandCount; c++) {
                    VmObject command = (VmObject) resolve(in.readInt());
                    if (owner != null && command != null) {
                        context.addCommand(owner, command);
                    }
                }
            }

            VmObject midlet = (VmObject) resolve(in.readInt());
            VmObject current = (VmObject) resolve(in.readInt());
            int keyStates = in.readInt();
            boolean fullScreen = in.readBoolean();

            if (midlet != null) {
                context.setMidlet(midlet);
            }
            context.setFullScreen(fullScreen);
            context.restoreKeyStates(keyStates);
            if (current != null) {
                context.setCurrent(current);
            }
            context.requestRepaint();
        }

        private Object resolve(int id) {
            return id <= 0 || id >= objects.length ? null : objects[id];
        }

        private int[] readArrayData(VmArray array, int length) throws IOException {
            char kind = array.componentKind();
            switch (kind) {
                case 'L': case '[': {
                    int[] ids = new int[length];
                    for (int i = 0; i < length; i++) {
                        ids[i] = in.readInt();
                    }
                    return ids;
                }
                case 'J': {
                    long[] values = array.longs();
                    for (int i = 0; i < length; i++) {
                        values[i] = in.readLong();
                    }
                    return null;
                }
                case 'D': {
                    double[] values = array.doubles();
                    for (int i = 0; i < length; i++) {
                        values[i] = in.readDouble();
                    }
                    return null;
                }
                case 'F': {
                    float[] values = array.floats();
                    for (int i = 0; i < length; i++) {
                        values[i] = in.readFloat();
                    }
                    return null;
                }
                case 'B': case 'Z': {
                    in.readFully(array.bytes(), 0, length);
                    return null;
                }
                case 'C': {
                    char[] values = array.chars();
                    for (int i = 0; i < length; i++) {
                        values[i] = in.readChar();
                    }
                    return null;
                }
                case 'S': {
                    short[] values = array.shorts();
                    for (int i = 0; i < length; i++) {
                        values[i] = in.readShort();
                    }
                    return null;
                }
                default: {
                    int[] values = array.ints();
                    for (int i = 0; i < length; i++) {
                        values[i] = in.readInt();
                    }
                    return null;
                }
            }
        }

        /** @return element ids when the payload is a list, else null */
        private int[] readHost(VmObject object) throws IOException {
            int kind = in.readByte();
            switch (kind) {
                case HOST_STRING: object.host = in.readUTF(); break;
                case HOST_BUILDER: object.host = new StringBuilder(in.readUTF()); break;
                case HOST_INTEGER: object.host = Integer.valueOf(in.readInt()); break;
                case HOST_LONG: object.host = Long.valueOf(in.readLong()); break;
                case HOST_FLOAT: object.host = Float.valueOf(in.readFloat()); break;
                case HOST_DOUBLE: object.host = Double.valueOf(in.readDouble()); break;
                case HOST_BOOLEAN: object.host = Boolean.valueOf(in.readBoolean()); break;
                case HOST_CHARACTER: object.host = Character.valueOf(in.readChar()); break;
                case HOST_IMAGE: {
                    int width = in.readInt();
                    int height = in.readInt();
                    Framebuffer image = new Framebuffer(width, height);
                    int[] pixels = image.pixels();
                    for (int i = 0; i < pixels.length; i++) {
                        pixels[i] = in.readInt();
                    }
                    object.host = image;
                    break;
                }
                case HOST_RANDOM: {
                    JavaRandom random = new JavaRandom(0);
                    random.restoreRawSeed(in.readLong());
                    object.host = random;
                    break;
                }
                case HOST_LIST: {
                    int size = in.readInt();
                    int[] ids = new int[size];
                    for (int i = 0; i < size; i++) {
                        ids[i] = in.readInt();
                    }
                    return ids;
                }
                case HOST_GAME: {
                    String gameKind = in.readUTF();
                    int[] numbers = new int[in.readInt()];
                    for (int i = 0; i < numbers.length; i++) {
                        numbers[i] = in.readInt();
                    }
                    int refCount = in.readInt();
                    Object[] refs = new Object[refCount];
                    int[] pending = null;
                    for (int i = 0; i < refCount; i++) {
                        if (in.readByte() == HOST_IMAGE) {
                            int width = in.readInt();
                            int height = in.readInt();
                            Framebuffer image = new Framebuffer(width, height);
                            int[] pixels = image.pixels();
                            for (int p = 0; p < pixels.length; p++) {
                                pixels[p] = in.readInt();
                            }
                            refs[i] = image;
                        } else {
                            if (pending == null) {
                                pending = new int[refCount];
                            }
                            pending[i] = in.readInt();
                        }
                    }
                    // Layers are objects, and the objects they point at may not
                    // exist yet, so the state is finished in the second pass.
                    gameStates.put(Integer.valueOf(currentId),
                            new PendingGame(gameKind, numbers, refs, pending));
                    return null;
                }
                default:
                    object.host = null;
                    break;
            }
            return null;
        }
    }
}
