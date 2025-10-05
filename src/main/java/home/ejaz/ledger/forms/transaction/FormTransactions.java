package home.ejaz.ledger.forms.transaction;

import com.opencsv.CSVWriter;
import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.models.TransactionsTableModel;
import home.ejaz.ledger.util.TableUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class FormTransactions extends JPanel {
  private final JButton jbNew = new JButton("New");
  private final JButton jbEdit = new JButton("Edit");
  private final JButton jbDel = new JButton("Del");
  private final JButton jbPost = new JButton("Post");
  private final JButton jbUnPost = new JButton("UnPost");
  private final JButton jbRefresh = new JButton("Refresh");
  private final JButton jbExport = new JButton("Export");
  private final JTextField jtFilter = new JTextField("");
  private final JLabel lbStatus = new JLabel("Status ...");
  private final java.util.List<Transaction> list = new ArrayList<>();
  private final TransactionsTableModel txTableModel = new TransactionsTableModel();
  private final JTable table = new JTable(txTableModel);
  private FormTransaction formTransaction;
  private boolean init = false;
  private long lastSelectTx = -1;
  private final JFrame jframe;

  private void refresh() {
    try {
      this.list.clear();
      this.list.addAll(DAOTransaction.getInstance().getTransactions(Registry.getAcctId(), jtFilter.getText()));
      this.txTableModel.setTransactions(list);
      BigDecimal balance = BigDecimal.ZERO;
      for (int row = 0; row < this.list.size(); row++) {
        Transaction tx = this.txTableModel.getTransaction(row);
        balance = balance.add(tx.amount);
        if (tx.id == lastSelectTx) {
          this.table.addRowSelectionInterval(row, row);
        }
      }
      lbStatus.setText(" Balance: " + new DecimalFormat("###,###,###.00").format(balance));
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private void doTxDelete() {
    java.util.List<Transaction> delTxList = new ArrayList<>();
    for (int row : this.table.getSelectedRows()) {
      if (row != -1) {
        delTxList.add(this.txTableModel.getTransaction(row));
      }
    }
    for (Transaction tx : delTxList) {
      try {
        DAOTransaction.getInstance().delete(tx.id);
        Registry.getBucketsListener().txDelete(tx.id);
      } catch (SQLException e) {
        e.printStackTrace(System.err);
      }
    }
    refresh();
  }

  private void doTxAdd() {
    formTransaction.clear(true);
    formTransaction.init();
    formTransaction.setVisible(true);
    refresh();
    Registry.getBucketsListener().txAdded(-1);
  }

  private void doTxEdit() {
    formTransaction.clear(true);
    formTransaction.init();
    for (int row : table.getSelectedRows()) {
      Transaction tx = this.txTableModel.getTransaction(row);
      this.lastSelectTx = tx.id;
      formTransaction.setTransaction(tx);
      formTransaction.setVisible(true);
      Registry.getBucketsListener().txUpdate(-1);
    }
    refresh();
  }

  private void doTxPost() throws SQLException {
    DAOTransaction daoTransaction = DAOTransaction.getInstance();
    for (int row : table.getSelectedRows()) {
      Transaction tx = this.txTableModel.getTransaction(row);
      this.lastSelectTx = tx.id;
      daoTransaction.post(tx.id);
    }
    refresh();
  }

  private void doUnTxPost() throws SQLException {
    DAOTransaction daoTransaction = DAOTransaction.getInstance();
    for (int row : table.getSelectedRows()) {
      Transaction tx = this.txTableModel.getTransaction(row);
      this.lastSelectTx = tx.id;
      daoTransaction.unpost(tx.id);
    }
    refresh();
  }

  private void doExport() throws IOException {
    File file = File.createTempFile("ledger-", ".csv");

    try (FileWriter fw = new FileWriter(file); CSVWriter cw = new CSVWriter(fw)) {
      cw.writeNext(txTableModel.colNames);
      for (Transaction tx : this.list) {
        // new String[]{"Posted", "Id", "TxDate", "Bucket", "Amount", "Note"};
        cw.writeNext(new String[]{
          "" + tx.posted,
          "" + tx.id,
          "" + tx.txDate,
          tx.bucket,
          "" + tx.amount,
          tx.note
        });
      }
    }

    StringSelection stringSelection = new StringSelection(file.getAbsolutePath());
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(stringSelection, null);
    this.lbStatus.setText("Data saved in " + file.getAbsolutePath());
  }

  public void init() {
    refresh();

    if (!init) {
      TableUtils.formatTable(table);

      this.table.getSelectionModel().addListSelectionListener(l -> {
        int row = table.getSelectedRow();
        if (row != -1) {
          Transaction tx = this.txTableModel.getTransaction(row);
          this.lastSelectTx = tx.id;
        }
      });

      this.table.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() >= 2) {
            doTxEdit();
          }
        }
      });

      formTransaction = new FormTransaction(jframe);

      jbRefresh.addActionListener(al -> refresh());
      jbNew.addActionListener(al -> doTxAdd());
      jbEdit.addActionListener(al -> doTxEdit());
      jbDel.addActionListener(al -> doTxDelete());
      jbPost.addActionListener(al -> {
        try {
          doTxPost();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });
      jbUnPost.addActionListener(al -> {
        try {
          doUnTxPost();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });
      jbExport.addActionListener(e -> {
        try {
          doExport();
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      });
      jtFilter.addActionListener(e -> refresh());

      init = true;
    }
  }

  public FormTransactions(JFrame jframe) {
    this.jframe = jframe;

    init();

    JPanel main = new JPanel();
    main.setLayout(new BorderLayout());
    int gap = Registry.getGap();
    // main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, gap, gap));
    btnPanel.add(jbNew);
    btnPanel.add(jbEdit);
    btnPanel.add(jbDel);
    btnPanel.add(jbPost);
    btnPanel.add(jbUnPost);
    btnPanel.add(jbRefresh);
    btnPanel.add(jbExport);
    main.add(btnPanel, BorderLayout.NORTH);

    JPanel jp2 = new JPanel(new BorderLayout(3,3));
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    JScrollPane jsp = new JScrollPane(table);
    jp2.add(jsp, BorderLayout.CENTER);
    jp2.add(jtFilter, BorderLayout.SOUTH);
    main.add(jp2, BorderLayout.CENTER);

    main.add(lbStatus, BorderLayout.SOUTH);

    setLayout(new BorderLayout());
    add(main, BorderLayout.CENTER);
  }
}
