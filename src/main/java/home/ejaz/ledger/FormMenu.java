package home.ejaz.ledger;

import home.ejaz.ledger.dao.DAOAccounts;
import home.ejaz.ledger.forms.bucket.FormBuckets;
import home.ejaz.ledger.forms.calc.FormCalc;
import home.ejaz.ledger.forms.transaction.FormTransactions;
import home.ejaz.ledger.models.Account;
import home.ejaz.ledger.models.AccountsTableModel;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.BiFunction;

/**
 * Main form with menu & Accounts table.
 * Allows user to open Buckets, Transactions & Calculator screens.
 *
 * @author Ejaz Mohammed
 * @since 1.0
 */
public class FormMenu extends JFrame implements LedgerListener {
  private static final Logger logger = Logger.getLogger(FormMenu.class.getName());

  private JMenuBar mb = new JMenuBar();
  private JMenu mMenu = new JMenu("Menu");
  private JMenuItem miBuckets = new JMenuItem("Buckets");
  private JMenuItem miTransactions = new JMenuItem("Transactions");
  private JMenuItem miCalc = new JMenuItem("Calculator");
  private JMenuItem miExit = new JMenuItem("Exit");
  private final AccountsTableModel acctsTableModel = new AccountsTableModel();
  private final JTable table = new JTable(acctsTableModel);
  private final JButton select = new JButton("Select");
  private final java.util.List<Account> accounts = new ArrayList<>();

  private FormBuckets formBuckets;
  private FormTransactions formTransactions;
  private FormCalc formCalc;
  private boolean init = false;
  private int lastSelectAcctId = -1;

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
        this.acctSelected(acct.id);
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

  private void init() {
    if (!init) {
      refresh();

      formBuckets = new FormBuckets(this);
      miBuckets.addActionListener(al -> {
        formBuckets.init();
        formBuckets.setVisible(true);
      });

      formTransactions = new FormTransactions(this);
      miTransactions.addActionListener(al -> {
        formTransactions.init();
        formTransactions.setVisible(true);
      });

      formCalc = new FormCalc(this);
      miCalc.addActionListener(e -> {
        formCalc.init();
        formCalc.setVisible(true);
      });

      this.select.addActionListener(l -> {
        if (table.getSelectedRow() != -1) {
          updateSelection(selectByRow);
        }
      });

      miExit.addActionListener(al -> System.exit(0));
      init = true;
    }
  }

  public FormMenu() {
    super();

    init();

    mMenu.setMnemonic('M');
    mMenu.add(miBuckets);
    miBuckets.setMnemonic('B');
    mMenu.add(miTransactions);
    miTransactions.setMnemonic('T');
    mMenu.add(miCalc);
    miCalc.setMnemonic('C');
    mMenu.addSeparator();
    mMenu.add(miExit);
    miExit.setMnemonic('x');

    mb.add(mMenu);

    JPanel main = new JPanel();
    main.setBorder(BorderFactory.createEmptyBorder(7,7,7,7));
    main.setLayout(new BorderLayout(3, 3));
    main.add(new JLabel("Your Accounts (selected marked *):"), BorderLayout.NORTH);

    table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    table.setRowHeight(Config.getDotsPerSquare());
    table.setIntercellSpacing(new Dimension(5, 5));
    table.getColumnModel().getColumn(0).setMaxWidth(20);
    table.getColumnModel().getColumn(1).setMaxWidth(50);
    JScrollPane jsp = new JScrollPane(table);
    main.add(jsp, BorderLayout.CENTER);

    FlowLayout fl = new FlowLayout(FlowLayout.TRAILING);
    JPanel btnPanel = new JPanel(fl);
    btnPanel.add(this.select);
    main.add(btnPanel, BorderLayout.SOUTH);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(main, BorderLayout.CENTER);

    setJMenuBar(mb);

    setTitle(Config.getTitle());
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setAlwaysOnTop(true);
    setSize(500, 300);
    // pack();
    setVisible(true);
  }

  /**
   * Listener method called when new Transaction added.
   * Refresh Accounts and Buckets forms.
   *
   * @param id - New Transaction Id
   */
  @Override
  public void txAdded(long id) {
    logger.info("txAdded --");
    this.formBuckets.init();
    this.refresh();
  }

  /**
   * Listener method called when new Transaction update.
   * Refresh Accounts and Buckets forms.
   *
   * @param id -  Transaction Id
   */
  @Override
  public void txUpdate(long id) {
    logger.info("txUpdate --");
    this.formBuckets.init();
    this.refresh();
  }

  /**
   * Listener method called when new Transaction deleted.
   * Refresh Accounts and Buckets forms.
   *
   * @param id -  Transaction Id
   */
  @Override
  public void txDelete(long id) {
    logger.info("txDelete --");
    this.formBuckets.init();
    this.refresh();
  }

  /**
   * Listener method called when new Bucket added.
   * Refresh Accounts and Transactions forms.
   *
   * @param id -  New Bucket Id
   */
  @Override
  public void bkAdded(int id) {
    logger.info("bkAdded --");
    this.formTransactions.init();
    this.refresh();
  }

  /**
   * Listener method called when new Bucket updated.
   * Refresh Accounts and Transactions forms.
   *
   * @param id -  Bucket Id
   */
  @Override
  public void bkUpdate(int id) {
    logger.info("bkUpdate --");
    this.formTransactions.init();
    this.refresh();
  }

  /**
   * Listener method called when new Bucket deleted.
   * Refresh Accounts and Transactions forms.
   *
   * @param id -  Bucket Id
   */
  @Override
  public void bkDelete(int id) {
    logger.info("bkDelete --");
    this.formTransactions.init();
    this.refresh();
  }

  /**
   * Listener method called new Account selected.
   * Refresh Buckets and Transactions forms.
   *
   * @param id -  Account Id
   */
  @Override
  public void acctSelected(int id) {
    logger.info("acctSelected --");
    setTitle(Config.getTitle());
    if (this.formTransactions != null) {
      this.formTransactions.init();
    }
    if (this.formBuckets != null) {
      this.formBuckets.init();
    }
  }
}
