import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * AppTheme.java
 * Central design-token file for the Personal Expense Tracker.
 *
 * Performance notes:
 *   - NumberFormat is cached (creating it per-call is expensive)
 *   - BasicStroke instances are cached as static constants
 *   - RenderingHints map is cached to avoid per-call HashMap allocation
 *   - All colours, fonts, and spacing are static final constants
 */
public final class AppTheme {

    private AppTheme() {} // utility class — no instances

    // =========================================================================
    // Colour palette
    // =========================================================================
    public static final Color SIDEBAR_BG      = new Color(0x17, 0x20, 0x33);
    public static final Color SIDEBAR_ACTIVE  = new Color(0x25, 0x63, 0xEB);
    public static final Color SIDEBAR_HOVER   = new Color(0x1E, 0x2D, 0x48);
    public static final Color SIDEBAR_TEXT    = new Color(0xCB, 0xD5, 0xE1);
    public static final Color SIDEBAR_MUTED   = new Color(0x64, 0x74, 0x8B);
    public static final Color PAGE_BG         = new Color(0xF4, 0xF7, 0xFC);
    public static final Color CARD_BG         = Color.WHITE;
    public static final Color BORDER          = new Color(0xE2, 0xE8, 0xF0);
    public static final Color PRIMARY         = new Color(0x25, 0x63, 0xEB);
    public static final Color PRIMARY_DARK    = new Color(0x1D, 0x4E, 0xD8);
    public static final Color PRIMARY_LIGHT   = new Color(0xDB, 0xEA, 0xFE);
    public static final Color TEAL            = new Color(0x0D, 0x94, 0x88);
    public static final Color TEAL_LIGHT      = new Color(0xCC, 0xFB, 0xF1);
    public static final Color VIOLET          = new Color(0x7C, 0x3A, 0xED);
    public static final Color VIOLET_LIGHT    = new Color(0xED, 0xE9, 0xFE);
    public static final Color ORANGE          = new Color(0xEA, 0x58, 0x0C);
    public static final Color ORANGE_LIGHT    = new Color(0xFF, 0xED, 0xD5);
    public static final Color SUCCESS         = new Color(0x16, 0xA3, 0x4A);
    public static final Color SUCCESS_LIGHT   = new Color(0xDC, 0xFC, 0xE7);
    public static final Color DANGER          = new Color(0xDC, 0x26, 0x26);
    public static final Color DANGER_LIGHT    = new Color(0xFE, 0xE2, 0xE2);
    public static final Color WARNING         = new Color(0xD9, 0x77, 0x06);
    public static final Color WARNING_LIGHT   = new Color(0xFE, 0xF9, 0xC3);
    public static final Color SECONDARY       = new Color(0x38, 0xBD, 0xF8);
    public static final Color CYAN            = new Color(0x06, 0x82, 0x99);
    public static final Color TEXT_PRIMARY    = new Color(0x1E, 0x29, 0x3B);
    public static final Color TEXT_SECONDARY  = new Color(0x64, 0x74, 0x8B);
    public static final Color TEXT_MUTED      = new Color(0x9C, 0xA3, 0xAF);
    public static final Color TEXT_WHITE      = Color.WHITE;
    public static final Color TABLE_HDR_BG    = new Color(0xF8, 0xFA, 0xFC);
    public static final Color TABLE_SEL_BG    = new Color(0xDB, 0xEA, 0xFE);
    public static final Color TABLE_ALT_BG    = new Color(0xF8, 0xFA, 0xFF);
    public static final Color TABLE_GRID      = new Color(0xF1, 0xF5, 0xF9);
    public static final Color SEC_BTN_BG      = new Color(0xE2, 0xE8, 0xF0);
    public static final Color SEC_BTN_HOVER   = new Color(0xCB, 0xD5, 0xE1);

    // =========================================================================
    // Fonts
    // =========================================================================
    private static final String FF = "Segoe UI";
    public static final Font FONT_H1        = new Font(FF, Font.BOLD,  22);
    public static final Font FONT_H2        = new Font(FF, Font.BOLD,  17);
    public static final Font FONT_H3        = new Font(FF, Font.BOLD,  14);
    public static final Font FONT_BODY      = new Font(FF, Font.PLAIN, 13);
    public static final Font FONT_BODY_MED  = new Font(FF, Font.BOLD,  13);
    public static final Font FONT_SMALL     = new Font(FF, Font.PLAIN, 11);
    public static final Font FONT_TINY      = new Font(FF, Font.PLAIN, 10);
    public static final Font FONT_NAV       = new Font(FF, Font.BOLD,  13);
    public static final Font FONT_CARD_VAL  = new Font(FF, Font.BOLD,  24);
    public static final Font FONT_CARD_LBL  = new Font(FF, Font.PLAIN, 12);
    public static final Font FONT_BTN       = new Font(FF, Font.BOLD,  13);
    public static final Font FONT_TABLE     = new Font(FF, Font.PLAIN, 13);
    public static final Font FONT_TABLE_HDR = new Font(FF, Font.BOLD,  12);

    // =========================================================================
    // Spacing
    // =========================================================================
    public static final int PAD_SM =  8;
    public static final int PAD_MD = 16;
    public static final int PAD_LG = 24;
    public static final int PAD_XL = 32;

    // =========================================================================
    // Cached strokes — allocated once, reused everywhere
    // =========================================================================
    public static final BasicStroke STROKE_1   = new BasicStroke(1.0f);
    public static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    public static final BasicStroke STROKE_2   = new BasicStroke(2.0f);

