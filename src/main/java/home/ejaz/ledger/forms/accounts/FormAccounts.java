package home.ejaz.ledger.forms.accounts;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOAccounts;
import home.ejaz.ledger.models.*;
import home.ejaz.ledger.util.TableUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
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
  private final JButton jbNew = new JButton("New");
  private final JButton jbEdit = new JButton("Edit");
  private final java.util.List<Account> accounts = new ArrayList<>();
  private FormAccount formAccount;
  private final JFrame parent;
  private boolean init;
  private int lastTouchedAcctId = -1;

  BiFunction<Account, Integer, Boolean> selectByRow =
    (acct, index) -> index == table.getSelectedRow();

  BiFunction<Account, Integer, Boolean> selectById =
    (acct, index) -> acct.id == Registry.getAcctId();


  /* Common Accounts table update */
  private void updateSelection(BiFunction<Account, Integer, Boolean> selector) {
    int srow = -1;
    for (int row = 0; row < accounts.size(); row++) {
      Account acct = accounts.get(row);
      logger.info("A/c Id: " + acct.id + " Last selected A/c: " + lastTouchedAcctId);
      if (acct.id == lastTouchedAcctId) {
        logger.info("Add row selection --");
        srow = row;
      }
      acct.selected = selector.apply(acct, row);
      if (acct.selected) {
        Registry.setAcctId(acct.id);
        Registry.setTitle(acct.name);
        Registry.getBucketsListener().acctSelected(acct.id);
      }
    }
    acctsTableModel.fireTableDataChanged();
    if (srow != -1)
      this.table.addRowSelectionInterval(srow, srow);
  }

  /* This method is called when bucket/transaction updates */
  private void refresh() {
    accounts.clear();
    accounts.addAll(DAOAccounts.getInstance().getAccounts(Registry.getUserId()));
    acctsTableModel.setAccounts(accounts);
    updateSelection(selectById);
  }

  public void init() {
    refresh();

    if (!init) {
      TableUtils.formatTable(table);

      this.table.getColumnModel().getColumn(0).setMinWidth(Registry.getGutterSize());
      this.table.getColumnModel().getColumn(0).setMaxWidth(Registry.getGutterSize());

      formAccount = new FormAccount(parent);

      this.select.addActionListener(l -> doSelect());
      this.jbNew.addActionListener(al -> doAcctAdd());
      this.jbEdit.addActionListener(al -> doAcctEdit());

      init = true;
    }
  }

  private void doSelect() {
    if (table.getSelectedRow() != -1) {
      lastTouchedAcctId = accounts.get(table.getSelectedRow()).id;
      logger.info("Selected A/c: " + lastTouchedAcctId);
      updateSelection(selectByRow);
    }
  }

  private void doAcctAdd() {
    formAccount.init();
    formAccount.setVisible(true);
    refresh();
    Registry.getBucketsListener().acctAdded(-1);
  }

  private void doAcctEdit() {
    Set<Integer> selectedRows = Arrays.stream(this.table.getSelectedRows()).boxed().collect(Collectors.toSet());
    if (selectedRows.size() != 1) {
      JOptionPane.showMessageDialog(
        this, // Parent component (can be null for a default Frame)
        "Zero/1+ rows selected. Please try again.", // The message to display
        "Error", // The title of the dialog box
        JOptionPane.ERROR_MESSAGE // The message type, which determines the icon and style
      );
    }

    int row = table.getSelectedRow();
    if (row != -1) {
      formAccount.init();
      Account account = acctsTableModel.getAccount(row);
      this.lastTouchedAcctId = account.id;
      formAccount.setAccount(account);
      // Set values
      formAccount.setVisible(true);
      refresh();
      Registry.getBucketsListener().bkUpdate(-1);
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
    btnPanel.add(this.jbNew);
    btnPanel.add(this.jbEdit);
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
