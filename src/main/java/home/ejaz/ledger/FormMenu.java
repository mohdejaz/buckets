package home.ejaz.ledger;

import home.ejaz.ledger.forms.bucket.FormBuckets;
import home.ejaz.ledger.forms.calc.FormCalc;
import home.ejaz.ledger.forms.transaction.FormTransactions;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;

public class FormMenu extends JFrame implements LedgerListener {
  private static final Logger logger = Logger.getLogger(FormMenu.class.getName());

  private JMenuBar mb = new JMenuBar();
  private JMenu mMenu = new JMenu("Menu");
  private JMenuItem miBuckets = new JMenuItem("Buckets");
  private JMenuItem miTransactions = new JMenuItem("Transactions");
  private JMenuItem miCalc = new JMenuItem("Calculator");
  private JMenuItem miExit = new JMenuItem("Exit");

//  private JButton jbBuckets = new JButton("Buckets");
//  private JButton jbTransactions = new JButton("Transactions");
//  private JButton jbCalc = new JButton("Calculator");
//  private JButton jbExit = new JButton("Exit");

  private int gap = Config.getGap();
  private FormBuckets formBuckets;
  private FormTransactions formTransactions;
  private FormCalc formCalc;
  private boolean init = false;

  private void init() {
    if (!init) {
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

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(main, BorderLayout.CENTER);

    setJMenuBar(mb);

    setTitle("Budget");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setAlwaysOnTop(true);
    setSize(150, 200);
    pack();
    setVisible(true);
  }

  private JPanel getJPanel() {
    JPanel main = new JPanel();
    main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    main.setLayout(new BorderLayout());

    JLabel message = new JLabel(
      "<html>" +
        "<b>Welcome to Ejaz family budget tool!</b>" +
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
}
