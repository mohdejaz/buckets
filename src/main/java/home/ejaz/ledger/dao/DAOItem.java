package home.ejaz.ledger.dao;

import home.ejaz.ledger.models.Bucket;
import home.ejaz.ledger.models.Item;
import home.ejaz.ledger.util.ConnectionUtil;
import org.apache.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DAOItem {
    private Connection conn;
    private static DAOItem instance = new DAOItem();
    private Logger logger = Logger.getLogger(DAOItem.class);

    public static DAOItem getInstance() {
        return instance;
    }

    private void init() {
        conn = ConnectionUtil.getConnection();
    }

    private DAOItem() {
        init();
    }

    public List<Item> getItems(Long trnsId) throws SQLException {
        List<Item> items = new ArrayList<>();

        StringBuilder buff = new StringBuilder();
        buff.append("SELECT * FROM Items WHERE");
        if (trnsId != null) {
            buff.append(" TRNS_ID = ?");
        } else {
            buff.append(" 1 == 1");
        }

        try (PreparedStatement ps = conn.prepareStatement(buff.toString())) {
            if (trnsId != null) {
                ps.setLong(1, trnsId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = new Item(
                            rs.getLong("id"),
                            rs.getLong("trans_id"),
                            rs.getString("name"),
                            rs.getString("merchant"),
                            rs.getString("brand"),
                            rs.getDouble("price"),
                            rs.getDouble("qty"),
                            rs.getDate("create_d")
                    );
                    items.add(item);
                }
            }
        }

        return items;
    }

    public void save(Item item) throws SQLException {
        StringBuilder buff = new StringBuilder();
        if (item.id() == null) {
            // new
            buff.append("insert into Items(trns_id, name, merchant, brand, price, qty, create_dt)");
            buff.append(" values (?, ?, ?, ?, ?, ?, ?)");
            try (PreparedStatement ps = conn.prepareStatement(buff.toString(), Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, item.trnsId());
                ps.setString(2, item.name());
                ps.setString(3, item.merchant());
                ps.setString(4, item.brand());
                ps.setDouble(5, item.price());
                ps.setDouble(6, item.qty());
                ps.setDate(7, new java.sql.Date(item.createDt().getTime()));

                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error inserting Item", e);
                throw e;
            }
        } else {
            // update
            buff.append("update Items set");
            buff.append(" trns_id = ?,");
            buff.append(" name = ?,");
            buff.append(" merchant = ?,");
            buff.append(" brand = ?,");
            buff.append(" price = ?,");
            buff.append(" qty = ?,");
            buff.append(" create_dt = ?");
            buff.append(" where id = ?");
            try (PreparedStatement ps = conn.prepareStatement(buff.toString())) {
                ps.setLong(1, item.trnsId());
                ps.setString(2, item.name());
                ps.setString(3, item.merchant());
                ps.setString(4, item.brand());
                ps.setDouble(5, item.price());
                ps.setDouble(6, item.qty());
                ps.setDate(7, new java.sql.Date(item.createDt().getTime()));
                ps.setLong(8, item.id());

                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating Item with id " + item.id(), e);
                throw e;
            }
        }
    }
}
