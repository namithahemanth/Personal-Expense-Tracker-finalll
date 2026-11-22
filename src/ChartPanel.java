import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * ChartPanel.java
 * A lightweight, self-contained Swing panel that draws either:
 *   - A vertical BAR CHART  (monthly totals)
 *   - A PIE CHART           (category-wise breakdown)
 *
 * Uses only java.awt.Graphics2D — no external charting library needed.
 * Data is supplied via setData(...) and the panel calls repaint() automatically.
 */
public class ChartPanel extends JPanel {

    // Chart type constants
    public static final int BAR = 0;
    public static final int PIE = 1;

    private final int chartType;

    // Data fields
    private String[] labels  = new String[0];
    private double[] values  = new double[0];
    private Color[]  colors  = new Color[0];   // used for pie; bar uses a single barColor
    private Color    barColor = new Color(37, 99, 235); // default blue for bar chart

    // Default palette for pie (expanded per category count)
    private static final Color[] DEFAULT_PALETTE = {
        new Color(37,  99, 235),   // blue
        new Color(109, 40, 217),   // purple
        new Color(5,  150, 105),   // green
        new Color(234, 88,  12),   // orange
        new Color(220,  38,  38),  // red
        new Color(217, 119,   6),  // amber
        new Color(6,  182, 212),   // cyan
        new Color(236,  72, 153),  // pink
    };

    private static final Locale INDIA = Locale.of("en", "IN");
    private static final Font FONT_AXIS  = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_VAL   = new Font("Segoe UI", Font.BOLD,  11);
    private static final Font FONT_EMPTY = new Font("Segoe UI", Font.ITALIC, 13);
    private static final Font FONT_PIE_LEGEND = new Font("Segoe UI", Font.PLAIN, 12);

