package home.ejaz.ledger.util;

import home.ejaz.ledger.Config;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionUtil {
    private static Connection conn;
    private static Logger logger = Logger.getLogger(ConnectionUtil.class);

    static {
        try {
            Class.forName("org.h2.Driver");
            logger.info("url = " + Config.getDBUrl());
            conn = DriverManager.getConnection(Config.getDBUrl(), Config.getDBUser(), Config.getDBPass());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static Connection getConnection() {
        return conn;
    }
}
