package home.ejaz.ledger.dao;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.util.DbUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DAOTransaction {
  private static DAOTransaction instance = new DAOTransaction();
  private Logger logger = Logger.getLogger(DAOTransaction.class);

  public static DAOTransaction getInstance() {
    return instance;
  }

  private DAOTransaction() {
  }

  private SparkSession spark = DbUtils.getSparkSession();

  protected Dataset<Row> getTransactionsDS() throws SQLException {
    StructType schema = new StructType(new StructField[]{
      new StructField("tid", DataTypes.LongType, false, Metadata.empty()),
      new StructField("tx_date", DataTypes.DateType, false, Metadata.empty()),
      new StructField("bucket", DataTypes.IntegerType, false, Metadata.empty()),
      new StructField("amount", DataTypes.createDecimalType(10, 2), false, Metadata.empty()),
      new StructField("note", DataTypes.StringType, false, Metadata.empty()),
      new StructField("posted", DataTypes.BooleanType, false, Metadata.empty())
    });

    List<Row> transactions = new ArrayList<>();
    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement(
        "select * from Transactions t")) {
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            transactions.add(RowFactory.create(
              rs.getInt("id"),
              rs.getDate("tx_date"),
              rs.getInt("bucket"),
              rs.getBigDecimal("amount"),
              rs.getString("note"),
              rs.getBoolean("posted")));
          }
        }
      }
    }

    return spark.createDataFrame(transactions, schema);
  }

  public java.util.List<Transaction> getTransactions(String filter) throws SQLException {
    java.util.List<Transaction> transactions = new ArrayList<>();

    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM (SELECT t.id, t.tx_date, b.name bucket, t.amount," +
          " t.note, t.posted" +
          " FROM Transactions t JOIN Buckets b ON b.id = t.bucket" +
          " WHERE b.acct_id = ? " +
          " ORDER by t.tx_date desc, t.id desc)" +
          " WHERE " + (!StringUtils.isEmpty(filter) ? filter : "1 = 1"))) {
        ps.setInt(1, Config.getAcctId());
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            Transaction tx = new Transaction();
            tx.id = rs.getLong("id");
            tx.txDate = rs.getDate("tx_date");
            tx.bucket = rs.getString("bucket");
            tx.amount = rs.getBigDecimal("amount");
            // tx.balance = rs.getBigDecimal("balance");
            tx.note = rs.getString("note");
            tx.posted = rs.getBoolean("posted");
            transactions.add(tx);
          }
        }
      }
    }

    return transactions;
  }

  public void save(Transaction tx) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
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
  }

  public void delete(long id) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement("delete from Transactions where id = ?")) {
        ps.setLong(1, id);
        ps.executeUpdate();
      }
    }
  }

  public void post(long id) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement("update Transactions set posted = true where id = ?")) {
        ps.setLong(1, id);
        ps.executeUpdate();
      }
    }
  }

  public void unpost(long id) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement("update Transactions set posted = false where id = ?")) {
        ps.setLong(1, id);
        ps.executeUpdate();
      }
    }
  }
}
