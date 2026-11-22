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
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * DashboardReportPDF.java
 *
 * Generates a professional PDF Dashboard Summary report using Apache PDFBox 3.x.
 * Mirrors what is visible on the Dashboard: KPI cards, monthly trend, category
 * breakdown, and the 10 most recent transactions.
 *
 * Structure:
 *   Page 1 : Banner header → KPI summary → Category breakdown table
 *   Page 2+: Recent transactions table (multi-page if needed)
 */
public class DashboardReportPDF {

    // -------------------------------------------------------------------------
    // Layout constants (A4 portrait: 595 × 842 pt)
    // -------------------------------------------------------------------------
    private static final float PAGE_W    = PDRectangle.A4.getWidth();
    private static final float PAGE_H    = PDRectangle.A4.getHeight();
    private static final float MARGIN_L  = 48f;
    private static final float MARGIN_R  = 48f;
    private static final float MARGIN_T  = 48f;
    private static final float MARGIN_B  = 48f;
    private static final float CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R;

    private static final float ROW_H     = 18f;
    private static final float HDR_ROW_H = 20f;

    private static final float FS_TITLE    = 20f;
    private static final float FS_SUBTITLE = 13f;
    private static final float FS_SECTION  = 12f;
    private static final float FS_BODY     = 10f;
    private static final float FS_SMALL    =  9f;

    private static final Color COL_HEADER_BG  = new Color(0x17, 0x20, 0x33);
    private static final Color COL_HEADER_FG  = Color.WHITE;
    private static final Color COL_ACCENT     = new Color(0x25, 0x63, 0xEB);
    private static final Color COL_TBL_HDR_BG = new Color(0x37, 0x47, 0x5C);
    private static final Color COL_TBL_HDR_FG = Color.WHITE;
    private static final Color COL_ALT_ROW    = new Color(0xF4, 0xF7, 0xFC);
    private static final Color COL_BORDER     = new Color(0xCB, 0xD5, 0xE1);
    private static final Color COL_TOTAL_BG   = new Color(0x25, 0x63, 0xEB);
    private static final Color COL_TOTAL_FG   = Color.WHITE;
    private static final Color COL_STAT_BG    = new Color(0xEF, 0xF4, 0xFF);

    private static final NumberFormat RUPEE_FMT =
            NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    /** Formats an amount as PDF-safe rupee string, e.g. "Rs. 1,234.50" */
    private static String pdfRupee(double v) {
        return RUPEE_FMT.format(v).replace("\u20B9", "Rs.");
    }

