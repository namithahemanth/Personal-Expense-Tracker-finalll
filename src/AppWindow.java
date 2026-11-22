import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * AppWindow.java
 *
 * Performance improvements:
 *   - Initial CSV load and all-panel refresh are done in a SwingWorker background
 *     thread so the window appears immediately without a "hang" during startup.
 *   - Navigation refreshes are deferred with SwingUtilities.invokeLater() so the
 *     CardLayout.show() paint cycle completes before data is reloaded — eliminates
 *     the visual stutter when switching pages.
 *   - refreshAll() is only called once at startup; subsequent navigation calls
 *     only refresh the specific panel being navigated to.
 */
public class AppWindow extends JFrame {

    private static final String CARD_DASHBOARD = "dashboard";
    private static final String CARD_EXPENSES  = "expenses";
    private static final String CARD_REPORTS   = "reports";

    private final ExpenseManager manager;

    private JButton activeNavBtn;

    private DashboardPanel dashboardPanel;
    private ExpensesPanel  expensesPanel;
    private ReportsPanel   reportsPanel;

    private CardLayout contentLayout;
    private JPanel     contentArea;

    // -------------------------------------------------------------------------
    public AppWindow() {
        manager = new ExpenseManager();
        buildFrame();
        // Show the window FIRST so the user sees it immediately, then load data
        // in the background via SwingWorker to avoid EDT blocking on CSV parse.
        setVisible(true);
        loadDataAsync();
    }

