package home.ejaz.ledger.util;

import home.ejaz.ledger.Registry;

import javax.swing.*;
import java.awt.*;

public class TableUtils {

  public static void formatTable(JTable table) {
    table.setShowGrid(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.setGridColor(Color.gray);
    table.getTableHeader().setReorderingAllowed(false);
    table.setRowHeight(Registry.getDotsPerSquare());
    // table.setIntercellSpacing(new Dimension(3, 3));
    table.getTableHeader().setForeground(Color.BLUE);
    table.getTableHeader().setOpaque(true);

    for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
      table.getColumnModel().getColumn(i).setCellRenderer(new CellRenderer());
    }
  }
}
