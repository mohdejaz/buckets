package home.ejaz.ledger.dao;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.util.DbUtils;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import static org.apache.spark.sql.functions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DAOBucket {
  private static DAOBucket instance = new DAOBucket();
  private Logger logger = Logger.getLogger(DAOBucket.class);

  public static DAOBucket getInstance() {
    return instance;
  }

  private DAOBucket() {
  }

  private SparkSession spark = DbUtils.getSparkSession();

  protected Dataset<Row> getBucketsDS() throws SQLException {
    StructType schema = new StructType(new StructField[]{
      new StructField("bid", DataTypes.IntegerType, false, Metadata.empty()),
      new StructField("name", DataTypes.StringType, false, Metadata.empty()),
      new StructField("budget", DataTypes.createDecimalType(10, 2), false, Metadata.empty()),
      new StructField("refill", DataTypes.DoubleType, false, Metadata.empty()),
      new StructField("acct_id", DataTypes.IntegerType, false, Metadata.empty()),

    });

    List<Row> buckets = new ArrayList<>();
    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement("select * from Buckets b")) {
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            buckets.add(RowFactory.create(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getBigDecimal("budget"),
              rs.getDouble("refill"),
              rs.getInt("acct_id")));
          }
        }
      }
    }

    return spark.createDataFrame(buckets, schema);
  }

  public java.util.List<Bucket> getBuckets() throws SQLException {
    List<Bucket> result = new ArrayList<>();

    logger.info("acct_id = " + Config.getAcctId());

    Dataset<Row> buckDS = getBucketsDS();
    Dataset<Row> transDS = DAOTransaction.getInstance().getTransactionsDS();

    spark.sqlContext().registerDataFrameAsTable(buckDS, "Buckets");
    spark.sqlContext().registerDataFrameAsTable(transDS, "Transactions");

    spark.conf().set("acct_id", Config.getAcctId());
    Dataset<Row> ds = spark.sql(
      "WITH" +
        " Buckets2 AS (select * from Buckets where acct_id = ${acct_id})," +
        " Transactions2 AS (select * from Transactions where bucket in (select bid from Buckets2))" +
        "SELECT a.name, a.budget, a.balance, coalesce(sum(t2.amount),0.00) refill_mtd FROM " +
        " (SELECT b.bid, b.name, b.budget, coalesce(sum(t.amount),0.00) balance " +
        "  FROM Buckets2 b LEFT JOIN Transactions2 t ON t.bucket = b.bid " +
        "  GROUP BY b.bid, b.name, b.budget) a" +
        " INNER JOIN Transactions2 t2 on a.bid = t2.bucket " +
        " WHERE t2.amount >= 0 and t2.tx_date >= trunc(current_date(), 'MM')" +
        " GROUP BY a.name, a.budget, a.balance"
    );
    ds.show();

    try (Connection conn = DbUtils.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement(
        "SELECT b.id, b.NAME, b.BUDGET," +
          " (select nvl(sum(amount),0.00) from Transactions where tx_date >= date_trunc(month, current_date)" +
          "  and bucket = b.id and amount >= 0) rtd, " +
          " nvl(sum(t.amount),0.00) amt," +
          " refill " +
          " FROM BUCKETS b LEFT JOIN TRANSACTIONS t ON t.BUCKET = b.ID " +
          " WHERE b.acct_id = ?" +
          " GROUP BY b.id, b.NAME, b.BUDGET")) {
        ps.setInt(1, Config.getAcctId());
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            Bucket bucket = new Bucket();
            bucket.id = rs.getInt("id");
            bucket.name = rs.getString("name");
            bucket.budget = rs.getBigDecimal("budget");
            bucket.refillMtd = rs.getBigDecimal("rtd");
            bucket.balance = rs.getBigDecimal("amt");
            bucket.refill = rs.getDouble("refill");
            result.add(bucket);
          }
        }
      }
    }

    return result;
  }

  public void save(Bucket bucket) throws SQLException {
    try (Connection conn = DbUtils.getConnection()) {
      if (bucket.id == null) {
        // new
        try (PreparedStatement ps = conn.prepareStatement(
          "insert into Buckets(name, budget, acct_id, refill) values (?,?,?,?)")) {
          ps.setString(1, bucket.name);
          ps.setBigDecimal(2, bucket.budget);
          ps.setInt(3, Config.getAcctId());
          ps.setDouble(4, bucket.refill);
          ps.executeUpdate();
        }
      } else {
        // update
        try (PreparedStatement ps = conn.prepareStatement(
          "update Buckets set name = ?, budget = ?, refill = ? where id = ?")) {
          ps.setString(1, bucket.name);
          ps.setBigDecimal(2, bucket.budget);
          ps.setDouble(3, bucket.refill);
          ps.setInt(4, bucket.id);
          ps.executeUpdate();
        }
      }
    }
  }
}
