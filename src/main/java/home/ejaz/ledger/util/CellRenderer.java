package home.ejaz.ledger.util;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.Number2;
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
    Number2 num2 = ((NumberModel) table.getModel()).getValue(row);

    int INSET = 7;
    setBorder(createEmptyBorder(INSET, INSET, INSET, INSET));
    if (value instanceof Boolean) {
      ((JLabel) c).setText((boolean) value ? "Y" : "N");
    }

    c.setBackground(Color.darkGray);
    c.setForeground(Color.BLACK);
    this.setOpaque(true);

    Font f = c.getFont();
    Font f2 = new Font(f.getName(), Font.BOLD, f.getSize());
    c.setForeground(Color.RED);
    if (!isSelected) {
      c.setForeground(Color.BLACK);
      f2 = new Font(f.getName(), Font.PLAIN, f.getSize());
    }
    c.setFont(f2);

    ((JLabel) c).setHorizontalAlignment(JLabel.LEADING);
    if (value instanceof Number || value instanceof java.util.Date) {
      ((JLabel) c).setHorizontalAlignment(JLabel.TRAILING);
    }

    if (num2 != null) {
      if (num2.num().equals(value) && num2.pos() == col) {
        ((JLabel) c).setText(df.format(num2.num()));
      }
      if (num2.num().doubleValue() < 0.0) {
        String[] rgb = Registry.getNegRGB().split(",");
        c.setBackground(new Color(parseInt(rgb[0]), parseInt(rgb[1]), parseInt(rgb[2])));
      } else {
        // 66, 245, 209
        String[] rgb = Registry.getPosRGB().split(",");
        c.setBackground(new Color(parseInt(rgb[0]), parseInt(rgb[1]), parseInt(rgb[2])));
      }
    }

    return c;
  }
}
