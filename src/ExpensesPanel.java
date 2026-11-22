import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Arrays;

/**
 * ExpensesPanel.java
 *
 * Performance improvements:
 *   - Document listener for search is debounced with a 180 ms Swing Timer so
 *     fast typists don't trigger a full table rebuild on every keystroke.
 *   - populateTable() uses setDataVector() (single model event) instead of
 *     setRowCount(0) + N×addRow() (N model events → N repaints).
 *   - JScrollPane configured with AppTheme.smoothScroll().
 *   - Table is double-buffered via buildStyledTable().
 */
public class ExpensesPanel extends JPanel {

    private final ExpenseManager manager;
    private final Frame          parentFrame;
    /** Called after any add, edit, or delete so other panels stay in sync. */
    private final Runnable       onDataChange;

    // Toolbar components
    private JTextField        tfSearch;
    private JComboBox<String> cbCatFilter;
    private JLabel            lblCount;

    // Table
    private DefaultTableModel tableModel;
    private JTable            table;

    // Buttons
    private JButton btnAdd, btnEdit, btnDelete;

    // Debounce timer for search — avoids rebuilding the table on every keystroke
    private javax.swing.Timer searchDebounceTimer;
    private static final int  DEBOUNCE_MS = 180;

    private static final String ALL_CAT = "All Categories";

    // Column names — static, never change
    private static final String[] COLS = {"ID", "Date", "Category", "Description", "Amount"};

    // -------------------------------------------------------------------------
    public ExpensesPanel(ExpenseManager manager, Frame parentFrame) {
        this(manager, parentFrame, null);
    }

    public ExpensesPanel(ExpenseManager manager, Frame parentFrame, Runnable onDataChange) {
        this.manager      = manager;
        this.parentFrame  = parentFrame;
        this.onDataChange = onDataChange;
        setLayout(new BorderLayout(0, 0));
        setBackground(AppTheme.PAGE_BG);
        setDoubleBuffered(true);
        buildUI();
    }

