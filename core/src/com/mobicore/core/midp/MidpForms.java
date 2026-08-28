package com.mobicore.core.midp;

import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The high level half of LCDUI: {@code Form}, {@code List}, {@code TextBox},
 * {@code Alert} and the items that go inside them.
 *
 * <p>A game that draws its own pixels only needs {@code Canvas}, and that is
 * where an emulator's effort naturally goes. But almost every commercial MIDlet
 * still reaches for these screens the moment it wants a menu, a name to be
 * typed in or a "are you sure?" — and a missing class is not a missing feature
 * here, it is a {@code NoClassDefFoundError} the instant the game is loaded.
 * So they exist, they hold their state, and {@link ScreenRenderer} paints
 * them.</p>
 *
 * <p>The screens are drawn by the emulator rather than by the game, which is
 * exactly the split MIDP defines: the specification describes what a List
 * <em>is</em>, never what it looks like, because that was the handset's to
 * decide.</p>
 */
public final class MidpForms {

    public static final String SCREEN = "javax/microedition/lcdui/Screen";
    public static final String ITEM = "javax/microedition/lcdui/Item";
    public static final String STRING_ITEM = "javax/microedition/lcdui/StringItem";
    public static final String IMAGE_ITEM = "javax/microedition/lcdui/ImageItem";
    public static final String TEXT_FIELD = "javax/microedition/lcdui/TextField";
    public static final String GAUGE = "javax/microedition/lcdui/Gauge";
    public static final String DATE_FIELD = "javax/microedition/lcdui/DateField";
    public static final String CHOICE = "javax/microedition/lcdui/Choice";
    public static final String CHOICE_GROUP = "javax/microedition/lcdui/ChoiceGroup";
    public static final String TICKER = "javax/microedition/lcdui/Ticker";
    public static final String FORM = "javax/microedition/lcdui/Form";
    public static final String LIST = "javax/microedition/lcdui/List";
    public static final String TEXT_BOX = "javax/microedition/lcdui/TextBox";
    public static final String ALERT = "javax/microedition/lcdui/Alert";
    public static final String ALERT_TYPE = "javax/microedition/lcdui/AlertType";
    public static final String ITEM_STATE_LISTENER = "javax/microedition/lcdui/ItemStateListener";

    /** Choice types, as {@code Choice} defines them. */
    public static final int EXCLUSIVE = 1;
    public static final int MULTIPLE = 2;
    public static final int IMPLICIT = 3;
    public static final int POPUP = 4;

    /** {@code TextField} constraints. */
    public static final int ANY = 0;
    public static final int EMAILADDR = 1;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;
    public static final int CONSTRAINT_MASK = 0xFFFF;
    public static final int PASSWORD = 0x10000;
    public static final int UNEDITABLE = 0x20000;

    /** {@code Alert.FOREVER}: an alert that waits for the player. */
    public static final int FOREVER = -2;

    /** {@code Gauge.INDEFINITE}: a bar with no known length. */
    public static final int INDEFINITE = -1;

    private MidpForms() {
    }

    /**
     * The strings and images behind a {@code List} or a {@code ChoiceGroup}.
     *
     * <p>Kept as host objects rather than emulated arrays because a MIDlet
     * appends to a list one element at a time and the specification promises
     * no capacity limit.</p>
     */
    public static final class Choices {

        private final List<String> strings = new ArrayList<String>();
        private final List<VmObject> images = new ArrayList<VmObject>();
        private final List<Boolean> flags = new ArrayList<Boolean>();

        public int size() {
            return strings.size();
        }

        public String string(int index) {
            return strings.get(index);
        }

        public VmObject image(int index) {
            return images.get(index);
        }

        public boolean selected(int index) {
            return index >= 0 && index < flags.size() && flags.get(index).booleanValue();
        }

        public void setSelected(int index, boolean value) {
            if (index >= 0 && index < flags.size()) {
                flags.set(index, Boolean.valueOf(value));
            }
        }

        public void insert(int index, String text, VmObject image) {
            strings.add(index, text);
            images.add(index, image);
            flags.add(index, Boolean.FALSE);
        }

        public void append(String text, VmObject image) {
            insert(strings.size(), text, image);
        }

        public void set(int index, String text, VmObject image) {
            strings.set(index, text);
            images.set(index, image);
        }

        public void delete(int index) {
            strings.remove(index);
            images.remove(index);
            flags.remove(index);
        }

        public void clear() {
            strings.clear();
            images.clear();
            flags.clear();
        }

