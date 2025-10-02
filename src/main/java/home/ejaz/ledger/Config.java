package home.ejaz.ledger;

import org.apache.log4j.Logger;
import java.util.Properties;

public class Config {
  private static final org.apache.log4j.Logger logger = Logger.getLogger(Config.class.getName());
  private static String prof = "dev";
  private static Properties props;
  private static int acctId = -1;
  private static String title = "Buckets";

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

  public static int getFontSize() {
    return Integer.parseInt(getValue("font.size", "14"));
  }

  public static int getGap() {
    return Integer.parseInt(getValue("gap", "7"));
  }

  public static int getDotsPerSquare() {
    return Integer.parseInt(getValue("dps", "30"));
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

  public static int getUserId() {
    return Integer.parseInt(getValue("user.id", "1"));
  }

  public synchronized static int getAcctId() {
    if (acctId != -1) return acctId;
    return Integer.parseInt(getValue("acct.id", "1"));
  }

  public synchronized static void setAcctId(int value) {
    logger.info("Prev acct_id: " + acctId + " New value: " + value);
    acctId = value;
  }

  public synchronized static String getTitle() {
    return getValue("title","Buckets") + " - " + title;
  }

  public synchronized static void setTitle(String value) {
    title = value;
  }

  public static boolean enableRefill() {
    System.out.println("enabled.refill: " + getValue("enabled.refill", "true"));
    return Boolean.parseBoolean(getValue("enabled.refill", "true"));
  }

  public static boolean enableReset() {
    System.out.println("enabled.reset: " + getValue("enabled.reset", "true"));
    return Boolean.parseBoolean(getValue("enabled.reset", "true"));
  }
}
