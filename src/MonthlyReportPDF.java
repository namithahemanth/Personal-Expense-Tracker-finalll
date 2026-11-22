import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * MonthlyReportPDF.java
 *
 * Professional monthly expense report using Apache PDFBox 3.x.
 * All data from ExpenseManager — no sample data.
 *
 * Single continuous flow on one or more A4 pages:
 *   Banner → Monthly Summary stats → Category Breakdown table
 *   → Detailed Expense Transactions table → Total row → Footer
 */
public class MonthlyReportPDF {

    // ── Page geometry (A4: 595 × 842 pt) ────────────────────────────────────
    private static final float PAGE_W    = PDRectangle.A4.getWidth();
    private static final float PAGE_H    = PDRectangle.A4.getHeight();
    private static final float MARGIN_L  = 45f;
    private static final float MARGIN_R  = 45f;
    private static final float MARGIN_T  = 45f;
    private static final float MARGIN_B  = 45f;
    private static final float CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R;   // 505 pt

    // ── Row / header heights ─────────────────────────────────────────────────
    private static final float ROW_H        = 19f;
    private static final float TBL_HDR_H    = 22f;
    private static final float SECTION_H    = 22f;   // height consumed by a section title
    private static final float STAT_CARD_H  = 52f;
    private static final float TOTAL_ROW_H  = 28f;
    private static final float FOOTER_H     = 20f;

    // ── Font sizes ───────────────────────────────────────────────────────────
    private static final float FS_TITLE    = 20f;
    private static final float FS_SUBTITLE = 12f;
    private static final float FS_SECTION  = 11.5f;
    private static final float FS_TABLE_H  = 9.5f;
    private static final float FS_BODY     = 9.5f;
    private static final float FS_SMALL    = 8.5f;
    private static final float FS_TOTAL    = 11f;

    // ── Colour palette ───────────────────────────────────────────────────────
    private static final Color C_BANNER_BG  = new Color(0x17, 0x20, 0x33);
    private static final Color C_BANNER_FG  = Color.WHITE;
    private static final Color C_BANNER_SUB = new Color(0xA5, 0xB4, 0xC8);
    private static final Color C_BANNER_DTE = new Color(0x6B, 0x7A, 0x90);

    private static final Color C_ACCENT     = new Color(0x1D, 0x4E, 0xD8); // brand blue
    private static final Color C_SECTION_FG = new Color(0x0F, 0x17, 0x2A);

    private static final Color C_TBL_HDR_BG = new Color(0x1E, 0x29, 0x3B);
    private static final Color C_TBL_HDR_FG = Color.WHITE;
    private static final Color C_ROW_WHITE  = Color.WHITE;
    private static final Color C_ROW_ALT    = new Color(0xF0, 0xF4, 0xFF);
    private static final Color C_ROW_BORDER = new Color(0xD1, 0xDB, 0xEB);
    private static final Color C_TEXT_DARK  = new Color(0x0F, 0x17, 0x2A);
    private static final Color C_TEXT_MID   = new Color(0x3A, 0x4A, 0x60);

    private static final Color C_TOTAL_BG   = new Color(0x1D, 0x4E, 0xD8);
    private static final Color C_TOTAL_FG   = Color.WHITE;

    private static final Color C_STAT_BG    = new Color(0xEE, 0xF2, 0xFF);
    private static final Color C_STAT_BD    = new Color(0xC7, 0xD2, 0xFE);

    private static final Color C_DIVIDER    = new Color(0xE2, 0xE8, 0xF0);
    private static final Color C_FOOTER_FG  = new Color(0x94, 0xA3, 0xB8);

    // ── Number / date formatters ─────────────────────────────────────────────
    private static final NumberFormat NF_IN =
            NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** PDF-safe rupee: replaces ₹ (U+20B9, not in WinAnsiEncoding) with Rs. */
    private static String rs(double v) {
        return NF_IN.format(v).replace("\u20B9", "Rs.");
    }

