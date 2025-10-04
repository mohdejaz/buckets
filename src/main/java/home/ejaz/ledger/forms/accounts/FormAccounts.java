package home.ejaz.ledger.forms.accounts;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOAccounts;
import home.ejaz.ledger.models.*;
import home.ejaz.ledger.util.CellRenderer;
import home.ejaz.ledger.util.TableUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.BiFunction;

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
    (acct, index) -> acct.id == Registry.getAcctId();


  /* Common Accounts table update */
  private void updateSelection(BiFunction<Account, Integer, Boolean> selector) {
    for (int i = 0; i < accounts.size(); i++) {
      Account acct = acctsTableModel.getAccount(i);
      acct.selected = selector.apply(acct, i);
      if (acct.selected) {
        lastSelectAcctId = acct.id;
        Registry.setAcctId(acct.id);
        Registry.setTitle(acct.name);
        Registry.getBucketsListener().acctSelected(acct.id);
      }
    }
    acctsTableModel.fireTableDataChanged();
  }

  /* This method is called when bucket/transaction updates */
  private void refresh() {
    accounts.clear();
    accounts.addAll(DAOAccounts.getInstance().getAccounts(Registry.getUserId()));
    for (Account acct : accounts) {
      acct.selected = (acct.id == lastSelectAcctId);
    }
    acctsTableModel.setAccounts(accounts);
    updateSelection(selectById);
  }

  public void init() {
    refresh();

    if (!init) {
      TableUtils.formatTable(table);

      this.table.getColumnModel().getColumn(0).setMinWidth(Registry.getGutterSize());
      this.table.getColumnModel().getColumn(0).setMaxWidth(Registry.getGutterSize());

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
    // main.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
    main.setLayout(new BorderLayout());
    main.add(new JLabel("Your Accounts (selected marked *):"), BorderLayout.SOUTH);

    FlowLayout fl = new FlowLayout(FlowLayout.CENTER);
    JPanel btnPanel = new JPanel(fl);
    btnPanel.add(this.select);
    main.add(btnPanel, BorderLayout.NORTH);

    table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    table.getColumnModel().getColumn(0).setMinWidth(25);
    table.getColumnModel().getColumn(0).setMaxWidth(25);
    table.getColumnModel().getColumn(0).setPreferredWidth(25);
    table.getColumnModel().getColumn(1).setMaxWidth(50);
    JScrollPane jsp = new JScrollPane(table);
    main.add(jsp, BorderLayout.CENTER);

    setLayout(new BorderLayout());
    add(main, BorderLayout.CENTER);
  }
}
