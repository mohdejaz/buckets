package home.ejaz.ledger.layout;

public class EConstaint {
  public final int row;
  public final int col;
  public final int width;
  public final int height;

  public EConstaint(int row, int col, int width, int height) {
    this.row = row;
    this.col = col;
    this.width = width;
    this.height = height;
  }

  public String toString() {
    return "row=" + row + ";col=" + col + ";width=" + width + ";height=" + height;
  }
}
