package home.ejaz.ledger.dao;

import home.ejaz.ledger.Registry;
import home.ejaz.ledger.models.Item;
import home.ejaz.ledger.util.DbUtils;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DAOItems {
    private final Logger logger = Logger.getLogger(DAOItems.class);

    private static final DAOItems instance = new DAOItems();

    public static DAOItems getInstance() {
        return instance;
    }

    private DAOItems() {
    }

    public List<Item> getItems(String filter) throws SQLException {
        List<Item> result = new ArrayList<>();

        StringBuilder query = new StringBuilder();
        query.append("select * from items");
        if (filter != null && !filter.trim().isEmpty()) {
            query.append(" where ").append(filter);
        }
        query.append(" order by name, dop desc");

        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(query.toString())) {
                if (ps.execute()) {
                    try (ResultSet rs = ps.getResultSet()) {
                        while (rs.next()) {
                            Item item = new Item();
                            item.id = rs.getLong("id");
                            item.name = rs.getString("name");
                            item.dop = rs.getDate("dop");
                            item.price = rs.getBigDecimal("price");
                            item.note = rs.getString("note");
                            result.add(item);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warn("ERR: ", e);
            }
        }

        return result;
    }

    public void save(Item item) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            if (item.id == null) {
                // new
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO Items(name, dop, price, note) values (?,?,?,?)")) {
                    ps.setString(1, item.name);
                    ps.setDate(2, new java.sql.Date(item.dop.getTime()));
                    ps.setBigDecimal(3, item.price);
                    ps.setString(4, item.note);
                    ps.executeUpdate();
                }
            } else {
                // update
                try (PreparedStatement ps = conn.prepareStatement(
                        "update Items set name = ?," +
                                " dop = ?, " +
                                " price = ?," +
                                " note = ?" +
                                " where id = ?")) {
                    ps.setString(1, item.name);
                    ps.setDate(2, new java.sql.Date(item.dop.getTime()));
                    ps.setBigDecimal(3, item.price);
                    ps.setString(4, item.note);
                    ps.setLong(5, item.id);
                    ps.executeUpdate();
                }
            }
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("delete from Items where id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }
    }

}
