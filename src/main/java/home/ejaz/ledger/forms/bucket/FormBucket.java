package home.ejaz.ledger.forms.bucket;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import home.ejaz.ledger.models.Bucket;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static home.ejaz.ledger.util.DateUtils.getNextRun;

public class FormBucket extends JDialog {
    private static final Logger logger = Logger.getLogger(FormBucket.class.getName());

    private final JTextField jtfId = new JTextField();
    private final JTextField jtfName = new JTextField();
    private final JTextField jtfBudget = new JTextField();
    private final JTextField jtfRefillSchd = new JTextField();
    private final JButton jbClear = new JButton("Clear");
    private final JButton jbSave = new JButton("Save");
    private boolean init = false;
    private JFrame parent;

    public void init() {
        clear();
        jtfId.setEnabled(false);
        jtfId.setEditable(false);

        if (!init) {
            jbSave.addActionListener(al -> doSave());
            jbClear.addActionListener(al -> doClear());

            KeyListener kl = new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        doSave();
                    }
                }
            };

            jtfName.addKeyListener(kl);
            jtfBudget.addKeyListener(kl);

            init = true;
        }
    }


    private void doSave() {
        if (jtfName.getText().isEmpty()) {
            jtfName.setOpaque(true);
            jtfName.setBackground(Color.RED);
            return;
        }
        if (jtfBudget.getText().isEmpty()) {
            jtfBudget.setOpaque(true);
            jtfBudget.setBackground(Color.RED);
            return;
        }
        Bucket bucket = getBucket();
        try {
            if (bucket.refillSchedule != null) {
                bucket.nextRefill = getNextRun(jtfRefillSchd.getText(), new Date());
            }
            DAOBucket.getInstance().save(bucket);
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

        jtfBudget.setText("");
        jtfBudget.setOpaque(true);
        jtfBudget.setBackground(Color.WHITE);
    }

    public void clear() {
        jtfId.setText("");
        jtfName.setText("");
        jtfBudget.setText("");
    }

    public void setBucket(Bucket bucket) {
        jtfId.setText(bucket.id == null ? "" : bucket.id.toString());
        jtfName.setText(bucket.name == null ? "" : bucket.name);
        jtfBudget.setText(bucket.budget == null ? "" : bucket.budget.toString());
        jtfRefillSchd.setText(bucket.refillSchedule == null ? "" : bucket.refillSchedule);
    }

    public Bucket getBucket() {
        Bucket bucket = new Bucket();
        bucket.id = jtfId.getText().isEmpty() ? null : Integer.valueOf(jtfId.getText());
        bucket.name = jtfName.getText().trim().isEmpty() ? null : jtfName.getText().trim();
        bucket.budget = jtfBudget.getText().trim().isEmpty() ? null : new BigDecimal(jtfBudget.getText().trim());
        bucket.refillSchedule = jtfRefillSchd.getText().trim().isEmpty() ? null : jtfRefillSchd.getText();
        bucket.acctId = Registry.getAcctId();

        return bucket;
    }

    public FormBucket(JFrame parent) {
        super(parent);

        init();

        JPanel main = new JPanel();

        int gap = Registry.getGap();
        ELayout layout = new ELayout(5, 10, Registry.getDotsPerSquare(), gap);
        main.setLayout(layout);
        main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

        JLabel jlbId = new JLabel("Id:");
        layout.setConstraints(jlbId, new EConstaint(1, 1, 3, 1));
        main.add(jlbId);

        layout.setConstraints(jtfId, new EConstaint(1, 4, 3, 1));
        main.add(jtfId);

        JLabel jlbName = new JLabel("Name:");
        layout.setConstraints(jlbName, new EConstaint(2, 1, 3, 1));
        main.add(jlbName);

        layout.setConstraints(jtfName, new EConstaint(2, 4, 7, 1));
        main.add(jtfName);

        JLabel jlbBudget = new JLabel("Budget:");
        layout.setConstraints(jlbBudget, new EConstaint(3, 1, 3, 1));
        main.add(jlbBudget);

        layout.setConstraints(jtfBudget, new EConstaint(3, 4, 3, 1));
        main.add(jtfBudget);

        JLabel jlbRefFactor = new JLabel("Ref %:");
        layout.setConstraints(jlbRefFactor, new EConstaint(4, 1, 3, 1));
        main.add(jlbRefFactor);

        layout.setConstraints(jtfRefillSchd, new EConstaint(4, 4, 3, 1));
        main.add(jtfRefillSchd);

        layout.setConstraints(jbClear, new EConstaint(5, 5, 3, 1));
        main.add(jbClear);

        layout.setConstraints(jbSave, new EConstaint(5, 8, 3, 1));
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
