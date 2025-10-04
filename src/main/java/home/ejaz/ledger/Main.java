package home.ejaz.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;


public class Main extends JFrame {
  private final static Logger logger = LoggerFactory.getLogger(Main.class);

  public Main() {
  }

  private static void setFont(FontUIResource myFont) {
    UIManager.put("CheckBoxMenuItem.acceleratorFont", myFont);
    UIManager.put("Button.font", myFont);
    UIManager.put("ToggleButton.font", myFont);
    UIManager.put("RadioButton.font", myFont);
    UIManager.put("CheckBox.font", myFont);
    UIManager.put("ColorChooser.font", myFont);
    UIManager.put("ComboBox.font", myFont);
    UIManager.put("Label.font", myFont);
    UIManager.put("List.font", myFont);
    UIManager.put("MenuBar.font", myFont);
    UIManager.put("Menu.acceleratorFont", myFont);
    UIManager.put("RadioButtonMenuItem.acceleratorFont", myFont);
    UIManager.put("MenuItem.acceleratorFont", myFont);
    UIManager.put("MenuItem.font", myFont);
    UIManager.put("RadioButtonMenuItem.font", myFont);
    UIManager.put("CheckBoxMenuItem.font", myFont);
    UIManager.put("OptionPane.buttonFont", myFont);
    UIManager.put("OptionPane.messageFont", myFont);
    UIManager.put("Menu.font", myFont);
    UIManager.put("PopupMenu.font", myFont);
    UIManager.put("OptionPane.font", myFont);
    UIManager.put("Panel.font", myFont);
    UIManager.put("ProgressBar.font", myFont);
    UIManager.put("ScrollPane.font", myFont);
    UIManager.put("Viewport.font", myFont);
    UIManager.put("TabbedPane.font", myFont);
    UIManager.put("Slider.font", myFont);
    UIManager.put("Table.font", myFont);
    UIManager.put("TableHeader.font", myFont);
    UIManager.put("TextField.font", myFont);
    UIManager.put("Spinner.font", myFont);
    UIManager.put("PasswordField.font", myFont);
    UIManager.put("TextArea.font", myFont);
    UIManager.put("TextPane.font", myFont);
    UIManager.put("EditorPane.font", myFont);
    UIManager.put("TabbedPane.smallFont", myFont);
    UIManager.put("TitledBorder.font", myFont);
    UIManager.put("ToolBar.font", myFont);
    UIManager.put("ToolTip.font", myFont);
    UIManager.put("Tree.font", myFont);
    UIManager.put("FormattedTextField.font", myFont);
    UIManager.put("IconButton.font", myFont);
    UIManager.put("InternalFrame.optionDialogTitleFont", myFont);
    UIManager.put("InternalFrame.paletteTitleFont", myFont);
    UIManager.put("InternalFrame.titleFont", myFont);
  }

  public static void main(String[] args) throws Exception {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
             UnsupportedLookAndFeelException e) {
      e.printStackTrace(System.err);
    }

    setFont(new FontUIResource(new Font("Verdana", Font.PLAIN, Registry.getFontSize())));
    new FormMenu();
  }
}