package home.ejaz.ledger.dao;

import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.util.ConnectionUtil;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.ArrayList;

public class DAOTransaction {
  private Connection conn;
  private static DAOTransaction instance = new DAOTransaction();

  public static DAOTransaction getInstance() {
    return instance;
  }

  private void init() {
    conn = ConnectionUtil.getConnection();
  }

  private DAOTransaction() {
    init();
  }

  public java.util.List<Transaction> getTransactions() throws SQLException {
    return getTransactions(null);
  }

  public java.util.List<Transaction> getTransactions(String filter) throws SQLException {
    java.util.List<Transaction> transactions = new ArrayList<>();

    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT * FROM (SELECT t.id, t.tx_date txdate, b.name bucket, t.amount," +
        " t.note, t.posted" +
        " FROM Transactions t JOIN Buckets b ON b.id = t.bucket" +
        " ORDER by t.tx_date desc, t.id desc)" +
        " WHERE " + (!StringUtils.isEmpty(filter) ? filter : "1 = 1"))) {
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Transaction tx = new Transaction();
          tx.id = rs.getLong("id");
          tx.txDate = rs.getDate("txdate");
          tx.bucket = rs.getString("bucket");
          tx.amount = rs.getBigDecimal("amount");
          // tx.balance = rs.getBigDecimal("balance");
          tx.note = rs.getString("note");
          tx.posted = rs.getBoolean("posted");
          transactions.add(tx);
        }
      }
    }

    return transactions;
  }

  public void save(Transaction tx) throws SQLException {
    if (tx.id == null) {
      // new
      try (PreparedStatement ps = conn.prepareStatement("insert into Transactions(tx_date, bucket, amount, note)" +
        " values (?, select id from Buckets where name = ?, ?, ?)")) {
        ps.setDate(1, new java.sql.Date(tx.txDate.getTime()));
        ps.setString(2, tx.bucket);
        ps.setBigDecimal(3, tx.amount);
        ps.setString(4, tx.note);
        ps.executeUpdate();
      }
    } else {
      // update
      try (PreparedStatement ps = conn.prepareStatement("update Transactions set tx_date = ?," +
        " bucket = (select id from Buckets where name = ?), amount = ?, note = ?" +
        " where id = ?")) {
        ps.setDate(1, new java.sql.Date(tx.txDate.getTime()));
        ps.setString(2, tx.bucket);
        ps.setBigDecimal(3, tx.amount);
        ps.setString(4, tx.note);
        ps.setLong(5, tx.id);
        ps.executeUpdate();
      }
    }
  }

  public void delete(long id) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("delete from Transactions where id = ?")) {
      ps.setLong(1, id);
      ps.executeUpdate();
    }
  }

  public void post(long id) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("update Transactions set posted = true where id = ?")) {
      ps.setLong(1, id);
      ps.executeUpdate();
    }
  }

  public void unpost(long id) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("update Transactions set posted = false where id = ?")) {
      ps.setLong(1, id);
      ps.executeUpdate();
    }
  }
}
