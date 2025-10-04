package home.ejaz.ledger.dao;

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
          " select a.id, a.name, a.user_id, sum(t.amount) balance" +
          " from Accounts a inner join Buckets b on b.acct_id = a.id" +
          " inner join Transactions t on t.bucket = b.id" +
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
}
