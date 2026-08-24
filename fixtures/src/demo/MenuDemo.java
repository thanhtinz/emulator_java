package demo;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

/**
 * The high level counterpart to Sky Runner.
 *
 * Sky Runner paints its own pixels and so exercises Canvas, Graphics and the
 * game package. This one never draws anything: it is built entirely from List,
 * Form, TextBox and Alert, which is how most commercial MIDlets put their menus
 * together, and so it is what proves those screens work.
 */
public class MenuDemo extends MIDlet implements CommandListener, ItemStateListener {

    private List menu;
    private Form options;
    private TextBox nameBox;

    private final Command back = new Command("Quay lại", Command.BACK, 1);
    private final Command exit = new Command("Thoát", Command.EXIT, 2);
    private final Command save = new Command("Lưu", Command.OK, 1);
    private final Command reset = new Command("Đặt lại", Command.SCREEN, 2);
    private final Command help = new Command("Trợ giúp", Command.HELP, 3);
    private final Command about = new Command("Giới thiệu", Command.SCREEN, 4);

    private TextField nameField;
    private Gauge volume;
    private ChoiceGroup difficulty;
    private StringItem status;

    protected void startApp() {
        if (menu == null) {
            menu = new List("Sky Runner", List.IMPLICIT);
            menu.append("Chơi ngay", null);
            menu.append("Tuỳ chọn", null);
            menu.append("Nhập tên", null);
            menu.append("Bảng điểm", null);
            menu.append("Giới thiệu", null);
            menu.addCommand(exit);
            menu.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(menu);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /** The settings screen: one of every item kind a Form can hold. */
    public Form optionsForm() {
        if (options == null) {
            options = new Form("Tuỳ chọn");
            status = new StringItem("Hồ sơ", "Khách");
            nameField = new TextField("Tên người chơi", "Nam", 16, TextField.ANY);
            volume = new Gauge("Âm lượng", true, 10, 7);
            difficulty = new ChoiceGroup("Độ khó", ChoiceGroup.EXCLUSIVE);
            difficulty.append("Dễ", null);
            difficulty.append("Thường", null);
            difficulty.append("Khó", null);
            difficulty.setSelectedIndex(1, true);
            options.append(status);
            options.append(nameField);
            options.append(volume);
            options.append(difficulty);
            // Four commands that all want the left key, which is what puts the
            // last three behind the "Tuỳ chọn" menu.
            options.addCommand(save);
            options.addCommand(reset);
            options.addCommand(help);
            options.addCommand(about);
            options.addCommand(back);
            options.setItemStateListener(this);
            options.setCommandListener(this);
        }
        return options;
    }

    public TextBox nameBox() {
        if (nameBox == null) {
            nameBox = new TextBox("Nhập tên", "", 12, TextField.ANY);
            nameBox.addCommand(save);
            nameBox.addCommand(back);
            nameBox.setCommandListener(this);
        }
        return nameBox;
    }

    public Alert aboutAlert() {
        Alert alert = new Alert("Giới thiệu",
                "MobiCore chạy trò chơi J2ME. Màn hình này do máy vẽ, không phải trò chơi.",
                null, AlertType.INFO);
        alert.setTimeout(Alert.FOREVER);
        alert.addCommand(back);
        alert.setCommandListener(this);
        return alert;
    }

    public List menu() {
        return menu;
    }

    public void commandAction(Command command, Displayable screen) {
        Display display = Display.getDisplay(this);
        if (command == exit) {
            notifyDestroyed();
            return;
        }
        if (command == back || command == save) {
            display.setCurrent(menu);
            return;
        }
        if (command == reset) {
            volume.setValue(5);
            difficulty.setSelectedIndex(0, true);
            status.setText("Đã đặt lại");
            return;
        }
        if (command == help || command == about) {
            display.setCurrent(aboutAlert());
            return;
        }
        if (screen == menu) {
            select(display, menu.getSelectedIndex());
        }
    }

    private void select(Display display, int index) {
        if (index == 1) {
            display.setCurrent(optionsForm());
        } else if (index == 2) {
            display.setCurrent(nameBox());
        } else if (index == 4) {
            display.setCurrent(aboutAlert());
        }
    }

    public void itemStateChanged(Item item) {
        if (item == volume) {
            status.setText("Âm lượng " + volume.getValue());
        } else if (item == difficulty) {
            status.setText(difficulty.getString(difficulty.getSelectedIndex()));
        }
    }
}
