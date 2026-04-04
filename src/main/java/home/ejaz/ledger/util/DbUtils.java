package home.ejaz.ledger.util;

import home.ejaz.ledger.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbUtils {
  private final static Logger logger = LogManager.getLogger(DbUtils.class);

  public static Connection getConnection() {
    try {
      Class.forName("org.h2.Driver");
      Connection conn = DriverManager.getConnection(Registry.getDBUrl(), Registry.getDBUser(), Registry.getDBPass());
      conn.setAutoCommit(false);
      return conn;
    } catch (SQLException | ClassNotFoundException e) {
      e.printStackTrace(System.err);
      throw new RuntimeException(e);
    }
  }

}
