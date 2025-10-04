package home.ejaz.ledger.forms.accounts;

import home.ejaz.ledger.BucketsListener;
import home.ejaz.ledger.Config;
import home.ejaz.ledger.FormMenu;
import home.ejaz.ledger.dao.DAOAccounts;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.forms.bucket.FormBucket;
import home.ejaz.ledger.models.*;
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class FormAccounts extends JPanel {
  private static final Logger logger = Logger.getLogger(FormAccounts.class.getName());

  private final AccountsTableModel acctsTableModel = new AccountsTableModel();
  private final JTable table = new JTable(acctsTableModel);
  private final JButton select = new JButton("Select");
  private final java.util.List<Account> accounts = new ArrayList<>();
  private int lastSelectAcctId = -1;
  private final JFrame parent;
  private boolean init;

  BiFunction<Account, Integer, Boolean> selectByRow =
    (acct, index) -> index == table.getSelectedRow();

  BiFunction<Account, Integer, Boolean> selectById =
    (acct, index) -> acct.id == Config.getAcctId();


  /* Common Accounts table update */
  private void updateSelection(BiFunction<Account, Integer, Boolean> selector) {
    for (int i = 0; i < accounts.size(); i++) {
      Account acct = acctsTableModel.getAccount(i);
      acct.selected = selector.apply(acct, i);
      if (acct.selected) {
        lastSelectAcctId = acct.id;
        Config.setAcctId(acct.id);
        Config.setTitle(acct.name);
        Config.getBucketsListener().acctSelected(acct.id);
      }
    }
    acctsTableModel.fireTableDataChanged();
  }

  /* This method is called when bucket/transaction updates */
  private void refresh() {
    accounts.clear();
    accounts.addAll(DAOAccounts.getInstance().getAccounts());
    for (Account acct : accounts) {
      acct.selected = (acct.id == lastSelectAcctId);
    }
    acctsTableModel.setAccounts(accounts);
    updateSelection(selectById);
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

      this.select.addActionListener(l -> {
        if (table.getSelectedRow() != -1) {
          updateSelection(selectByRow);
        }
      });

      init = true;
    }
  }

  public FormAccounts(JFrame parent) {
    this.parent = parent;

    init();

    JPanel main = new JPanel();
    main.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
    main.setLayout(new BorderLayout(3, 3));
    main.add(new JLabel("Your Accounts (selected marked *):"), BorderLayout.SOUTH);

    FlowLayout fl = new FlowLayout(FlowLayout.CENTER);
    JPanel btnPanel = new JPanel(fl);
    btnPanel.add(this.select);
    main.add(btnPanel, BorderLayout.NORTH);

    table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    table.setRowHeight(Config.getDotsPerSquare());
    table.setIntercellSpacing(new Dimension(5, 5));
    table.getColumnModel().getColumn(0).setMaxWidth(20);
    table.getColumnModel().getColumn(1).setMaxWidth(50);
    JScrollPane jsp = new JScrollPane(table);
    main.add(jsp, BorderLayout.CENTER);

    setLayout(new BorderLayout());
    add(main, BorderLayout.CENTER);
  }
}
