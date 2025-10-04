package home.ejaz.ledger.forms.accounts;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOAccounts;
import home.ejaz.ledger.dao.DAOBucket;
import home.ejaz.ledger.layout.EConstaint;
import home.ejaz.ledger.layout.ELayout;
import home.ejaz.ledger.models.Account;
import home.ejaz.ledger.models.Bucket;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;

public class FormAccount extends JDialog {
  private static final Logger logger = Logger.getLogger(FormAccount.class.getName());

  private final JTextField jtfId = new JTextField();
  private final JTextField jtfName = new JTextField();
  private final JTextField jtfBalance = new JTextField();
  private final JButton jbClear = new JButton("Clear");
  private final JButton jbSave = new JButton("Save");
  private boolean init = false;
  private JFrame parent;

  public void init() {
    clear();

    if (!init) {
      jbSave.addActionListener(al -> doSave());
      jbClear.addActionListener(al -> doClear());

      KeyListener kl = new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            doSave();
          }
        }
      };

      jtfName.addKeyListener(kl);
      jtfBalance.addKeyListener(kl);

      init = true;
    }
  }

  private void doSave() {
    if (jtfName.getText().isEmpty()) {
      jtfName.setOpaque(true);
      jtfName.setBackground(Color.RED);
      return;
    }
    Account account = getAccount();
    try {
      DAOAccounts.getInstance().save(account);
      clear();
      this.setVisible(false);
    } catch (Exception e) {
      logger.warn("Error", e);
    }
  }

  private void doClear() {
    // jtfName.setText("");
    jtfName.setOpaque(true);
    jtfName.setBackground(Color.WHITE);
  }

  public void clear() {
    jtfId.setText("");
    jtfName.setText("");
    jtfBalance.setText("");
  }

  public void setAccount(Account account) {
    jtfId.setText(account.id == null ? "" : account.id.toString());
    jtfName.setText(account.name == null ? "" : account.name);
    jtfBalance.setText(account.balance == null ? "" : account.balance.toString());
  }

  public Account getAccount() {
    Account account = new Account();
    account.id = jtfId.getText().isEmpty() ? null : Integer.valueOf(jtfId.getText());
    account.name = jtfName.getText().trim().isEmpty() ? null : jtfName.getText().trim();
    account.balance = jtfBalance.getText().trim().isEmpty() ? null : new BigDecimal(jtfBalance.getText().trim());
    account.userId = Registry.getUserId();

    return account;
  }

  public FormAccount(JFrame parent) {
    super(parent);

    this.parent = parent;

    init();

    JPanel main = new JPanel();

    int gap = Registry.getGap();
    ELayout layout = new ELayout(4, 10, Registry.getDotsPerSquare(), gap);
    main.setLayout(layout);
    main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    JLabel jlbId = new JLabel("Id:");
    layout.setConstraints(jlbId, new EConstaint(1, 1, 3, 1));
    main.add(jlbId);

    layout.setConstraints(jtfId, new EConstaint(1, 4, 3, 1));
    jtfId.setEnabled(false);
    jtfId.setEditable(false);
    main.add(jtfId);

    JLabel jlbName = new JLabel("Name:");
    layout.setConstraints(jlbName, new EConstaint(2, 1, 3, 1));
    main.add(jlbName);

    layout.setConstraints(jtfName, new EConstaint(2, 4, 7, 1));
    main.add(jtfName);

    JLabel jlbBudget = new JLabel("Balance:");
    layout.setConstraints(jlbBudget, new EConstaint(3, 1, 3, 1));
    main.add(jlbBudget);

    layout.setConstraints(jtfBalance, new EConstaint(3, 4, 3, 1));
    jtfBalance.setEnabled(false);
    jtfBalance.setEditable(false);
    main.add(jtfBalance);

    layout.setConstraints(jbClear, new EConstaint(4, 5, 3, 1));
    main.add(jbClear);

    layout.setConstraints(jbSave, new EConstaint(4, 8, 3, 1));
    main.add(jbSave);

    setLayout(new BorderLayout());
    add(main, BorderLayout.CENTER);

    setResizable(false);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setModal(true);
    pack();
    setLocationRelativeTo(parent);
  }
}
