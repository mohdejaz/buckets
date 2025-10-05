package home.ejaz.ledger;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Registry {
  private static final org.apache.log4j.Logger logger = Logger.getLogger(Registry.class.getName());
  private static String prof = "dev";
  private static Properties props;
  private static int acctId = -1;
  private static String title = "Buckets";
  private static BucketsListener bucketsListener;
  private static String welcomeMessage = "N/A";

  static {
    try {
      props = new Properties();
      props.load(Registry.class.getResourceAsStream("/config.props"));
      prof = System.getProperty("profile", "dev") + ".";
      try (InputStream inp = Registry.class.getResourceAsStream("/welcome.htm")) {
        if (inp != null) {
          try (BufferedReader rdr = new BufferedReader(new InputStreamReader(inp))) {
            StringBuilder buff = new StringBuilder();
            String line;
            while ((line = rdr.readLine()) != null) {
              buff.append(line);
            }
            welcomeMessage = buff.toString();
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }

  public static String getWelcomeMessage() {
    return welcomeMessage;
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
    return getValue("title", "Buckets") + " - " + title;
  }

  public synchronized static void setTitle(String value) {
    title = value;
  }

  public static BucketsListener getBucketsListener() {
    return bucketsListener;
  }

  public static void setBucketsListener(BucketsListener value) {
    bucketsListener = value;
  }
}
