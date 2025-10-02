package home.ejaz.ledger.util;

import home.ejaz.ledger.Config;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbUtils {
  private final static Logger logger = Logger.getLogger(DbUtils.class);

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