    // =========================================================================
    // Cached rendering hints map — avoids per-call HashMap allocation
    // =========================================================================
    public static final RenderingHints HINTS_AA;
    static {
        HINTS_AA = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                                      RenderingHints.VALUE_ANTIALIAS_ON);
        HINTS_AA.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                     RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        HINTS_AA.put(RenderingHints.KEY_RENDERING,
                     RenderingHints.VALUE_RENDER_QUALITY);
    }

    // =========================================================================
    // Formatters
    // =========================================================================
    public static final Locale          INDIA_LOCALE  = Locale.of("en", "IN");
    public static final DateTimeFormatter DATE_DISPLAY  = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter DATE_INPUT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter MONTH_DISPLAY = DateTimeFormatter.ofPattern("MMM yyyy");

    /**
     * Cached NumberFormat instance — NOT thread-safe but Swing is single-threaded
     * (EDT only), so caching is safe here.
     * Creating a new NumberFormat per call is ~5–10 µs; with large tables this
     * was the biggest per-row cost.
     */
    private static final NumberFormat RUPEE_FMT =
            NumberFormat.getCurrencyInstance(INDIA_LOCALE);

    /** Formats a number as Indian Rupee: ₹1,234.50 */
    public static String rupee(double v) {
        return RUPEE_FMT.format(v);
    }

    // =========================================================================
    // Button factories
    // =========================================================================

    public static JButton primaryButton(String text) {
        return makeColorButton(text, PRIMARY, PRIMARY_DARK, TEXT_WHITE);
    }

    public static JButton dangerButton(String text) {
        return makeColorButton(text, DANGER, new Color(0xB9, 0x1C, 0x1C), TEXT_WHITE);
    }

    public static JButton secondaryButton(String text) {
        return makeColorButton(text, SEC_BTN_BG, SEC_BTN_HOVER, TEXT_PRIMARY);
    }

    public static JButton successButton(String text) {
        return makeColorButton(text, SUCCESS, new Color(0x15, 0x80, 0x3D), TEXT_WHITE);
    }

    /**
     * Core button factory.
     * Performance: STROKE_1_5 is now a cached constant — no allocation in paintComponent.
     */
    public static JButton makeColorButton(String text, Color bg, Color hoverBg, Color fg) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHints(HINTS_AA);
                g2.setColor(hovered ? hoverBg : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setFont(getFont());
                g2.setColor(fg);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                if (isFocusOwner()) {
                    g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 120));
                    g2.setStroke(STROKE_1_5); // cached — no allocation
                    g2.drawRoundRect(2, 2, getWidth()-4, getHeight()-4, 6, 6);
                }
                g2.dispose();
            }

            @Override protected void paintBorder(Graphics g) {}
        };
        btn.setFont(FONT_BTN);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(
            btn.getFontMetrics(FONT_BTN).stringWidth(text) + 40, 34));
        return btn;
    }

    // =========================================================================
    // Field / combo factories
    // =========================================================================

    public static JTextField textField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setFont(FONT_BODY);
        tf.setBackground(CARD_BG);
        tf.setForeground(TEXT_PRIMARY);
        tf.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(BORDER, 6, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        return tf;
    }

    public static <T> JComboBox<T> comboBox(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setFont(FONT_BODY);
        cb.setBackground(CARD_BG);
        cb.setForeground(TEXT_PRIMARY);
        return cb;
    }

    // =========================================================================
    // Label / panel factories
    // =========================================================================

    public static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BODY_MED);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    public static JPanel pageHeader(String title, String subtitle) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel h1 = new JLabel(title);
        h1.setFont(FONT_H1);
        h1.setForeground(TEXT_PRIMARY);
        h1.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = new JLabel(subtitle);
        sub.setFont(FONT_BODY);
        sub.setForeground(TEXT_SECONDARY);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(h1);
        p.add(Box.createVerticalStrut(3));
        p.add(sub);
        return p;
    }

    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(BORDER, 10, 1),
            BorderFactory.createEmptyBorder(PAD_MD, PAD_MD, PAD_MD, PAD_MD)
        ));
        return p;
    }

    /**
     * Configures a JScrollPane for smooth, fast scrolling.
     * Call this on every JScrollPane in the app.
     */
    public static void smoothScroll(JScrollPane sp) {
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setBlockIncrement(60);
        sp.getHorizontalScrollBar().setUnitIncrement(18);
        sp.setBorder(null);
        // Use blit scrolling where available for smoother repaints
        sp.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
    }

    // =========================================================================
    // RoundedBorder
    // Performance: uses cached STROKE_1 constant instead of new BasicStroke().
    // =========================================================================
    public static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int   arc;
        private final int   thickness;
        // Pre-computed stroke — created once per border instance, not per paint
        private final BasicStroke stroke;

        public RoundedBorder(Color color, int arc, int thickness) {
            this.color     = color;
            this.arc       = arc;
            this.thickness = thickness;
            this.stroke    = (thickness == 1) ? STROKE_1
                           : (thickness == 2) ? STROKE_2
                           : new BasicStroke(thickness);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            if (thickness <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHints(HINTS_AA);
            g2.setColor(color);
            g2.setStroke(stroke); // cached — no allocation
            g2.drawRoundRect(x + thickness/2, y + thickness/2,
                             w - thickness, h - thickness, arc, arc);
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) {
            return new Insets(thickness+2, thickness+2, thickness+2, thickness+2);
        }
        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(thickness+2, thickness+2, thickness+2, thickness+2); return i;
        }
    }
}
