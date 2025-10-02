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

  private void init() {
    if (!init) {
      accounts.clear();
      accounts.addAll(DAOAccounts.getInstance().getAccounts());
      if (!accounts.isEmpty()) {
        acctsTableModel.setAccounts(accounts);
        accounts.get(0).selected = true;
        Config.setAcctId(accounts.get(0).id);
        this.acctSelected(accounts.get(0).id);
      }

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
        int row = table.getSelectedRow();
        if (row != -1) {
          logger.info("selected row: " + row);
          for (int i = 0; i < accounts.size(); i++) {
            Account acct = acctsTableModel.getAccount(i);
            acct.selected = i == row;
            if (acct.selected) {
              Config.setAcctId(accounts.get(i).id);
              this.acctSelected(accounts.get(i).id);
            }
          }
          acctsTableModel.fireTableDataChanged();
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

    JPanel main = getJPanel();
    main.setLayout(new BorderLayout(3, 3));
    main.add(new JLabel("Your Accounts (selected account marked by *):"), BorderLayout.NORTH);

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
    setSize(600, 300);
    // pack();
    setVisible(true);
  }

  private JPanel getJPanel() {
    JPanel main = new JPanel();
    main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    main.setLayout(new BorderLayout());

    JLabel message = new JLabel(
      "<html>" +
        "<b>Welcome to budget tool!</b>" +
        "<br>Use menu to manage buckets,transactions and calculator." +
        "<br>You can exit by Menu > Exit or clicking on X on top right corner." +
        "<br><br>Feel free to close child windows, they will retain their state." +
        "</html>");
    main.add(message);
    return main;
  }

  @Override
  public void txAdded(long id) {
    logger.info("txAdded --");
    this.formBuckets.init();
  }

  @Override
  public void txUpdate(long id) {
    logger.info("txUpdate --");
    this.formBuckets.init();
  }

  @Override
  public void txDelete(long id) {
    logger.info("txDelete --");
    this.formBuckets.init();
  }

  @Override
  public void bkAdded(int id) {
    logger.info("bkAdded --");
    this.formTransactions.init();
  }

  @Override
  public void bkUpdate(int id) {
    logger.info("bkUpdate --");
    this.formTransactions.init();
  }

  @Override
  public void bkDelete(int id) {
    logger.info("bkDelete --");
    this.formTransactions.init();
  }

  @Override
  public void acctSelected(int id) {
    logger.info("acctSelected --");
    if (this.formTransactions != null) {
      this.formTransactions.init();
    }
    if (this.formBuckets != null) {
      this.formBuckets.init();
    }
  }
}
