package home.ejaz.ledger.dao;

import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.util.DbUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DAOBucket {
    private static final DAOBucket instance = new DAOBucket();
    private final static Logger logger = LogManager.getLogger(DAOBucket.class);

    public static DAOBucket getInstance() {
        return instance;
    }

    private DAOBucket() {
    }

    public Bucket getBucket(int acctId, String bname) throws SQLException {
        Optional<Bucket> bucket = getBuckets(acctId).stream().filter(buck -> buck.getName().equals(bname)).findFirst();
        return bucket.orElse(null);
    }

    public java.util.List<Bucket> getBuckets(int acctId) throws SQLException {
        List<Bucket> result = new ArrayList<>();

        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT b.id, b.NAME, b.BUDGET," +
                            " (select nvl(sum(t2.amount),0.00) from Transactions t2 where t2.tx_date >= date_trunc(month, current_date)" +
                            "  and t2.bucket = b.id and amount >= 0) rtd," +
                            " nvl(sum(t.amount),0.00) amt," +
                            " (select nvl(sum(t2.amount),0.00) from Transactions t2 where t2.tx_date < date_trunc(month, current_date)" +
                            " and t2.bucket = b.id) prev_amt," +
                            " acct_id," +
                            " refill_factor " +
                            " FROM BUCKETS b LEFT JOIN TRANSACTIONS t ON t.BUCKET = b.ID " +
                            " WHERE b.acct_id = ?" +
                            " GROUP BY b.id, b.NAME, b.BUDGET")) {
                ps.setInt(1, acctId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Bucket bucket = new Bucket();
                        bucket.setId(rs.getInt("id"));
                        bucket.setName(rs.getString("name"));
                        bucket.setBudget(rs.getBigDecimal("budget"));
                        bucket.setRefillMtd(rs.getBigDecimal("rtd"));
                        bucket.setBalance(rs.getBigDecimal("amt"));
                        bucket.setPrevBalance(rs.getBigDecimal("prev_amt"));
                        bucket.setAcctId(rs.getInt("acct_id"));
                        bucket.setRefillFactor(rs.getBigDecimal("refill_factor"));
                        result.add(bucket);
                        logger.debug("Bucket: {}, Prev Balance: {}, Balance: {}", bucket.getName(), bucket.getPrevBalance(), bucket.getBalance());
                    }
                }
            }
        }

        return result;
    }

    public void save(Bucket bucket) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try {
                if (bucket.getId() == null) {
                    // new
                    try (PreparedStatement ps = conn.prepareStatement(
                            "insert into Buckets(name, budget, acct_id, refill_factor) values (?,?,?,?)")) {
                        ps.setString(1, bucket.getName());
                        ps.setBigDecimal(2, bucket.getBudget());
                        ps.setInt(3, bucket.getAcctId());
                        ps.setBigDecimal(4, bucket.getRefillFactor());
                        ps.executeUpdate();
                    }
                } else {
                    // update
                    try (PreparedStatement ps = conn.prepareStatement(
                            "update Buckets set name = ?, budget = ?, acct_id = ?, refill_factor = ? where id = ?")) {
                        ps.setString(1, bucket.getName());
                        ps.setBigDecimal(2, bucket.getBudget());
                        ps.setInt(3, bucket.getAcctId());
                        ps.setBigDecimal(4, bucket.getRefillFactor());
                        ps.setInt(5, bucket.getId());
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