        /** Leaves exactly one element selected, as an exclusive choice must. */
        public void selectOnly(int index) {
            for (int i = 0; i < flags.size(); i++) {
                flags.set(i, Boolean.valueOf(i == index));
            }
        }
    }

    // --------------------------------------------------------------- install

    public static void install(final Vm vm, final MidpContext context) {
        ticker(vm, context);
        screen(vm, context);
        item(vm, context);
        stringItem(vm, context);
        imageItem(vm, context);
        textField(vm, context);
        gauge(vm, context);
        dateField(vm, context);
        choice(vm);
        choiceGroup(vm, context);
        itemStateListener(vm);
        form(vm, context);
        list(vm, context);
        textBox(vm, context);
        alertType(vm);
        alert(vm, context);
    }

    private static NativeMethod noop() {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                return null;
            }
        };
    }

    // ---------------------------------------------------------------- access

    /** Host choice model of a List or ChoiceGroup, created on demand. */
    public static Choices choicesOf(VmObject self) {
        if (!(self.host instanceof Choices)) {
            self.host = new Choices();
        }
        return (Choices) self.host;
    }

    /** Host text buffer of a TextBox or TextField, created on demand. */
    public static StringBuilder textOf(VmObject self) {
        if (!(self.host instanceof StringBuilder)) {
            self.host = new StringBuilder();
        }
        return (StringBuilder) self.host;
    }

    /** Form chứa một item, hoặc null khi item chưa thuộc về màn hình nào. */
    public static VmObject formOf(VmObject item) {
        Object owner = item == null ? null : item.get("owner");
        return owner instanceof VmObject ? (VmObject) owner : null;
    }

    /** Ghi lại rằng item này thuộc về form kia, rồi trả lại chính nó. */
    static VmObject own(VmObject form, VmObject item) {
        if (item != null) {
            item.set("owner", form);
        }
        return item;
    }

    /** Host item list of a Form, created on demand. */
    @SuppressWarnings("unchecked")
    public static List<VmObject> itemsOf(VmObject self) {
        if (!(self.host instanceof List)) {
            self.host = new ArrayList<VmObject>();
        }
        return (List<VmObject>) self.host;
    }

    public static int intField(VmObject self, String name) {
        Object value = self.get(name);
        return value == null ? 0 : ((Number) value).intValue();
    }

    // ---------------------------------------------------------------- Ticker

    private static void ticker(final Vm vm, final MidpContext context) {
        vm.builtin(TICKER, Vm.OBJECT)
                .field("string", "Ljava/lang/String;")
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("string", args[0]);
                        return null;
                    }
                })
                .method("getString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("string");
                    }
                })
                .method("setString", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("string", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .define();
    }

    // ---------------------------------------------------------------- Screen

    /**
     * Everything that is not a Canvas descends from Screen. It adds only the
     * ticker; the interesting state belongs to the subclasses.
     */
    private static void screen(final Vm vm, final MidpContext context) {
        vm.builtin(SCREEN, MidpUi.DISPLAYABLE)
                .field("ticker", "Ljavax/microedition/lcdui/Ticker;")
                // Where the player is in the screen, and how far it has been
                // scrolled. A handset remembers both across a repaint, so they
                // live on the screen rather than in the renderer.
                .field("focus", "I")
                .field("scroll", "I")
                .method("<init>", "()V", noop())
                .method("setTicker", "(Ljavax/microedition/lcdui/Ticker;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("ticker", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getTicker", "()Ljavax/microedition/lcdui/Ticker;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("ticker");
                    }
                })
                .define();
    }

    // ------------------------------------------------------------------ Item

    private static void item(final Vm vm, final MidpContext context) {
        vm.builtin(ITEM, Vm.OBJECT)
                .field("label", "Ljava/lang/String;")
                // Form chứa item này. MIDP cho phép nhảy thẳng tới một item
                // (setCurrentItem), mà từ item thì không có đường nào tìm ra
                // màn hình chứa nó — nên đường ấy được ghi lại lúc thêm vào.
                .field("owner", "Ljavax/microedition/lcdui/Displayable;")
                .field("layout", "I")
                .staticField("LAYOUT_DEFAULT", "I").staticField("LAYOUT_LEFT", "I")
                .staticField("LAYOUT_RIGHT", "I").staticField("LAYOUT_CENTER", "I")
                .staticField("LAYOUT_NEWLINE_BEFORE", "I").staticField("LAYOUT_NEWLINE_AFTER", "I")
                .staticField("PLAIN", "I").staticField("HYPERLINK", "I").staticField("BUTTON", "I")
                .method("<init>", "()V", noop())
                .method("getLabel", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("label");
                    }
                })
                .method("setLabel", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("setLayout", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("layout", Integer.valueOf(Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("getLayout", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("layout");
                    }
                })
                .define();

        VmClass type = vm.loadClass(ITEM);
        vm.initialize(type);
        MidpGfx.setStatic(vm, type, "LAYOUT_DEFAULT", 0);
        MidpGfx.setStatic(vm, type, "LAYOUT_LEFT", 1);
        MidpGfx.setStatic(vm, type, "LAYOUT_RIGHT", 2);
        MidpGfx.setStatic(vm, type, "LAYOUT_CENTER", 3);
        MidpGfx.setStatic(vm, type, "LAYOUT_NEWLINE_BEFORE", 0x100);
        MidpGfx.setStatic(vm, type, "LAYOUT_NEWLINE_AFTER", 0x200);
        MidpGfx.setStatic(vm, type, "PLAIN", 0);
        MidpGfx.setStatic(vm, type, "HYPERLINK", 1);
        MidpGfx.setStatic(vm, type, "BUTTON", 2);
    }

    private static void stringItem(final Vm vm, final MidpContext context) {
        vm.builtin(STRING_ITEM, ITEM)
                .field("text", "Ljava/lang/String;")
                .field("appearance", "I")
                .method("<init>", "(Ljava/lang/String;Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("text", args[1]);
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("text", args[1]);
                        self.set("appearance", Integer.valueOf(Rt.i(args, 2)));
                        return null;
                    }
                })
                .method("getText", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("text");
                    }
                })
                .method("setText", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("text", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getAppearanceMode", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("appearance");
                    }
                })
                .define();
    }

    private static void imageItem(final Vm vm, final MidpContext context) {
        vm.builtin(IMAGE_ITEM, ITEM)
                .field("image", "Ljavax/microedition/lcdui/Image;")
                .field("altText", "Ljava/lang/String;")
                .method("<init>",
                        "(Ljava/lang/String;Ljavax/microedition/lcdui/Image;ILjava/lang/String;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("label", args[0]);
                                self.set("image", args[1]);
                                self.set("layout", Integer.valueOf(Rt.i(args, 2)));
                                self.set("altText", args[3]);
                                return null;
                            }
                        })
                .method("getImage", "()Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("image");
                    }
                })
                .method("setImage", "(Ljavax/microedition/lcdui/Image;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("image", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getAltText", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("altText");
                    }
                })
                .define();
    }

    // ------------------------------------------------------------- TextField

    /** The editing half, shared by TextField and TextBox. */
    private static void textMethods(com.mobicore.core.vm.BuiltinBuilder builder,
                                    final MidpContext context) {
        builder
                .method("getString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(textOf(self).toString());
                    }
                })
                .method("setString", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        StringBuilder text = textOf(self);
                        text.setLength(0);
                        if (args[0] != null) {
                            text.append(vm.stringOf(args[0]));
                        }
                        self.set("caret", Integer.valueOf(text.length()));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("insert", "(Ljava/lang/String;I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        StringBuilder text = textOf(self);
                        int at = clamp(Rt.i(args, 1), 0, text.length());
                        text.insert(at, Rt.s(vm, args, 0));
                        self.set("caret", Integer.valueOf(at + Rt.s(vm, args, 0).length()));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("delete", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        StringBuilder text = textOf(self);
                        int offset = clamp(Rt.i(args, 0), 0, text.length());
                        int end = clamp(offset + Rt.i(args, 1), offset, text.length());
                        text.delete(offset, end);
                        self.set("caret", Integer.valueOf(Math.min(intField(self, "caret"), text.length())));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("size", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(textOf(self).length());
                    }
                })
                .method("getMaxSize", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("maxSize");
                    }
                })
                .method("setMaxSize", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        int max = Math.max(1, Rt.i(args, 0));
                        self.set("maxSize", Integer.valueOf(max));
                        StringBuilder text = textOf(self);
                        if (text.length() > max) {
                            text.setLength(max);
                        }
                        return Integer.valueOf(max);
                    }
                })
                .method("getConstraints", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("constraints");
                    }
                })
                .method("setConstraints", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("constraints", Integer.valueOf(Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("getCaretPosition", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("caret");
                    }
                })
                .method("getChars", "([C)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String text = textOf(self).toString();
                        VmArray out = Rt.array(args, 0);
                        int count = Math.min(text.length(), out.length());
                        text.getChars(0, count, out.chars(), 0);
                        return Integer.valueOf(count);
                    }
                });
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static void textField(final Vm vm, final MidpContext context) {
        com.mobicore.core.vm.BuiltinBuilder builder = vm.builtin(TEXT_FIELD, ITEM)
                .field("maxSize", "I")
                .field("constraints", "I")
                .field("caret", "I")
                .staticField("ANY", "I").staticField("EMAILADDR", "I").staticField("NUMERIC", "I")
                .staticField("PHONENUMBER", "I").staticField("URL", "I").staticField("DECIMAL", "I")
                .staticField("PASSWORD", "I").staticField("UNEDITABLE", "I")
                .staticField("CONSTRAINT_MASK", "I")
                .method("<init>", "(Ljava/lang/String;Ljava/lang/String;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("maxSize", Integer.valueOf(Math.max(1, Rt.i(args, 2))));
                        self.set("constraints", Integer.valueOf(Rt.i(args, 3)));
                        StringBuilder text = textOf(self);
                        if (args[1] != null) {
                            text.append(vm.stringOf(args[1]));
                        }
                        self.set("caret", Integer.valueOf(text.length()));
                        return null;
                    }
                });
        textMethods(builder, context);
        builder.define();

        VmClass type = vm.loadClass(TEXT_FIELD);
        vm.initialize(type);
        MidpGfx.setStatic(vm, type, "ANY", ANY);
        MidpGfx.setStatic(vm, type, "EMAILADDR", EMAILADDR);
        MidpGfx.setStatic(vm, type, "NUMERIC", NUMERIC);
        MidpGfx.setStatic(vm, type, "PHONENUMBER", PHONENUMBER);
        MidpGfx.setStatic(vm, type, "URL", URL);
        MidpGfx.setStatic(vm, type, "DECIMAL", DECIMAL);
        MidpGfx.setStatic(vm, type, "PASSWORD", PASSWORD);
        MidpGfx.setStatic(vm, type, "UNEDITABLE", UNEDITABLE);
        MidpGfx.setStatic(vm, type, "CONSTRAINT_MASK", CONSTRAINT_MASK);
    }

    // ----------------------------------------------------------------- Gauge

    private static void gauge(final Vm vm, final MidpContext context) {
        vm.builtin(GAUGE, ITEM)
                .field("value", "I")
                .field("maxValue", "I")
                .field("interactive", "I")
                .staticField("INDEFINITE", "I")
                .staticField("CONTINUOUS_IDLE", "I").staticField("INCREMENTAL_IDLE", "I")
                .staticField("CONTINUOUS_RUNNING", "I").staticField("INCREMENTAL_UPDATING", "I")
                .method("<init>", "(Ljava/lang/String;ZII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("interactive", Integer.valueOf(Rt.bool(args, 1) ? 1 : 0));
                        self.set("maxValue", Integer.valueOf(Rt.i(args, 2)));
                        self.set("value", Integer.valueOf(Rt.i(args, 3)));
                        return null;
                    }
                })
                .method("getValue", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("value");
                    }
                })
                .method("setValue", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("value", Integer.valueOf(Rt.i(args, 0)));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getMaxValue", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("maxValue");
                    }
                })
                .method("setMaxValue", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("maxValue", Integer.valueOf(Rt.i(args, 0)));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("isInteractive", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("interactive");
                    }
                })
                .define();

        VmClass type = vm.loadClass(GAUGE);
        vm.initialize(type);
        MidpGfx.setStatic(vm, type, "INDEFINITE", INDEFINITE);
        MidpGfx.setStatic(vm, type, "CONTINUOUS_IDLE", 0);
        MidpGfx.setStatic(vm, type, "INCREMENTAL_IDLE", 1);
        MidpGfx.setStatic(vm, type, "CONTINUOUS_RUNNING", 2);
        MidpGfx.setStatic(vm, type, "INCREMENTAL_UPDATING", 3);
    }

    // ------------------------------------------------------------- DateField

    private static void dateField(final Vm vm, final MidpContext context) {
        vm.builtin(DATE_FIELD, ITEM)
                .field("date", "Ljava/util/Date;")
                .field("inputMode", "I")
                .staticField("DATE", "I").staticField("TIME", "I").staticField("DATE_TIME", "I")
                .method("<init>", "(Ljava/lang/String;I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("label", args[0]);
                        self.set("inputMode", Integer.valueOf(Rt.i(args, 1)));
                        return null;
                    }
                })
                .method("getDate", "()Ljava/util/Date;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("date");
                    }
                })
                .method("setDate", "(Ljava/util/Date;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("date", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getInputMode", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("inputMode");
                    }
                })
                .method("setInputMode", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("inputMode", Integer.valueOf(Rt.i(args, 0)));
                        return null;
                    }
                })
                .define();

        VmClass type = vm.loadClass(DATE_FIELD);
        vm.initialize(type);
        MidpGfx.setStatic(vm, type, "DATE", 1);
        MidpGfx.setStatic(vm, type, "TIME", 2);
        MidpGfx.setStatic(vm, type, "DATE_TIME", 3);
    }

    // ---------------------------------------------------------------- Choice

    private static void choice(final Vm vm) {
        vm.builtin(CHOICE, Vm.OBJECT, new String[0], true)
                .staticField("EXCLUSIVE", "I").staticField("MULTIPLE", "I")
                .staticField("IMPLICIT", "I").staticField("POPUP", "I")
                .staticField("TEXT_WRAP_DEFAULT", "I").staticField("TEXT_WRAP_ON", "I")
                .staticField("TEXT_WRAP_OFF", "I")
                .define();

        VmClass type = vm.loadClass(CHOICE);
        vm.initialize(type);
        MidpGfx.setStatic(vm, type, "EXCLUSIVE", EXCLUSIVE);
        MidpGfx.setStatic(vm, type, "MULTIPLE", MULTIPLE);
        MidpGfx.setStatic(vm, type, "IMPLICIT", IMPLICIT);
        MidpGfx.setStatic(vm, type, "POPUP", POPUP);
        MidpGfx.setStatic(vm, type, "TEXT_WRAP_DEFAULT", 0);
        MidpGfx.setStatic(vm, type, "TEXT_WRAP_ON", 1);
        MidpGfx.setStatic(vm, type, "TEXT_WRAP_OFF", 2);
    }

    /**
     * The element methods {@code List} and {@code ChoiceGroup} share. They are
     * the same API on the same model; only the frame around them differs.
     */
    private static void choiceMethods(com.mobicore.core.vm.BuiltinBuilder builder,
                                      final MidpContext context) {
        builder
                .method("append", "(Ljava/lang/String;Ljavax/microedition/lcdui/Image;)I",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                Choices choices = choicesOf(self);
                                choices.append(Rt.s(vm, args, 0), Rt.obj(args, 1));
                                selectFirstIfNeeded(self, choices);
                                context.requestRepaint();
                                return Integer.valueOf(choices.size() - 1);
                            }
                        })
                .method("insert", "(ILjava/lang/String;Ljavax/microedition/lcdui/Image;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                Choices choices = choicesOf(self);
                                choices.insert(clamp(Rt.i(args, 0), 0, choices.size()),
                                        Rt.s(vm, args, 1), Rt.obj(args, 2));
                                selectFirstIfNeeded(self, choices);
                                context.requestRepaint();
                                return null;
                            }
                        })
                .method("set", "(ILjava/lang/String;Ljavax/microedition/lcdui/Image;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                Choices choices = choicesOf(self);
                                int index = Rt.i(args, 0);
                                if (index >= 0 && index < choices.size()) {
                                    choices.set(index, Rt.s(vm, args, 1), Rt.obj(args, 2));
                                }
                                context.requestRepaint();
                                return null;
                            }
                        })
                .method("delete", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        int index = Rt.i(args, 0);
                        if (index >= 0 && index < choices.size()) {
                            choices.delete(index);
                        }
                        self.set("focus", Integer.valueOf(
                                clamp(intField(self, "focus"), 0, Math.max(0, choices.size() - 1))));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("deleteAll", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        choicesOf(self).clear();
                        self.set("focus", Integer.valueOf(0));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("size", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(choicesOf(self).size());
                    }
                })
                .method("getString", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        int index = Rt.i(args, 0);
                        return index < 0 || index >= choices.size() ? null
                                : vm.newString(choices.string(index));
                    }
                })
                .method("getImage", "(I)Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        int index = Rt.i(args, 0);
                        return index < 0 || index >= choices.size() ? null : choices.image(index);
                    }
                })
                .method("getSelectedIndex", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        if (intField(self, "choiceType") == MULTIPLE) {
                            // A multiple choice has no single selection, and the
                            // specification says so with -1.
                            return Integer.valueOf(-1);
                        }
                        for (int i = 0; i < choices.size(); i++) {
                            if (choices.selected(i)) {
                                return Integer.valueOf(i);
                            }
                        }
                        return Integer.valueOf(-1);
                    }
                })
                .method("setSelectedIndex", "(IZ)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        int index = Rt.i(args, 0);
                        if (index < 0 || index >= choices.size()) {
                            return null;
                        }
                        if (intField(self, "choiceType") == MULTIPLE) {
                            choices.setSelected(index, Rt.bool(args, 1));
                        } else if (Rt.bool(args, 1)) {
                            choices.selectOnly(index);
                            self.set("focus", Integer.valueOf(index));
                        }
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("isSelected", "(I)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(choicesOf(self).selected(Rt.i(args, 0)));
                    }
                })
                .method("getSelectedFlags", "([Z)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        VmArray out = Rt.array(args, 0);
                        byte[] flags = out.bytes();
                        int count = 0;
                        for (int i = 0; i < flags.length; i++) {
                            boolean on = i < choices.size() && choices.selected(i);
                            flags[i] = (byte) (on ? 1 : 0);
                            if (on) {
                                count++;
                            }
                        }
                        return Integer.valueOf(count);
                    }
                })
                .method("setSelectedFlags", "([Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Choices choices = choicesOf(self);
                        byte[] flags = Rt.array(args, 0).bytes();
                        for (int i = 0; i < choices.size(); i++) {
                            choices.setSelected(i, i < flags.length && flags[i] != 0);
                        }
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("setFitPolicy", "(I)V", noop())
                .method("getFitPolicy", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(0);
                    }
                });
    }

    /**
     * An exclusive or implicit choice always has exactly one element selected
     * once it has any elements at all, which is what makes the first append
     * meaningful.
     */
    private static void selectFirstIfNeeded(VmObject self, Choices choices) {
        int type = intField(self, "choiceType");
        if (type == MULTIPLE || choices.size() != 1) {
            return;
        }
        choices.selectOnly(0);
    }

    private static void choiceGroup(final Vm vm, final MidpContext context) {
        com.mobicore.core.vm.BuiltinBuilder builder =
                vm.builtin(CHOICE_GROUP, ITEM, new String[]{CHOICE}, false)
                        .field("choiceType", "I")
                        .field("focus", "I")
                        .method("<init>", "(Ljava/lang/String;I)V", new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("label", args[0]);
                                self.set("choiceType", Integer.valueOf(Rt.i(args, 1)));
                                choicesOf(self);
                                return null;
                            }
                        })
                        .method("<init>",
                                "(Ljava/lang/String;I[Ljava/lang/String;[Ljavax/microedition/lcdui/Image;)V",
                                new NativeMethod() {
                                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                                        self.set("label", args[0]);
                                        self.set("choiceType", Integer.valueOf(Rt.i(args, 1)));
                                        fill(vm, self, (VmArray) args[2], (VmArray) args[3]);
                                        return null;
                                    }
                                });
        choiceMethods(builder, context);
        builder.define();
    }

    /** Fills a choice from the string and image arrays a constructor took. */
    private static void fill(Vm vm, VmObject self, VmArray strings, VmArray images) {
        Choices choices = choicesOf(self);
        if (strings != null) {
            for (int i = 0; i < strings.length(); i++) {
                VmObject image = images == null || i >= images.length()
                        ? null : (VmObject) images.objects()[i];
                choices.append(vm.stringOf(strings.objects()[i]), image);
            }
        }
        if (choices.size() > 0 && intField(self, "choiceType") != MULTIPLE) {
            choices.selectOnly(0);
        }
    }

    private static void itemStateListener(final Vm vm) {
        vm.builtin(ITEM_STATE_LISTENER, Vm.OBJECT, new String[0], true)
                .abstractMethod("itemStateChanged", "(Ljavax/microedition/lcdui/Item;)V")
                .define();
    }

    // ------------------------------------------------------------------ Form

    private static void form(final Vm vm, final MidpContext context) {
        vm.builtin(FORM, SCREEN)
                .field("itemStateListener", "Ljavax/microedition/lcdui/ItemStateListener;")
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("title", args[0]);
                        itemsOf(self);
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;[Ljavax/microedition/lcdui/Item;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("title", args[0]);
                                List<VmObject> items = itemsOf(self);
                                VmArray given = (VmArray) args[1];
                                if (given != null) {
                                    for (int i = 0; i < given.length(); i++) {
                                        items.add(own(self, (VmObject) given.objects()[i]));
                                    }
                                }
                                return null;
                            }
                        })
                .method("append", "(Ljavax/microedition/lcdui/Item;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<VmObject> items = itemsOf(self);
                        items.add(own(self, Rt.obj(args, 0)));
                        context.requestRepaint();
                        return Integer.valueOf(items.size() - 1);
                    }
                })
                .method("append", "(Ljava/lang/String;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject item = vm.newInstance(STRING_ITEM);
                        item.set("text", args[0]);
                        List<VmObject> items = itemsOf(self);
                        items.add(own(self, item));
                        context.requestRepaint();
                        return Integer.valueOf(items.size() - 1);
                    }
                })
                .method("append", "(Ljavax/microedition/lcdui/Image;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject item = vm.newInstance(IMAGE_ITEM);
                        item.set("image", args[0]);
                        List<VmObject> items = itemsOf(self);
                        items.add(own(self, item));
                        context.requestRepaint();
                        return Integer.valueOf(items.size() - 1);
                    }
                })
                .method("insert", "(ILjavax/microedition/lcdui/Item;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<VmObject> items = itemsOf(self);
                        items.add(clamp(Rt.i(args, 0), 0, items.size()), own(self, Rt.obj(args, 1)));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("set", "(ILjavax/microedition/lcdui/Item;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<VmObject> items = itemsOf(self);
                        int index = Rt.i(args, 0);
                        if (index >= 0 && index < items.size()) {
                            items.set(index, own(self, Rt.obj(args, 1)));
                        }
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("delete", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<VmObject> items = itemsOf(self);
                        int index = Rt.i(args, 0);
                        if (index >= 0 && index < items.size()) {
                            own(null, items.remove(index));
                        }
                        self.set("focus", Integer.valueOf(
                                clamp(intField(self, "focus"), 0, Math.max(0, items.size() - 1))));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("deleteAll", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        itemsOf(self).clear();
                        self.set("focus", Integer.valueOf(0));
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("get", "(I)Ljavax/microedition/lcdui/Item;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        List<VmObject> items = itemsOf(self);
                        int index = Rt.i(args, 0);
                        return index < 0 || index >= items.size() ? null : items.get(index);
                    }
                })
                .method("size", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(itemsOf(self).size());
                    }
                })
                .method("setItemStateListener", "(Ljavax/microedition/lcdui/ItemStateListener;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("itemStateListener", args[0]);
                                return null;
                            }
                        })
                .define();
    }

    // ------------------------------------------------------------------ List

    private static void list(final Vm vm, final MidpContext context) {
        com.mobicore.core.vm.BuiltinBuilder builder =
                vm.builtin(LIST, SCREEN, new String[]{CHOICE}, false)
                        .field("choiceType", "I")
                        .field("selectCommand", "Ljavax/microedition/lcdui/Command;")
                        .staticField("SELECT_COMMAND", "Ljavax/microedition/lcdui/Command;")
                        .method("<init>", "(Ljava/lang/String;I)V", new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("title", args[0]);
                                self.set("choiceType", Integer.valueOf(Rt.i(args, 1)));
                                choicesOf(self);
                                installSelectCommand(vm, context, self);
                                return null;
                            }
                        })
                        .method("<init>",
                                "(Ljava/lang/String;I[Ljava/lang/String;[Ljavax/microedition/lcdui/Image;)V",
                                new NativeMethod() {
                                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                                        self.set("title", args[0]);
                                        self.set("choiceType", Integer.valueOf(Rt.i(args, 1)));
                                        fill(vm, self, (VmArray) args[2], (VmArray) args[3]);
                                        installSelectCommand(vm, context, self);
                                        return null;
                                    }
                                })
                        .method("setSelectCommand", "(Ljavax/microedition/lcdui/Command;)V",
                                new NativeMethod() {
                                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                                        VmObject previous = (VmObject) self.get("selectCommand");
                                        if (previous != null) {
                                            context.removeCommand(self, previous);
                                        }
                                        self.set("selectCommand", args[0]);
                                        if (args[0] != null) {
                                            context.addCommand(self, Rt.obj(args, 0));
                                        }
                                        return null;
                                    }
                                });
        choiceMethods(builder, context);
        builder.define();

        VmClass type = vm.loadClass(LIST);
        vm.initialize(type);
        // A single shared Command instance, as the specification requires: a
        // MIDlet compares the command it is handed against List.SELECT_COMMAND
        // by identity to tell a selection from its own commands.
        VmObject select = vm.newInstance(MidpUi.COMMAND);
        select.set("label", vm.newString("Chọn"));
        select.set("commandType", Integer.valueOf(MidpContext.COMMAND_SCREEN));
        select.set("priority", Integer.valueOf(0));
        type.staticRefs()[type.findField("SELECT_COMMAND").slot()] = select;
    }

    /**
     * An implicit list selects by pressing the key, so the handset shows a
     * command for it whether the game added one or not.
     */
    private static void installSelectCommand(Vm vm, MidpContext context, VmObject self) {
        if (intField(self, "choiceType") != IMPLICIT) {
            return;
        }
        VmClass type = vm.loadClass(LIST);
        vm.initialize(type);
        VmObject select = (VmObject) type.staticRefs()[type.findField("SELECT_COMMAND").slot()];
        self.set("selectCommand", select);
        context.addCommand(self, select);
    }

    // --------------------------------------------------------------- TextBox

    private static void textBox(final Vm vm, final MidpContext context) {
        com.mobicore.core.vm.BuiltinBuilder builder = vm.builtin(TEXT_BOX, SCREEN)
                .field("maxSize", "I")
                .field("constraints", "I")
                .field("caret", "I")
                .method("<init>", "(Ljava/lang/String;Ljava/lang/String;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("title", args[0]);
                        self.set("maxSize", Integer.valueOf(Math.max(1, Rt.i(args, 2))));
                        self.set("constraints", Integer.valueOf(Rt.i(args, 3)));
                        StringBuilder text = textOf(self);
                        if (args[1] != null) {
                            text.append(vm.stringOf(args[1]));
                        }
                        self.set("caret", Integer.valueOf(text.length()));
                        return null;
                    }
                });
        textMethods(builder, context);
        builder.define();
    }

    // ----------------------------------------------------------------- Alert

    private static void alertType(final Vm vm) {
        vm.builtin(ALERT_TYPE, Vm.OBJECT)
                .field("kind", "I")
                .staticField("INFO", "Ljavax/microedition/lcdui/AlertType;")
                .staticField("WARNING", "Ljavax/microedition/lcdui/AlertType;")
                .staticField("ERROR", "Ljavax/microedition/lcdui/AlertType;")
                .staticField("ALARM", "Ljavax/microedition/lcdui/AlertType;")
                .staticField("CONFIRMATION", "Ljavax/microedition/lcdui/AlertType;")
                .method("<init>", "()V", noop())
                .method("playSound", "(Ljavax/microedition/lcdui/Display;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // There is no audio device yet, and the specification
                        // lets a device say so rather than pretend.
                        return Rt.box(false);
                    }
                })
                .define();

        VmClass type = vm.loadClass(ALERT_TYPE);
        vm.initialize(type);
        String[] names = {"INFO", "WARNING", "ERROR", "ALARM", "CONFIRMATION"};
        for (int i = 0; i < names.length; i++) {
            VmObject instance = vm.newInstance(type);
            instance.set("kind", Integer.valueOf(i));
            type.staticRefs()[type.findField(names[i]).slot()] = instance;
        }
    }

    /** Which of the five alert types an instance is, for the renderer. */
    public static int alertKind(VmObject alertType) {
        return alertType == null ? 0 : intField(alertType, "kind");
    }

    private static void alert(final Vm vm, final MidpContext context) {
        vm.builtin(ALERT, SCREEN)
                .field("string", "Ljava/lang/String;")
                .field("image", "Ljavax/microedition/lcdui/Image;")
                .field("alertType", "Ljavax/microedition/lcdui/AlertType;")
                .field("indicator", "Ljavax/microedition/lcdui/Gauge;")
                .field("timeout", "I")
                .staticField("FOREVER", "I")
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("title", args[0]);
                        self.set("timeout", Integer.valueOf(FOREVER));
                        return null;
                    }
                })
                .method("<init>",
                        "(Ljava/lang/String;Ljava/lang/String;Ljavax/microedition/lcdui/Image;"
                                + "Ljavax/microedition/lcdui/AlertType;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                self.set("title", args[0]);
                                self.set("string", args[1]);
                                self.set("image", args[2]);
                                self.set("alertType", args[3]);
                                self.set("timeout", Integer.valueOf(FOREVER));
                                return null;
                            }
                        })
                .method("getString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("string");
                    }
                })
                .method("setString", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("string", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getImage", "()Ljavax/microedition/lcdui/Image;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("image");
                    }
                })
                .method("setImage", "(Ljavax/microedition/lcdui/Image;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("image", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getType", "()Ljavax/microedition/lcdui/AlertType;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("alertType");
                    }
                })
                .method("setType", "(Ljavax/microedition/lcdui/AlertType;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("alertType", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getTimeout", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("timeout");
                    }
                })
                .method("setTimeout", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("timeout", Integer.valueOf(Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("getDefaultTimeout", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(FOREVER);
                    }
                })
                .method("setIndicator", "(Ljavax/microedition/lcdui/Gauge;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("indicator", args[0]);
                        context.requestRepaint();
                        return null;
                    }
                })
                .method("getIndicator", "()Ljavax/microedition/lcdui/Gauge;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("indicator");
                    }
                })
                .define();

        VmClass type = vm.loadClass(ALERT);
        vm.initialize(type);
        MidpGfx.setStatic(vm, type, "FOREVER", FOREVER);
    }
}
