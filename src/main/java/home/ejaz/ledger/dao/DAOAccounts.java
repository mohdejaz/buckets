package home.ejaz.ledger.dao;

import home.ejaz.ledger.models.Account;
import home.ejaz.ledger.util.DbUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DAOAccounts {
  private static final DAOAccounts instance = new DAOAccounts();
  private static final Logger logger = LogManager.getLogger(DAOAccounts.class);

  public static DAOAccounts getInstance() {
    return instance;
  }

  private DAOAccounts() {
  }

  public List<Account> getAccounts(int userId) {
    List<Account> result = new ArrayList<>();

        try (Connection conn = DbUtils.getConnection()) {
          try (PreparedStatement ps = conn.prepareStatement(
            " -- query\n" +
              " select a.id, a.name, a.user_id, nvl(sum(t.amount),0.00) balance" +
              " from Accounts a " +
              " left join Buckets b on b.acct_id = a.id" +
              " left join Transactions t on t.bucket = b.id" +
              " where a.user_id = ?" +
              " group by a.id, a.name, a.user_id" +
              " order by a.id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                Account account = new Account();
                account.setId(rs.getInt("id"));
                account.setName(rs.getString("name"));
                account.setUserId(rs.getInt("user_id"));
                account.setBalance(rs.getBigDecimal("balance"));
                result.add(account);
              }
            }
          }
          return result;
        } catch (SQLException e) {
          e.printStackTrace(System.err);
        }

//    try (SparkSession spark = SparkUtils.getSparkSession()) {
//      Map<String, Dataset<Row>> entities = SparkUtils.getEntities(spark);
//
//      Dataset<Row> a = entities.get("accountsDs");
//      Dataset<Row> b = entities.get("bucketsDs");
//      Dataset<Row> t = entities.get("transactionsDs");
//
//      Dataset<Row> joined = a.filter(col("userId").equalTo(userId))
//        .join(b, a.col("id").equalTo(b.col("acctId")), "left")
//        .join(t, b.col("id").equalTo(t.col("bucketId")), "left")
//        .groupBy(a.col("id"), a.col("name"), a.col("userId"))
//        .agg(coalesce(sum(t.col("amount")), lit(BigDecimal.ZERO)).alias("balance"))
//        .orderBy(a.col("id"));
//      joined.show();
//
//      result = joined.as(Encoders.bean(Account.class)).collectAsList();
//    } catch (Exception e) {
//      e.printStackTrace(System.err);
//    }

    return result;
  }

  public void save(Account account) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
      try {
        if (account.getId() == null) {
          // new
          try (PreparedStatement ps = conn.prepareStatement(
                  "insert into Accounts(name, user_id) values (?,?)")) {
            ps.setString(1, account.getName());
            ps.setInt(2, account.getUserId());
            ps.executeUpdate();
          }
        } else {
          // update
          try (PreparedStatement ps = conn.prepareStatement(
                  "update Accounts set name = ?, user_id = ? where id = ?")) {
            ps.setString(1, account.getName());
            ps.setInt(2, account.getUserId());
            ps.setInt(3, account.getId());
            ps.executeUpdate();
          }
        }
        conn.commit();
      } catch (Exception e) {
        logger.error(e);
        conn.rollback();
      }
    }
  }
}
