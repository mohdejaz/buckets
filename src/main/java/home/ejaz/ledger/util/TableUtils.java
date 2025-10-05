package home.ejaz.ledger.util;

import home.ejaz.ledger.Registry;

import javax.swing.*;
import java.awt.*;

public class TableUtils {

  public static void formatTable(JTable table) {
    table.setShowGrid(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.setGridColor(Color.lightGray);
    table.getTableHeader().setReorderingAllowed(false);
    table.setRowHeight(Registry.getDotsPerSquare());

    for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
      table.getColumnModel().getColumn(i).setCellRenderer(new CellRenderer());
    }
  }
}
