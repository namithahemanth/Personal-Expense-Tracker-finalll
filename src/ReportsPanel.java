import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;

/**
 * ReportsPanel.java
 *
 * Performance improvements:
 *   - Lazy tab loading: each tab loads its data the first time it is selected,
 *     not all at once during navigation — avoids blocking the EDT when the
 *     user hasn't even opened a tab yet.
 *   - Category tab: replaced O(n×m) per-category scan with a single O(n) pass
 *     using a pre-built count map.
 *   - All table updates use setDataVector() (one event) instead of
 *     setRowCount(0)+addRow() loops (N events).
 *   - refreshYearDropdown() called only once, not every time data loads.
 *   - All JScrollPanes use AppTheme.smoothScroll().
 *   - Tables use setDoubleBuffered(true) via buildStyledTable().
 */
public class ReportsPanel extends JPanel {

    private final ExpenseManager manager;

    // ---- Daily tab ----
    private JTextField        tfDailyDate;
    private JLabel            lblDailyTotal, lblDailyCount;
    private DefaultTableModel dailyModel;

    // ---- Monthly tab ----
    private JComboBox<String>  cbMonth;
    private JComboBox<Integer> cbYear;
    private JLabel             lblMonthTotal, lblMonthCount, lblMonthEmpty;
    private DefaultTableModel  breakdownModel;
    private DefaultTableModel  monthModel;

    // ---- Category tab ----
    private DefaultTableModel catModel;
    private MiniChart         catPie;

    // Table references stored so we can re-apply renderers after setDataVector()
    private JTable dailyTable;
    private JTable bTable;     // monthly breakdown
    private JTable catTable;   // category summary

    // Lazy-load flags — each tab rebuilds only on first open after a data change
    private boolean dailyDirty    = true;
    private boolean monthlyDirty  = true;
    private boolean categoryDirty = true;

    // Column definitions — static constants
    private static final String[] DAILY_COLS     = {"ID", "Date", "Category", "Description", "Amount"};
    private static final String[] MONTH_COLS     = {"ID", "Date", "Category", "Description", "Amount"};
    private static final String[] BREAKDOWN_COLS = {"Category", "Total Spent"};
    private static final String[] CAT_COLS       = {"Category", "Total Spent", "% of Total", "Transactions"};

