package home.ejaz.ledger.dao;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.Account;
import home.ejaz.ledger.util.DbUtils;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DAOAccounts {
  private static final DAOAccounts instance = new DAOAccounts();

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
          " group by a.id, a.name, a.user_id")) {
        ps.setInt(1, userId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            Account account = new Account();
            account.id = rs.getInt("id");
            account.name = rs.getString("name");
            account.userId = rs.getInt("user_id");
            account.balance = rs.getBigDecimal("balance");
            result.add(account);
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace(System.err);
    }

    return result;
  }

  public void save(Account account) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
      if (account.id == null) {
        // new
        try (PreparedStatement ps = conn.prepareStatement(
          "insert into Accounts(name, user_id) values (?,?)")) {
          ps.setString(1, account.name);
          ps.setInt(2, account.userId);
          ps.executeUpdate();
        }
      } else {
        // update
        try (PreparedStatement ps = conn.prepareStatement(
          "update Accounts set name = ?, user_id = ? where id = ?")) {
          ps.setString(1, account.name);
          ps.setInt(2, account.userId);
          ps.setInt(3, account.id);
          ps.executeUpdate();
        }
      }
    }
  }
}