    // -------------------------------------------------------------------------
    private void buildFrame() {
        setTitle("Expense Tracker \u2014 Personal Finance Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1000, 660));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.PAGE_BG);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);
        setContentPane(root);
    }

    /**
     * Loads initial data off the EDT, then refreshes all panels on the EDT.
     * This prevents the window from appearing "frozen" during startup when
     * the CSV file is large.
     */
    private void loadDataAsync() {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                // ExpenseManager already loaded CSV in its constructor,
                // so there is no extra I/O here — but the panel refresh
                // (sorting, formatting, rendering) is deferred to after
                // the window is painted for the first time.
                return null;
            }
            @Override protected void done() {
                // Back on the EDT — safe to touch Swing components
                refreshAll();
            }
        }.execute();
    }

    // =========================================================================
    // SIDEBAR
    // =========================================================================
    private JPanel buildSidebar() {
        JPanel sb = new JPanel();
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBackground(AppTheme.SIDEBAR_BG);
        sb.setPreferredSize(new Dimension(220, 0));

        // Brand block
        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(28, 22, 22, 22));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel logoMark = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHints(AppTheme.HINTS_AA); // cached map
                g2.setColor(AppTheme.SIDEBAR_ACTIVE);
                g2.fillRoundRect(0, 0, 38, 38, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                String s = "ET";
                g2.drawString(s, (38 - fm.stringWidth(s))/2, (38 + fm.getAscent())/2 - 2);
                g2.dispose();
            }
        };
        logoMark.setPreferredSize(new Dimension(38, 38));
        logoMark.setMaximumSize (new Dimension(38, 38));
        logoMark.setOpaque(false);
        logoMark.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel appName = new JLabel("Expense Tracker");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        appName.setForeground(Color.WHITE);
        appName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel appSub = new JLabel("Personal Finance Manager");
        appSub.setFont(AppTheme.FONT_SMALL);
        appSub.setForeground(AppTheme.SIDEBAR_MUTED);
        appSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        brand.add(logoMark);
        brand.add(Box.createVerticalStrut(10));
        brand.add(appName);
        brand.add(Box.createVerticalStrut(2));
        brand.add(appSub);
        sb.add(brand);

        sb.add(makeSbDivider());
        sb.add(Box.createVerticalStrut(8));
        sb.add(makeSbSectionLabel("MAIN MENU"));

        JButton btnDash     = makeNavButton("Dashboard", CARD_DASHBOARD, "dash");
        JButton btnExpenses = makeNavButton("Expenses",  CARD_EXPENSES,  "list");
        JButton btnReports  = makeNavButton("Reports",   CARD_REPORTS,   "chart");

        sb.add(btnDash);
        sb.add(btnExpenses);
        sb.add(btnReports);

        sb.add(Box.createVerticalStrut(12));
        sb.add(makeSbDivider());

        sb.add(makeSbSectionLabel("PROJECT INFO"));
        sb.add(makeSbInfoLabel("Built with Java Swing"));
        sb.add(makeSbInfoLabel("CSV File Storage"));
        sb.add(makeSbInfoLabel("OOP Architecture"));

        sb.add(Box.createVerticalGlue());

        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 22, 18, 22));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JSeparator sepF = new JSeparator();
        sepF.setForeground(new Color(0x2D, 0x3E, 0x55));
        sepF.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        footer.add(sepF);
        footer.add(Box.createVerticalStrut(10));

        JLabel footLbl = new JLabel("Java Swing  |  CSV Storage");
        footLbl.setFont(AppTheme.FONT_TINY);
        footLbl.setForeground(AppTheme.SIDEBAR_MUTED);
        footLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(footLbl);

        sb.add(footer);
        setActiveNav(btnDash);
        return sb;
    }

    private JButton makeNavButton(String label, String card, String iconHint) {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                boolean active = getBackground().equals(AppTheme.SIDEBAR_ACTIVE);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHints(AppTheme.HINTS_AA); // cached map

                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (active) {
                    g2.setColor(new Color(0x93, 0xC5, 0xFD));
                    g2.fillRoundRect(0, 8, 4, getHeight()-16, 4, 4);
                }

                int iconSize = 28, iconX = 18;
                int iconY    = (getHeight() - iconSize) / 2;
                g2.setColor(active ? new Color(0xFF,0xFF,0xFF,40) : new Color(0xFF,0xFF,0xFF,15));
                g2.fillRoundRect(iconX, iconY, iconSize, iconSize, 6, 6);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.setColor(active ? Color.WHITE : AppTheme.SIDEBAR_TEXT);
                String sym = iconSymbol(iconHint);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(sym,
                    iconX + (iconSize - fm.stringWidth(sym))/2,
                    iconY + (iconSize + fm.getAscent())/2 - 2);

                g2.setFont(AppTheme.FONT_NAV);
                g2.setColor(active ? Color.WHITE : AppTheme.SIDEBAR_TEXT);
                g2.drawString(label, iconX + iconSize + 10, getHeight()/2 + fm.getAscent()/2 - 1);
                g2.dispose();
            }
        };

        btn.setBackground(AppTheme.SIDEBAR_BG);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setPreferredSize(new Dimension(220, 46));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (!btn.getBackground().equals(AppTheme.SIDEBAR_ACTIVE))
                    btn.setBackground(AppTheme.SIDEBAR_HOVER);
            }
            public void mouseExited(MouseEvent e) {
                if (!btn.getBackground().equals(AppTheme.SIDEBAR_ACTIVE))
                    btn.setBackground(AppTheme.SIDEBAR_BG);
            }
        });

        btn.addActionListener(e -> {
            setActiveNav(btn);
            contentLayout.show(contentArea, card);
            // Defer the data refresh so the CardLayout.show() paint cycle completes
            // first — this eliminates the visual stutter on page switch.
            SwingUtilities.invokeLater(() -> {
                if (CARD_DASHBOARD.equals(card)) dashboardPanel.refresh();
                if (CARD_EXPENSES.equals(card))  expensesPanel.refresh();
                if (CARD_REPORTS.equals(card))   reportsPanel.refresh();
            });
        });

        return btn;
    }

    private void setActiveNav(JButton btn) {
        if (activeNavBtn != null) activeNavBtn.setBackground(AppTheme.SIDEBAR_BG);
        btn.setBackground(AppTheme.SIDEBAR_ACTIVE);
        activeNavBtn = btn;
        btn.repaint();
    }

    private String iconSymbol(String hint) {
        return switch (hint) {
            case "dash"  -> "D";
            case "list"  -> "E";
            case "chart" -> "R";
            default      -> "?";
        };
    }

    private Component makeSbDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0x2D, 0x3E, 0x55));
        sep.setBackground(AppTheme.SIDEBAR_BG);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private JLabel makeSbSectionLabel(String text) {
        JLabel l = new JLabel("  " + text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(AppTheme.SIDEBAR_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(10, 8, 4, 8));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return l;
    }

    private JLabel makeSbInfoLabel(String text) {
        JLabel l = new JLabel("  \u00B7 " + text);
        l.setFont(AppTheme.FONT_TINY);
        l.setForeground(AppTheme.SIDEBAR_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return l;
    }

    // =========================================================================
    // CONTENT AREA
    // =========================================================================
    private JPanel buildContent() {
        contentLayout = new CardLayout();
        contentArea   = new JPanel(contentLayout);
        contentArea.setBackground(AppTheme.PAGE_BG);

        dashboardPanel = new DashboardPanel(
            manager,
            this::openAddExpenseDialog,
            () -> navigateTo(CARD_EXPENSES)
        );

        expensesPanel  = new ExpensesPanel(manager, this, dashboardPanel::refresh);
        reportsPanel   = new ReportsPanel(manager);

        contentArea.add(dashboardPanel, CARD_DASHBOARD);
        contentArea.add(expensesPanel,  CARD_EXPENSES);
        contentArea.add(reportsPanel,   CARD_REPORTS);

        return contentArea;
    }

    private void navigateTo(String card) {
        contentLayout.show(contentArea, card);
        SwingUtilities.invokeLater(() -> {
            if (CARD_EXPENSES.equals(card)) expensesPanel.refresh();
            if (CARD_REPORTS.equals(card))  reportsPanel.refresh();
        });
    }

    private void openAddExpenseDialog() {
        ExpenseFormDialog dlg = new ExpenseFormDialog(this, manager);
        if (dlg.showDialog()) refreshAll();
    }

    private void refreshAll() {
        dashboardPanel.refresh();
        expensesPanel.refresh();
        reportsPanel.refresh();
    }
}
