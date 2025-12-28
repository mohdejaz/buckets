package home.ejaz.ledger.models;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class ItemsTableModel extends AbstractTableModel implements NumberModel {
    private final String[] colNames = new String[]{"Id", "Name", "DOP", "Price", "Note"};
    private List<Item> items = new ArrayList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

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
        return switch (columnIndex) {
            case 0 -> item.id;
            case 1 -> item.name;
            case 2 -> item.dop;
            case 3 -> item.price;
            case 4 -> item.note;
            default -> null;
        };
    }

    @Override
    public Class getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> Integer.class;
            case 2, 3, 5 -> BigDecimal.class;
            default -> String.class;
        };
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
    public boolean formatNumber(int col) {
        return col == 2;
    }
}