    // -------------------------------------------------------------------------
    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(0, AppTheme.PAD_MD));
        content.setBackground(AppTheme.PAGE_BG);
        content.setBorder(BorderFactory.createEmptyBorder(
            AppTheme.PAD_LG, AppTheme.PAD_LG, AppTheme.PAD_LG, AppTheme.PAD_LG));

        content.add(buildHeader(),    BorderLayout.NORTH);
        content.add(buildTableCard(), BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(content);
        AppTheme.smoothScroll(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    // ---- Page header ----------------------------------------------------------
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JPanel left = AppTheme.pageHeader("Expenses",
            "Manage, search, and analyse your expense records.");
        left.setOpaque(false);

        btnAdd = AppTheme.primaryButton("+ Add Expense");
        btnAdd.addActionListener(e -> openAddDialog());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(btnAdd);

        p.add(left,  BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ---- Table card -----------------------------------------------------------
    private JPanel buildTableCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppTheme.CARD_BG);
        card.setBorder(new AppTheme.RoundedBorder(AppTheme.BORDER, 10, 1));

        card.add(buildToolbar(),   BorderLayout.NORTH);
        card.add(buildTable(),     BorderLayout.CENTER);
        card.add(buildActionBar(), BorderLayout.SOUTH);
        return card;
    }

    // ---- Toolbar --------------------------------------------------------------
    private JPanel buildToolbar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        tfSearch = AppTheme.textField("Search expenses...");
        tfSearch.setPreferredSize(new Dimension(240, 32));
        tfSearch.addActionListener(e -> applyFilter()); // Enter key → immediate

        // Debounced document listener — fires 180 ms after the user stops typing.
        // Without debouncing, every keystroke rebuilds the entire table.
        searchDebounceTimer = new javax.swing.Timer(DEBOUNCE_MS, e -> applyFilter());
        searchDebounceTimer.setRepeats(false);

        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { restartDebounce(); }
            public void removeUpdate(DocumentEvent e)  { restartDebounce(); }
            public void changedUpdate(DocumentEvent e) { restartDebounce(); }
        });

        String[] catOptions = buildCategoryOptions();
        cbCatFilter = AppTheme.comboBox(catOptions);
        cbCatFilter.setPreferredSize(new Dimension(170, 32));
        cbCatFilter.addActionListener(e -> applyFilter()); // combo is instant, no debounce needed

        JButton btnClear = AppTheme.secondaryButton("Clear");
        btnClear.addActionListener(e -> {
            searchDebounceTimer.stop();
            tfSearch.setText("");
            cbCatFilter.setSelectedIndex(0);
            applyFilter();
        });

        left.add(new JLabel("Search:") {{
            setFont(AppTheme.FONT_BODY); setForeground(AppTheme.TEXT_SECONDARY);
        }});
        left.add(tfSearch);
        left.add(new JLabel("Category:") {{
            setFont(AppTheme.FONT_BODY); setForeground(AppTheme.TEXT_SECONDARY);
        }});
        left.add(cbCatFilter);
        left.add(btnClear);

        lblCount = new JLabel("");
        lblCount.setFont(AppTheme.FONT_SMALL);
        lblCount.setForeground(AppTheme.TEXT_MUTED);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        right.setOpaque(false);
        right.add(lblCount);

        p.add(left,  BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private void restartDebounce() {
        searchDebounceTimer.restart();
    }

    // ---- Main table -----------------------------------------------------------
    private JScrollPane buildTable() {
        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 0 ? Integer.class : String.class;
            }
        };

        table = DashboardPanel.buildStyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        DashboardPanel.rightAlignCol(table, 4);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openEditDialog();
            }
        });

        JScrollPane sp = new JScrollPane(table);
        AppTheme.smoothScroll(sp);
        sp.getViewport().setBackground(AppTheme.CARD_BG);
        return sp;
    }

    // ---- Action bar -----------------------------------------------------------
    private JPanel buildActionBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.CARD_BG);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER));

        JLabel hint = new JLabel("Select a row to edit or delete  \u00B7  Double-click to edit");
        hint.setFont(AppTheme.FONT_SMALL);
        hint.setForeground(AppTheme.TEXT_MUTED);

        btnEdit   = AppTheme.secondaryButton("Edit Selected");
        btnDelete = AppTheme.dangerButton("Delete Selected");

        btnEdit.addActionListener(e   -> openEditDialog());
        btnDelete.addActionListener(e -> deleteSelected());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        left.setOpaque(false);
        left.add(hint);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        right.setOpaque(false);
        right.add(btnEdit);
        right.add(btnDelete);

        p.add(left,  BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------
    private void openAddDialog() {
        ExpenseFormDialog dlg = new ExpenseFormDialog(parentFrame, manager);
        if (dlg.showDialog()) { refresh(); notifyDataChange(); }
    }

    private void openEditDialog() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(parentFrame,
                "Please select a row to edit.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Expense target = manager.findById(id);
        if (target == null) return;

        ExpenseFormDialog dlg = new ExpenseFormDialog(parentFrame, manager, target);
        if (dlg.showDialog()) { refresh(); notifyDataChange(); }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(parentFrame,
                "Please select a row to delete.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int    id   = (int)    tableModel.getValueAt(row, 0);
        String desc = (String) tableModel.getValueAt(row, 3);

        int ok = JOptionPane.showConfirmDialog(parentFrame,
            "<html>Delete expense <b>#" + id + "</b>?<br><i>" + desc + "</i><br><br>"
            + "This action cannot be undone.</html>",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (ok == JOptionPane.YES_OPTION) {
            try {
                manager.deleteExpense(id);
                refresh();
                notifyDataChange();
                JOptionPane.showMessageDialog(parentFrame,
                    "Expense deleted successfully.", "Deleted", JOptionPane.INFORMATION_MESSAGE);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(parentFrame,
                    ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void notifyDataChange() {
        if (onDataChange != null) onDataChange.run();
    }

    // -------------------------------------------------------------------------
    // Filter + table refresh
    // -------------------------------------------------------------------------
    private void applyFilter() {
        String keyword = tfSearch.getText().trim();
        String cat     = (String) cbCatFilter.getSelectedItem();

        ArrayList<Expense> results = manager.globalSearch(keyword);

        if (cat != null && !cat.equals(ALL_CAT)) {
            results.removeIf(e -> !e.getCategory().equalsIgnoreCase(cat));
        }

        populateTable(results);
    }

    /**
     * Replaces all table rows in a single setDataVector() call.
     * This fires ONE tableDataChanged event instead of N addRow() events,
     * eliminating N-1 unnecessary repaints for large lists.
     */
    private void populateTable(ArrayList<Expense> list) {
        Vector<Vector<Object>> rows = new Vector<>(list.size());
        for (Expense e : list) {
            Vector<Object> row = new Vector<>(5);
            row.add(e.getId());
            row.add(e.getDate().format(AppTheme.DATE_DISPLAY));
            row.add(e.getCategory());
            row.add(e.getDescription());
            row.add(AppTheme.rupee(e.getAmount()));
            rows.add(row);
        }
        tableModel.setDataVector(rows, new Vector<>(Arrays.asList(COLS)));
        // Re-apply column widths and right-align after setDataVector (it resets them)
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        DashboardPanel.rightAlignCol(table, 4);

        int n = list.size();
        lblCount.setText(n + " record" + (n == 1 ? "" : "s") + " found");
    }

    private String[] buildCategoryOptions() {
        java.util.LinkedHashSet<String> cats = new java.util.LinkedHashSet<>();
        cats.add(ALL_CAT);
        for (Expense e : manager.getAllExpenses()) cats.add(e.getCategory());
        for (String c : Expense.CATEGORIES) cats.add(c);
        return cats.toArray(new String[0]);
    }

    /** Called after add/edit/delete — preserves the current filter selection. */
    public void refresh() {
        String prevCat = (String) cbCatFilter.getSelectedItem();
        cbCatFilter.setModel(new DefaultComboBoxModel<>(buildCategoryOptions()));
        for (int i = 0; i < cbCatFilter.getItemCount(); i++) {
            if (cbCatFilter.getItemAt(i).equals(prevCat)) {
                cbCatFilter.setSelectedIndex(i);
                break;
            }
        }
        applyFilter();
    }
}
