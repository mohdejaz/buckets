package home.ejaz.ledger.forms.items;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.dao.DAOItem;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.models.Item;
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

public class FormItem extends JDialog {
  private static final Logger logger = Logger.getLogger(FormItem.class.getName());

  private int gap = Config.getGap();
  private ELayout layout = new ELayout(5, 15, Config.getDotsPerSquare(), gap);
  private JTextField jtfId = new JTextField();
  private DatePicker picker = new JDatePicker(new UtilDateModel());
  private JTextField jtfName = new JTextField();
  private JTextField jtfMerchant = new JTextField();
  private JTextField jtfBrand = new JTextField();
  private JTextField jtfQty = new JTextField();
  private JTextField jtfPrice = new JTextField();
  private JButton jbClear = new JButton("Clear");
  private JButton jbSave = new JButton("Save");
  private Date lastDate = new Date();
  private boolean init = false;
  private boolean itemAdded = false;
  private boolean itemModified = false;

  private void refresh() {
    try {
      this.jtfName.setText("");
      this.jtfMerchant.setText("N/A");
      this.jtfBrand.setText("N/A");
      this.jtfQty.setText("0.0");
      this.jtfPrice.setText("0.0");
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public void clear(boolean clearId) {
//    clearError(jtfId);
//    if (clearId) {
//      jtfId.setText("");
//    }
//
//    clearError((JComponent) picker);
//
//    clearError(jtfMerchant);
//    jtfMerchant.setText("");
//
//    clearError(jtfBrand);
//    jtfBrand.setText("");
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
    if (jtfMerchant.getText().isEmpty()) {
      setError(jtfMerchant);
      return;
    }
    if (jtfBrand.getText().isEmpty()) {
      setError(jtfBrand);
      return;
    }
    Transaction tx = getTransaction();
    try {
      DAOTransaction.getInstance().save(tx);
      lastDate = getDateFromPicker();
      if (jtfId.getText().isEmpty()) {
        this.itemAdded = true;
      } else {
        this.itemModified = true;
      }
      logger.info("txAdded=" + itemAdded + ";txModified=" + itemModified);
      clear(true);
      setVisible(false);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void init() {
    refresh();

    this.itemAdded = false;
    this.itemModified = false;
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

      jtfMerchant.addKeyListener(kl);
      jtfBrand.addKeyListener(kl);

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
    tx.bucket = jtfName.getSelectedItem().toString();
    tx.amount = !jtfMerchant.getText().isEmpty() ? new BigDecimal(jtfMerchant.getText()) : null;
    tx.note = jtfBrand.getText();
    System.out.println("tx --> " + tx);
    return tx;
  }

  public void setTransaction(Transaction tx) {
    jtfId.setText(tx.id.toString());
    setDateToPicker(tx.txDate);
    jtfName.setSelectedItem(tx.bucket);
    jtfMerchant.setText(tx.amount.toString());
    jtfBrand.setText(tx.note);
  }

  public FormItem(JDialog parent) {
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

    layout.setConstraints(jtfName, new EConstaint(3, 4, 7, 1));
    main.add(jtfName);

    JLabel jlbAmount = new JLabel("Amt:", JLabel.TRAILING);
    layout.setConstraints(jlbAmount, new EConstaint(3, 11, 2, 1));
    main.add(jlbAmount);

    layout.setConstraints(jtfMerchant, new EConstaint(3, 13, 3, 1));
    main.add(jtfMerchant);

    JLabel jlbNote = new JLabel("Note:");
    layout.setConstraints(jlbNote, new EConstaint(4, 1, 3, 1));
    main.add(jlbNote);

    layout.setConstraints(jtfBrand, new EConstaint(4, 4, 12, 1));
    main.add(jtfBrand);

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
