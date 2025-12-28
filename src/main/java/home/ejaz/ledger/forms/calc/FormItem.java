package home.ejaz.ledger.forms.calc;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOAccounts;
import home.ejaz.ledger.dao.DAOItems;
import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import home.ejaz.ledger.models.Account;
import home.ejaz.ledger.models.Item;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

public class FormItem extends JDialog {
    private static final Logger logger = Logger.getLogger(FormItem.class.getName());

    private final JTextField jtfId = new JTextField();
    private final JTextField jtfName = new JTextField();
    private final JTextField jtfDtOfPurchase = new JTextField();
    private final JTextField jtfPrice = new JTextField();
    private final JTextField jtfNote = new JTextField();

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd");
    private final JButton jbClear = new JButton("Clear");
    private final JButton jbSave = new JButton("Save");

    private boolean init = false;
    private JFrame parent;

    public void init() {
        clear();

        if (!init) {
            jbSave.addActionListener(al -> doSave());
            jbClear.addActionListener(al -> doClear());


            init = true;
        }
    }

    private void doSave() {
        if (jtfName.getText().isEmpty()) {
            jtfName.setOpaque(true);
            jtfName.setBackground(Color.RED);
            return;
        }
        Item item = getItem();
        try {
            DAOItems.getInstance().save(item);
            clear();
            this.setVisible(false);
        } catch (Exception e) {
            logger.warn("Error", e);
        }
    }

    private void doClear() {
        // jtfName.setText("");
        jtfName.setOpaque(true);
        jtfName.setBackground(Color.WHITE);
    }

    public void clear() {
        jtfId.setText("");
        jtfName.setText("");
        jtfPrice.setText("");
        jtfDtOfPurchase.setText("");
        jtfNote.setText("");
    }

    public void setItem(Item item) {
        jtfId.setText(item.id == null ? "" : item.id.toString());
        jtfName.setText(item.name == null ? "" : item.name);
        jtfDtOfPurchase.setText(item.dop == null ? "" : sdf.format(item.dop));
        jtfPrice.setText(item.price == null ? "" : item.price.toString());
        jtfNote.setText(item.note == null ? "" : item.note);
    }

    public Item getItem() {
        Item item = new Item();
        item.id = jtfId.getText().isEmpty() ? null : Long.valueOf(jtfId.getText());
        item.name = jtfName.getText().trim().isEmpty() ? null : jtfName.getText().trim();
        item.dop = jtfDtOfPurchase.getText().trim().isEmpty() ? null
                : sdf.parse(jtfDtOfPurchase.getText().trim(), new ParsePosition(0));
        item.price = new BigDecimal(jtfPrice.getText());
        item.note = jtfNote.getText();

        return item;
    }

    public FormItem(JFrame parent) {
        super(parent);

        this.parent = parent;

        init();

        JPanel main = new JPanel();

        int gap = Registry.getGap();
        ELayout layout = new ELayout(6, 10, Registry.getDotsPerSquare(), gap);
        main.setLayout(layout);
        main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

        JLabel jlbId = new JLabel("Id:");
        layout.setConstraints(jlbId, new EConstaint(1, 1, 3, 1));
        main.add(jlbId);

        layout.setConstraints(jtfId, new EConstaint(1, 4, 3, 1));
        jtfId.setEnabled(false);
        jtfId.setEditable(false);
        main.add(jtfId);

        JLabel jlbName = new JLabel("Name:");
        layout.setConstraints(jlbName, new EConstaint(2, 1, 3, 1));
        main.add(jlbName);

        layout.setConstraints(jtfName, new EConstaint(2, 4, 7, 1));
        main.add(jtfName);

        JLabel jlbBudget = new JLabel("DOP:");
        layout.setConstraints(jlbBudget, new EConstaint(3, 1, 3, 1));
        main.add(jlbBudget);

        layout.setConstraints(jtfDtOfPurchase, new EConstaint(3, 4, 3, 1));
        main.add(jtfDtOfPurchase);

        JLabel jlbPrice = new JLabel("Price:");
        layout.setConstraints(jlbPrice, new EConstaint(4, 1, 3, 1));
        main.add(jlbPrice);

        layout.setConstraints(jtfPrice, new EConstaint(4, 4, 3, 1));
        main.add(jtfPrice);

        JLabel jlbNote = new JLabel("Note:");
        layout.setConstraints(jlbNote, new EConstaint(5, 1, 3, 1));
        main.add(jlbNote);

        layout.setConstraints(jtfNote, new EConstaint(5, 4, 7, 1));
        main.add(jtfNote);

        layout.setConstraints(jbClear, new EConstaint(6, 5, 3, 1));
        main.add(jbClear);

        layout.setConstraints(jbSave, new EConstaint(6, 8, 3, 1));
        main.add(jbSave);

        setLayout(new BorderLayout());
        add(main, BorderLayout.CENTER);

        setResizable(false);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setModal(true);
        pack();
        setLocationRelativeTo(parent);
    }
}
