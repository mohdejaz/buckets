package home.ejaz.ledger.models;

import org.apache.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TransactionsTableModel extends AbstractTableModel implements NumberModel {
  public String[] colNames = new String[]{"Posted", "Id", "TxDate", "Bucket", "Amount", "Note"};
  private List<Transaction> transactions = new ArrayList<>();
  private Logger logger = Logger.getLogger(TransactionsTableModel.class);

  @Override
  public int getRowCount() {
    return transactions.size();
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
    Transaction tx = transactions.get(rowIndex);
    switch (columnIndex) {
      case 0: {
        return tx.posted;
      }
      case 1: {
        return tx.id;
      }
      case 2: {
        return tx.txDate;
      }
      case 3: {
        return tx.bucket;
      }
      case 4: {
        return tx.amount;
      }
      case 5: {
        return tx.note;
      }
      default:
        return null;
    }
  }

  @Override
  public Class getColumnClass(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return Boolean.class;
      case 1:
        return Long.class;
      case 2:
        return Date.class;
      case 3:
        return String.class;
      case 4:
        return BigDecimal.class;
      case 5:
        return String.class;
      default:
        return String.class;
    }
  }

  public void setTransactions(List<Transaction> transactions) {
    this.transactions.clear();
    this.transactions.addAll(transactions);
    this.fireTableDataChanged();
  }

  public Transaction getTransaction(int row) {
    return transactions.get(row);
  }

  @Override
  public Number getValue(int row) {
    Transaction tx = transactions.get(row);
    return tx == null ? null : tx.amount;
  }
}
