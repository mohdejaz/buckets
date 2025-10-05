package home.ejaz.ledger;

import home.ejaz.ledger.forms.accounts.FormAccounts;
import home.ejaz.ledger.forms.bucket.FormBuckets;
import home.ejaz.ledger.forms.calc.FormCalc;
import home.ejaz.ledger.forms.transaction.FormTransactions;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.InputStreamReader;
import java.io.StringReader;

/**
 * Main form with menu & Accounts table.
 * Allows user to open Buckets, Transactions & Calculator screens.
 *
 * @author Ejaz Mohammed
 * @since 1.0
 */
public class FormMenu extends JFrame implements BucketsListener {
  private static final Logger logger = Logger.getLogger(FormMenu.class.getName());

  private JMenuBar mb = new JMenuBar();
  private JMenu mMenu = new JMenu("Menu");
  private JMenuItem miAccounts = new JMenuItem("Accounts");
  private JMenuItem miBuckets = new JMenuItem("Buckets");
  private JMenuItem miTransactions = new JMenuItem("Transactions");
  private JMenuItem miCalc = new JMenuItem("Calculator");
  private JMenuItem miExit = new JMenuItem("Exit");
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel cardPanel = new JPanel(cardLayout);
  private final JLabel cardTitle = new JLabel("Title ...");
  private FormBuckets formBuckets;
  private FormTransactions formTransactions;
  private FormCalc formCalc;
  private FormAccounts formAccounts;
  private boolean init = false;

  private void init() {
    if (!init) {
      Registry.setBucketsListener(this);

      cardTitle.setText("> Welcome");
      JLabel lb = new JLabel(Registry.getWelcomeMessage());
      lb.setVerticalAlignment(JLabel.TOP);
      cardPanel.add(lb, "Welcome");

      formAccounts = new FormAccounts(this);
      cardPanel.add(formAccounts, "Accounts");
      miAccounts.addActionListener(al -> {
        formAccounts.init();
        cardTitle.setText("> Accounts");
        cardLayout.show(cardPanel, "Accounts");
      });

      formBuckets = new FormBuckets(this);
      cardPanel.add(formBuckets, "Buckets");
      miBuckets.addActionListener(al -> {
        formBuckets.init();
        cardTitle.setText("> Buckets");
        cardLayout.show(cardPanel, "Buckets");
      });

      formTransactions = new FormTransactions(this);
      cardPanel.add(formTransactions, "Transactions");
      miTransactions.addActionListener(al -> {
        formTransactions.init();
        cardTitle.setText("> Transactions");
        cardLayout.show(cardPanel, "Transactions");
      });

      formCalc = new FormCalc(this);
      cardPanel.add(formCalc, "Calculator");
      miCalc.addActionListener(e -> {
        formCalc.init();
        cardTitle.setText("> Calculator");
        cardLayout.show(cardPanel, "Calculator");
      });

      miExit.addActionListener(al -> System.exit(0));
      init = true;
    }
  }

  public FormMenu() {
    super();

    init();

    mMenu.setMnemonic('M');

    mMenu.add(miAccounts);
    miAccounts.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK));

    mMenu.add(miBuckets);
    miBuckets.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK));

    mMenu.add(miTransactions);
    miTransactions.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.ALT_DOWN_MASK));

    mMenu.add(miCalc);
    miCalc.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK));

    mMenu.addSeparator();

    mMenu.add(miExit);
    miExit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK));

    mb.add(mMenu);

    JPanel main = new JPanel();
    main.setLayout(new BorderLayout(3,3));
    cardTitle.setBackground(Color.GRAY);
    cardTitle.setForeground(Color.WHITE);
    cardTitle.setOpaque(true);
    cardTitle.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
    main.add(cardTitle, BorderLayout.NORTH);
    main.add(cardPanel, BorderLayout.CENTER);
    main.setBorder(BorderFactory.createEmptyBorder(7,7,7,7));

    getContentPane().add(main);
    setJMenuBar(mb);

    setTitle(Registry.getTitle());
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null);
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
    // this.refresh();
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
    // this.refresh();
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
    // this.refresh();
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
    // this.refresh();
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
    // this.refresh();
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
    // this.refresh();
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
    setTitle(Registry.getTitle());
    if (this.formTransactions != null) {
      this.formTransactions.init();
    }
    if (this.formBuckets != null) {
      this.formBuckets.init();
    }
  }

  @Override
  public void acctAdded(int id) {
  }

  @Override
  public void acctUpdated(int id) {
  }

  @Override
  public void acctDeleted(int id) {
  }
}
