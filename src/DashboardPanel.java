import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DashboardPanel.java
 *
 * Main dashboard panel showing:
 *   - KPI cards (Year total, Today, This Month, Transaction count)
 *   - Monthly spending bar chart (last 6 months)
 *   - Category breakdown pie chart
 *   - Recent 10 transactions table with sorting
 *
 * Performance notes:
 *   - applySort() uses setDataVector() — one model event instead of N addRow events.
 *   - KPI card and icon pill paintComponent use cached HINTS_AA map.
 *   - buildStyledTable uses setDoubleBuffered(true).
 *   - Outer JScrollPane configured with AppTheme.smoothScroll().
 */
public class DashboardPanel extends JPanel {

    private final ExpenseManager manager;
    private final Runnable       onAddExpense;
    private final Runnable       onViewAll;

    // KPI value labels
    private JLabel lblTotal, lblToday, lblMonth, lblCount;
    // KPI subtitle labels
    private JLabel lblTotalSub, lblTodaySub, lblMonthSub, lblCountSub;

    // Charts
    private MiniChart barChart, pieChart;

    // Recent table
    private DefaultTableModel recentModel;
    private JTable            recentTable;

    // Sorting controls
    private JComboBox<String> cbSortBy, cbSortOrder;
    private java.util.List<Expense> recentExpenses = new ArrayList<>();

    // Sort-by options
    private static final String[] SORT_BY_OPTIONS   = {"Date", "Category", "Amount"};
    private static final String[] SORT_DATE_OPTIONS = {"Newest to Oldest", "Oldest to Newest"};
    private static final String[] SORT_CAT_OPTIONS  = {"A to Z", "Z to A"};
    private static final String[] SORT_AMT_OPTIONS  = {"Highest to Lowest", "Lowest to Highest"};

    // Column names for the recent table (static — never change)
    private static final String[] RECENT_COLS = {"Date", "Category", "Description", "Amount"};

    // Formatters
    private static final DateTimeFormatter FMT_TODAY   =
            DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter FMT_MONTH   =
            DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter FMT_BAR_LBL =
            DateTimeFormatter.ofPattern("MMM yy");

    // -------------------------------------------------------------------------
    public DashboardPanel(ExpenseManager manager, Runnable onAddExpense, Runnable onViewAll) {
        this.manager      = manager;
        this.onAddExpense = onAddExpense;
        this.onViewAll    = onViewAll;
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

        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildBody(),   BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(content);
        AppTheme.smoothScroll(scroll);
        scroll.setBackground(AppTheme.PAGE_BG);
        add(scroll, BorderLayout.CENTER);
    }

    // ---- Header ---------------------------------------------------------------
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JPanel left = AppTheme.pageHeader("Dashboard",
            "Welcome back  \u00B7  " + LocalDate.now().format(AppTheme.DATE_DISPLAY));
        left.setOpaque(false);

        JButton btnAdd = AppTheme.primaryButton("+ Add Expense");
        btnAdd.addActionListener(e -> onAddExpense.run());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(btnAdd);

        p.add(left,  BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ---- Body -----------------------------------------------------------------
    private JPanel buildBody() {
        JPanel p = new JPanel(new BorderLayout(0, AppTheme.PAD_MD));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, 0, 0));