    public ChartPanel(int chartType) {
        this.chartType = chartType;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(300, 260));
    }

    // -------------------------------------------------------------------------
    // Data setters
    // -------------------------------------------------------------------------

    /**
     * Used for BAR chart — sets labels, values, and the bar fill colour.
     */
    public void setData(String[] labels, double[] values, Color barColor) {
        this.labels   = labels   != null ? labels   : new String[0];
        this.values   = values   != null ? values   : new double[0];
        this.barColor = barColor != null ? barColor : DEFAULT_PALETTE[0];
        this.colors   = new Color[0];
        repaint();
    }

    /**
     * Used for PIE chart — sets labels, values, and per-slice colours.
     */
    public void setData(String[] labels, double[] values, Color[] colors) {
        this.labels = labels != null ? labels : new String[0];
        this.values = values != null ? values : new double[0];
        this.colors = colors != null ? colors : DEFAULT_PALETTE;
        repaint();
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        // Anti-aliasing for smooth edges
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (labels.length == 0 || values.length == 0) {
            drawEmpty(g2);
        } else if (chartType == BAR) {
            drawBarChart(g2);
        } else {
            drawPieChart(g2);
        }
        g2.dispose();
    }

    // -------------------------------------------------------------------------
    // Empty state
    // -------------------------------------------------------------------------
    private void drawEmpty(Graphics2D g2) {
        g2.setFont(FONT_EMPTY);
        g2.setColor(new Color(148, 163, 184));
        String msg = "No data yet. Add expenses to see the chart.";
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth()  - fm.stringWidth(msg)) / 2;
        int y = getHeight() / 2;
        g2.drawString(msg, x, y);
    }

    // =========================================================================
    // BAR CHART
    // =========================================================================
    private void drawBarChart(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();

        // Margins: top, right, bottom (for labels), left (for Y axis)
        int marginTop    = 20;
        int marginRight  = 16;
        int marginBottom = 50;
        int marginLeft   = 64;

        int chartW = w - marginLeft - marginRight;
        int chartH = h - marginTop  - marginBottom;

        // Draw background grid
        double maxVal = 0;
        for (double v : values) if (v > maxVal) maxVal = v;
        if (maxVal == 0) maxVal = 1; // prevent division by zero

        int gridLines = 5;
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= gridLines; i++) {
            double fraction = (double) i / gridLines;
            int y = marginTop + (int) ((1 - fraction) * chartH);
            // Grid line
            g2.setColor(new Color(241, 245, 249));
            g2.drawLine(marginLeft, y, marginLeft + chartW, y);
            // Y-axis value label
            g2.setFont(FONT_AXIS);
            g2.setColor(new Color(100, 116, 139));
            String yLabel = shortRupee(maxVal * fraction);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(yLabel, marginLeft - fm.stringWidth(yLabel) - 6, y + fm.getAscent() / 2);
        }

        // Draw X-axis line
        g2.setColor(new Color(203, 213, 225));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(marginLeft, marginTop + chartH, marginLeft + chartW, marginTop + chartH);

        // Draw bars
        int n         = labels.length;
        int groupW    = chartW / n;
        int barW      = Math.max(12, (int) (groupW * 0.55));
        int barOffset = (groupW - barW) / 2;

        Color barFill    = barColor;
        Color barHover   = barFill.brighter();

        for (int i = 0; i < n; i++) {
            double fraction = values[i] / maxVal;
            int barH   = (int) (fraction * chartH);
            int x      = marginLeft + i * groupW + barOffset;
            int y      = marginTop + chartH - barH;

            // Bar shadow (subtle)
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(x + 2, y + 2, barW, barH, 6, 6);

            // Bar fill
            g2.setColor(barFill);
            g2.fillRoundRect(x, y, barW, barH, 6, 6);

            // Value label on top of bar
            g2.setFont(FONT_VAL);
            g2.setColor(new Color(30, 58, 138));
            String valStr = shortRupee(values[i]);
            FontMetrics fm = g2.getFontMetrics();
            int vx = x + (barW - fm.stringWidth(valStr)) / 2;
            if (y - 4 > marginTop) {
                g2.drawString(valStr, vx, y - 4);
            }

            // X-axis label (rotated for long month labels)
            g2.setFont(FONT_AXIS);
            g2.setColor(new Color(100, 116, 139));
            String xLabel = labels[i].length() > 7 ? labels[i].substring(2) : labels[i]; // e.g. "2024-06" → "24-06"
            fm = g2.getFontMetrics();
            int lx = x + (barW - fm.stringWidth(xLabel)) / 2;
            g2.drawString(xLabel, lx, marginTop + chartH + 16);
        }
    }

    // =========================================================================
    // PIE CHART
    // =========================================================================
    private void drawPieChart(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();

        // Legend on the right; pie on the left
        int legendWidth = 140;
        int pieArea     = w - legendWidth - 16;
        int padding     = 20;
        int diameter    = Math.min(pieArea - padding * 2, h - padding * 2);
        diameter        = Math.max(diameter, 60);

        int cx = padding + (pieArea - diameter) / 2;
        int cy = (h - diameter) / 2;

        // Total
        double total = 0;
        for (double v : values) total += v;
        if (total == 0) { drawEmpty(g2); return; }

        // Draw slices
        double startAngle = 0;
        for (int i = 0; i < values.length; i++) {
            double arcAngle = (values[i] / total) * 360.0;
            Color sliceColor = (colors != null && i < colors.length)
                ? colors[i] : DEFAULT_PALETTE[i % DEFAULT_PALETTE.length];

            // Slice shadow
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fill(new Arc2D.Double(cx + 2, cy + 2, diameter, diameter,
                startAngle, arcAngle, Arc2D.PIE));

            // Slice fill
            g2.setColor(sliceColor);
            g2.fill(new Arc2D.Double(cx, cy, diameter, diameter,
                startAngle, arcAngle, Arc2D.PIE));

            // Thin white border between slices
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Arc2D.Double(cx, cy, diameter, diameter,
                startAngle, arcAngle, Arc2D.PIE));

            startAngle += arcAngle;
        }

        // Legend
        int legendX  = pieArea + 4;
        int legendY  = cy + 4;
        int rowH     = 22;
        int boxSize  = 12;

        g2.setFont(FONT_PIE_LEGEND);
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < values.length && i < labels.length; i++) {
            Color sliceColor = (colors != null && i < colors.length)
                ? colors[i] : DEFAULT_PALETTE[i % DEFAULT_PALETTE.length];

            int ly = legendY + i * rowH;
            if (ly + rowH > h - 4) break; // don't overflow panel

            // Colour swatch
            g2.setColor(sliceColor);
            g2.fillRoundRect(legendX, ly, boxSize, boxSize, 4, 4);

            // Label
            g2.setColor(new Color(15, 23, 42));
            String pct = String.format("%.0f%%", (values[i] / total) * 100);
            String txt = labels[i] + " " + pct;
            // Truncate if too long
            while (fm.stringWidth(txt) > legendWidth - boxSize - 8 && txt.length() > 4) {
                txt = txt.substring(0, txt.length() - 2) + "…";
            }
            g2.drawString(txt, legendX + boxSize + 6, ly + boxSize);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Formats a rupee value compactly for chart labels (e.g. ₹1.2K, ₹45K). */
    private String shortRupee(double v) {
        if (v >= 1_00_000) return String.format("₹%.1fL",  v / 1_00_000.0);
        if (v >= 1_000)    return String.format("₹%.1fK",  v / 1_000.0);
        return String.format("₹%.0f", v);
    }
}
