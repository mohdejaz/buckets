package home.ejaz.ledger.util;

import home.ejaz.ledger.Config;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbUtils {
  private final static Logger logger = Logger.getLogger(DbUtils.class);

  private static final SparkSession sparkSession;

  static {
    SparkConf conf = new SparkConf()
      .setAppName("Budgets")
      .setMaster("local[*]")
      .set("spark.driver.host", "127.0.0.1")
      .set("spark.driver.extraJavaOptions",
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
      .set("spark.executor.extraJavaOptions",
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
    sparkSession = SparkSession.builder().config(conf).getOrCreate();
    sparkSession.conf().set("spark.sql.shuffle.partitions", "16");
  }

  public static SparkSession getSparkSession() {
    return sparkSession;
  }

  public static Connection getConnection() {
    try {
      Class.forName("org.h2.Driver");
      logger.info("url = " + Config.getDBUrl());
      return DriverManager.getConnection(Config.getDBUrl(), Config.getDBUser(), Config.getDBPass());
    } catch (SQLException | ClassNotFoundException e) {
      e.printStackTrace(System.err);
      throw new RuntimeException(e);
    }
  }

}
