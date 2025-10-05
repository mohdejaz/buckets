package home.ejaz.ledger.util;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.NumberModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.Serial;
import java.text.DecimalFormat;

import static java.lang.Integer.parseInt;
import static javax.swing.BorderFactory.*;

public class CellRenderer extends DefaultTableCellRenderer {
  @Serial
  private static final long serialVersionUID = 1L;
  private final DecimalFormat df = new DecimalFormat("###,###,###.00");

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
    NumberModel numModel = ((NumberModel) table.getModel());

    int INSET = 7;
    setBorder(createEmptyBorder(INSET, INSET, INSET, INSET));
    if (value instanceof Boolean) {
      ((JLabel) c).setText((boolean) value ? "Y" : "N");
    }

    ((JLabel) c).setHorizontalAlignment(JLabel.LEADING);
    if (value instanceof Number || value instanceof java.util.Date) {
      ((JLabel) c).setHorizontalAlignment(JLabel.TRAILING);
      if ((value instanceof Number)) {
        if (numModel.formatNumber(col)) {
          ((JLabel) c).setText(df.format(value));
        }
      }
    }


    return c;
  }
}
