package home.ejaz.ledger.forms.calc;

import home.ejaz.ledger.BucketsListener;
import home.ejaz.ledger.Registry;
import home.ejaz.ledger.dao.DAOItems;
import home.ejaz.ledger.dao.DAOTransaction;
import home.ejaz.ledger.forms.transaction.FormTransaction;
import home.ejaz.ledger.models.Item;
import home.ejaz.ledger.models.ItemsTableModel;
import home.ejaz.ledger.models.Transaction;
import home.ejaz.ledger.util.TableUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import static java.awt.BorderLayout.SOUTH;

public class FormCalc extends JPanel {
    private final Logger logger = Logger.getLogger(FormCalc.class);

    private final JButton jbNew = new JButton("New");
    private final JButton jbEdit = new JButton("Edit");
    private final JButton jbDel = new JButton("Delete");
    private final JTextField jtFilter = new JTextField("");
    private final JLabel status = new JLabel("");
    private boolean init = false;
    private final java.util.List<Item> list = new ArrayList<>();
    private final DAOItems daoItems = DAOItems.getInstance();
    private final ItemsTableModel itemsTableModel = new ItemsTableModel();
    private final JTable table = new JTable(itemsTableModel);
    private long lastSelectItem = -1;
    private FormItem formItem;

    private final JFrame frame;

    private void refresh() {
        try {
            this.list.clear();
            this.list.addAll(daoItems.getItems(jtFilter.getText()));
            this.itemsTableModel.setItems(list);
            status.setText(list.size() + " rows added");
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public void init() {
        refresh();

        if (!init) {
            TableUtils.formatTable(table);

            this.table.getSelectionModel().addListSelectionListener(l -> {
                int row = table.getSelectedRow();
                if (row != -1) {
                    Item tx = this.itemsTableModel.getItem(row);
                    this.lastSelectItem = tx.id;
                }
            });

            this.table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2) {
                        doEdit();
                    }
                }
            });

            formItem = new FormItem(frame);

            // jbRefresh.addActionListener(al -> refresh());
            jbNew.addActionListener(al -> doAdd());
            jbEdit.addActionListener(al -> doEdit());
            jbDel.addActionListener(al -> doDelete());
            jtFilter.addActionListener(e -> refresh());

            init = true;
        }
    }

    private void doEdit() {
        formItem.clear();
        formItem.init();
        for (int row : table.getSelectedRows()) {
            Item tx = itemsTableModel.getItem(row);
            this.lastSelectItem = tx.id;
            formItem.setItem(tx);
            formItem.setVisible(true);
            Registry.getBucketsListener().txUpdate(-1);
        }
        refresh();
    }

    private void doAdd() {
        formItem.clear();
        formItem.init();
        formItem.setVisible(true);
        refresh();
        Registry.getBucketsListener().txAdded(-1);
    }

    public void doDelete() {
        java.util.List<Item> delTxList = new ArrayList<>();
        for (int row : this.table.getSelectedRows()) {
            if (row != -1) {
                delTxList.add(this.itemsTableModel.getItem(row));
            }
        }
        for (Item tx : delTxList) {
            try {
                DAOTransaction.getInstance().delete(tx.id);
                Registry.getBucketsListener().txDelete(tx.id);
            } catch (SQLException e) {
                e.printStackTrace(System.err);
            }
        }
        refresh();
    }

    public FormCalc(JFrame frame) {
        this.frame = frame;

        init();

        JPanel main = new JPanel();
        main.setLayout(new BorderLayout());
        int gap = Registry.getGap();

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, gap, gap));
        btnPanel.add(jbNew);
        btnPanel.add(jbEdit);
        btnPanel.add(jbDel);
        main.add(btnPanel, BorderLayout.NORTH);

        JPanel jp2 = new JPanel(new BorderLayout(3,3));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane jsp = new JScrollPane(table);
        jp2.add(jsp, BorderLayout.CENTER);
        jp2.add(jtFilter, BorderLayout.SOUTH);
        main.add(jp2, BorderLayout.CENTER);

        main.add(status, SOUTH);

        setLayout(new BorderLayout());
        add(main, BorderLayout.CENTER);
    }
}
