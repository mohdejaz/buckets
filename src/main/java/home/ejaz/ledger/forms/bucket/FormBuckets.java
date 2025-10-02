package home.ejaz.ledger.forms.bucket;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.FormMenu;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.models.BucketsTableModel;
import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.util.CellRenderer;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

public class FormBuckets extends JDialog {
  private static final Logger logger = Logger.getLogger(FormBuckets.class.getName());

  private final JButton jbNew = new JButton("New");
  private final JButton jbEdit = new JButton("Edit");
  private final JButton jbRefill = new JButton("Refill");
  private final JButton jbReset = new JButton("Reset");

  private final BucketsTableModel bucketsTableModel = new BucketsTableModel();
  private final JTable table = new JTable(bucketsTableModel);
  private final JLabel lbStatus = new JLabel("Status ...");

  private final java.util.List<Bucket> list = new ArrayList<>();
  private FormBucket formBucket;
  private boolean init = false;
  private int lastSelectBk = -1;
  private final FormMenu parent;

  private void refresh() {
    try {
      this.list.clear();
      this.list.addAll(DAOBucket.getInstance().getBuckets());
      this.bucketsTableModel.setBuckets(list);
      BigDecimal balance = BigDecimal.ZERO;
      for (int row = 0; row < this.list.size(); row++) {
        Bucket bucket = this.list.get(row);
        balance = balance.add(bucket.balance);
        if (bucket.id == lastSelectBk) {
          this.table.addRowSelectionInterval(row, row);
          break;
        }
      }
      lbStatus.setText(" Balance: " + balance);
    } catch (Exception e) {
      logger.warn("Error", e);
      System.exit(1);
    }
  }

  private void doBuckAdd() {
    formBucket.init();
    formBucket.setVisible(true);
    refresh();
    parent.bkAdded(-1);
  }

  private void doBuckEdit() {
    Set<Integer> selectedRows = Arrays.stream(this.table.getSelectedRows()).boxed().collect(Collectors.toSet());
    if (selectedRows.size() != 1) {
      JOptionPane.showMessageDialog(
        this, // Parent component (can be null for a default Frame)
        "Zero/1+ rows selected for refill. Please try again.", // The message to display
        "Error", // The title of the dialog box
        JOptionPane.ERROR_MESSAGE // The message type, which determines the icon and style
      );
    }

    int row = table.getSelectedRow();
    if (row != -1) {
      formBucket.init();
      Bucket bucket = bucketsTableModel.getBucket(row);
      this.lastSelectBk = bucket.id;
      formBucket.setBucket(bucket);
      // Set values
      formBucket.setVisible(true);
      refresh();
      parent.bkUpdate(-1);
    }
  }

  public void doBuckReset() throws SQLException {
    refresh();

    DAOTransaction daoTransaction = DAOTransaction.getInstance();
    LocalDate dt = LocalDate.now();
    for (int i = 0; i < bucketsTableModel.getRowCount(); i++) {
      Bucket bucket = bucketsTableModel.getBucket(i);
      if (bucket.balance.doubleValue() > 0) {
        logger.info("Resetting " + bucket.name + " --");
        Transaction tx = new Transaction();
        tx.bucket = bucket.name;
        tx.amount = bucket.balance.multiply(BigDecimal.valueOf(-1.0));
        tx.txDate = java.sql.Date.valueOf(dt);
        tx.note = "Reset";
        daoTransaction.save(tx);
        logger.info("TX Saved --");
        refresh();
        parent.bkUpdate(bucket.id);
      }
    }
  }

  public void doBuckRefill() throws SQLException {
    DAOTransaction daoTransaction = DAOTransaction.getInstance();
    LocalDate dt = LocalDate.now();
    Set<Integer> selectedRows = Arrays.stream(this.table.getSelectedRows()).boxed().collect(Collectors.toSet());
    if (selectedRows.isEmpty()) {
      JOptionPane.showMessageDialog(
        this, // Parent component (can be null for a default Frame)
        "No rows selected for refill. Please try again.", // The message to display
        "Error", // The title of the dialog box
        JOptionPane.ERROR_MESSAGE // The message type, which determines the icon and style
      );
    }
    for (int i = 0; i < bucketsTableModel.getRowCount(); i++) {
      if (selectedRows.contains(i)) {
        Bucket bucket = bucketsTableModel.getBucket(i);
        logger.info("Refilling " + bucket.name + " --");
        Transaction tx = new Transaction();
        tx.bucket = bucket.name;
        tx.amount = bucket.budget.multiply(BigDecimal.valueOf(bucket.refill));
        tx.note = "Refill";
        tx.txDate = java.sql.Date.valueOf(
          (dt.getDayOfMonth() < 15) ? dt.withDayOfMonth(1) : dt.withDayOfMonth(15));
        daoTransaction.save(tx);
        logger.info("TX Saved --");
        refresh();
        parent.bkUpdate(bucket.id);
      }
    }
  }

  public void init() {
    refresh();

    if (!init) {
      table.setShowGrid(true);
      table.setShowHorizontalLines(true);
      table.setShowVerticalLines(true);
      table.getTableHeader().setReorderingAllowed(false);
      // table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

      for (int i = 1; i < table.getColumnModel().getColumnCount(); i++) {
        table.getColumnModel().getColumn(i).setCellRenderer(new CellRenderer());
      }

      this.table.getColumnModel().getColumn(0).setMinWidth(Config.getGutterSize());
      this.table.getColumnModel().getColumn(0).setMaxWidth(Config.getGutterSize());

      this.table.getSelectionModel().addListSelectionListener(l -> {
        int row = table.getSelectedRow();
        if (row != -1) {
          Bucket bucket = bucketsTableModel.getBucket(row);
          this.lastSelectBk = bucket.id;
        }
      });

      this.table.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() >= 2) {
            doBuckEdit();
          }
        }
      });

      formBucket = new FormBucket(this);

      this.jbNew.addActionListener(al -> doBuckAdd());
      this.jbEdit.addActionListener(al -> doBuckEdit());
      this.jbRefill.addActionListener(al -> {
        try {
          doBuckRefill();
        } catch (SQLException e) {
          logger.warn("Error", e);
          throw new RuntimeException(e);
        }
      });
      this.jbReset.addActionListener(al -> {
        try {
          doBuckReset();
        } catch (SQLException e) {
          logger.warn("Error", e);
          throw new RuntimeException(e);
        }
      });

      init = true;
    }
  }

  public FormBuckets(FormMenu parent) {
    super(parent);

    this.parent = parent;

    init();

    JPanel main = new JPanel();
    main.setLayout(new BorderLayout());
    int gap = Config.getGap();
    main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    btnPanel.add(jbNew);
    btnPanel.add(jbEdit);
    btnPanel.add(jbRefill);
    btnPanel.add(jbReset);
    main.add(btnPanel, BorderLayout.NORTH);

    jbRefill.setEnabled(Config.enableRefill());
    jbReset.setEnabled(Config.enableReset());

    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setRowHeight(Config.getDotsPerSquare());
    JScrollPane jsp = new JScrollPane(table);
    main.add(jsp, BorderLayout.CENTER);

    main.add(lbStatus, BorderLayout.SOUTH);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(main, BorderLayout.CENTER);

    setTitle("Buckets");
    setSize(600, 500);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setLocationRelativeTo(null);
  }
}
