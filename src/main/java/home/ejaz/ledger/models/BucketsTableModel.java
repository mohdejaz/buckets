package home.ejaz.ledger.models;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BucketsTableModel extends AbstractTableModel implements NumberModel {
  private String[] colNames = new String[]{"ID", "NAME", "BUDGET", "REFILL(MTD)", "REFILL(F)", "BALANCE"};
  private List<Bucket> buckets = new ArrayList<>();

  @Override
  public int getRowCount() {
    return buckets.size();
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
    Bucket bucket = buckets.get(rowIndex);
    switch (columnIndex) {
      case 0: {
        return bucket.id;
      }
      case 1: {
        return bucket.name;
      }
      case 2: {
        return bucket.budget;
      }
      case 3: {
        return bucket.refillMtd;
      }
      case 4: {
        return bucket.refill;
      }
      case 5: {
        return bucket.balance;
      }
      default:
        return null;
    }
  }

  @Override
  public Class getColumnClass(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return Integer.class;
      case 1:
        return String.class;
      case 2:
        return BigDecimal.class;
      case 3:
        return BigDecimal.class;
      case 4:
        return Double.class;
      case 5:
        return BigDecimal.class;
      default:
        return String.class;
    }
  }

  public void setBuckets(List<Bucket> buckets) {
    this.buckets.clear();
    this.buckets.addAll(buckets);
    this.fireTableDataChanged();
  }

  public Bucket getBucket(int row) {
    return buckets.get(row);
  }

  @Override
  public Number getValue(int row) {
    Bucket bucket = buckets.get(row);
    return bucket == null ? null : bucket.balance;
  }
}