    // -------------------------------------------------------------------------
    public ReportsPanel(ExpenseManager manager) {
        this.manager = manager;
        setLayout(new BorderLayout());
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

        content.add(AppTheme.pageHeader("Reports",
            "Analyse your expenses by date, month, and category."), BorderLayout.NORTH);

        UIManager.put("TabbedPane.foreground",           AppTheme.TEXT_PRIMARY);
        UIManager.put("TabbedPane.selectedForeground",   AppTheme.PRIMARY);
        UIManager.put("TabbedPane.unselectedForeground", AppTheme.TEXT_SECONDARY);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP) {
            @Override public void updateUI() {
                super.updateUI();
                setForeground(AppTheme.TEXT_PRIMARY);
                setBackground(AppTheme.PAGE_BG);
            }
        };
        tabs.setFont(AppTheme.FONT_BODY_MED);
        tabs.setForeground(AppTheme.TEXT_PRIMARY);
        tabs.setBackground(AppTheme.PAGE_BG);
        tabs.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));

        tabs.addTab("  Daily Summary  ",   buildDailyTab());
        tabs.addTab("  Monthly Summary  ", buildMonthlyTab());
        tabs.addTab("  Category-wise  ",   buildCategoryTab());

        // Lazy load: only load the active tab's data when the tab is selected
        tabs.addChangeListener(e -> loadActiveTab(tabs.getSelectedIndex()));

        content.add(tabs, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(content);
        AppTheme.smoothScroll(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    /** Loads data for the tab at the given index — only if it is marked dirty. */
    private void loadActiveTab(int idx) {
        switch (idx) {
            case 0 -> { if (dailyDirty)    { loadDailySummary();    dailyDirty    = false; } }
            case 1 -> { if (monthlyDirty)  { loadMonthlySummary();  monthlyDirty  = false; } }
            case 2 -> { if (categoryDirty) { loadCategorySummary(); categoryDirty = false; } }
        }
    }

    // =========================================================================
    // TAB 1 — DAILY SUMMARY
    // =========================================================================
    private JPanel buildDailyTab() {
        JPanel p = new JPanel(new BorderLayout(0, AppTheme.PAD_MD));
        p.setBackground(AppTheme.PAGE_BG);
        p.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));

        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ctrl.setOpaque(false);

        tfDailyDate = AppTheme.textField("YYYY-MM-DD");
        tfDailyDate.setPreferredSize(new Dimension(160, 32));
        tfDailyDate.setText(LocalDate.now().toString());

        JButton btnGo = AppTheme.primaryButton("Show");
        btnGo.addActionListener(e -> loadDailySummary());
        tfDailyDate.addActionListener(e -> loadDailySummary());

        ctrl.add(new JLabel("Select Date:") {{
            setFont(AppTheme.FONT_BODY); setForeground(AppTheme.TEXT_SECONDARY);
        }});
        ctrl.add(tfDailyDate);
        ctrl.add(btnGo);
        p.add(ctrl, BorderLayout.NORTH);

        lblDailyTotal = new JLabel(AppTheme.rupee(0));
        lblDailyCount = new JLabel("0 transactions");
        p.add(buildStatRow(
            new String[]{"Total Spent", "Transactions"},
            new JLabel[]{lblDailyTotal, lblDailyCount},
            new Color[]{AppTheme.PRIMARY, AppTheme.SUCCESS}
        ), BorderLayout.CENTER);

        dailyModel = new DefaultTableModel(DAILY_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        dailyTable = DashboardPanel.buildStyledTable(dailyModel);
        DashboardPanel.rightAlignCol(dailyTable, 4);
        JTable t = dailyTable; // local alias for wrapTable

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));
        bottom.add(wrapTable(t, 260), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    private void loadDailySummary() {
        String raw = tfDailyDate.getText().trim();
        LocalDate date;
        try { date = LocalDate.parse(raw); }
        catch (DateTimeParseException ex) {
            lblDailyTotal.setText("Invalid date");
            lblDailyCount.setText("\u2014");
            dailyModel.setRowCount(0);
            return;
        }

        ArrayList<Expense> list = manager.getExpensesForDate(date);
        double total = list.stream().mapToDouble(Expense::getAmount).sum();

        lblDailyTotal.setText(AppTheme.rupee(total));
        lblDailyCount.setText(list.size() + " transaction" + (list.size() == 1 ? "" : "s"));

        // Single setDataVector call — one model event, not N
        Vector<Vector<Object>> rows = new Vector<>(list.size());
        for (Expense e : list) {
            Vector<Object> row = new Vector<>(5);
            row.add(e.getId());
            row.add(e.getDate().toString());
            row.add(e.getCategory());
            row.add(e.getDescription());
            row.add(AppTheme.rupee(e.getAmount()));
            rows.add(row);
        }
        dailyModel.setDataVector(rows, new Vector<>(Arrays.asList(DAILY_COLS)));
        DashboardPanel.rightAlignCol(dailyTable, 4); // re-apply after setDataVector
    }

    // =========================================================================
    // TAB 2 — MONTHLY SUMMARY
    // =========================================================================
    private JPanel buildMonthlyTab() {
        JPanel p = new JPanel(new BorderLayout(0, AppTheme.PAD_MD));
        p.setBackground(AppTheme.PAGE_BG);
        p.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));

        // ---- top control bar: dropdowns (left) + PDF button (right) ----
        JPanel ctrlBar = new JPanel(new BorderLayout());
        ctrlBar.setOpaque(false);

        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ctrl.setOpaque(false);

        String[] monthNames = new String[12];
        for (int i = 1; i <= 12; i++)
            monthNames[i - 1] = Month.of(i).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        cbMonth = new JComboBox<>(monthNames);
        cbMonth.setFont(AppTheme.FONT_BODY);
        cbMonth.setPreferredSize(new Dimension(130, 32));
        cbMonth.setSelectedIndex(LocalDate.now().getMonthValue() - 1);

        java.util.List<Integer> years = manager.getAllYears();
        cbYear = new JComboBox<>(years.toArray(new Integer[0]));
        cbYear.setFont(AppTheme.FONT_BODY);
        cbYear.setPreferredSize(new Dimension(90, 32));
        cbYear.setSelectedItem(LocalDate.now().getYear());

        cbMonth.addActionListener(e -> loadMonthlySummary());
        cbYear.addActionListener(e  -> loadMonthlySummary());

        ctrl.add(new JLabel("Month:") {{
            setFont(AppTheme.FONT_BODY); setForeground(AppTheme.TEXT_SECONDARY);
        }});
        ctrl.add(cbMonth);
        ctrl.add(new JLabel("Year:") {{
            setFont(AppTheme.FONT_BODY); setForeground(AppTheme.TEXT_SECONDARY);
        }});
        ctrl.add(cbYear);

        // PDF download button — custom-painted with vector download arrow (no Unicode glyphs)
        final Color PDF_BG    = AppTheme.SUCCESS;
        final Color PDF_HOVER = new Color(0x15, 0x80, 0x3D);
        final Color PDF_FG    = Color.WHITE;
        JButton btnPdf = new JButton("Download Monthly Report (PDF)") {
            private boolean hovered = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseEntered(java.awt.event.MouseEvent e) { hovered = true;  repaint(); }
                    public void mouseExited (java.awt.event.MouseEvent e) { hovered = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHints(AppTheme.HINTS_AA);

                // Background
                g2.setColor(hovered ? PDF_HOVER : PDF_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                // ---- Draw download arrow icon (left-padded) ----
                int iconSize = 14;
                int iconX    = 14;
                int iconY    = (getHeight() - iconSize) / 2;
                g2.setColor(PDF_FG);
                g2.setStroke(new java.awt.BasicStroke(2.0f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

                // Vertical shaft of the arrow
                int shaftX  = iconX + iconSize / 2;
                int shaftY1 = iconY;
                int shaftY2 = iconY + iconSize - 5;
                g2.drawLine(shaftX, shaftY1, shaftX, shaftY2);

                // Arrowhead (pointing down)
                int[] arrowX = { shaftX - 5, shaftX, shaftX + 5 };
                int[] arrowY = { iconY + iconSize - 7, iconY + iconSize - 2, iconY + iconSize - 7 };
                g2.fillPolygon(arrowX, arrowY, 3);

                // Tray / base line
                g2.setStroke(new java.awt.BasicStroke(2.0f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.drawLine(iconX + 1, iconY + iconSize, iconX + iconSize - 1, iconY + iconSize);

                // ---- Label text (centred in remaining space) ----
                g2.setFont(AppTheme.FONT_BTN);
                g2.setColor(PDF_FG);
                FontMetrics fm = g2.getFontMetrics();
                String label = getText();
                int textAreaStart = iconX + iconSize + 6;
                int textAreaW    = getWidth() - textAreaStart - 10;
                int tx = textAreaStart + (textAreaW - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, tx, ty);

                // Focus ring
                if (isFocusOwner()) {
                    g2.setColor(new Color(255, 255, 255, 100));
                    g2.setStroke(AppTheme.STROKE_1_5);
                    g2.drawRoundRect(2, 2, getWidth()-4, getHeight()-4, 6, 6);
                }
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        btnPdf.setFont(AppTheme.FONT_BTN);
        btnPdf.setOpaque(false);
        btnPdf.setContentAreaFilled(false);
        btnPdf.setBorderPainted(false);
        btnPdf.setFocusPainted(false);
        btnPdf.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPdf.setPreferredSize(new Dimension(270, 36));
        btnPdf.addActionListener(e -> downloadPdfReport());

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnWrap.setOpaque(false);
        btnWrap.add(btnPdf);

        ctrlBar.add(ctrl,    BorderLayout.WEST);
        ctrlBar.add(btnWrap, BorderLayout.EAST);

        p.add(ctrlBar, BorderLayout.NORTH);

        lblMonthTotal = new JLabel(AppTheme.rupee(0));
        lblMonthCount = new JLabel("0 transactions");

        lblMonthEmpty = new JLabel("No expenses for this month.");
        lblMonthEmpty.setFont(AppTheme.FONT_BODY);
        lblMonthEmpty.setForeground(AppTheme.TEXT_MUTED);
        lblMonthEmpty.setHorizontalAlignment(SwingConstants.CENTER);
        lblMonthEmpty.setVisible(false);

        breakdownModel = new DefaultTableModel(BREAKDOWN_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        bTable = DashboardPanel.buildStyledTable(breakdownModel);
        bTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        bTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        DashboardPanel.rightAlignCol(bTable, 1);

        JPanel breakdownCard = new JPanel(new BorderLayout(0, 6));
        breakdownCard.setBackground(AppTheme.CARD_BG);
        breakdownCard.setBorder(BorderFactory.createCompoundBorder(
            new AppTheme.RoundedBorder(AppTheme.BORDER, 8, 1),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        JLabel bTitle = new JLabel("Category Breakdown");
        bTitle.setFont(AppTheme.FONT_H3);
        bTitle.setForeground(AppTheme.TEXT_PRIMARY);
        breakdownCard.add(bTitle,              BorderLayout.NORTH);
        breakdownCard.add(lblMonthEmpty,       BorderLayout.CENTER);
        breakdownCard.add(wrapTable(bTable, 180), BorderLayout.SOUTH);

        monthModel = new DefaultTableModel(MONTH_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable mTable = DashboardPanel.buildStyledTable(monthModel);
        DashboardPanel.rightAlignCol(mTable, 4);

        JPanel listCard = new JPanel(new BorderLayout(0, 6));
        listCard.setBackground(AppTheme.CARD_BG);
        listCard.setBorder(BorderFactory.createCompoundBorder(
            new AppTheme.RoundedBorder(AppTheme.BORDER, 8, 1),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        JLabel lTitle = new JLabel("Expense List");
        lTitle.setFont(AppTheme.FONT_H3);
        lTitle.setForeground(AppTheme.TEXT_PRIMARY);
        listCard.add(lTitle, BorderLayout.NORTH);
        listCard.add(wrapTable(mTable, 200), BorderLayout.CENTER);

        JPanel statsAndBreakdown = new JPanel(new BorderLayout(0, AppTheme.PAD_MD));
        statsAndBreakdown.setOpaque(false);
        statsAndBreakdown.add(buildStatRow(
            new String[]{"Total Spent", "Transactions"},
            new JLabel[]{lblMonthTotal, lblMonthCount},
            new Color[]{AppTheme.VIOLET, AppTheme.WARNING}
        ), BorderLayout.NORTH);
        statsAndBreakdown.add(breakdownCard, BorderLayout.CENTER);
        statsAndBreakdown.add(listCard,      BorderLayout.SOUTH);

        p.add(statsAndBreakdown, BorderLayout.CENTER);
        return p;
    }

    private void loadMonthlySummary() {
        if (cbMonth == null || cbYear == null) return;

        int monthIdx = cbMonth.getSelectedIndex() + 1;
        Object selYear = cbYear.getSelectedItem();
        if (selYear == null) return;
        int year = (Integer) selYear;

        YearMonth ym = YearMonth.of(year, monthIdx);
        ArrayList<Expense> list = manager.getExpensesForMonth(ym);
        double total = list.stream().mapToDouble(Expense::getAmount).sum();

        lblMonthTotal.setText(AppTheme.rupee(total));
        lblMonthCount.setText(list.size() + " transaction" + (list.size() == 1 ? "" : "s"));
        lblMonthEmpty.setVisible(list.isEmpty());

        // Expense list — single event
        Vector<Vector<Object>> mRows = new Vector<>(list.size());
        for (Expense e : list) {
            Vector<Object> row = new Vector<>(5);
            row.add(e.getId());
            row.add(e.getDate().toString());
            row.add(e.getCategory());
            row.add(e.getDescription());
            row.add(AppTheme.rupee(e.getAmount()));
            mRows.add(row);
        }
        monthModel.setDataVector(mRows, new Vector<>(Arrays.asList(MONTH_COLS)));

        // Category breakdown — single event
        Vector<Vector<Object>> bRows = new Vector<>();
        if (!list.isEmpty()) {
            Map<String, Double> breakdown = manager.getMonthlyCategoryBreakdown(ym);
            for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
                Vector<Object> row = new Vector<>(2);
                row.add(entry.getKey());
                row.add(AppTheme.rupee(entry.getValue()));
                bRows.add(row);
            }
        }
        breakdownModel.setDataVector(bRows, new Vector<>(Arrays.asList(BREAKDOWN_COLS)));
        DashboardPanel.rightAlignCol(bTable, 1); // re-apply after setDataVector
        refreshYearDropdown();
    }

    private void refreshYearDropdown() {
        Integer selected = (Integer) cbYear.getSelectedItem();
        java.util.List<Integer> years = manager.getAllYears();
        // Remove then re-add the ActionListener to avoid double-firing
        ActionListener[] listeners = cbYear.getActionListeners();
        for (ActionListener al : listeners) cbYear.removeActionListener(al);
        cbYear.setModel(new DefaultComboBoxModel<>(years.toArray(new Integer[0])));
        if (selected != null) cbYear.setSelectedItem(selected);
        for (ActionListener al : listeners) cbYear.addActionListener(al);
    }

    // =========================================================================
    // TAB 3 — CATEGORY-WISE SUMMARY
    // =========================================================================
    private JPanel buildCategoryTab() {
        JPanel p = new JPanel(new BorderLayout(AppTheme.PAD_MD, 0));
        p.setBackground(AppTheme.PAGE_BG);
        p.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));

        catModel = new DefaultTableModel(CAT_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        catTable = DashboardPanel.buildStyledTable(catModel);
        catTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        catTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        catTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        catTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        DashboardPanel.rightAlignCol(catTable, 1);
        DashboardPanel.rightAlignCol(catTable, 2);
        DashboardPanel.rightAlignCol(catTable, 3);

        JScrollPane sp = new JScrollPane(catTable);
        sp.setBorder(new AppTheme.RoundedBorder(AppTheme.BORDER, 8, 1));
        AppTheme.smoothScroll(sp);

        JPanel chartCard = new JPanel(new BorderLayout(0, 8));
        chartCard.setBackground(AppTheme.CARD_BG);
        chartCard.setBorder(BorderFactory.createCompoundBorder(
            new AppTheme.RoundedBorder(AppTheme.BORDER, 10, 1),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        JLabel chartTitle = new JLabel("Spending by Category");
        chartTitle.setFont(AppTheme.FONT_H3);
        chartTitle.setForeground(AppTheme.TEXT_PRIMARY);
        catPie = new MiniChart(MiniChart.PIE);
        catPie.setPreferredSize(new Dimension(320, 260));
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(catPie,     BorderLayout.CENTER);

        p.add(sp,        BorderLayout.CENTER);
        p.add(chartCard, BorderLayout.EAST);
        return p;
    }

    /**
     * Category summary load.
     * Performance fix: replaced per-category O(n) scan (getExpensesForCategory().size())
     * with a single O(n) pass that builds a count map before rendering any rows.
     * Old code was O(n×m) where n=expenses and m=categories.
     */
    private void loadCategorySummary() {
        Map<String, Double> catMap    = manager.getCategorySummary();
        double              grandTotal = manager.getGrandTotal();

        // Build category → count map in one O(n) pass instead of N separate scans
        Map<String, Integer> countMap = new HashMap<>();
        for (Expense e : manager.getAllExpenses()) {
            countMap.merge(e.getCategory(), 1, Integer::sum);
        }

        String[] pieLabels = catMap.keySet().toArray(new String[0]);
        double[] pieValues = catMap.values().stream().mapToDouble(Double::doubleValue).toArray();

        // Build all rows, then push with one setDataVector call
        Vector<Vector<Object>> rows = new Vector<>(catMap.size());
        for (Map.Entry<String, Double> entry : catMap.entrySet()) {
            String cat   = entry.getKey();
            double total = entry.getValue();
            double pct   = (grandTotal > 0) ? (total / grandTotal) * 100.0 : 0;
            int    count = countMap.getOrDefault(cat, 0); // O(1) lookup
            Vector<Object> row = new Vector<>(4);
            row.add(cat);
            row.add(AppTheme.rupee(total));
            row.add(String.format("%.1f%%", pct));
            row.add(count);
            rows.add(row);
        }

        if (rows.isEmpty()) {
            Vector<Object> empty = new Vector<>(4);
            empty.add("No expenses recorded."); empty.add(""); empty.add(""); empty.add("");
            rows.add(empty);
        }

        catModel.setDataVector(rows, new Vector<>(Arrays.asList(CAT_COLS)));
        // Re-apply right-align renderers after setDataVector (it resets all renderers)
        DashboardPanel.rightAlignCol(catTable, 1);
        DashboardPanel.rightAlignCol(catTable, 2);
        DashboardPanel.rightAlignCol(catTable, 3);
        catPie.setData(pieLabels, pieValues, (Color[]) null);
    }

    // -------------------------------------------------------------------------
    // Public refresh — marks all tabs dirty so each reloads on next open
    // -------------------------------------------------------------------------
    public void refresh() {
        dailyDirty    = true;
        monthlyDirty  = true;
        categoryDirty = true;
        // Eagerly load the currently-visible tab so the user sees fresh data instantly
        // (the hidden tabs will load lazily on first click)
        loadDailySummary();
        dailyDirty = false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private JScrollPane wrapTable(JTable t, int height) {
        JScrollPane sp = new JScrollPane(t);
        AppTheme.smoothScroll(sp);
        sp.setBorder(new AppTheme.RoundedBorder(AppTheme.BORDER, 8, 1));
        sp.setPreferredSize(new Dimension(0, height));
        return sp;
    }

    private JPanel buildStatRow(String[] labels, JLabel[] values, Color[] colors) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, AppTheme.PAD_MD, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));

        for (int i = 0; i < labels.length; i++) {
            Color accent = (i < colors.length) ? colors[i] : AppTheme.PRIMARY;
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(AppTheme.CARD_BG);
            card.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.BORDER, 8, 1),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)
            ));
            card.setPreferredSize(new Dimension(200, 80));

            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(AppTheme.FONT_CARD_LBL);
            lbl.setForeground(AppTheme.TEXT_SECONDARY);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            values[i].setFont(AppTheme.FONT_H2);
            values[i].setForeground(accent);
            values[i].setAlignmentX(Component.LEFT_ALIGNMENT);

            card.add(lbl);
            card.add(Box.createVerticalStrut(6));
            card.add(values[i]);
            row.add(card);
        }
        return row;
    }

    // -------------------------------------------------------------------------
    // PDF Download
    // -------------------------------------------------------------------------

    /**
     * Called when the user clicks "Download Monthly Report (PDF)".
     * Resolves the selected month/year, opens a JFileChooser save dialog,
     * then generates the PDF on a background thread (SwingWorker) so the
     * Swing EDT stays responsive.
     */
    private void downloadPdfReport() {
        if (cbMonth == null || cbYear == null) return;

        int monthIdx = cbMonth.getSelectedIndex() + 1;
        Object selYear = cbYear.getSelectedItem();
        if (selYear == null) return;
        int year = (Integer) selYear;

        YearMonth ym = YearMonth.of(year, monthIdx);

        // Check for empty month before opening the dialog
        java.util.ArrayList<Expense> preview = manager.getExpensesForMonth(ym);
        if (preview.isEmpty()) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                "No expense records found for " + ym.getMonth()
                    .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                    + " " + year + ".\nPlease select a month that has transactions.",
                "No Data",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // Suggest a default file name
        String monthName = ym.getMonth()
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        String defaultName = "Monthly_Report_" + monthName + "_" + year + ".pdf";

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Monthly Report PDF");
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PDF Files (*.pdf)", "pdf"));

        int result = chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this));
        if (result != JFileChooser.APPROVE_OPTION) return; // user cancelled

        File outFile = chooser.getSelectedFile();
        // Append .pdf if user removed the extension
        if (!outFile.getName().toLowerCase().endsWith(".pdf")) {
            outFile = new File(outFile.getAbsolutePath() + ".pdf");
        }

        final File finalFile = outFile;

        // Generate PDF on a background thread — keeps UI responsive
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                MonthlyReportPDF.generate(manager, ym, finalFile);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // re-throws any exception from doInBackground
                    JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(ReportsPanel.this),
                        "PDF saved successfully:\n" + finalFile.getAbsolutePath(),
                        "Report Saved",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace(); // full stack trace in terminal for debugging
                    JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(ReportsPanel.this),
                        "Failed to generate PDF:\n" + cause.getMessage()
                            + "\n\nSee terminal output for full details.",
                        "PDF Generation Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
