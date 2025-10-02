package home.ejaz.ledger.layout;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ELayout implements LayoutManager {
  private final int rows, cols, dps, gap;
  private final Map<Component, EConstaint> csMap = new HashMap<>();

  public ELayout(int rows, int cols, int dps, int gap) {
    this.rows = rows;
    this.cols = cols;
    this.dps = dps;
    this.gap = gap;
  }

  private void log(String msg) {
    System.out.println(msg);
  }

  public void setConstraints(Component comp, EConstaint constraint) {
    csMap.put(comp, constraint);
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
  }

  @Override
  public void removeLayoutComponent(Component comp) {
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    Dimension d = new Dimension(0, 0);

    Insets insets = parent.getInsets();
    d.width = insets.left + (cols * dps + (cols + 1) * gap) + insets.right;
    d.height = insets.top + (rows * dps + (rows + 1) * gap) + insets.bottom;

    return d;
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return preferredLayoutSize(parent);
  }

  @Override
  public void layoutContainer(Container parent) {
    int nComps = parent.getComponentCount();
    Insets insets = parent.getInsets();
    int idx = 0;
    int x = 0, y = 0, w = 0, h = 0;

    while (idx < nComps) {
      Component c = parent.getComponent(idx);
      EConstaint cs = csMap.get(c);
      // log(idx + ";" + cs);
      if (c.isVisible()) {
        x = insets.left + (cs.col() - 1) * dps + (cs.col() - 1) * gap + gap;
        y = insets.top + (cs.row() - 1) * dps + (cs.row() - 1) * gap + gap;
        w = cs.width() * dps + (cs.width() - 1) * gap;
        h = cs.height() * dps + (cs.height() - 1) * gap;

        // log("x=" + x + ";y=" + y + ";w=" + w + ";h=" + h);
        c.setBounds(x, y, w, h);
      }
      idx++;
    }
  }
}
