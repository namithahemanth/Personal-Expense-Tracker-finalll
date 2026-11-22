import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * ExpenseManager.java
 * All business logic: add, edit, delete, search, summaries.
 */
public class ExpenseManager {

    private ArrayList<Expense> expenses;
    private int nextId;

    public ExpenseManager() {
        expenses = FileHandler.loadExpenses();
        nextId = expenses.stream().mapToInt(Expense::getId).max().orElse(0) + 1;
    }

    // -------------------------------------------------------------------------
    // Retrieval
    // -------------------------------------------------------------------------

    public ArrayList<Expense> getAllExpenses() {
        return expenses;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    public LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty())
            throw new IllegalArgumentException("Date cannot be empty.");
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use YYYY-MM-DD.");
        }
    }

    public double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty())
            throw new IllegalArgumentException("Amount cannot be empty.");
        try {
            double amount = Double.parseDouble(amountStr.trim());
            if (amount <= 0)
                throw new IllegalArgumentException("Amount must be a positive number.");
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount. Please enter a valid number.");
        }
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    public Expense addExpense(String dateStr, String category, String description, String amountStr) {
        LocalDate date   = parseDate(dateStr);
        double    amount = parseAmount(amountStr);
        if (description == null || description.trim().isEmpty())
            throw new IllegalArgumentException("Description cannot be empty.");

        Expense expense = new Expense(nextId++, date, category, description.trim(), amount);
        expenses.add(expense);
        FileHandler.saveExpenses(expenses);
        return expense;
    }

    public void editExpense(int id, String dateStr, String category,
                            String description, String amountStr) {
        Expense expense = findById(id);
        if (expense == null)
            throw new IllegalArgumentException("Expense with ID " + id + " not found.");

        LocalDate date   = parseDate(dateStr);
        double    amount = parseAmount(amountStr);
        if (description == null || description.trim().isEmpty())
            throw new IllegalArgumentException("Description cannot be empty.");

        expense.setDate(date);
        expense.setCategory(category);
        expense.setDescription(description.trim());
        expense.setAmount(amount);
        FileHandler.saveExpenses(expenses);
    }

    public void deleteExpense(int id) {
        Expense expense = findById(id);
        if (expense == null)
            throw new IllegalArgumentException("Expense with ID " + id + " not found.");
        expenses.remove(expense);
        FileHandler.saveExpenses(expenses);
    }

    public Expense findById(int id) {
        for (Expense e : expenses) {
            if (e.getId() == id) return e;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    public ArrayList<Expense> searchExpenses(String field, String keyword) {
        ArrayList<Expense> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty())
            return new ArrayList<>(expenses);
        String kw = keyword.trim().toLowerCase();

        for (Expense e : expenses) {
            switch (field) {
                case "ID":
                    if (String.valueOf(e.getId()).equals(kw)) results.add(e);
                    break;
                case "Date":
                    if (e.getDate().toString().contains(kw)) results.add(e);
                    break;
                case "Category":
                    if (e.getCategory().toLowerCase().contains(kw)) results.add(e);
                    break;
                case "Description":
                    if (e.getDescription().toLowerCase().contains(kw)) results.add(e);
                    break;
                default:
                    results.add(e);
            }
        }
        return results;
    }

    public ArrayList<Expense> globalSearch(String keyword) {
        ArrayList<Expense> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>(expenses);
        String kw = keyword.trim().toLowerCase();
        for (Expense e : expenses) {
            if (String.valueOf(e.getId()).contains(kw)
                    || e.getDate().toString().contains(kw)
                    || e.getCategory().toLowerCase().contains(kw)
                    || e.getDescription().toLowerCase().contains(kw)
                    || String.valueOf(e.getAmount()).contains(kw)) {
                result.add(e);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Summaries
    // -------------------------------------------------------------------------

    public Map<LocalDate, Double> getDailySummary() {
        Map<LocalDate, Double> summary = new LinkedHashMap<>();
        expenses.stream()
                .sorted(Comparator.comparing(Expense::getDate))
                .forEach(e -> summary.merge(e.getDate(), e.getAmount(), Double::sum));
        return summary;
    }

    public Map<YearMonth, Double> getMonthlySummary() {
        Map<YearMonth, Double> summary = new LinkedHashMap<>();
        expenses.stream()
                .sorted(Comparator.comparing(e -> YearMonth.from(e.getDate())))
                .forEach(e -> summary.merge(YearMonth.from(e.getDate()), e.getAmount(), Double::sum));
        return summary;
    }

    public Map<String, Double> getCategorySummary() {
        Map<String, Double> summary = new LinkedHashMap<>();
        expenses.stream()
                .sorted(Comparator.comparing(Expense::getCategory))
                .forEach(e -> summary.merge(e.getCategory(), e.getAmount(), Double::sum));
        return summary;
    }

    /**
     * Returns category → total breakdown for a specific YearMonth.
     * Used by the Monthly Report tab.
     */
    public Map<String, Double> getMonthlyCategoryBreakdown(YearMonth ym) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (Expense e : expenses) {
            if (!YearMonth.from(e.getDate()).equals(ym)) continue;
            breakdown.merge(e.getCategory(), e.getAmount(), Double::sum);
        }
        return breakdown;
    }

    public double getGrandTotal() {
        return expenses.stream().mapToDouble(Expense::getAmount).sum();
    }

    /**
     * Returns the total of all expenses whose date falls within the given
     * calendar year (January 1 – December 31).
     */
    public double getYearTotal(int year) {
        return expenses.stream()
                .filter(e -> e.getDate().getYear() == year)
                .mapToDouble(Expense::getAmount).sum();
    }

    public double getTodayTotal() {
        LocalDate today = LocalDate.now();
        return expenses.stream()
                .filter(e -> e.getDate().equals(today))
                .mapToDouble(Expense::getAmount).sum();
    }

    public double getMonthTotal() {
        YearMonth current = YearMonth.now();
        return expenses.stream()
                .filter(e -> YearMonth.from(e.getDate()).equals(current))
                .mapToDouble(Expense::getAmount).sum();
    }

    public int getTransactionCount() {
        return expenses.size();
    }

    public ArrayList<Expense> getExpensesForDate(LocalDate date) {
        ArrayList<Expense> result = new ArrayList<>();
        for (Expense e : expenses) {
            if (e.getDate().equals(date)) result.add(e);
        }
        return result;
    }

    public ArrayList<Expense> getExpensesForMonth(YearMonth ym) {
        ArrayList<Expense> result = new ArrayList<>();
        for (Expense e : expenses) {
            if (YearMonth.from(e.getDate()).equals(ym)) result.add(e);
        }
        return result;
    }

    public ArrayList<Expense> getExpensesForCategory(String category) {
        ArrayList<Expense> result = new ArrayList<>();
        for (Expense e : expenses) {
            if (e.getCategory().equalsIgnoreCase(category)) result.add(e);
        }
        return result;
    }

    /**
     * Returns all distinct years present in expense records plus the current year.
     * Sorted descending (most recent first).
     */
    public List<Integer> getAllYears() {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(LocalDate.now().getYear());
        for (Expense e : expenses) {
            years.add(e.getDate().getYear());
        }
        return new ArrayList<>(years);
    }
}
