package home.ejaz.ledger;

import home.ejaz.ledger.forms.accounts.FormAccounts;
import home.ejaz.ledger.forms.bucket.FormBuckets;
import home.ejaz.ledger.forms.transaction.FormTransactions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Main form with menu & Accounts table.
 * Allows user to open Buckets, Transactions & Calculator screens.
 *
 * @author Ejaz Mohammed
 * @since 1.0
 */
public class FormMenu extends JFrame {
  private static final Logger logger = LogManager.getLogger(FormMenu.class.getName());

  private final JMenuBar mb = new JMenuBar();
  private final JMenu mMenu = new JMenu("Menu");
  private final JMenuItem miAccounts = new JMenuItem("Accounts");
  private final JMenuItem miBuckets = new JMenuItem("Buckets");
  private final JMenuItem miTransactions = new JMenuItem("Transactions");
  private final JMenuItem miExit = new JMenuItem("Exit");
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel cardPanel = new JPanel(cardLayout);
  private final JLabel cardTitle = new JLabel("Title ...");
  private FormBuckets formBuckets;
  private FormTransactions formTransactions;
  private FormAccounts formAccounts;
  private boolean init = false;

  private void init() {
    if (!init) {
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

    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null);
    setVisible(true);
  }
}
