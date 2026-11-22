import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * FileHandler.java
 * Handles reading and writing expenses to/from a CSV file.
 *
 * Supported input formats (all loaded transparently):
 *   v1 (5 cols): id,date,category,description,amount
 *   v2 (6 cols): id,date,mainCat,subCat,description,amount  ← subCat ignored on load
 *
 * Output format is always v1 (5 cols): id,date,category,description,amount
 */
public class FileHandler {

    private static final String DATA_DIR  = "data";
    private static final String FILE_PATH = "data/expenses.csv";

    /**
     * Loads all expenses from the CSV file.
     * Handles both v1 (5-column) and v2 (6-column) lines gracefully.
     */
    public static ArrayList<Expense> loadExpenses() {
        ArrayList<Expense> expenses = new ArrayList<>();

        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(FILE_PATH);
        if (!file.exists()) return expenses;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    Expense e = parseLine(line);
                    if (e != null) expenses.add(e);
                } catch (Exception ex) {
                    System.err.println("Skipping malformed line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }

        return expenses;
    }

    /**
     * Parses a single CSV line into an Expense, handling both formats.
     *
     * v1 (5 fields): id, date, category, description, amount
     * v2 (6 fields): id, date, mainCat, subCat, description, amount  ← subCat dropped
     *
     * Detection: if there are exactly 6 split fields AND fields[5] parses as a
     * double, it's v2.  Otherwise treat as v1.
     */
    private static Expense parseLine(String line) {
        String[] raw = splitCsv(line);

        if (raw.length < 5) return null;

        int       id   = Integer.parseInt(raw[0].trim());
        LocalDate date = LocalDate.parse(raw[1].trim());

        if (raw.length >= 6) {
            // Detect v2: 6th field should be amount (numeric)
            try {
                double amount = Double.parseDouble(raw[5].trim());
                // v2: mainCat=raw[2], subCat=raw[3] (ignored), desc=raw[4]
                String category = raw[2].trim();
                String desc     = raw[4].trim();
                return new Expense(id, date, category, desc, amount);
            } catch (NumberFormatException ignored) {
                // Not a valid v2 line — fall through to v1 parse below
            }
        }

        // v1: category=raw[2], desc=raw[3], amount=raw[4]
        String category = raw[2].trim();
        String desc     = raw[3].trim();
        double amount   = Double.parseDouble(raw[4].trim());
        return new Expense(id, date, category, desc, amount);
    }

    /**
     * Minimal CSV splitter that handles double-quoted fields containing commas.
     */
    private static String[] splitCsv(String line) {
        ArrayList<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Saves all expenses to the CSV file (v1 / 5-column format).
     * Called after every add, edit, or delete.
     */
    public static void saveExpenses(ArrayList<Expense> expenses) {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, false))) {
            for (Expense expense : expenses) {
                writer.write(expense.toCsvLine());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving file: " + e.getMessage());
        }
    }
}
