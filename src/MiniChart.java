import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * MiniChart.java — Enhanced visual version
 *
 * BAR chart improvements:
 *   - Each bar uses a distinct colour from a coordinated palette (blue, teal,
 *     violet, orange, green, coral, amber, cyan) — no more single-colour bars.
 *   - Gradient from a lighter tint at the top to the base colour at the bottom.
 *   - Rounded corners (arc = 8).
 *   - Subtle drop-shadow behind every bar.
 *   - Dashed grid lines for a modern look.
 *   - Hover tooltip showing "Month · ₹ amount" in a floating pill.
 *
 * PIE / DONUT chart improvements:
 *   - Donut hole enlarged to 46 % for a more elegant ring.
 *   - Slice separator gap reduced to 1.5 px white stroke for cleaner appearance.
 *   - Legend shows colour swatch + name + percentage on one row;
 *     if the amount fits, it is also shown on the same line.
 *   - Hover tooltip showing "Category · ₹ amount" in a floating pill.
 *   - Hover slice is drawn 3 px larger (pulled outward) for a "pop" effect.
 *
 * Performance:
 *   - Arc2D.Double instance reused (setArc) — no per-slice allocation.
 *   - Cached HINTS_AA rendering map from AppTheme.
 *   - Cached stroke constants from AppTheme.
 *   - GRID_COLOR, shadow colour, and palette are static final constants.
 */
public class MiniChart extends JPanel {

    public static final int BAR = 0;
    public static final int PIE = 1;

    private final int type;
    private String[] labels      = {};
    private double[] values      = {};
    private Color[]  sliceColors;   // optional override for pie slices
    // barColor field kept for API compatibility; when null the per-bar palette is used
    private Color    barColor    = null;

    // =========================================================================
    // Colour palettes
    // =========================================================================

    /** Six-colour bar palette — one colour per month, coordinated and muted. */
    private static final Color[] BAR_PALETTE = {
        new Color(0x25, 0x63, 0xEB),   // indigo-blue   (Jan-style)
        new Color(0x0D, 0x94, 0x88),   // teal
        new Color(0x7C, 0x3A, 0xED),   // violet
        new Color(0xEA, 0x58, 0x0C),   // orange
        new Color(0x16, 0xA3, 0x4A),   // green
        new Color(0xE1, 0x1D, 0x48),   // coral-rose
        new Color(0xD9, 0x77, 0x06),   // amber
        new Color(0x06, 0x82, 0x99),   // cyan
    };

    /** Pie palette — same family, slightly adjusted for donut use. */
    private static final Color[] PIE_PALETTE = {
        new Color(0x25, 0x63, 0xEB),   // blue
        new Color(0x7C, 0x3A, 0xED),   // violet
        new Color(0x0D, 0x94, 0x88),   // teal
        new Color(0xEA, 0x58, 0x0C),   // orange
        new Color(0xDC, 0x26, 0x26),   // red
        new Color(0x16, 0xA3, 0x4A),   // green
        new Color(0xD9, 0x77, 0x06),   // amber
        new Color(0x06, 0x82, 0x99),   // cyan
        new Color(0xE1, 0x1D, 0x48),   // coral
        new Color(0x4F, 0x46, 0xE5),   // indigo
    };