        p.add(buildKpiRow(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(1, 2, AppTheme.PAD_MD, 0));
        middle.setOpaque(false);
        middle.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, AppTheme.PAD_MD, 0));
        middle.add(buildBarChartCard());
        middle.add(buildPieChartCard());

        p.add(middle,            BorderLayout.CENTER);
        p.add(buildRecentCard(), BorderLayout.SOUTH);
        return p;
    }

    // ---- KPI cards ------------------------------------------------------------
    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppTheme.PAD_MD, 0));
        row.setOpaque(false);

        int       year  = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();

        lblTotal    = new JLabel(AppTheme.rupee(0));
        lblTotalSub = new JLabel(String.valueOf(year));
        row.add(makeKpiCard("Total Expenses This Year", lblTotal, lblTotalSub,
                AppTheme.PRIMARY, AppTheme.PRIMARY_LIGHT, "YR"));

        lblToday    = new JLabel(AppTheme.rupee(0));
        lblTodaySub = new JLabel("Today \u00B7 " + today.format(FMT_TODAY));
        row.add(makeKpiCard("Today's Spending", lblToday, lblTodaySub,
                AppTheme.TEAL, AppTheme.TEAL_LIGHT, "TD"));

        lblMonth    = new JLabel(AppTheme.rupee(0));
        lblMonthSub = new JLabel(today.format(FMT_MONTH));
        row.add(makeKpiCard("This Month's Spending", lblMonth, lblMonthSub,
                AppTheme.VIOLET, AppTheme.VIOLET_LIGHT, "MO"));

        lblCount    = new JLabel("0");
        lblCountSub = new JLabel("All time");
        row.add(makeKpiCard("Total Transactions", lblCount, lblCountSub,
                AppTheme.ORANGE, AppTheme.ORANGE_LIGHT, "TX"));

        return row;
    }

    private JPanel makeKpiCard(String title, JLabel valueLabel, JLabel subLabel,
                                Color accent, Color iconBg, String iconAbbr) {

        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHints(AppTheme.HINTS_AA);

                // Subtle card shadow
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fillRoundRect(2, 3, getWidth() - 2, getHeight() - 2, 12, 12);

                // White card face
                g2.setColor(AppTheme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                // 5-px accent bar at top
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth(), 6, 8, 8);
                g2.fillRect(0, 3, getWidth(), 3);

                g2.dispose();
            }
        };
        wrapper.setBackground(AppTheme.PAGE_BG);
        wrapper.setDoubleBuffered(true);
        wrapper.setBorder(BorderFactory.createEmptyBorder(22, 18, 18, 18));

        JPanel iconPill = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHints(AppTheme.HINTS_AA);
                g2.setColor(iconBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(accent);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(iconAbbr)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(iconAbbr, tx, ty);
                g2.dispose();
            }
        };
        iconPill.setPreferredSize(new Dimension(46, 46));
        iconPill.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(AppTheme.FONT_CARD_LBL);
        titleLbl.setForeground(AppTheme.TEXT_SECONDARY);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(AppTheme.FONT_CARD_VAL);
        valueLabel.setForeground(accent);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        subLabel.setFont(AppTheme.FONT_SMALL);
        subLabel.setForeground(AppTheme.TEXT_MUTED);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        textCol.add(titleLbl);
        textCol.add(Box.createVerticalStrut(8));
        textCol.add(valueLabel);
        textCol.add(Box.createVerticalStrut(5));
        textCol.add(subLabel);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.add(iconPill, BorderLayout.WEST);
        body.add(textCol,  BorderLayout.CENTER);

        wrapper.add(body, BorderLayout.CENTER);
        return wrapper;
    }

    // ---- Bar chart card -------------------------------------------------------
    private JPanel buildBarChartCard() {
        JPanel card = new JPanel(new BorderLayout(0, AppTheme.PAD_SM));
        card.setBackground(AppTheme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new AppTheme.RoundedBorder(AppTheme.BORDER, 10, 1),
            BorderFactory.createEmptyBorder(18, 18, 14, 18)
        ));

        JLabel titleLbl = new JLabel("Monthly Spending Trend");
        titleLbl.setFont(AppTheme.FONT_H3);
        titleLbl.setForeground(AppTheme.TEXT_PRIMARY);

        JLabel subLbl = new JLabel("Last 6 months");
        subLbl.setFont(AppTheme.FONT_SMALL);
        subLbl.setForeground(AppTheme.TEXT_MUTED);

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.add(titleLbl, BorderLayout.WEST);
        hdr.add(subLbl,   BorderLayout.EAST);

        barChart = new MiniChart(MiniChart.BAR);
        barChart.setPreferredSize(new Dimension(0, 220));

        card.add(hdr,      BorderLayout.NORTH);
        card.add(barChart, BorderLayout.CENTER);
        return card;
    }

    // ---- Pie chart card -------------------------------------------------------
    private JPanel buildPieChartCard() {
        JPanel card = new JPanel(new BorderLayout(0, AppTheme.PAD_SM));
        card.setBackground(AppTheme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new AppTheme.RoundedBorder(AppTheme.BORDER, 10, 1),
            BorderFactory.createEmptyBorder(18, 18, 14, 18)
        ));

        JLabel titleLbl = new JLabel("Category Breakdown");
        titleLbl.setFont(AppTheme.FONT_H3);
        titleLbl.setForeground(AppTheme.TEXT_PRIMARY);

        JLabel subLbl = new JLabel("All time");
        subLbl.setFont(AppTheme.FONT_SMALL);
        subLbl.setForeground(AppTheme.TEXT_MUTED);

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.add(titleLbl, BorderLayout.WEST);
        hdr.add(subLbl,   BorderLayout.EAST);

        pieChart = new MiniChart(MiniChart.PIE);
        pieChart.setPreferredSize(new Dimension(0, 220));

        card.add(hdr,      BorderLayout.NORTH);
        card.add(pieChart, BorderLayout.CENTER);
        return card;
    }

    // ---- Recent transactions card ---------------------------------------------
    private JPanel buildRecentCard() {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(AppTheme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new AppTheme.RoundedBorder(AppTheme.BORDER, 10, 1),
            BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));

        JLabel titleLbl = new JLabel("Recent Transactions");
        titleLbl.setFont(AppTheme.FONT_H3);
        titleLbl.setForeground(AppTheme.TEXT_PRIMARY);

        JButton btnViewAll = AppTheme.secondaryButton("View All");
        btnViewAll.addActionListener(e -> { if (onViewAll != null) onViewAll.run(); });

        JPanel topHdr = new JPanel(new BorderLayout());
        topHdr.setOpaque(false);
        topHdr.setBorder(BorderFactory.createEmptyBorder(0, 0, AppTheme.PAD_SM, 0));
        topHdr.add(titleLbl,   BorderLayout.WEST);
        topHdr.add(btnViewAll, BorderLayout.EAST);
        card.add(topHdr, BorderLayout.NORTH);

        // Sorting control bar
        JPanel sortBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        sortBar.setBackground(AppTheme.TABLE_HDR_BG);
        sortBar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppTheme.BORDER));

        JLabel sortLbl = new JLabel("Sort by:");
        sortLbl.setFont(AppTheme.FONT_SMALL);
        sortLbl.setForeground(AppTheme.TEXT_SECONDARY);

        cbSortBy    = AppTheme.comboBox(SORT_BY_OPTIONS);
        cbSortOrder = AppTheme.comboBox(SORT_DATE_OPTIONS);
        cbSortBy.setPreferredSize(new Dimension(110, 26));
        cbSortOrder.setPreferredSize(new Dimension(160, 26));

        cbSortBy.addActionListener(e -> {
            String by = (String) cbSortBy.getSelectedItem();
            String[] opts = switch (by) {
                case "Category" -> SORT_CAT_OPTIONS;
                case "Amount"   -> SORT_AMT_OPTIONS;
                default         -> SORT_DATE_OPTIONS;
            };
            cbSortOrder.setModel(new DefaultComboBoxModel<>(opts));
            applySort();
        });
        cbSortOrder.addActionListener(e -> applySort());

        sortBar.add(sortLbl);
        sortBar.add(cbSortBy);
        sortBar.add(cbSortOrder);

        recentModel = new DefaultTableModel(RECENT_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        recentTable = buildStyledTable(recentModel);
        recentTable.getColumnModel().getColumn(0).setPreferredWidth(105);
        recentTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        recentTable.getColumnModel().getColumn(2).setPreferredWidth(380);
        recentTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        rightAlignCol(recentTable, 3);

        JScrollPane sp = new JScrollPane(recentTable);
        sp.setBorder(null);
        sp.getViewport().setBackground(AppTheme.CARD_BG);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        sp.setPreferredSize(new Dimension(0, 33 * 10 + 28));

        JPanel tableWrapper = new JPanel(new BorderLayout(0, 0));
        tableWrapper.setOpaque(false);
        tableWrapper.add(sortBar, BorderLayout.NORTH);
        tableWrapper.add(sp,      BorderLayout.CENTER);

        card.add(tableWrapper, BorderLayout.CENTER);
        return card;
    }

    // -------------------------------------------------------------------------
    // Refresh — called whenever data changes (always on the EDT)
    // -------------------------------------------------------------------------
    public void refresh() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        double yearTotal  = manager.getYearTotal(year);
        double todayTotal = manager.getTodayTotal();
        double monthTotal = manager.getMonthTotal();
        int    count      = manager.getTransactionCount();

        lblTotal.setText(AppTheme.rupee(yearTotal));
        lblTotalSub.setText("Jan 1 \u2013 Dec 31 \u00B7 " + year);

        lblToday.setText(AppTheme.rupee(todayTotal));
        lblTodaySub.setText("Today \u00B7 " + today.format(FMT_TODAY));

        lblMonth.setText(AppTheme.rupee(monthTotal));
        lblMonthSub.setText(today.format(FMT_MONTH));

        lblCount.setText(String.valueOf(count));
        lblCountSub.setText("All time \u00B7 " + count + " record" + (count == 1 ? "" : "s"));

        // Bar chart — last 6 months
        Map<YearMonth, Double> monthly = manager.getMonthlySummary();
        java.util.List<Map.Entry<YearMonth, Double>> mList = new ArrayList<>(monthly.entrySet());
        int start = Math.max(0, mList.size() - 6);
        java.util.List<Map.Entry<YearMonth, Double>> recent6 = mList.subList(start, mList.size());
        String[] bLabels = recent6.stream()
            .map(e -> e.getKey().atDay(1).format(FMT_BAR_LBL))
            .toArray(String[]::new);
        double[] bValues = recent6.stream().mapToDouble(Map.Entry::getValue).toArray();
        barChart.setData(bLabels, bValues, (Color) null);

        // Pie chart
        Map<String, Double> catMap = manager.getCategorySummary();
        String[] pLabels = catMap.keySet().toArray(new String[0]);
        double[] pValues = catMap.values().stream().mapToDouble(Double::doubleValue).toArray();
        pieChart.setData(pLabels, pValues, (Color[]) null);

        // Recent 10 (newest first before sort)
        ArrayList<Expense> all = manager.getAllExpenses();
        int rStart = Math.max(0, all.size() - 10);
        recentExpenses = new ArrayList<>();
        for (int i = all.size() - 1; i >= rStart; i--) {
            recentExpenses.add(all.get(i));
        }

        applySort();
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------
    private void applySort() {
        if (recentExpenses == null || cbSortBy == null || cbSortOrder == null) return;

        String by    = (String) cbSortBy.getSelectedItem();
        String order = (String) cbSortOrder.getSelectedItem();

        java.util.List<Expense> sorted = new ArrayList<>(recentExpenses);

        Comparator<Expense> comp = switch (by) {
            case "Category" -> Comparator.comparing(Expense::getCategory,
                                   String.CASE_INSENSITIVE_ORDER);
            case "Amount"   -> Comparator.comparingDouble(Expense::getAmount);
            default         -> Comparator.comparing(Expense::getDate);
        };

        boolean descending = order != null && (
            order.equals("Newest to Oldest") ||
            order.equals("Z to A")           ||
            order.equals("Highest to Lowest")
        );
        if (descending) comp = comp.reversed();
        sorted.sort(comp);

        Vector<Vector<Object>> rows = new Vector<>(sorted.size());
        for (Expense e : sorted) {
            Vector<Object> row = new Vector<>(4);
            row.add(e.getDate().format(AppTheme.DATE_DISPLAY));
            row.add(e.getCategory());
            row.add(e.getDescription());
            row.add(AppTheme.rupee(e.getAmount()));
            rows.add(row);
        }
        recentModel.setDataVector(rows, new Vector<>(Arrays.asList(RECENT_COLS)));
        rightAlignCol(recentTable, 3);
    }

    // -------------------------------------------------------------------------
    // Shared table factory — used by DashboardPanel and ReportsPanel
    // -------------------------------------------------------------------------
    static JTable buildStyledTable(DefaultTableModel model) {
        JTable t = new JTable(model) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? AppTheme.CARD_BG : AppTheme.TABLE_ALT_BG);
                }
                return c;
            }
        };
        t.setFont(AppTheme.FONT_TABLE);
        t.setRowHeight(33);
        t.setShowVerticalLines(false);
        t.setGridColor(AppTheme.TABLE_GRID);
        t.setSelectionBackground(AppTheme.TABLE_SEL_BG);
        t.setSelectionForeground(AppTheme.TEXT_PRIMARY);
        t.setIntercellSpacing(new Dimension(12, 0));
        t.setFillsViewportHeight(true);
        t.setDoubleBuffered(true);

        JTableHeader th = t.getTableHeader();
        th.setFont(AppTheme.FONT_TABLE_HDR);
        th.setBackground(AppTheme.TABLE_HDR_BG);
        th.setForeground(AppTheme.TEXT_SECONDARY);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER));
        th.setReorderingAllowed(false);
        return t;
    }

    static void rightAlignCol(JTable t, int col) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        t.getColumnModel().getColumn(col).setCellRenderer(r);
    }
}
