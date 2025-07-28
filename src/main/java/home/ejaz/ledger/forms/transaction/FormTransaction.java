package home.ejaz.ledger.forms.transaction;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.models.Transaction;
import org.apache.log4j.Logger;
import org.jdatepicker.DatePicker;
import org.jdatepicker.JDatePicker;
import org.jdatepicker.UtilDateModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

public class FormTransaction extends JDialog {
  private static final Logger logger = Logger.getLogger(FormTransaction.class.getName());

  private int gap = Config.getGap();
  private ELayout layout = new ELayout(5, 15, Config.getDotsPerSquare(), gap);
  private JTextField jtfId = new JTextField();
  private DatePicker picker = new JDatePicker(new UtilDateModel());
  private JComboBox jcBucket = new JComboBox();
  private JTextField jtfAmount = new JTextField();
  private JTextField jtfNote = new JTextField();
  private JButton jbClear = new JButton("Clear");
  private JButton jbSave = new JButton("Save");
  private Date lastDate = new Date();
  private boolean init = false;
  private boolean txAdded = false;
  private boolean txModified = false;

  private void refresh() {
    try {
      this.jcBucket.removeAllItems();
      for (Bucket bucket : DAOBucket.getInstance().getBuckets()) {
        this.jcBucket.addItem(bucket.name);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public void clear(boolean clearId) {
    clearError(jtfId);
    if (clearId) jtfId.setText("");
    clearError((JComponent) picker);
    clearError(jtfAmount);
    jtfAmount.setText("");
    clearError(jtfNote);
    jtfNote.setText("");
  }

  private void setError(JComponent jcomp) {
    jcomp.setOpaque(true);
    jcomp.setBackground(Color.RED);
  }

  private void clearError(JComponent jcomp) {
    jcomp.setOpaque(true);
    jcomp.setBackground(Color.WHITE);
  }

  private void doSave() {
    if (jtfAmount.getText().isEmpty()) {
      setError(jtfAmount);
      return;
    }
    if (jtfNote.getText().isEmpty()) {
      setError(jtfNote);
      return;
    }
    Transaction tx = getTransaction();
    try {
      DAOTransaction.getInstance().save(tx);
      lastDate = getDateFromPicker();
      if (jtfId.getText().isEmpty()) {
        this.txAdded = true;
      } else {
        this.txModified = true;
      }
      logger.info("txAdded=" + txAdded + ";txModified=" + txModified);
      clear(true);
      setVisible(false);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void init() {
    refresh();

    this.txAdded = false;
    this.txModified = false;
    jtfId.setEnabled(false);
    jtfId.setEditable(false);
    setDateToPicker(lastDate);

    if (!init) {
      KeyListener kl = new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            doSave();
          }
        }
      };

      jtfAmount.addKeyListener(kl);
      jtfNote.addKeyListener(kl);

      jbSave.addActionListener(al -> doSave());

      jbClear.addActionListener(al -> {
        clear(false);
      });

      init = true;
    }
  }

  private Date getDateFromPicker() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, picker.getModel().getYear());
    cal.set(Calendar.MONTH, picker.getModel().getMonth());
    cal.set(Calendar.DAY_OF_MONTH, picker.getModel().getDay());
    return cal.getTime();
  }

  private void setDateToPicker(Date date) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    picker.getModel().setDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
    picker.getModel().setSelected(true);
  }

  public Transaction getTransaction() {
    Transaction tx = new Transaction();
    tx.id = !jtfId.getText().isEmpty() ? Long.parseLong(jtfId.getText()) : null;
    tx.txDate = getDateFromPicker();
    tx.bucket = jcBucket.getSelectedItem().toString();
    tx.amount = !jtfAmount.getText().isEmpty() ? new BigDecimal(jtfAmount.getText()) : null;
    tx.note = jtfNote.getText();
    System.out.println("tx --> " + tx);
    return tx;
  }

  public void setTransaction(Transaction tx) {
    jtfId.setText(tx.id.toString());
    setDateToPicker(tx.txDate);
    jcBucket.setSelectedItem(tx.bucket);
    jtfAmount.setText(tx.amount.toString());
    jtfNote.setText(tx.note);
  }

  public FormTransaction(JDialog parent) {
    super(parent);

    init();

    JPanel main = new JPanel();

    main.setLayout(layout);
    main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    JLabel jlbId = new JLabel("Id:");
    layout.setConstraints(jlbId, new EConstaint(1, 1, 3, 1));
    main.add(jlbId);

    layout.setConstraints(jtfId, new EConstaint(1, 4, 2, 1));
    main.add(jtfId);

    JLabel jlbName = new JLabel("Date:");
    layout.setConstraints(jlbName, new EConstaint(2, 1, 3, 1));
    main.add(jlbName);

    picker.setTextEditable(true);
    picker.setShowYearButtons(true);
    picker.setTextEditable(false);
    layout.setConstraints((JComponent) picker, new EConstaint(2, 4, 6, 1));
    main.add((JComponent) picker);

    JLabel jlbBudget = new JLabel("Bucket:");
    layout.setConstraints(jlbBudget, new EConstaint(3, 1, 4, 1));
    main.add(jlbBudget);

    layout.setConstraints(jcBucket, new EConstaint(3, 4, 7, 1));
    main.add(jcBucket);

    JLabel jlbAmount = new JLabel("Amt:", JLabel.TRAILING);
    layout.setConstraints(jlbAmount, new EConstaint(3, 11, 2, 1));
    main.add(jlbAmount);

    layout.setConstraints(jtfAmount, new EConstaint(3, 13, 3, 1));
    main.add(jtfAmount);

    JLabel jlbNote = new JLabel("Note:");
    layout.setConstraints(jlbNote, new EConstaint(4, 1, 3, 1));
    main.add(jlbNote);

    layout.setConstraints(jtfNote, new EConstaint(4, 4, 12, 1));
    main.add(jtfNote);

    layout.setConstraints(jbClear, new EConstaint(5, 10, 3, 1));
    main.add(jbClear);

    layout.setConstraints(jbSave, new EConstaint(5, 13, 3, 1));
    main.add(jbSave);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(main, BorderLayout.CENTER);

    setTitle("Add/Edit Transaction");
    setResizable(false);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setLocationRelativeTo(null);
    pack();
    setModal(true);
  }
}
