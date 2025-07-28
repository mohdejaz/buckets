package home.ejaz.ledger.util;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.models.NumberModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

import static java.lang.Integer.parseInt;
import static javax.swing.BorderFactory.*;

public class CellRenderer extends DefaultTableCellRenderer {
  private static final long serialVersionUID = 1L;
  private final int LEN = 5;

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
    Number num = ((NumberModel) table.getModel()).getValue(row);

    setBorder(createEmptyBorder(LEN, LEN, LEN, LEN));
    if (value instanceof Boolean) {
      ((JLabel) c).setText((boolean) value ? "Y" : "N");
    }

    if (isSelected) {
      setBorder(createCompoundBorder(
        createDashedBorder(Color.BLACK),
        createEmptyBorder(LEN, LEN, LEN, LEN)
      ));
    }

    ((JLabel) c).setHorizontalAlignment(JLabel.LEADING);
    if (value instanceof Number || value instanceof java.util.Date) {
      ((JLabel) c).setHorizontalAlignment(JLabel.TRAILING);
    }

    this.setOpaque(true);
    c.setBackground(Color.WHITE);
    c.setForeground(Color.BLACK);


    if (num != null) {
      if (num.doubleValue() < 0.0) {
        String[] rgb = Config.getNegRGB().split(",");
        c.setBackground(new Color(parseInt(rgb[0]), parseInt(rgb[1]), parseInt(rgb[2])));
      } else {
        // 66, 245, 209
        String[] rgb = Config.getPosRGB().split(",");
        c.setBackground(new Color(parseInt(rgb[0]), parseInt(rgb[1]), parseInt(rgb[2])));
      }
    }

    return c;
  }
}
