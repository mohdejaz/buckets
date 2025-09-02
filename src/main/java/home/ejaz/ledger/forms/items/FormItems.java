package home.ejaz.ledger.forms.items;

import home.ejaz.ledger.Config;
import home.ejaz.ledger.FormMenu;
import home.ejaz.ledger.models.Item;
import home.ejaz.ledger.models.ItemsTableModel;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class FormItems extends JDialog {
    private static final Logger logger = Logger.getLogger(FormItems.class.getName());

    private JButton jbNew = new JButton("New");
    private JButton jbEdit = new JButton("Edit");
    private JButton jbDel = new JButton("Del");
    private JButton jbRefresh = new JButton("Refresh");
    private JTextField jtFilter = new JTextField("");

    private int gap = Config.getGap();
    private java.util.List<Item> list = new ArrayList<>();
    private ItemsTableModel itemTblModel = new ItemsTableModel();
    private JTable table = new JTable(itemTblModel);
    private FormMenu parent;
    private boolean init = false;
    private long lastSelectItem = -1;

//    private FormTransaction formTransaction;

    private void refresh() {
//        try {
//            this.list.clear();
//            this.list.addAll(DAOTransaction.getInstance().getTransactions(jtFilter.getText()));
//            this.itemTblModel.setTransactions(list);
//            for (int row = 0; row < this.list.size(); row++) {
//                Transaction tx = this.itemTblModel.getTransaction(row);
//                if (tx.id == lastSelectTx) {
//                    this.table.addRowSelectionInterval(row, row);
//                    break;
//                }
//            }
//            lbStatus.setText(" Balance: " + getBalance());
//        } catch (Exception e) {
//            e.printStackTrace(System.err);
//            System.exit(1);
//        }
    }

    private void doDelete() {
//        if (this.table.getSelectedRow() != -1) {
//            Transaction tx = this.itemTblModel.getTransaction(this.table.getSelectedRow());
//            try {
//                DAOTransaction.getInstance().delete(tx.id);
//                refresh();
//                parent.txDelete(tx.id);
//            } catch (SQLException e) {
//                e.printStackTrace(System.err);
//            }
//        }
    }

    private void doAdd() {
//        formTransaction.clear(true);
//        formTransaction.init();
//        formTransaction.setVisible(true);
//        refresh();
//        parent.txAdded(-1);
    }

    private void doEdit() {
//        formTransaction.clear(true);
//        formTransaction.init();
//        for (int row : table.getSelectedRows()) {
//            Transaction tx = this.itemTblModel.getTransaction(row);
//            this.lastSelectTx = tx.id;
//            formTransaction.setTransaction(tx);
//            formTransaction.setVisible(true);
//            parent.txUpdate(-1);
//        }
//        refresh();
    }


    public void init() {
        refresh();

        if (!init) {
            table.setShowGrid(true);
            table.setShowHorizontalLines(true);
            table.setShowVerticalLines(true);
            table.setGridColor(Color.lightGray);
            table.getTableHeader().setReorderingAllowed(false);
            table.setIntercellSpacing(new Dimension(5, 5));

            this.table.getSelectionModel().addListSelectionListener(l -> {
                int row = table.getSelectedRow();
                if (row != -1) {
                    Item tx = this.itemTblModel.getItem(row);
                    this.lastSelectItem = tx.id();
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

            // formTransaction = new FormTransaction(this);

            jbRefresh.addActionListener(al -> refresh());
            jbNew.addActionListener(al -> doAdd());
            jbEdit.addActionListener(al -> doEdit());
            jbDel.addActionListener(al -> doDelete());

            init = true;
        }
    }

    public FormItems(FormMenu parent) {
        super(parent);

        this.parent = parent;

        init();

        JPanel main = new JPanel();
        main.setLayout(new BorderLayout());
        main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

        JPanel jp1 = new JPanel(new GridLayout(2, 1));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, gap, gap));
        btnPanel.add(jbNew);
        btnPanel.add(jbEdit);
        btnPanel.add(jbDel);
        btnPanel.add(jbRefresh);
        jp1.add(btnPanel);
        jp1.add(jtFilter);
        main.add(jp1, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowHeight(Config.getDotsPerSquare());
        JScrollPane jsp = new JScrollPane(table);
        main.add(jsp, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(main, BorderLayout.CENTER);

        setTitle("Items");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLocationRelativeTo(null);
        setSize(800, 600);
    }
}
