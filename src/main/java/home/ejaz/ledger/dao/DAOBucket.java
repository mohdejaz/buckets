package home.ejaz.ledger.dao;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.util.ConnectionUtil;
import org.apache.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;

public class DAOBucket {
  private Connection conn;
  private static DAOBucket instance = new DAOBucket();
  private Logger logger = Logger.getLogger(DAOBucket.class);

  public static DAOBucket getInstance() {
    return instance;
  }

  private void init() {
    conn = ConnectionUtil.getConnection();
  }

  private DAOBucket() {
    init();
  }

  public java.util.List<Bucket> getBuckets() throws SQLException {
    java.util.List<Bucket> buckets = new ArrayList<>();

    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT b.id, b.NAME, b.BUDGET," +
        "(select nvl(sum(amount),0.00) from Transactions where tx_date >= date_trunc(month, current_date)" +
        " and bucket = b.id and bucket = b.id and amount >= 0) rtd, " +
        "nvl(sum(t.amount),0.00) amt " +
        "FROM BUCKETS b LEFT JOIN TRANSACTIONS t ON t.BUCKET = b.ID " +
        "GROUP BY b.id, b.NAME, b.BUDGET")) {
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Bucket bucket = new Bucket();
          bucket.id = rs.getInt("id");
          bucket.name = rs.getString("name");
          bucket.budget = rs.getBigDecimal("budget");
          bucket.refillMtd = rs.getBigDecimal("rtd");
          bucket.balance = rs.getBigDecimal("amt");
          buckets.add(bucket);
        }
      }
    }

    return buckets;
  }

  public void save(Bucket bucket) throws SQLException {
    if (bucket.id == null) {
      // new
      try (PreparedStatement ps = conn.prepareStatement(
          "insert into Buckets(name, budget) values (?,?)", Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, bucket.name);
        ps.setBigDecimal(2, bucket.budget);
        ps.executeUpdate();
        try (ResultSet rs = ps.getGeneratedKeys()) {
          if (rs.next()) {
            bucket.id = rs.getInt(1);
          }
        }
      } catch (SQLException e) {
        logger.error("Error inserting bucket", e);
        throw e;
      }
    } else {
      // update
      try (PreparedStatement ps = conn.prepareStatement(
          "update Buckets set name = ?, budget = ? where id = ?")) {
        ps.setString(1, bucket.name);
        ps.setBigDecimal(2, bucket.budget);
        ps.setInt(3, bucket.id);
        ps.executeUpdate();
      } catch (SQLException e) {
        logger.error("Error updating bucket with id " + bucket.id, e);
        throw e;
      }
    }
  }
}
