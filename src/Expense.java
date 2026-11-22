import java.time.LocalDate;

/**
 * Expense.java
 * Represents a single expense entry.
 *
 * CSV format: id,date,category,description,amount
 *
 * Backward-compatible loading:
 *   v1 (5 cols): id,date,category,description,amount           ← native format
 *   v2 (6 cols): id,date,mainCat,subCat,description,amount     ← old format, subCat ignored
 */
public class Expense {

    // -------------------------------------------------------------------------
    // Flat category list — all selectable options in one dropdown.
    // Built from: broad categories + all subcategory-level entries, deduplicated.
    // -------------------------------------------------------------------------
    public static final String[] CATEGORIES = {
        // Food group
        "Groceries",
        "Restaurants",
        "Cafes",
        "Food Delivery",
        "Snacks",
        "Fruits and Vegetables",
        // Transportation group
        "Bus",
        "Train",
        "Auto Rickshaw",
        "Taxi/Cab",
        "Fuel",
        "Vehicle Maintenance",
        "Parking",
        // Bills and Utilities group
        "Electricity Bill",
        "Water Bill",
        "Internet Bill",
        "Mobile Bill",
        "Gas Bill",
        "Rent",
        "Maintenance Charges",
        // Shopping group
        "Clothing",
        "Footwear",
        "Electronics",
        "Accessories",
        "Household Items",
        "Personal Care",
        // Education group
        "Tuition Fees",
        "Books and Stationery",
        "Online Courses",
        "Examination Fees",
        "College Expenses",
        // Healthcare group
        "Doctor Consultation",
        "Medicines",
        "Medical Tests",
        "Hospital Expenses",
        "Dental Care",
        // Entertainment group
        "Movies",
        "Events and Concerts",
        "Gaming",
        "Subscriptions",
        "Recreation",
        // Travel group
        "Hotels and Accommodation",
        "Flights",
        "Train Tickets",
        "Bus Tickets",
        "Sightseeing",
        "Travel Food",
        // Working Employee group
        "Office Commute",
        "Work Meals",
        "Office Supplies",
        "Work Clothing",
        "Professional Development",
        // Groceries (standalone)
        "Dairy Products",
        "Household Essentials",
        "Food Grains",
        "Other Groceries",
        // Subscriptions (standalone)
        "Streaming Services",
        "Music Subscriptions",
        "Software Subscriptions",
        "News and Magazine Subscriptions",
        "Other Subscriptions",
        // Loans
        "Education Loan",
        "Personal Loan",
        "Home Loan",
        "Vehicle Loan",
        "Loan Interest",
        "Other Loan Payments",
        // Legacy broad-category names (kept so existing CSV records remain valid)
        "Food",
        "Transportation",
        "Bills and Utilities",
        "Shopping",
        "Education",
        "Healthcare",
        "Entertainment",
        "Travel",
        "Working Employee",
        "Bills",
        "Loans",
        // Extra legacy names used in older CSV records
        "Transport",
        "Health",
        // Other
        "Gifts",
        "Donations",
        "Miscellaneous",
        "Other"
    };

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------
    private int       id;
    private LocalDate date;
    private String    category;
    private String    description;
    private double    amount;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public Expense(int id, LocalDate date, String category, String description, double amount) {
        this.id          = id;
        this.date        = date;
        this.category    = (category == null || category.trim().isEmpty()) ? "Other" : category.trim();
        this.description = description;
        this.amount      = amount;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------
    public int       getId()          { return id; }
    public LocalDate getDate()        { return date; }
    public String    getCategory()    { return category; }
    public String    getDescription() { return description; }
    public double    getAmount()      { return amount; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------
    public void setDate(LocalDate date)           { this.date = date; }
    public void setCategory(String category)      { this.category = category; }
    public void setDescription(String description){ this.description = description; }
    public void setAmount(double amount)          { this.amount = amount; }

    /**
     * Converts this expense to a CSV line.
     * Format: id,date,category,description,amount
     * Description is quoted if it contains commas.
     */
    public String toCsvLine() {
        String safeDesc = description.contains(",") ? "\"" + description + "\"" : description;
        String safeCat  = category.contains(",")    ? "\"" + category    + "\"" : category;
        return id + "," + date.toString() + "," + safeCat + "," + safeDesc + "," + amount;
    }

    @Override
    public String toString() {
        return "Expense{id=" + id + ", date=" + date + ", category=" + category
                + ", description=" + description + ", amount=" + amount + "}";
    }
}