    // =========================================================================
    // PUBLIC ENTRY POINT
    // =========================================================================
    public static void generate(ExpenseManager manager, YearMonth ym, File out)
            throws IOException {

        ArrayList<Expense> expenses = manager.getExpensesForMonth(ym);
        Map<String, Double> breakdown = manager.getMonthlyCategoryBreakdown(ym);
        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        int    txCount = expenses.size();
        double avg     = txCount > 0 ? total / txCount : 0.0;

        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String period    = monthName + " " + ym.getYear();
        String genDate   = LocalDate.now().format(DATE_FMT);

        // fonts (instantiated once, reused)
        PDType1Font fBold  = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fReg   = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fObliq = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument doc = new PDDocument()) {

            // ── State for multi-page rendering ──────────────────────────────
            PDPage            curPage = newPage(doc);
            PDPageContentStream cs   = openStream(doc, curPage);
            float y = PAGE_H - MARGIN_T;

            // ── 1. BANNER ────────────────────────────────────────────────────
            y = drawBanner(cs, fBold, fReg, y, period, genDate);
            y -= 16f;

            // ── 2. MONTHLY SUMMARY ───────────────────────────────────────────
            y = drawSectionHeading(cs, fBold, y, "Monthly Summary");
            y -= 8f;
            y = drawStatCards(cs, fBold, fReg, y, total, txCount, avg);
            y -= 20f;

            // ── 3. CATEGORY BREAKDOWN ────────────────────────────────────────
            y = drawSectionHeading(cs, fBold, y, "Category-wise Spending Breakdown");
            y -= 6f;

            float[] catW  = { CONTENT_W * 0.52f, CONTENT_W * 0.26f, CONTENT_W * 0.22f };
            String[] catH = { "Category", "Total Spent", "% of Total" };
            y = drawTableHeader(cs, fBold, y, catW, catH,
                                new boolean[]{false, true, true});

            int ri = 0;
            for (Map.Entry<String, Double> e : breakdown.entrySet()) {
                double pct = total > 0 ? e.getValue() / total * 100.0 : 0.0;
                // Check page space — leave room for at least 1 more row + total
                if (y - ROW_H < MARGIN_B + TOTAL_ROW_H + FOOTER_H + 4) {
                    cs = pageBreak(doc, cs, curPage = newPage(doc));
                    y  = PAGE_H - MARGIN_T;
                }
                drawDataRow(cs, fReg, y, catW,
                    new String[]{ e.getKey(), rs(e.getValue()), String.format("%.1f%%", pct) },
                    ri % 2 == 1, new boolean[]{false, true, true});
                y -= ROW_H;
                ri++;
            }
            drawTableBottomBorder(cs, y);
            y -= 18f;

            // ── 4. DETAILED EXPENSE TRANSACTIONS ────────────────────────────
            // Check if enough space for heading + table header + at least 1 row
            float neededForTxHeader = SECTION_H + 8f + TBL_HDR_H + ROW_H + TOTAL_ROW_H + FOOTER_H;
            if (y - neededForTxHeader < MARGIN_B) {
                cs = pageBreak(doc, cs, curPage = newPage(doc));
                y  = PAGE_H - MARGIN_T;
            }

            y = drawSectionHeading(cs, fBold, y,
                "Detailed Expense Transactions  -  " + period);
            y -= 6f;

            // Columns: S.No. | Date | Category | Description | Amount
            float[] txW   = {
                CONTENT_W * 0.07f,   // S.No.
                CONTENT_W * 0.14f,   // Date
                CONTENT_W * 0.19f,   // Category
                CONTENT_W * 0.40f,   // Description
                CONTENT_W * 0.20f    // Amount
            };
            String[] txHdr = { "S.No.", "Date", "Category", "Description", "Amount" };
            boolean[] txRA = { true, false, false, false, true };

            y = drawTableHeader(cs, fBold, y, txW, txHdr, txRA);

            int sno = 1;
            for (Expense e : expenses) {
                // Page break if needed — repeat heading + column header on new page
                if (y - ROW_H < MARGIN_B + TOTAL_ROW_H + FOOTER_H + 4) {
                    drawTableBottomBorder(cs, y);
                    cs = pageBreak(doc, cs, curPage = newPage(doc));
                    y  = PAGE_H - MARGIN_T;

                    y = drawSectionHeading(cs, fBold, y,
                        "Detailed Expense Transactions (continued)  -  " + period);
                    y -= 6f;
                    y = drawTableHeader(cs, fBold, y, txW, txHdr, txRA);
                }

                drawDataRow(cs, fReg, y, txW,
                    new String[]{
                        String.valueOf(sno),
                        e.getDate().format(DATE_FMT),
                        e.getCategory(),
                        e.getDescription(),
                        rs(e.getAmount())
                    },
                    sno % 2 == 0, txRA);
                y -= ROW_H;
                sno++;
            }
            drawTableBottomBorder(cs, y);
            y -= 8f;

            // ── 5. TOTAL ROW ─────────────────────────────────────────────────
            if (y - TOTAL_ROW_H - FOOTER_H < MARGIN_B) {
                cs = pageBreak(doc, cs, curPage = newPage(doc));
                y  = PAGE_H - MARGIN_T;
            }
            y = drawTotalRow(cs, fBold, y, total);
            y -= 6f;

            // ── 6. FOOTER ────────────────────────────────────────────────────
            drawFooter(cs, fObliq, period, genDate);

            cs.close();
            doc.save(out);
        }
    }

    // =========================================================================
    // DRAWING HELPERS
    // =========================================================================

    // ── Banner ─────────────────────────────────────────────────────────────
    private static float drawBanner(PDPageContentStream cs,
                                    PDType1Font fBold, PDType1Font fReg,
                                    float y, String period, String genDate)
            throws IOException {

        float bH = 86f;
        // Background
        fillRect(cs, MARGIN_L, y - bH, CONTENT_W, bH, C_BANNER_BG);
        // Left accent stripe
        fillRect(cs, MARGIN_L, y - bH, 4f, bH, C_ACCENT);

        // Title
        text(cs, fBold, FS_TITLE, C_BANNER_FG,
             MARGIN_L + 16f, y - 28f, "PERSONAL EXPENSE TRACKER");

        // Subtitle
        text(cs, fReg, FS_SUBTITLE, C_BANNER_SUB,
             MARGIN_L + 16f, y - 46f, "Monthly Expense Report  |  " + period);

        // Report period label (smaller, below subtitle)
        text(cs, fReg, FS_SMALL, new Color(0x7A, 0x90, 0xA8),
             MARGIN_L + 16f, y - 60f, "Covers all expenses recorded for " + period);

        // Generated date — right-aligned
        String gs = "Generated: " + genDate;
        float gw  = fReg.getStringWidth(gs) / 1000f * FS_SMALL;
        text(cs, fReg, FS_SMALL, C_BANNER_DTE,
             MARGIN_L + CONTENT_W - gw - 14f, y - 72f, gs);

        return y - bH;
    }

    // ── Section heading with accent bar ────────────────────────────────────
    private static float drawSectionHeading(PDPageContentStream cs,
                                            PDType1Font fBold,
                                            float y, String title)
            throws IOException {
        // Accent left bar
        fillRect(cs, MARGIN_L, y - 16f, 4f, 16f, C_ACCENT);
        text(cs, fBold, FS_SECTION, C_SECTION_FG, MARGIN_L + 10f, y - 13f, title);
        return y - SECTION_H;
    }

    // ── Stat cards (Total Spent | Transactions | Avg per Transaction) ───────
    private static float drawStatCards(PDPageContentStream cs,
                                       PDType1Font fBold, PDType1Font fReg,
                                       float y, double total, int count, double avg)
            throws IOException {
        float gap   = 10f;
        float cardW = (CONTENT_W - gap * 2) / 3f;

        String[] labels  = { "Total Spent",          "No. of Transactions",   "Avg per Transaction" };
        String[] values  = { rs(total),               String.valueOf(count),   rs(avg) };
        Color[]  accents = {
            new Color(0x1D, 0x4E, 0xD8),   // blue
            new Color(0x05, 0x96, 0x69),   // green
            new Color(0x6D, 0x28, 0xD9)    // violet
        };

        for (int i = 0; i < 3; i++) {
            float cx = MARGIN_L + i * (cardW + gap);

            // Card background + border
            fillRect(cs, cx, y - STAT_CARD_H, cardW, STAT_CARD_H, C_STAT_BG);
            strokeRect(cs, cx, y - STAT_CARD_H, cardW, STAT_CARD_H, C_STAT_BD);
            // Top accent strip (3 pt)
            fillRect(cs, cx, y - 3f, cardW, 3f, accents[i]);

            // Label
            text(cs, fReg, FS_SMALL, new Color(0x60, 0x74, 0x8A),
                 cx + 10f, y - 17f, labels[i]);

            // Value (large, coloured)
            text(cs, fBold, 12f, accents[i],
                 cx + 10f, y - 35f, values[i]);

            // Small underline below value (no alpha — setStrokingColor only takes RGB)
            setStrokeColor(cs, new Color(
                Math.min(255, accents[i].getRed()   + 80),
                Math.min(255, accents[i].getGreen() + 80),
                Math.min(255, accents[i].getBlue()  + 80)));
            cs.setLineWidth(0.5f);
            cs.moveTo(cx + 10f, y - 38f);
            cs.lineTo(cx + cardW - 10f, y - 38f);
            cs.stroke();
            cs.setLineWidth(1f);
        }
        return y - STAT_CARD_H;
    }

    // ── Table column header row ─────────────────────────────────────────────
    private static float drawTableHeader(PDPageContentStream cs,
                                         PDType1Font fBold, float y,
                                         float[] colW, String[] hdrs,
                                         boolean[] rightAlign)
            throws IOException {
        float x = MARGIN_L;
        fillRect(cs, x, y - TBL_HDR_H, CONTENT_W, TBL_HDR_H, C_TBL_HDR_BG);

        for (int i = 0; i < hdrs.length; i++) {
            float tw   = fBold.getStringWidth(hdrs[i]) / 1000f * FS_TABLE_H;
            boolean ra = rightAlign != null && i < rightAlign.length && rightAlign[i];
            float tx   = ra ? x + colW[i] - tw - 5f : x + 5f;
            text(cs, fBold, FS_TABLE_H, C_TBL_HDR_FG, tx, y - TBL_HDR_H + 7f, hdrs[i]);
            x += colW[i];
        }

        // Bottom border
        setStrokeColor(cs, C_ACCENT);
        cs.setLineWidth(1.0f);
        cs.moveTo(MARGIN_L, y - TBL_HDR_H);
        cs.lineTo(MARGIN_L + CONTENT_W, y - TBL_HDR_H);
        cs.stroke();
        cs.setLineWidth(1f);

        return y - TBL_HDR_H;
    }

    // ── Data row ────────────────────────────────────────────────────────────
    private static void drawDataRow(PDPageContentStream cs,
                                    PDType1Font fReg, float y,
                                    float[] colW, String[] cells,
                                    boolean alt, boolean[] rightAlign)
            throws IOException {
        float x = MARGIN_L;
        fillRect(cs, x, y - ROW_H, CONTENT_W, ROW_H, alt ? C_ROW_ALT : C_ROW_WHITE);

        for (int i = 0; i < cells.length; i++) {
            String txt = cells[i] == null ? "" : cells[i];
            txt = truncate(txt, fReg, FS_BODY, colW[i] - 10f);

            boolean ra = rightAlign != null && i < rightAlign.length && rightAlign[i];
            float tx;
            if (ra) {
                float tw = fReg.getStringWidth(txt) / 1000f * FS_BODY;
                tx = x + colW[i] - tw - 5f;
            } else {
                tx = x + 5f;
            }
            text(cs, fReg, FS_BODY, C_TEXT_DARK, tx, y - ROW_H + 6f, txt);
            x += colW[i];
        }

        // Subtle row bottom border
        setStrokeColor(cs, C_ROW_BORDER);
        cs.setLineWidth(0.4f);
        cs.moveTo(MARGIN_L, y - ROW_H);
        cs.lineTo(MARGIN_L + CONTENT_W, y - ROW_H);
        cs.stroke();
        cs.setLineWidth(1f);
    }

    // ── Solid bottom border of a table block ───────────────────────────────
    private static void drawTableBottomBorder(PDPageContentStream cs, float y)
            throws IOException {
        setStrokeColor(cs, C_DIVIDER);
        cs.setLineWidth(1f);
        cs.moveTo(MARGIN_L, y);
        cs.lineTo(MARGIN_L + CONTENT_W, y);
        cs.stroke();
    }

    // ── Total row ──────────────────────────────────────────────────────────
    private static float drawTotalRow(PDPageContentStream cs,
                                      PDType1Font fBold, float y, double total)
            throws IOException {
        fillRect(cs, MARGIN_L, y - TOTAL_ROW_H, CONTENT_W, TOTAL_ROW_H, C_TOTAL_BG);

        text(cs, fBold, FS_TOTAL, C_TOTAL_FG,
             MARGIN_L + 10f, y - TOTAL_ROW_H + 9f,
             "TOTAL MONTHLY EXPENDITURE:");

        String ts = rs(total);
        float  tw = fBold.getStringWidth(ts) / 1000f * FS_TOTAL;
        text(cs, fBold, FS_TOTAL, C_TOTAL_FG,
             MARGIN_L + CONTENT_W - tw - 10f, y - TOTAL_ROW_H + 9f, ts);

        return y - TOTAL_ROW_H;
    }

    // ── Page footer ───────────────────────────────────────────────────────
    private static void drawFooter(PDPageContentStream cs,
                                   PDType1Font fObliq,
                                   String period, String genDate)
            throws IOException {
        float fy = MARGIN_B - 10f;

        // Divider line
        setStrokeColor(cs, C_DIVIDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN_L, fy + 12f);
        cs.lineTo(MARGIN_L + CONTENT_W, fy + 12f);
        cs.stroke();
        cs.setLineWidth(1f);

        String left  = "Personal Expense Tracker  |  " + period;
        String right = "Generated on " + genDate;
        float  rw    = fObliq.getStringWidth(right) / 1000f * FS_SMALL;

        text(cs, fObliq, FS_SMALL, C_FOOTER_FG, MARGIN_L, fy, left);
        text(cs, fObliq, FS_SMALL, C_FOOTER_FG, MARGIN_L + CONTENT_W - rw, fy, right);
    }

    // =========================================================================
    // PAGE MANAGEMENT
    // =========================================================================
    private static PDPage newPage(PDDocument doc) {
        PDPage p = new PDPage(PDRectangle.A4);
        doc.addPage(p);
        return p;
    }

    private static PDPageContentStream openStream(PDDocument doc, PDPage page)
            throws IOException {
        return new PDPageContentStream(doc, page);
    }

    /** Closes current stream, opens a new one for the given page, draws the page footer. */
    private static PDPageContentStream pageBreak(PDDocument doc,
                                                  PDPageContentStream oldCs,
                                                  PDPage newPage)
            throws IOException {
        oldCs.close();
        return openStream(doc, newPage);
    }

    // =========================================================================
    // GRAPHICS PRIMITIVES
    // =========================================================================
    private static void setFillColor(PDPageContentStream cs, Color c) throws IOException {
        cs.setNonStrokingColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
    }

    private static void setStrokeColor(PDPageContentStream cs, Color c) throws IOException {
        cs.setStrokingColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
    }

    private static void fillRect(PDPageContentStream cs,
                                  float x, float y, float w, float h, Color c)
            throws IOException {
        setFillColor(cs, c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private static void strokeRect(PDPageContentStream cs,
                                    float x, float y, float w, float h, Color c)
            throws IOException {
        setStrokeColor(cs, c);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size,
                              Color color, float x, float y, String str)
            throws IOException {
        setFillColor(cs, color);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(str);
        cs.endText();
    }

    // =========================================================================
    // STRING TRUNCATION
    // =========================================================================
    private static String truncate(String t, PDType1Font font, float size, float maxW)
            throws IOException {
        if (t == null) return "";
        if (font.getStringWidth(t) / 1000f * size <= maxW) return t;
        String sfx = "..";
        while (t.length() > 1) {
            t = t.substring(0, t.length() - 1);
            if (font.getStringWidth(t + sfx) / 1000f * size <= maxW) return t + sfx;
        }
        return t;
    }
}
