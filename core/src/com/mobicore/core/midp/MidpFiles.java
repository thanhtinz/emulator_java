package com.mobicore.core.midp;

import com.mobicore.core.rt.IoClasses;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code javax.microedition.io.file}: JSR-75's FileConnection, in a sandbox.
 *
 * <p>Record stores are the only storage MIDP itself has, and they are meant
 * for a few hundred bytes at a time — a high score, a settings blob. A game
 * with a level editor, a downloaded track, or a saved photo used files
 * instead, and on a handset that meant JSR-75. Without it those games throw
 * on the first save and there is nothing the player can do about it.</p>
 *
 * <p><strong>Everything is confined to the game's own folder.</strong> A J2ME
 * game asked the handset for {@code file:///c:/} or {@code file:///root1/} and
 * got the whole memory card; on a phone today that would be the owner's
 * photographs. So every path is resolved against one directory belonging to
 * this game and this game alone, whatever the game asked for, and a path that
 * climbs out with {@code ..} is refused rather than clipped — a game that
 * meant to escape should fail, not quietly write somewhere else.</p>
 *
 * <p>What is deliberately absent: {@code setHidden}, {@code setReadable} and
 * the rest of the permission calls. There are no permissions inside a folder
 * one game owns, and a call that pretends to set one would be a lie the game
 * could later read back.</p>
 */
public final class MidpFiles {

    public static final String FILE_CONNECTION =
            "javax/microedition/io/file/FileConnection";
    public static final String FILE_SYSTEM_REGISTRY =
            "javax/microedition/io/file/FileSystemRegistry";
    public static final String CONNECTION_CLOSED =
            "javax/microedition/io/ConnectionClosedException";

    /**
     * The one root a game is shown.
     *
     * <p>Handsets named theirs after the hardware — {@code c:/}, {@code
     * root1/}, {@code Memory card/} — and games hard-coded whichever one their
     * target handset had. Since every path lands in the same sandbox anyway,
     * what a game asks for does not matter; what it is <em>told</em> should be
     * something a person would recognise in a file listing.</p>
     */
    public static final String ROOT = "MobiCore/";

    private MidpFiles() {
    }

    /** What one open connection is looking at. */
    static final class FileState {

        /** Path inside the sandbox, without a leading slash; "" is the root. */
        String path;
        boolean closed;
        /** False for a read-only connection, which is what mode 1 asks for. */
        final boolean writable;

        FileState(String path, boolean writable) {
            this.path = path;
            this.writable = writable;
        }
    }

    public static void install(final Vm vm, final Vfs vfs, final StorageLayout layout,
                               final String suiteId) {
        final String base = StorageLayout.join(layout.gameDir(suiteId), "files");

        vm.builtin(FILE_SYSTEM_REGISTRY, Vm.OBJECT)
                .staticMethod("listRoots", "()Ljava/util/Enumeration;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<Object> roots = new ArrayList<Object>();
                        roots.add(vm.newString(ROOT));
                        return com.mobicore.core.rt.UtilClasses.enumerationOf(vm, roots);
                    }
                })
                // A game that asks to be told about cards being inserted is
                // asking about hardware this does not have; accepting the
                // listener and never calling it is what a phone with no slot
                // does too.
                .staticMethod("addFileSystemListener",
                        "(Ljavax/microedition/io/file/FileSystemListener;)V", ignored())
                .staticMethod("removeFileSystemListener",
                        "(Ljavax/microedition/io/file/FileSystemListener;)V", ignored())
                .define();

        vm.builtin(FILE_CONNECTION, Vm.OBJECT,
                        new String[]{MidpNet.CONNECTION}, false)
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        state(vm, self).closed = true;
                        return null;
                    }
                })
                .method("isOpen", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(!state(vm, self).closed);
                    }
                })
                .method("exists", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(vfs.exists(full(vm, base, self)));
                    }
                })
                .method("isDirectory", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(vfs.isDirectory(full(vm, base, self)));
                    }
                })
                .method("canRead", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(vfs.exists(full(vm, base, self)));
                    }
                })
                .method("canWrite", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(state(vm, self).writable);
                    }
                })
                .method("isHidden", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(false);
                    }
                })
                .method("fileSize", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = full(vm, base, self);
                        return Long.valueOf(vfs.exists(path) ? vfs.size(path) : -1L);
                    }
                })
                .method("directorySize", "(Z)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(directorySize(vfs, full(vm, base, self)));
                    }
                })
                .method("lastModified", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(vfs.modifiedAt(full(vm, base, self)));
                    }
                })
                .method("create", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = requireWritable(vm, self, base);
                        if (vfs.exists(path)) {
                            throw vm.raise("java/io/IOException", "Tệp đã có rồi");
                        }
                        write(vm, vfs, path, new byte[0]);
                        return null;
                    }
                })
                .method("mkdir", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = requireWritable(vm, self, base);
                        try {
                            vfs.mkdirs(path);
                        } catch (IOException e) {
                            throw vm.raise("java/io/IOException", String.valueOf(e.getMessage()));
                        }
                        return null;
                    }
                })
                .method("delete", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = requireWritable(vm, self, base);
                        if (!vfs.delete(path)) {
                            throw vm.raise("java/io/IOException", "Không có gì để xoá");
                        }
                        return null;
                    }
                })
                .method("truncate", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = requireWritable(vm, self, base);
                        long keep = Rt.l(args, 0);
                        byte[] data = read(vm, vfs, path);
                        if (keep < 0 || keep >= data.length) {
                            return null;
                        }
                        byte[] shorter = new byte[(int) keep];
                        System.arraycopy(data, 0, shorter, 0, shorter.length);
                        write(vm, vfs, path, shorter);
                        return null;
                    }
                })
                .method("rename", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        FileState file = state(vm, self);
                        String from = requireWritable(vm, self, base);
                        String name = Rt.s(vm, args, 0);
                        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
                            // Rename takes a name, not a path: JSR-75 says so,
                            // and a rename that could move a file across
                            // directories is a rename that could move it out.
                            throw vm.raise("java/lang/IllegalArgumentException",
                                    "Tên mới không được chứa dấu /");
                        }
                        String parent = parentOf(file.path);
                        String target = resolve(vm, base, StorageLayout.join(parent, name));
                        write(vm, vfs, target, read(vm, vfs, from));
                        vfs.delete(from);
                        file.path = trim(StorageLayout.join(parent, name));
                        return null;
                    }
                })
                .method("list", "()Ljava/util/Enumeration;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return listing(vm, vfs, base, self, "*", false);
                    }
                })
                .method("list", "(Ljava/lang/String;Z)Ljava/util/Enumeration;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return listing(vm, vfs, base, self, Rt.s(vm, args, 0), false);
                            }
                        })
                .method("getName", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = state(vm, self).path;
                        int slash = path.lastIndexOf('/');
                        String name = slash < 0 ? path : path.substring(slash + 1);
                        return vm.newString(vfs.isDirectory(full(vm, base, self))
                                ? name + "/" : name);
                    }
                })
                .method("getPath", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = state(vm, self).path;
                        int slash = path.lastIndexOf('/');
                        return vm.newString(ROOT + (slash < 0 ? "" : path.substring(0, slash + 1)));
                    }
                })
                .method("getURL", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString("file:///" + ROOT + state(vm, self).path);
                    }
                })
                .method("setFileConnection", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Moves this connection to a sibling or to "..", which
                        // is how a game walks a directory tree without opening
                        // a connection per step.
                        FileState file = state(vm, self);
                        String name = Rt.s(vm, args, 0);
                        String target = "..".equals(name)
                                ? parentOf(file.path)
                                : StorageLayout.join(parentDirOf(vfs, base, file), name);
                        // Resolved so it cannot climb past the sandbox, the
                        // same as any other path a game hands over.
                        resolve(vm, base, target);
                        file.path = trim(target);
                        return null;
                    }
                })
                .method("openInputStream", "()Ljava/io/InputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return IoClasses.newByteArrayInputStream(vm,
                                read(vm, vfs, full(vm, base, self)));
                    }
                })
                .method("openDataInputStream", "()Ljava/io/DataInputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject data = vm.newInstance("java/io/DataInputStream");
                        data.host = new java.io.DataInputStream(new java.io.ByteArrayInputStream(
                                read(vm, vfs, full(vm, base, self))));
                        return data;
                    }
                })
                .method("openOutputStream", "()Ljava/io/OutputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return outputStream(vm, vfs, requireWritable(vm, self, base), 0);
                    }
                })
                .method("openOutputStream", "(J)Ljava/io/OutputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return outputStream(vm, vfs, requireWritable(vm, self, base),
                                Rt.l(args, 0));
                    }
                })
                .method("openDataOutputStream", "()Ljava/io/DataOutputStream;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject stream = outputStream(vm, vfs,
                                        requireWritable(vm, self, base), 0);
                                VmObject data = vm.newInstance("java/io/DataOutputStream");
                                data.host = new java.io.DataOutputStream(
                                        (java.io.OutputStream) stream.host);
                                return data;
                            }
                        })
                // How much room is left. A phone has far more than a handset
                // did and a game that checks is usually deciding whether to
                // save at all, so the honest answer is a large one.
                .method("availableSize", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(64L * 1024 * 1024);
                    }
                })
                .method("totalSize", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(64L * 1024 * 1024);
                    }
                })
                .method("usedSize", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(directorySize(vfs, base));
                    }
                })
                .define();
    }

    /**
     * Opens a {@code file://} URL, or explains why it cannot be opened.
     *
     * <p>Called by {@link MidpNet}'s Connector, which is where a game asks.</p>
     */
    public static VmObject open(Vm vm, String url, int mode) {
        String path = url.substring("file://".length());
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        // Whatever root the game named — c:/, root1/, MobiCore/ — it lands in
        // the same sandbox; the first segment is what the handset called its
        // memory, not part of the file's name.
        int slash = path.indexOf('/');
        if (slash >= 0 && isRootName(path.substring(0, slash))) {
            path = path.substring(slash + 1);
        }
        VmObject connection = vm.newInstance(FILE_CONNECTION);
        connection.host = new FileState(trim(path), mode != 1);
        return connection;
    }

    /**
     * Which file inside the sandbox a connection is looking at.
     *
     * <p>Sandbox-relative, so it says nothing about where the storage is; what
     * it is for is checking that a URL a game handed over landed where it was
     * meant to.</p>
     */
    public static String pathOf(Vm vm, VmObject connection) {
        return state(vm, connection).path;
    }

    /**
     * True for the first segment of a path when it names a handset's storage.
     *
     * <p>Only these: a game that opens {@code file:///levels/1.dat} means a
     * file called {@code levels/1.dat}, and eating its first directory would
     * put the file somewhere the game does not expect to find it again.</p>
     */
    private static boolean isRootName(String segment) {
        String name = segment.toLowerCase();
        return "c:".equals(name) || "d:".equals(name) || "e:".equals(name)
                || name.startsWith("root")
                || "mobicore".equals(name)
                || "memory card".equals(name) || "memorycard".equals(name)
                || "phone memory".equals(name);
    }

    // ------------------------------------------------------------------ paths

    /**
     * A sandbox-relative path turned into a real one, or an exception.
     *
     * <p>The check is on the resolved segments rather than on the text: a
     * game that writes {@code a/../../b} is refused by what the path
     * <em>means</em>, not by what it looks like.</p>
     */
    public static String resolve(Vm vm, String base, String path) {
        String resolved = resolveOrNull(base, path);
        if (resolved == null) {
            // Out of the sandbox: refused rather than clipped, because a game
            // that meant to climb out should fail loudly rather than quietly
            // write somewhere else.
            throw vm.raise("java/io/IOException", "Đường dẫn ra ngoài thư mục của game");
        }
        return resolved;
    }

    /**
     * The same resolution without a machine to raise into.
     *
     * <p>Used by the bridge, which takes a file name from outside the
     * emulator and must confine it exactly as a game's own path is confined.
     * A name that climbs out comes back null rather than throwing, because
     * out there it is a message to show, not an exception to a game.</p>
     *
     * @return the real path, or null when the path leaves the sandbox
     */
    public static String resolveOrNull(String base, String path) {
        List<String> parts = new ArrayList<String>();
        String[] segments = trim(path).split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.length() == 0 || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.remove(parts.size() - 1);
                continue;
            }
            parts.add(segment);
        }
        String out = base;
        for (int i = 0; i < parts.size(); i++) {
            out = StorageLayout.join(out, parts.get(i));
        }
        return out;
    }

    /** The path with no leading or trailing slash. */
    static String trim(String path) {
        String value = path == null ? "" : path.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String parentOf(String path) {
        int slash = trim(path).lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /** The directory a connection is "in", which a directory is itself. */
    private static String parentDirOf(Vfs vfs, String base, FileState file) {
        String full = StorageLayout.join(base, file.path);
        return vfs.isDirectory(full) ? file.path : parentOf(file.path);
    }

    private static FileState state(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof FileState)) {
            throw vm.raise("java/io/IOException", "Kết nối tệp không hợp lệ");
        }
        FileState file = (FileState) self.host;
        if (file.closed) {
            throw vm.raise(CONNECTION_CLOSED, "Kết nối tệp đã đóng");
        }
        return file;
    }

    private static String full(Vm vm, String base, VmObject self) {
        return resolve(vm, base, state(vm, self).path);
    }

    private static String requireWritable(Vm vm, VmObject self, String base) {
        FileState file = state(vm, self);
        if (!file.writable) {
            throw vm.raise("java/io/IOException", "Kết nối này chỉ để đọc");
        }
        return resolve(vm, base, file.path);
    }

    // ------------------------------------------------------------------- io

    private static byte[] read(Vm vm, Vfs vfs, String path) {
        try {
            if (!vfs.exists(path)) {
                throw vm.raise("java/io/IOException", "Không có tệp này");
            }
            return vfs.read(path);
        } catch (IOException e) {
            throw vm.raise("java/io/IOException", String.valueOf(e.getMessage()));
        }
    }

    private static void write(Vm vm, Vfs vfs, String path, byte[] data) {
        try {
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                vfs.mkdirs(path.substring(0, slash));
            }
            vfs.write(path, data);
        } catch (IOException e) {
            throw vm.raise("java/io/IOException", String.valueOf(e.getMessage()));
        }
    }

    /**
     * A stream that writes into the sandbox when it is closed.
     *
     * <p>Buffered rather than written through: the emulator's storage takes
     * whole files, and a game writing a level a byte at a time would otherwise
     * rewrite the file once per byte.</p>
     *
     * @param at where in the file to start writing, which is how JSR-75
     *     appends
     */
    private static VmObject outputStream(final Vm vm, final Vfs vfs, final String path, long at) {
        final byte[] existing = vfs.exists(path) ? read(vm, vfs, path) : new byte[0];
        final int offset = at <= 0 ? 0 : (at > existing.length ? existing.length : (int) at);
        VmObject stream = vm.newInstance("java/io/OutputStream");
        stream.host = new ByteArrayOutputStream() {
            public void flush() {
                store();
            }

            public void close() {
                store();
            }

            /** What the game has written so far, over what was there. */
            private void store() {
                byte[] fresh = toByteArray();
                byte[] whole = new byte[Math.max(existing.length, offset + fresh.length)];
                System.arraycopy(existing, 0, whole, 0, existing.length);
                System.arraycopy(fresh, 0, whole, offset, fresh.length);
                MidpFiles.write(vm, vfs, path, whole);
            }
        };
        return stream;
    }

    private static VmObject listing(Vm vm, Vfs vfs, String base, VmObject self,
                                    String filter, boolean includeHidden) {
        String dir = full(vm, base, self);
        List<Object> names = new ArrayList<Object>();
        List<String> children = vfs.list(dir);
        for (int i = 0; i < children.size(); i++) {
            String name = children.get(i);
            if (!matches(name, filter)) {
                continue;
            }
            names.add(vm.newString(vfs.isDirectory(StorageLayout.join(dir, name))
                    ? name + "/" : name));
        }
        return com.mobicore.core.rt.UtilClasses.enumerationOf(vm, names);
    }

    /** JSR-75's filter: a name with at most one {@code *} standing in. */
    public static boolean matches(String name, String filter) {
        if (filter == null || filter.length() == 0 || "*".equals(filter)) {
            return true;
        }
        int star = filter.indexOf('*');
        if (star < 0) {
            return name.equals(filter);
        }
        String head = filter.substring(0, star);
        String tail = filter.substring(star + 1);
        return name.length() >= head.length() + tail.length()
                && name.startsWith(head) && name.endsWith(tail);
    }

    private static long directorySize(Vfs vfs, String path) {
        if (!vfs.isDirectory(path)) {
            return vfs.exists(path) ? vfs.size(path) : 0;
        }
        long total = 0;
        List<String> children = vfs.list(path);
        for (int i = 0; i < children.size(); i++) {
            total += directorySize(vfs, StorageLayout.join(path, children.get(i)));
        }
        return total;
    }

    private static NativeMethod ignored() {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return null;
            }
        };
    }
}
