package home.ejaz.ledger.forms.bucket;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.models.Transaction;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

public class FormBucketTransfer extends JDialog {
    private static final Logger logger = Logger.getLogger(FormBucketTransfer.class.getName());

    private final JComboBox<String> jcFromBucket = new JComboBox<>();
    private final JComboBox<String> jcToBucket = new JComboBox<>();
    private final JTextField jtfAmount = new JTextField();

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

        // Always repopulate combo boxes each time
        SwingUtilities.invokeLater(() -> {
            updateComboBox(jcFromBucket);
            updateComboBox(jcToBucket);
        });
    }

    /**
     * Clears and repopulates the given combo box with the latest buckets.
     */
    private void updateComboBox(JComboBox<String> comboBox) {
        // Use a new model for clean refresh
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();

        getBuckets().forEach(bucket -> {
            logger.info("Adding " + bucket.name);
            model.addElement(bucket.name);
        });

        comboBox.setModel(model); // Automatically refreshes UI
    }

    private java.util.List<Bucket> getBuckets() {
        java.util.List<Bucket> buckets = new ArrayList<>();
        DAOBucket daoBucket = DAOBucket.getInstance();

        try {
            logger.info("Get buckets for " + Registry.getAcctId());
            buckets.addAll(daoBucket.getBuckets(Registry.getAcctId()));
        } catch (Exception e) {
            logger.warn("ERR", e);
        }

        return buckets;
    }

    private void doSave() {
        DAOBucket daoBucket = DAOBucket.getInstance();
        DAOTransaction daoTransaction = DAOTransaction.getInstance();

        try {
            int acctId = Registry.getAcctId();
            Bucket fromBuck = daoBucket.getBucket(acctId, Objects.requireNonNull(jcFromBucket.getSelectedItem()).toString());
            if (fromBuck == null) {
                throw new Exception("Invalid From bucket!");
            }
            Bucket toBuck = daoBucket.getBucket(acctId, Objects.requireNonNull(jcToBucket.getSelectedItem()).toString());
            if (toBuck == null) {
                throw new Exception("Invalid To bucket!");
            }
            BigDecimal amount = new BigDecimal(jtfAmount.getText());
            if (fromBuck.balance.doubleValue() < amount.doubleValue()) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Insufficient funds!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            daoTransaction.transfer(fromBuck.name, toBuck.name, amount);
            clear();
            setVisible(false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    parent,          // your JFrame, JPanel, or null
                    e.getMessage(),  // message
                    "Error",                  // title bar text
                    JOptionPane.ERROR_MESSAGE // icon type
            );
        }
    }

    private void doClear() {
    }

    public void clear() {
        jtfAmount.setText("");
        jcFromBucket.removeAllItems();
        jcToBucket.removeAllItems();
    }

    public FormBucketTransfer(JFrame parent) {
        super(parent);

        init();

        JPanel main = new JPanel();

        int gap = Registry.getGap();
        ELayout layout = new ELayout(4, 10, Registry.getDotsPerSquare(), gap);
        main.setLayout(layout);
        main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

        JLabel jlbId = new JLabel("From:");
        layout.setConstraints(jlbId, new EConstaint(1, 1, 3, 1));
        main.add(jlbId);

        layout.setConstraints(jcFromBucket, new EConstaint(1, 4, 7, 1));
        main.add(jcFromBucket);

        JLabel jlbName = new JLabel("To:");
        layout.setConstraints(jlbName, new EConstaint(2, 1, 3, 1));
        main.add(jlbName);

        layout.setConstraints(jcToBucket, new EConstaint(2, 4, 7, 1));
        main.add(jcToBucket);

        JLabel jlbNote = new JLabel("Amount:");
        layout.setConstraints(jlbNote, new EConstaint(3, 1, 3, 1));
        main.add(jlbNote);

        layout.setConstraints(jtfAmount, new EConstaint(3, 4, 7, 1));
        main.add(jtfAmount);

        layout.setConstraints(jbClear, new EConstaint(4, 5, 3, 1));
        main.add(jbClear);

        layout.setConstraints(jbSave, new EConstaint(4, 8, 3, 1));
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