    // Cached constants
    private static final Color  GRID_COLOR    = new Color(0xED, 0xF2, 0xF7);
    private static final Color  SHADOW_COLOR  = new Color(0, 0, 0, 18);
    private static final Color  TOOLTIP_BG    = new Color(0x1E, 0x29, 0x3B, 230);
    private static final Color  TOOLTIP_FG    = Color.WHITE;
    // Dashed stroke for grid lines — created once
    private static final BasicStroke DASHED_GRID =
        new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{4f, 4f}, 0f);

    // Hover state
    private int     hoveredIndex = -1;   // which bar/slice is under the cursor
    private String  tooltipText  = null;
    private int     tooltipX, tooltipY;

    // Reusable geometry
    private final Arc2D.Double arcShape = new Arc2D.Double();

    // =========================================================================
    // Constructor
    // =========================================================================
    public MiniChart(int type) {
        this.type = type;
        setBackground(AppTheme.CARD_BG);
        setOpaque(true);

        // Mouse motion listener for hover tooltips
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                hoveredIndex = -1;
                tooltipText  = null;
                repaint();
            }
        });
    }

    // =========================================================================
    // Data setters
    // =========================================================================

    /**
     * BAR: supply labels and values.
     * barColor is accepted for API compatibility but ignored when null —
     * the per-bar palette is used instead.
     */
    public void setData(String[] labels, double[] values, Color barColor) {
        this.labels   = labels != null ? labels : new String[0];
        this.values   = values != null ? values : new double[0];
        this.barColor = barColor; // kept for backwards compat; may be null
        hoveredIndex  = -1;
        tooltipText   = null;
        repaint();
    }

    /** PIE: supply category labels, values, and optional per-slice colours. */
    public void setData(String[] labels, double[] values, Color[] colors) {
        this.labels      = labels  != null ? labels  : new String[0];
        this.values      = values  != null ? values  : new double[0];
        this.sliceColors = colors;
        hoveredIndex     = -1;
        tooltipText      = null;
        repaint();
    }

    // =========================================================================
    // Paint dispatch
    // =========================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (labels.length == 0 || values.length == 0) { drawEmpty(g); return; }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHints(AppTheme.HINTS_AA);
        if (type == BAR) drawBar(g2); else drawPie(g2);
        if (tooltipText != null) drawTooltip(g2, tooltipX, tooltipY, tooltipText);
        g2.dispose();
    }

    // =========================================================================
    // Hover hit-testing
    // =========================================================================
    private void updateHover(int mx, int my) {
        if (type == BAR) updateHoverBar(mx, my);
        else             updateHoverPie(mx, my);
    }

    private void updateHoverBar(int mx, int my) {
        int W = getWidth(), H = getHeight();
        int mTop = 28, mRight = 16, mBottom = 42, mLeft = 60;
        int cW = W - mLeft - mRight;
        int cH = H - mTop  - mBottom;
        if (cW <= 0 || cH <= 0) return;

        double maxVal = 0;
        for (double v : values) if (v > maxVal) maxVal = v;
        if (maxVal <= 0) maxVal = 1;

        int n    = labels.length;
        int grpW = cW / Math.max(n, 1);
        int barW = Math.max(10, (int)(grpW * 0.52));
        int bOff = (grpW - barW) / 2;

        int newHover = -1;
        String newTip = null;
        for (int i = 0; i < n; i++) {
            if (values[i] <= 0) continue;
            int bH = (int)((values[i] / maxVal) * cH);
            int x  = mLeft + i * grpW + bOff;
            int y  = mTop + cH - bH;
            if (mx >= x && mx <= x + barW && my >= y && my <= mTop + cH) {
                newHover = i;
                newTip   = labels[i] + "  \u2022  " + AppTheme.rupee(values[i]);
                tooltipX = mx;
                tooltipY = y - 10;
                break;
            }
        }
        if (newHover != hoveredIndex) {
            hoveredIndex = newHover;
            tooltipText  = newTip;
            repaint();
        }
    }

    private void updateHoverPie(int mx, int my) {
        int W = getWidth(), H = getHeight();
        int legendW  = 160;
        int pieAreaW = W - legendW - 12;
        int pad      = 18;
        int diam     = Math.max(60, Math.min(pieAreaW - pad * 2, H - pad * 2));
        int cx = pad + (pieAreaW - diam) / 2;
        int cy = (H - diam) / 2;

        double total = 0;
        for (double v : values) if (v > 0) total += v;
        if (total <= 0) return;

        // Check if cursor is inside the donut ring
        double centerX = cx + diam / 2.0;
        double centerY = cy + diam / 2.0;
        double radius  = diam / 2.0;
        double holeR   = radius * 0.46;
        double dist    = Math.hypot(mx - centerX, my - centerY);
        if (dist > radius || dist < holeR) {
            if (hoveredIndex != -1) { hoveredIndex = -1; tooltipText = null; repaint(); }
            return;
        }

        // Determine which slice
        double angle = Math.toDegrees(Math.atan2(-(my - centerY), mx - centerX));
        if (angle < 0) angle += 360;
        // We start at 90° and go clockwise (negative sweep)
        double startAngle = 90;
        int newHover = -1;
        String newTip = null;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            double sweep = (values[i] / total) * 360.0;
            double end   = startAngle - sweep;
            // Normalise to [0,360)
            double a = (startAngle % 360 + 360) % 360;
            double b = (end        % 360 + 360) % 360;
            boolean inside;
            if (a >= b) inside = angle <= a && angle > b;
            else        inside = angle <= a || angle > b;
            if (inside) {
                newHover = i;
                newTip   = labels[i] + "  \u2022  " + AppTheme.rupee(values[i]);
                tooltipX = mx;
                tooltipY = my - 14;
                break;
            }
            startAngle -= sweep;
        }
        if (newHover != hoveredIndex) {
            hoveredIndex = newHover;
            tooltipText  = newTip;
            repaint();
        }
    }

    // =========================================================================
    // Empty state
    // =========================================================================
    private void drawEmpty(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHints(AppTheme.HINTS_AA);
        g2.setFont(AppTheme.FONT_SMALL);
        g2.setColor(AppTheme.TEXT_MUTED);
        String msg = "No expense data yet.";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
        g2.dispose();
    }

    // =========================================================================
    // BAR CHART
    // =========================================================================
    private void drawBar(Graphics2D g2) {
        int W = getWidth(), H = getHeight();
        int mTop = 28, mRight = 16, mBottom = 48, mLeft = 64;
        int cW = W - mLeft - mRight;
        int cH = H - mTop  - mBottom;
        if (cW <= 0 || cH <= 0) return;

        double maxVal = 0;
        for (double v : values) if (v > maxVal) maxVal = v;
        if (maxVal <= 0) maxVal = 1;

        // --- Background ---
        g2.setColor(AppTheme.CARD_BG);
        g2.fillRect(mLeft, mTop, cW, cH);

        // --- Dashed horizontal grid lines + Y-axis labels ---
        int gridCount = 4;
        FontMetrics fmTiny = g2.getFontMetrics(AppTheme.FONT_TINY);
        g2.setFont(AppTheme.FONT_TINY);
        for (int i = 0; i <= gridCount; i++) {
            double frac = (double) i / gridCount;
            int y = mTop + cH - (int)(frac * cH);

            if (i > 0) {
                g2.setColor(GRID_COLOR);
                g2.setStroke(DASHED_GRID);         // dashed grid for modern look
                g2.drawLine(mLeft, y, mLeft + cW, y);
            }

            g2.setStroke(AppTheme.STROKE_1);
            g2.setColor(AppTheme.TEXT_MUTED);
            String yLbl = shortAmt(maxVal * frac);
            g2.drawString(yLbl, mLeft - fmTiny.stringWidth(yLbl) - 6, y + fmTiny.getAscent()/2 - 1);
        }

        // --- X-axis baseline ---
        g2.setColor(new Color(0xCB, 0xD5, 0xE1));
        g2.setStroke(AppTheme.STROKE_1_5);
        g2.drawLine(mLeft, mTop + cH, mLeft + cW, mTop + cH);

        // --- Bars ---
        int n    = labels.length;
        int grpW = cW / Math.max(n, 1);
        int barW = Math.max(12, (int)(grpW * 0.54));
        int bOff = (grpW - barW) / 2;

        for (int i = 0; i < n; i++) {
            // Choose colour — per-bar palette; fallback to barColor if set
            Color base = barColorFor(i);
            boolean hovered = (i == hoveredIndex);

            if (values[i] <= 0) {
                // Empty bar placeholder — subtle grey tick at baseline
                g2.setColor(new Color(0xE2, 0xE8, 0xF0));
                int x = mLeft + i * grpW + bOff;
                g2.fillRoundRect(x, mTop + cH - 4, barW, 4, 4, 4);
                // Still draw the X label
                g2.setFont(AppTheme.FONT_TINY);
                g2.setColor(AppTheme.TEXT_MUTED);
                String xLbl = labels[i];
                g2.drawString(xLbl, x + (barW - fmTiny.stringWidth(xLbl)) / 2, mTop + cH + 16);
                continue;
            }

            int bH = (int)((values[i] / maxVal) * cH);
            // Hovered bar: slightly taller & lighter for a "pop" effect
            if (hovered) bH = Math.min(bH + 3, cH);
            int x  = mLeft + i * grpW + bOff;
            int y  = mTop + cH - bH;

            // Drop shadow
            g2.setColor(SHADOW_COLOR);
            g2.fillRoundRect(x + 2, y + 3, barW, bH, 8, 8);

            // Gradient fill: light tint → base colour
            Color lighter = blend(Color.WHITE, base, 0.38f);
            g2.setPaint(new GradientPaint(x, y, lighter, x, y + bH, base));
            g2.fillRoundRect(x, y, barW, bH, 8, 8);

            // Thin highlight line at top of bar (gives a 3-D edge)
            g2.setColor(new Color(255, 255, 255, 80));
            g2.setStroke(AppTheme.STROKE_1);
            g2.drawRoundRect(x, y, barW, bH, 8, 8);

            // Value label above bar (bold when hovered)
            g2.setFont(hovered ? AppTheme.FONT_TINY.deriveFont(Font.BOLD) : AppTheme.FONT_TINY);
            g2.setColor(hovered ? base : AppTheme.TEXT_SECONDARY);
            String vStr = shortAmt(values[i]);
            int vx = x + (barW - fmTiny.stringWidth(vStr)) / 2;
            if (y - 4 > mTop + 8) g2.drawString(vStr, vx, y - 4);

            // X-axis label
            g2.setFont(AppTheme.FONT_TINY);
            g2.setColor(hovered ? base : AppTheme.TEXT_MUTED);
            String xLbl = labels[i];
            g2.drawString(xLbl, x + (barW - fmTiny.stringWidth(xLbl)) / 2, mTop + cH + 16);
        }
    }

    // =========================================================================
    // PIE / DONUT CHART
    // =========================================================================
    private void drawPie(Graphics2D g2) {
        int W = getWidth(), H = getHeight();
        int legendW  = 160;
        int pieAreaW = W - legendW - 12;
        int pad      = 16;
        int diam     = Math.max(60, Math.min(pieAreaW - pad * 2, H - pad * 2));
        int cx = pad + (pieAreaW - diam) / 2;
        int cy = (H - diam) / 2;

        double total = 0;
        for (double v : values) if (v > 0) total += v;
        if (total <= 0) { drawEmpty(g2); return; }

        double startAngle = 90;

        // ---- Pass 1: subtle shadow behind the whole ring ----
        g2.setColor(SHADOW_COLOR);
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            double sweep = (values[i] / total) * 360.0;
            arcShape.setArc(cx + 2, cy + 3, diam, diam, startAngle, -sweep, Arc2D.PIE);
            g2.fill(arcShape);
            startAngle -= sweep;
        }

        // ---- Pass 2: coloured slices ----
        startAngle = 90;
        g2.setColor(Color.WHITE);
        g2.setStroke(AppTheme.STROKE_1_5); // thinner separator = more elegant
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            double sweep = (values[i] / total) * 360.0;
            boolean hovered = (i == hoveredIndex);
            Color col = sliceColor(i);

            // Hovered slice: draw slightly larger (pulled outward by 3 px)
            int offX = 0, offY = 0, d = diam;
            if (hovered) {
                double midAngle = Math.toRadians(startAngle - sweep / 2.0);
                offX = (int)(Math.cos(midAngle) * 4);
                offY = (int)(-Math.sin(midAngle) * 4);
                d    = diam + 4;
            }

            arcShape.setArc(cx + offX, cy + offY, d, d, startAngle, -sweep, Arc2D.PIE);
            g2.setColor(col);
            g2.fill(arcShape);
            g2.setColor(Color.WHITE);
            g2.draw(arcShape);

            startAngle -= sweep;
        }

        // ---- Donut hole (46 % of diameter) ----
        int holeD = (int)(diam * 0.46);
        int hx = cx + (diam - holeD) / 2;
        int hy = cy + (diam - holeD) / 2;
        // Slightly off-white hole for depth
        g2.setColor(new Color(0xFA, 0xFC, 0xFF));
        g2.fillOval(hx, hy, holeD, holeD);
        // Thin inner ring border
        g2.setColor(new Color(0xE2, 0xE8, 0xF0, 160));
        g2.setStroke(AppTheme.STROKE_1);
        g2.drawOval(hx, hy, holeD, holeD);

        // ---- Legend ----
        int lx   = pieAreaW + 8;
        int ly   = Math.max(cy, 6);
        int rowH = 20;
        int boxSz = 10;
        g2.setFont(AppTheme.FONT_SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int maxLegW = legendW - boxSz - 16;

        // Count visible slices for centering
        int visible = 0;
        for (double v : values) if (v > 0) visible++;
        int totalLegH = visible * rowH;
        int legStartY = Math.max(cy, (H - totalLegH) / 2);

        int row = 0;
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i] <= 0) continue;
            int y = legStartY + row * rowH;
            if (y + rowH > H - 2) break;
            row++;

            boolean hovered = (i == hoveredIndex);
            Color col = sliceColor(i);

            // Colour swatch
            if (hovered) {
                g2.setColor(col.darker());
                g2.fillRoundRect(lx - 1, y + (rowH - boxSz)/2 - 1, boxSz + 2, boxSz + 2, 4, 4);
            }
            g2.setColor(col);
            g2.fillRoundRect(lx, y + (rowH - boxSz)/2, boxSz, boxSz, 4, 4);

            // Label text: "Name  xx%"
            double pct = (values[i] / total) * 100.0;
            String pctStr = String.format("%.0f%%", pct);
            String name   = clamp(labels[i], 11);
            String txt    = name + "  " + pctStr;
            if (fm.stringWidth(txt) > maxLegW)
                txt = clamp(name, 7) + "  " + pctStr;

            g2.setFont(hovered ? AppTheme.FONT_SMALL.deriveFont(Font.BOLD) : AppTheme.FONT_SMALL);
            g2.setColor(hovered ? AppTheme.TEXT_PRIMARY : AppTheme.TEXT_SECONDARY);
            g2.drawString(txt, lx + boxSz + 6, y + rowH / 2 + fm.getAscent() / 2 - 2);
        }
    }

    // =========================================================================
    // Tooltip pill
    // =========================================================================
    private void drawTooltip(Graphics2D g2, int x, int y, String text) {
        g2.setFont(AppTheme.FONT_SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getHeight();
        int padX = 10, padY = 6;
        int bw = tw + padX * 2;
        int bh = th + padY * 2;

        // Clamp tooltip inside component bounds
        int tx = Math.max(4, Math.min(x - bw / 2, getWidth()  - bw - 4));
        int ty = Math.max(4, Math.min(y - bh, getHeight() - bh - 4));

        // Background pill
        g2.setColor(TOOLTIP_BG);
        g2.fillRoundRect(tx, ty, bw, bh, 8, 8);

        // Text
        g2.setColor(TOOLTIP_FG);
        g2.drawString(text, tx + padX, ty + padY + fm.getAscent() - 1);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns the bar colour for index i, preferring the per-bar palette. */
    private Color barColorFor(int i) {
        if (barColor != null) return barColor; // explicit override from caller
        return BAR_PALETTE[i % BAR_PALETTE.length];
    }

    private Color sliceColor(int i) {
        if (sliceColors != null && i < sliceColors.length && sliceColors[i] != null)
            return sliceColors[i];
        return PIE_PALETTE[i % PIE_PALETTE.length];
    }

    /**
     * Linearly blends two colours.
     * @param a     first colour
     * @param b     second colour
     * @param t     0 = pure a, 1 = pure b
     */
    private static Color blend(Color a, Color b, float t) {
        float s = 1f - t;
        return new Color(
            clampInt((int)(a.getRed()   * s + b.getRed()   * t)),
            clampInt((int)(a.getGreen() * s + b.getGreen() * t)),
            clampInt((int)(a.getBlue()  * s + b.getBlue()  * t))
        );
    }

    private static int clampInt(int v) { return Math.max(0, Math.min(255, v)); }

    /** Compact rupee label for chart axes (no symbol, K/L suffix). */
    private String shortAmt(double v) {
        if (v >= 1_00_000) return String.format("%.1fL", v / 1_00_000.0);
        if (v >= 1_000)    return String.format("%.0fK", v / 1_000.0);
        return String.format("%.0f", v);
    }

    private String clamp(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "\u2026" : s;
    }
}
