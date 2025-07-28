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

public class FormBuckets extends JDialog {
  private static final Logger logger = Logger.getLogger(FormBuckets.class.getName());

  private JButton jbNew = new JButton("New");
  private JButton jbEdit = new JButton("Edit");
  private JButton jbRefill = new JButton("Refill");
  private BucketsTableModel bucketsTableModel = new BucketsTableModel();
  private JTable table = new JTable(bucketsTableModel);
  private JLabel lbStatus = new JLabel("Status ...");

  private int gap = Config.getGap();
  private java.util.List<Bucket> list = new ArrayList<>();
  private FormBucket formBucket;
  private boolean init = false;
  private int lastSelectBk = -1;
  private FormMenu parent;

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
      e.printStackTrace();
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

  public void doBuckRefill() throws SQLException {
    DAOTransaction daoTransaction = DAOTransaction.getInstance();
    LocalDate dt = LocalDate.now();
    for (int i = 0; i < bucketsTableModel.getRowCount(); i++) {
      Bucket bucket = bucketsTableModel.getBucket(i);
      logger.info("Refilling " + bucket.name + " --");
      Transaction tx = new Transaction();
      tx.bucket = bucket.name;
      tx.amount = bucket.budget.multiply(new BigDecimal(Config.getRefillFactor()));
      tx.note = "Refill";
      tx.txDate = java.sql.Date.valueOf(
        (dt.getDayOfMonth() < 15) ? dt.withDayOfMonth(1) : dt.withDayOfMonth(15));
      daoTransaction.save(tx);
      logger.info("TX Saved --");
      refresh();
      parent.bkUpdate(bucket.id);
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
          e.printStackTrace();
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
    main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    btnPanel.add(jbNew);
    btnPanel.add(jbEdit);
    btnPanel.add(jbRefill);
    main.add(btnPanel, BorderLayout.NORTH);

    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