    private static final DateTimeFormatter FMT_DISPLAY =
            DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter FMT_MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy");

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Generates and saves the dashboard summary PDF.
     *
     * @param manager    the application's ExpenseManager
     * @param outputFile the destination file chosen by the user
     * @throws IOException on any I/O or PDF generation error
     */
    public static void generate(ExpenseManager manager, File outputFile) throws IOException {
        LocalDate today    = LocalDate.now();
        int       year     = today.getYear();
        String    genDate  = today.format(FMT_DISPLAY);
        String    monthStr = today.format(FMT_MONTH);

        double yearTotal  = manager.getYearTotal(year);
        double todayTotal = manager.getTodayTotal();
        double monthTotal = manager.getMonthTotal();
        int    count      = manager.getTransactionCount();

        Map<String, Double> catMap   = manager.getCategorySummary();
        double              grandTotal = manager.getGrandTotal();

        // Recent 10 transactions (newest first)
        ArrayList<Expense> all = manager.getAllExpenses();
        int rStart = Math.max(0, all.size() - 10);
        ArrayList<Expense> recent = new ArrayList<>();
        for (int i = all.size() - 1; i >= rStart; i--) recent.add(all.get(i));

        try (PDDocument doc = new PDDocument()) {

            // ------------------------------------------------------------------
            // Page 1 : Banner + KPI cards + Category breakdown
            // ------------------------------------------------------------------
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                float y = PAGE_H - MARGIN_T;

                y = drawBanner(cs, y, genDate, monthStr);
                y -= 18f;

                y = drawSectionTitle(cs, y, "Key Performance Indicators");
                y -= 6f;
                y = drawKpiCards(cs, y, yearTotal, todayTotal, monthTotal, count, year, monthStr);
                y -= 18f;

                y = drawSectionTitle(cs, y, "All-time Category Breakdown");
                y -= 6f;

                float[] colW  = { CONTENT_W * 0.46f, CONTENT_W * 0.27f, CONTENT_W * 0.27f };
                String[] hdrs = { "Category", "Total Spent", "% of Total" };
                y = drawTableHeader(cs, y, colW, hdrs);

                int rowIdx = 0;
                for (Map.Entry<String, Double> entry : catMap.entrySet()) {
                    if (y - ROW_H < MARGIN_B + 30f) break; // won't overflow page 1
                    double pct = (grandTotal > 0) ? (entry.getValue() / grandTotal) * 100.0 : 0.0;
                    String[] cells = {
                        entry.getKey(),
                        pdfRupee(entry.getValue()),
                        String.format("%.1f%%", pct)
                    };
                    drawTableRow(cs, y, colW, cells, rowIdx % 2 == 1,
                                 new boolean[]{false, true, true});
                    y -= ROW_H;
                    rowIdx++;
                }

                // Bottom border of category table
                setStrokeColor(cs, COL_BORDER);
                cs.moveTo(MARGIN_L, y);
                cs.lineTo(MARGIN_L + CONTENT_W, y);
                cs.stroke();
            }

            // ------------------------------------------------------------------
            // Page 2 : Recent transactions
            // ------------------------------------------------------------------
            drawRecentTransactions(doc, recent, grandTotal, genDate);

            doc.save(outputFile);
        }
    }

    // -------------------------------------------------------------------------
    // Banner
    // -------------------------------------------------------------------------
    private static float drawBanner(PDPageContentStream cs, float y,
                                    String genDate, String monthStr)
            throws IOException {
        float bannerH = 82f;
        fillRect(cs, MARGIN_L, y - bannerH, CONTENT_W, bannerH, COL_HEADER_BG);

        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FS_TITLE);
        setFillColor(cs, COL_HEADER_FG);
        cs.newLineAtOffset(MARGIN_L + 18f, y - 26f);
        cs.showText("PERSONAL EXPENSE TRACKER");
        cs.endText();

        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FS_SUBTITLE);
        setFillColor(cs, new Color(0xCB, 0xD5, 0xE1));
        cs.newLineAtOffset(MARGIN_L + 18f, y - 44f);
        cs.showText("Dashboard Summary Report  |  " + monthStr);
        cs.endText();

        String dateStr = "Generated: " + genDate;
        PDType1Font sf = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float tw = sf.getStringWidth(dateStr) / 1000f * FS_SMALL;
        cs.beginText();
        cs.setFont(sf, FS_SMALL);
        setFillColor(cs, new Color(0x9C, 0xA3, 0xAF));
        cs.newLineAtOffset(MARGIN_L + CONTENT_W - tw - 18f, y - 65f);
        cs.showText(dateStr);
        cs.endText();

        return y - bannerH;
    }

    // -------------------------------------------------------------------------
    // Section title
    // -------------------------------------------------------------------------
    private static float drawSectionTitle(PDPageContentStream cs, float y, String title)
            throws IOException {
        fillRect(cs, MARGIN_L, y - 16f, 4f, 16f, COL_ACCENT);
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FS_SECTION);
        setFillColor(cs, COL_HEADER_BG);
        cs.newLineAtOffset(MARGIN_L + 10f, y - 13f);
        cs.showText(title);
        cs.endText();
        return y - 20f;
    }

    // -------------------------------------------------------------------------
    // KPI cards (2 × 2 grid)
    // -------------------------------------------------------------------------
    private static float drawKpiCards(PDPageContentStream cs, float y,
                                      double yearTotal, double todayTotal,
                                      double monthTotal, int count,
                                      int year, String monthStr)
            throws IOException {
        float cardH = 50f;
        float gap   =  8f;
        float cardW = (CONTENT_W - gap) / 2f;

        String[][] kpis = {
            { "Total Expenses This Year (" + year + ")", pdfRupee(yearTotal) },
            { "Today's Spending",                         pdfRupee(todayTotal) },
            { "This Month  (" + monthStr + ")",           pdfRupee(monthTotal) },
            { "Total Transactions (All Time)",             String.valueOf(count) + " records" }
        };
        Color[] accents = {
            new Color(0x25, 0x63, 0xEB),
            new Color(0x0D, 0x94, 0x88),
            new Color(0x7C, 0x3A, 0xED),
            new Color(0xEA, 0x58, 0x0C)
        };

        for (int i = 0; i < kpis.length; i++) {
            int col = i % 2;
            int row = i / 2;
            float x = MARGIN_L + col * (cardW + gap);
            float cy = y - row * (cardH + gap);

            fillRect(cs, x, cy - cardH, cardW, cardH, COL_STAT_BG);
            strokeRect(cs, x, cy - cardH, cardW, cardH, COL_BORDER);
            fillRect(cs, x, cy - 3f, cardW, 3f, accents[i]);

            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FS_SMALL);
            setFillColor(cs, new Color(0x64, 0x74, 0x8B));
            cs.newLineAtOffset(x + 8f, cy - 18f);
            cs.showText(kpis[i][0]);
            cs.endText();

            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11f);
            setFillColor(cs, accents[i]);
            cs.newLineAtOffset(x + 8f, cy - 36f);
            cs.showText(kpis[i][1]);
            cs.endText();
        }

        return y - 2 * (cardH + gap);
    }

    // -------------------------------------------------------------------------
    // Table header
    // -------------------------------------------------------------------------
    private static float drawTableHeader(PDPageContentStream cs, float y,
                                         float[] colWidths, String[] headers)
            throws IOException {
        float x = MARGIN_L;
        fillRect(cs, x, y - HDR_ROW_H, CONTENT_W, HDR_ROW_H, COL_TBL_HDR_BG);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.setFont(bold, FS_SMALL);
            setFillColor(cs, COL_TBL_HDR_FG);
            cs.newLineAtOffset(x + 4f, y - HDR_ROW_H + 6f);
            cs.showText(headers[i]);
            cs.endText();
            x += colWidths[i];
        }
        setStrokeColor(cs, COL_BORDER);
        cs.moveTo(MARGIN_L, y - HDR_ROW_H);
        cs.lineTo(MARGIN_L + CONTENT_W, y - HDR_ROW_H);
        cs.stroke();
        return y - HDR_ROW_H;
    }

    // -------------------------------------------------------------------------
    // Table data row
    // -------------------------------------------------------------------------
    private static void drawTableRow(PDPageContentStream cs, float y,
                                     float[] colWidths, String[] cells,
                                     boolean alternate, boolean[] rightAlign)
            throws IOException {
        float x = MARGIN_L;
        if (alternate) fillRect(cs, x, y - ROW_H, CONTENT_W, ROW_H, COL_ALT_ROW);
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (int i = 0; i < cells.length; i++) {
            String text = cells[i] == null ? "" : cells[i];
            text = truncate(text, font, FS_BODY, colWidths[i] - 8f);
            float tx;
            if (rightAlign != null && i < rightAlign.length && rightAlign[i]) {
                float tw = font.getStringWidth(text) / 1000f * FS_BODY;
                tx = x + colWidths[i] - tw - 4f;
            } else {
                tx = x + 4f;
            }
            cs.beginText();
            cs.setFont(font, FS_BODY);
            setFillColor(cs, new Color(0x1E, 0x29, 0x3B));
            cs.newLineAtOffset(tx, y - ROW_H + 5f);
            cs.showText(text);
            cs.endText();
            x += colWidths[i];
        }
        setStrokeColor(cs, new Color(0xF1, 0xF5, 0xF9));
        cs.moveTo(MARGIN_L, y - ROW_H);
        cs.lineTo(MARGIN_L + CONTENT_W, y - ROW_H);
        cs.stroke();
    }

    // -------------------------------------------------------------------------
    // Recent transactions section
    // -------------------------------------------------------------------------
    private static void drawRecentTransactions(PDDocument doc,
                                               ArrayList<Expense> recent,
                                               double grandTotal,
                                               String genDate) throws IOException {
        float[] txColW = {
            CONTENT_W * 0.07f,
            CONTENT_W * 0.15f,
            CONTENT_W * 0.22f,
            CONTENT_W * 0.36f,
            CONTENT_W * 0.20f
        };
        String[] txHdrs = { "ID", "Date", "Category", "Description", "Amount" };
        boolean[] rightAlign = { false, false, false, false, true };

        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        float y = PAGE_H - MARGIN_T;

        y = drawSectionTitle(cs, y, "Recent Transactions  (last 10)");
        y -= 6f;
        y = drawTableHeader(cs, y, txColW, txHdrs);

        int rowIdx = 0;
        for (Expense e : recent) {
            if (y - ROW_H < MARGIN_B + 40f) {
                setStrokeColor(cs, COL_BORDER);
                cs.moveTo(MARGIN_L, y); cs.lineTo(MARGIN_L + CONTENT_W, y); cs.stroke();
                cs.close();
                page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                y = PAGE_H - MARGIN_T;
                y = drawSectionTitle(cs, y, "Recent Transactions (continued)");
                y -= 6f;
                y = drawTableHeader(cs, y, txColW, txHdrs);
            }
            String[] cells = {
                String.valueOf(e.getId()),
                e.getDate().toString(),
                e.getCategory(),
                e.getDescription(),
                pdfRupee(e.getAmount())
            };
            drawTableRow(cs, y, txColW, cells, rowIdx % 2 == 1, rightAlign);
            y -= ROW_H;
            rowIdx++;
        }

        setStrokeColor(cs, COL_BORDER);
        cs.moveTo(MARGIN_L, y); cs.lineTo(MARGIN_L + CONTENT_W, y); cs.stroke();
        y -= 6f;

        // Grand total row
        float totalRowH = 24f;
        if (y - totalRowH < MARGIN_B) {
            cs.close();
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_H - MARGIN_T;
        }
        fillRect(cs, MARGIN_L, y - totalRowH, CONTENT_W, totalRowH, COL_TOTAL_BG);
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FS_SECTION);
        setFillColor(cs, COL_TOTAL_FG);
        cs.newLineAtOffset(MARGIN_L + 8f, y - totalRowH + 7f);
        cs.showText("Grand Total (All Time)");
        cs.endText();

        String totalStr = pdfRupee(grandTotal);
        PDType1Font bf = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float tw = bf.getStringWidth(totalStr) / 1000f * FS_SECTION;
        cs.beginText();
        cs.setFont(bf, FS_SECTION);
        setFillColor(cs, COL_TOTAL_FG);
        cs.newLineAtOffset(MARGIN_L + CONTENT_W - tw - 8f, y - totalRowH + 7f);
        cs.showText(totalStr);
        cs.endText();

        // Footer
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FS_SMALL);
        setFillColor(cs, new Color(0x9C, 0xA3, 0xAF));
        cs.newLineAtOffset(MARGIN_L, MARGIN_B - 14f);
        cs.showText("Personal Expense Tracker  |  Dashboard Report  |  Generated on " + genDate);
        cs.endText();

        cs.close();
    }

    // -------------------------------------------------------------------------
    // Graphics helpers
    // -------------------------------------------------------------------------
    private static void setFillColor(PDPageContentStream cs, Color c) throws IOException {
        cs.setNonStrokingColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
    }
    private static void setStrokeColor(PDPageContentStream cs, Color c) throws IOException {
        cs.setStrokingColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
    }
    private static void fillRect(PDPageContentStream cs,
                                  float x, float y, float w, float h, Color c) throws IOException {
        setFillColor(cs, c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }
    private static void strokeRect(PDPageContentStream cs,
                                    float x, float y, float w, float h, Color c) throws IOException {
        setStrokeColor(cs, c);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }
    private static String truncate(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        if (text == null) return "";
        if (font.getStringWidth(text) / 1000f * fontSize <= maxWidth) return text;
        String suffix = "..";
        while (text.length() > 1) {
            text = text.substring(0, text.length() - 1);
            if (font.getStringWidth(text + suffix) / 1000f * fontSize <= maxWidth)
                return text + suffix;
        }
        return text;
    }
}
