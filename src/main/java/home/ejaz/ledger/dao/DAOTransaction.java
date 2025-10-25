package home.ejaz.ledger.dao;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.util.DbUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

public class DAOTransaction {
    private static final DAOTransaction instance = new DAOTransaction();

    public static DAOTransaction getInstance() {
        return instance;
    }

    private DAOTransaction() {
    }

    public java.util.List<Transaction> getTransactions(int acctId, String filter)
            throws SQLException {
        java.util.List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM (SELECT t.id, t.tx_date, b.name bucket, t.amount," +
                            " t.note, t.posted" +
                            " FROM Transactions t JOIN Buckets b ON b.id = t.bucket" +
                            " WHERE b.acct_id = ? " +
                            " ORDER by t.tx_date desc, t.id desc)" +
                            " WHERE " + (!StringUtils.isEmpty(filter) ? filter : "1 = 1"))) {
                ps.setInt(1, acctId);
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
                try (PreparedStatement ps = conn.prepareStatement(
                        "-- update\n" +
                                "update Transactions set tx_date = ?," +
                                " bucket = (select id from Buckets where name = ? and acct_id = ?), " +
                                " amount = ?," +
                                " note = ?" +
                                " where id = ?")) {
                    ps.setDate(1, new java.sql.Date(tx.txDate.getTime()));
                    ps.setString(2, tx.bucket);
                    ps.setInt(3, Registry.getAcctId());
                    ps.setBigDecimal(4, tx.amount);
                    ps.setString(5, tx.note);
                    ps.setLong(6, tx.id);
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

    public void transfer(String fromBucket, String toBucket, BigDecimal amount) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("insert into Transactions(tx_date, bucket, amount, note)" +
                    " values (?, select id from Buckets where name = ?, ?, ?)")) {
                ps.setDate(1, new java.sql.Date(new Date().getTime()));
                ps.setString(2, fromBucket);
                ps.setBigDecimal(3, amount.multiply(new BigDecimal(-1)));
                ps.setString(4, "Transfer to " + toBucket);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("insert into Transactions(tx_date, bucket, amount, note)" +
                    " values (?, select id from Buckets where name = ?, ?, ?)")) {
                ps.setDate(1, new java.sql.Date(new Date().getTime()));
                ps.setString(2, toBucket);
                ps.setBigDecimal(3, amount);
                ps.setString(4, "Transfer from " + fromBucket);
                ps.executeUpdate();
            }
            conn.commit();
        }
    }
}
