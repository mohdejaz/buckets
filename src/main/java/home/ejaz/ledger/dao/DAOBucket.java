package home.ejaz.ledger.dao;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.util.DbUtils;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DAOBucket {
    private static final DAOBucket instance = new DAOBucket();

    public static DAOBucket getInstance() {
        return instance;
    }

    private DAOBucket() {
    }

    public java.util.List<Bucket> getBuckets(int acctId) throws SQLException {
        List<Bucket> result = new ArrayList<>();

        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT b.id, b.NAME, b.BUDGET," +
                            " (select nvl(sum(amount),0.00) from Transactions where tx_date >= date_trunc(month, current_date)" +
                            "  and bucket = b.id and amount >= 0) rtd," +
                            " nvl(sum(t.amount),0.00) amt," +
                            " refill_schd," +
                            " next_refill," +
                            " acct_id " +
                            " FROM BUCKETS b LEFT JOIN TRANSACTIONS t ON t.BUCKET = b.ID " +
                            " WHERE b.acct_id = ?" +
                            " GROUP BY b.id, b.NAME, b.BUDGET")) {
                ps.setInt(1, acctId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Bucket bucket = new Bucket();
                        bucket.id = rs.getInt("id");
                        bucket.name = rs.getString("name");
                        bucket.budget = rs.getBigDecimal("budget");
                        bucket.refillMtd = rs.getBigDecimal("rtd");
                        bucket.balance = rs.getBigDecimal("amt");
                        bucket.refillSchedule = rs.getString("refill_schd");
                        bucket.nextRefill = rs.getDate("next_refill");
                        bucket.acctId = rs.getInt("acct_id");
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
                        "insert into Buckets(name, budget, acct_id) values (?,?,?)")) {
                    ps.setString(1, bucket.name);
                    ps.setBigDecimal(2, bucket.budget);
                    ps.setInt(3, bucket.acctId);
                    ps.executeUpdate();
                }
            } else {
                // update
                try (PreparedStatement ps = conn.prepareStatement(
                        "update Buckets set name = ?, budget = ?, acct_id = ?, refill_schd = ?, next_refill = ? where id = ?")) {
                    ps.setString(1, bucket.name);
                    ps.setBigDecimal(2, bucket.budget);
                    ps.setInt(3, bucket.acctId);
                    ps.setString(4, bucket.refillSchedule);
                    ps.setDate(5, new java.sql.Date(bucket.nextRefill.getTime()));
                    ps.setInt(6, bucket.id);
                    ps.executeUpdate();
                }
            }
        }
    }
}
