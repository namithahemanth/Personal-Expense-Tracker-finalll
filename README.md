# Personal Expense Tracker

A beginner-friendly desktop application built with **Java Swing** to track, manage, and analyse personal expenses. Designed as a college micro-project demonstrating Object-Oriented Programming, file I/O, and GUI development.

---

## Features

| # | Feature |
|---|---------|
| 1 | Add expenses with an auto-generated unique ID, date, category, description, and amount |
| 2 | Display all expenses in a sortable table |
| 3 | Edit any existing expense by clicking its row |
| 4 | Delete selected expenses with a confirmation dialog |
| 5 | Search by ID, Date, Category, or Description |
| 6 | View **Daily** expense summaries as visual cards |
| 7 | View **Monthly** expense summaries as visual cards |
| 8 | View **Category-wise** expense summaries as visual cards |
| 9 | All amounts displayed in **Indian Rupees (₹)** |
| 10 | Data persisted in a CSV file — survives app restarts |
| 11 | Auto-saves after every add, edit, or delete |
| 12 | Input validation: date format, positive amount, non-empty fields |

---

## Technologies Used

- **Java 17+** (Java Swing for GUI)
- **Object-Oriented Programming** (4 classes)
- **ArrayList** for in-memory expense management
- **LocalDate** for date handling
- **BufferedReader / BufferedWriter** for CSV file I/O
- No external libraries, no database, no paid APIs

---

## Project Structure

```
PersonalExpenseTracker/
│
├── src/
│   ├── Expense.java          ← Data model (fields, getters, setters, toCsvLine)
│   ├── ExpenseManager.java   ← Business logic (add, edit, delete, search, summaries)
│   ├── FileHandler.java      ← CSV persistence (load on startup, save on change)
│   └── Main.java             ← Swing GUI (JFrame, JTable, input form, summary tabs)
│
├── data/
│   └── expenses.csv          ← Auto-created on first run; stores all expense records
│
├── out/                      ← Compiled .class files (generated during build)
│
└── README.md                 ← This file
```

---

## How to Compile and Run

### Prerequisites
- Java Development Kit (JDK) 17 or later installed
- Open a terminal (Command Prompt or PowerShell) in the `PersonalExpenseTracker` folder

### Step 1 — Compile

```powershell
javac -d out src\Expense.java src\FileHandler.java src\ExpenseManager.java src\Main.java
```

This compiles all four source files and places the `.class` files inside the `out/` folder.

### Step 2 — Run

```powershell
java -cp out Main
```

The application window will open. The `data/expenses.csv` file is created automatically on the first run.

---

## Using the Application

### Adding an Expense
1. Fill in **Date** (format: `YYYY-MM-DD`, e.g. `2024-06-15`)
2. Select a **Category** from the dropdown
3. Enter a **Description**
4. Enter the **Amount** (positive number, e.g. `150.50`)
5. Click **➕ Add Expense**

### Editing an Expense
1. Click any row in the table — the form fills automatically
2. Modify the fields as needed
3. Click **✏️ Update Expense**

### Deleting an Expense
1. Click the row you want to remove
2. Click **🗑 Delete Selected**
3. Confirm in the dialog that appears

### Searching
1. Choose a search field from the dropdown (ID / Date / Category / Description)
2. Type a keyword in the search box
3. Press **Enter** or click **🔍 Search**
4. Click **↺ Reset** to show all expenses again

### Summaries
- The **bottom tab panel** shows three views:
  - 📅 **Daily** — total spent per day
  - 📆 **Monthly** — total spent per month
  - 🏷 **Category** — total spent per category
- Each entry appears as a card with the label and formatted rupee amount

---

## CSV File Format

The `data/expenses.csv` file stores one expense per line:

```
id,date,category,description,amount
1,2024-06-15,Food,Lunch at canteen,150.0
2,2024-06-15,Travel,Auto to college,50.0
3,2024-06-16,Shopping,Notebook and pen,120.5
```

---

## Class Descriptions (for viva)

| Class | Responsibility |
|-------|---------------|
| `Expense` | Data model — holds one expense record with id, date, category, description, amount |
| `FileHandler` | Static utility — reads and writes the CSV file using BufferedReader/BufferedWriter |
| `ExpenseManager` | Business logic — manages the ArrayList, validates inputs, generates summaries |
| `Main` | GUI entry point — builds the JFrame with all panels, handles all user events |

---

## Author

Beginner BE Engineering Student  
Personal Expense Tracker — College Micro-Project
