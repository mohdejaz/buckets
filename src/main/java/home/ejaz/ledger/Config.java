package home.ejaz.ledger;

import java.util.Properties;

public class Config {
  private static String prof = "dev";
  private static Properties props;

  static {
    try {
      props = new Properties();
      props.load(Config.class.getResourceAsStream("/config.props"));
      prof = System.getProperty("profile", "dev") + ".";
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String getValue(String name, String defValue) {
    String key = prof + name;
    if (System.getProperties().containsKey(key)) {
      return System.getProperty(key);
    }
    return props.getProperty(key, defValue);
  }

  public static double getRefillFactor() {
    return new Double(getValue("refill.factor", "0.5"));
  }

  public static int getFontSize() {
    return new Integer(getValue("font.size", "14"));
  }

  public static int getGap() {
    return new Integer(getValue("gap", "7"));
  }

  public static int getDotsPerSquare() {
    return new Integer(getValue("dps", "30"));
  }

  public static String getDBUrl() {
    return getValue("db.url", "jdbc:h2:tcp://localhost/ledger");
  }

  public static String getDBUser() {
    return getValue("db.user", "sa");
  }

  public static String getDBPass() {
    return getValue("db.pass", "sa");
  }

  public static String getNegRGB() {
    return getValue("rgb.neg", "255,204,204");
  }

  public static String getPosRGB() {
    return getValue("rgb.pos", "166,247,235");
  }

  public static int getGutterSize() {
    return Integer.parseInt(getValue("gutter.size", "10"));
  }
}
