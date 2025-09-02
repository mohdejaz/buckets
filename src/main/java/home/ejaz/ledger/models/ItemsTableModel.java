package home.ejaz.ledger.models;

import org.apache.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ItemsTableModel extends AbstractTableModel implements NumberModel {
    public String[] colNames = new String[]{"Id", "TxId", "Name", "Merchant", "Brand", "Price", "Qty"};
    private List<Item> items = new ArrayList<>();
    private Logger logger = Logger.getLogger(ItemsTableModel.class);

    @Override
    public int getRowCount() {
        return items.size();
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
        Item item = items.get(rowIndex);
        switch (columnIndex) {
            case 0: {
                return item.id();
            }
            case 1: {
                return item.trnsId();
            }
            case 2: {
                return item.name();
            }
            case 3: {
                return item.merchant();
            }
            case 4: {
                return item.brand();
            }
            case 5: {
                return item.price();
            }
            case 6: {
                return item.qty();
            }
            default:
                return null;
        }
    }

    @Override
    public Class getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return Long.class;
            case 1:
                return Long.class;
            case 2:
                return String.class;
            case 3:
                return String.class;
            case 4:
                return String.class;
            case 5:
                return Double.class;
            case 6:
                return Double.class;
            default:
                return String.class;
        }
    }

    public void setItems(List<Item> items) {
        this.items.clear();
        this.items.addAll(items);
        this.fireTableDataChanged();
    }

    public Item getItem(int row) {
        return items.get(row);
    }

    @Override
    public Number getValue(int row) {
        Item item = items.get(row);
        return item == null ? null : item.price();
    }
}
