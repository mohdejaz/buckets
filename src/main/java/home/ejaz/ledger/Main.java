package home.ejaz.ledger;

import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;


public class Main extends JFrame {

    public Main() {
        ELayout glm = new ELayout(10, 18, 30, 7);
        JPanel main = new JPanel();
        main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        main.setLayout(glm);

        // row 1
        JLabel lbId = new JLabel("Id: ", JLabel.LEADING);
        glm.setConstraints(lbId, new EConstaint(1, 1, 3, 1));
        main.add(lbId);

        JTextField tfId = new JTextField();
        glm.setConstraints(tfId, new EConstaint(1, 4, 6, 1));
        main.add(tfId);

        // row 2
        JLabel lbDate = new JLabel("Date: ", JLabel.LEADING);
        glm.setConstraints(lbDate, new EConstaint(2, 1, 3, 1));
        main.add(lbDate);

        JTextField tfDate = new JTextField();
        glm.setConstraints(tfDate, new EConstaint(2, 4, 6, 1));
        main.add(tfDate);

        JLabel lbBucket = new JLabel("Bucket: ", JLabel.LEADING);
        glm.setConstraints(lbBucket, new EConstaint(2, 10, 3, 1));
        main.add(lbBucket);

        JComboBox jcBucket = new JComboBox(new String[]{"Apples", "Oranges", "Bananas", "Kiwis"});
        glm.setConstraints(jcBucket, new EConstaint(2, 13, 6, 1));
        main.add(jcBucket);

        // row 3
        JLabel lbMerchant = new JLabel("Merchant: ", JLabel.LEADING);
        glm.setConstraints(lbMerchant, new EConstaint(3, 1, 3, 1));
        main.add(lbMerchant);

        JTextField tfMerchant = new JTextField();
        glm.setConstraints(tfMerchant, new EConstaint(3, 4, 6, 1));
        main.add(tfMerchant);

        JLabel lbBudget = new JLabel("Bucket: ", JLabel.LEADING);
        glm.setConstraints(lbBudget, new EConstaint(3, 10, 3, 1));
        main.add(lbBudget);

        JTextField tfBudget = new JTextField();
        glm.setConstraints(tfBudget, new EConstaint(3, 13, 6, 1));
        main.add(tfBudget);

        // row 4
        JLabel lbAmount = new JLabel("Amount: ", JLabel.LEADING);
        glm.setConstraints(lbAmount, new EConstaint(4, 1, 3, 1));
        main.add(lbAmount);

        JTextField tfAmount = new JTextField();
        glm.setConstraints(tfAmount, new EConstaint(4, 4, 6, 1));
        main.add(tfAmount);

        JLabel lbBalance = new JLabel("Balance: ", JLabel.LEADING);
        glm.setConstraints(lbBalance, new EConstaint(4, 10, 3, 1));
        main.add(lbBalance);

        JTextField tfBalance = new JTextField();
        glm.setConstraints(tfBalance, new EConstaint(4, 13, 6, 1));
        main.add(tfBalance);

        // row 5
        JLabel lbNotes = new JLabel("Notes: ", JLabel.LEADING);
        lbNotes.setVerticalAlignment(JLabel.TOP);
        glm.setConstraints(lbNotes, new EConstaint(5, 1, 3, 1));
        main.add(lbNotes);

        JTextArea jta = new JTextArea();
        JScrollPane jsp = new JScrollPane(jta);
        glm.setConstraints(jsp, new EConstaint(5, 4, 15, 5));
        main.add(jsp);

        // row 6
        JButton jbClear = new JButton("Clear");
        glm.setConstraints(jbClear, new EConstaint(10, 14, 3, 1));
        main.add(jbClear);

        JButton jbSave = new JButton("Save");
        glm.setConstraints(jbSave, new EConstaint(10, 17, 2, 1));
        main.add(jbSave);


        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(main, BorderLayout.CENTER);

        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setVisible(true);
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
        setFont(new FontUIResource(new Font("Verdana", Font.PLAIN, Config.getFontSize())));
        UIManager.setLookAndFeel(
                UIManager.getSystemLookAndFeelClassName());
        // new Main();
        new FormMenu();
    }
}