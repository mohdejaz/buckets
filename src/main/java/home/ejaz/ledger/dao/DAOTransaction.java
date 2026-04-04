package home.ejaz.ledger.dao;

import home.ejaz.ledger.Context;
import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.util.DbUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

public class DAOTransaction {
    private final Logger logger = LogManager.getLogger(DAOTransaction.class);

    private static final DAOTransaction instance = new DAOTransaction();

    public static DAOTransaction getInstance() {
        return instance;
    }

    private DAOTransaction() {
    }

    public java.util.List<Transaction> getTransactions(int acctId, String filterExpr)
            throws SQLException {
        java.util.List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM (" +
                            " SELECT t.id, t.tx_date, b.name bucket, t.amount, t.note, t.posted" +
                            " FROM Transactions t JOIN Buckets b ON b.id = t.bucket" +
                            " WHERE b.acct_id = ? " +
                            " ORDER by t.tx_date desc, t.id desc)" +
                            " WHERE " + (!StringUtils.isEmpty(filterExpr) ? filterExpr : "1 = 1"))) {
                ps.setInt(1, acctId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Transaction tx = new Transaction();
                        tx.setId(rs.getLong("id"));
                        tx.setTxDate(rs.getDate("tx_date"));
                        tx.setBucket(rs.getString("bucket"));
                        tx.setAmount(rs.getBigDecimal("amount"));
                        tx.setNote(rs.getString("note"));
                        tx.setPosted(rs.getBoolean("posted"));
                        transactions.add(tx);
                    }
                }
            } catch (SQLException e) {
                logger.error(e);
            }
        }

        return transactions;
    }

    public void save(Transaction tx, Integer acctId) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try {
                if (tx.getId() == null) {
                    // new
                    logger.info("Bucket: " + tx.getBucket());
                    logger.info("Account: " + Context.getAcctId());
                    try (PreparedStatement ps = conn.prepareStatement("insert into Transactions(tx_date, bucket, amount, note)" +
                            " values (?, select id from Buckets where name = ? and acct_id = ?, ?, ?)")) {
                        ps.setDate(1, new java.sql.Date(tx.getTxDate().getTime()));
                        ps.setString(2, tx.getBucket());
                        ps.setInt(3, acctId);
                        ps.setBigDecimal(4, tx.getAmount());
                        ps.setString(5, tx.getNote());
                        ps.executeUpdate();
                    }
                } else {
                    // update
                    try (PreparedStatement ps = conn.prepareStatement(
                            "-- update\n" +
                                    "update Transactions set tx_date = ?," +
                                    " bucket = (select id from Buckets where name = ? and acct_id = ?), " +
                                    " amount = ?," +
                                    " note = ?" +
                                    " where id = ?")) {
                        ps.setDate(1, new java.sql.Date(tx.getTxDate().getTime()));
                        ps.setString(2, tx.getBucket());
                        ps.setInt(3, acctId);
                        ps.setBigDecimal(4, tx.getAmount());
                        ps.setString(5, tx.getNote());
                        ps.setLong(6, tx.getId());
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

    public void delete(long id) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try {
                try (PreparedStatement ps = conn.prepareStatement("delete from Transactions where id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                logger.error(e);
                conn.rollback();
            }
        }
    }

    public void post(long id) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try {
                try (PreparedStatement ps = conn.prepareStatement("update Transactions set posted = true where id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                logger.error(e);
                conn.rollback();
            }
        }
    }

    public void unpost(long id) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try {
                try (PreparedStatement ps = conn.prepareStatement("update Transactions set posted = false where id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                logger.error(e);
                conn.rollback();
            }
        }
    }

    public void transfer(String fromBucket, String toBucket, BigDecimal amount) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try {
                try (PreparedStatement ps = conn.prepareStatement("insert into Transactions(tx_date, bucket, amount, note, posted)" +
                        " values (?, select id from Buckets where name = ? and acct_id = ?, ?, ?, 'Y')")) {
                    ps.setDate(1, new java.sql.Date(new Date().getTime()));
                    ps.setString(2, fromBucket);
                    ps.setInt(3, Context.getAcctId());
                    ps.setBigDecimal(4, amount.multiply(new BigDecimal(-1)));
                    ps.setString(5, "Transfer to " + toBucket);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("insert into Transactions(tx_date, bucket, amount, note, posted)" +
                        " values (?, select id from Buckets where name = ? and acct_id = ?, ?, ?, 'Y')")) {
                    ps.setDate(1, new java.sql.Date(new Date().getTime()));
                    ps.setString(2, toBucket);
                    ps.setInt(3, Context.getAcctId());
                    ps.setBigDecimal(4, amount);
                    ps.setString(5, "Transfer from " + fromBucket);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                logger.error(e);
                conn.rollback();
            }
        }
    }
}
