package home.ejaz.ledger.forms.bucket;

import home.ejaz.ledger.Config;
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

public class FormBucket extends JDialog {
  private static final Logger logger = Logger.getLogger(FormBucket.class.getName());

  private final JTextField jtfId = new JTextField();
  private final JTextField jtfName = new JTextField();
  private final JTextField jtfBudget = new JTextField();
  private final JTextField jtfRefFac = new JTextField();
  private final JButton jbClear = new JButton("Clear");
  private final JButton jbSave = new JButton("Save");
  private boolean init = false;

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
    jtfRefFac.setText(bucket.refill == null ? "" : bucket.refill.toString());
  }

  public Bucket getBucket() {
    Bucket bucket = new Bucket();
    bucket.id = jtfId.getText().isEmpty() ? null : Integer.valueOf(jtfId.getText());
    bucket.name = jtfName.getText().trim().isEmpty() ? null : jtfName.getText().trim();
    bucket.budget = jtfBudget.getText().trim().isEmpty() ? null : new BigDecimal(jtfBudget.getText().trim());
    bucket.refill = jtfRefFac.getText().trim().isEmpty() ? null: Double.parseDouble(jtfRefFac.getText());

    return bucket;
  }

  public FormBucket(JDialog parent) {
    super(parent);

    init();

    JPanel main = new JPanel();

    int gap = Config.getGap();
    ELayout layout = new ELayout(5, 10, Config.getDotsPerSquare(), gap);
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

    layout.setConstraints(jtfRefFac, new EConstaint(4, 4, 3, 1));
    main.add(jtfRefFac);

    layout.setConstraints(jbClear, new EConstaint(5, 5, 3, 1));
    main.add(jbClear);

    layout.setConstraints(jbSave, new EConstaint(5, 8, 3, 1));
    main.add(jbSave);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(main, BorderLayout.CENTER);

    setTitle("New/Edit Bucket");
    setResizable(false);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setLocationRelativeTo(null);
    pack();
    setModal(true);
  }
}
