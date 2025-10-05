package home.ejaz.ledger.models;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AccountsTableModel extends AbstractTableModel implements NumberModel {
  private final String[] colNames = new String[]{"ID", "NAME", "BALANCE"};
  private final List<Account> accounts = new ArrayList<>();

  @Override
  public int getRowCount() {
    return accounts.size();
  }

  @Override
  public int getColumnCount() {
    return colNames.length;
  }

  @Override
  public String getColumnName(int i) {
    return colNames[i];
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Account account = accounts.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> account.id;
      case 1 -> account.name;
      case 2 -> account.balance;
      default -> null;
    };
  }

  @Override
  public Class getColumnClass(int columnIndex) {
    return switch (columnIndex) {
      case 0 -> Integer.class;
      case 2 -> BigDecimal.class;
      default -> String.class;
    };
  }

  public void setAccounts(List<Account> accounts) {
    this.accounts.clear();
    this.accounts.addAll(accounts);
    this.fireTableDataChanged();
  }

  public Account getAccount(int row) {
    return accounts.get(row);
  }

  @Override
  public boolean formatNumber(int col) {
    return switch (col) {
      case 2 -> true;
      default -> false;
    };
  }
}
