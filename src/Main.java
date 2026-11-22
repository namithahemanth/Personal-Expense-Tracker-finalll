/**
 * Main.java
 * Entry point for the Personal Expense Tracker.
 *
 * Launches AppWindow on the Swing Event Dispatch Thread.
 *
 * Compile:
 *   javac -d out src\AppTheme.java src\MiniChart.java src\Expense.java
 *         src\FileHandler.java src\ExpenseManager.java
 *         src\ExpenseFormDialog.java src\DashboardPanel.java
 *         src\ExpensesPanel.java src\ReportsPanel.java
 *         src\AppWindow.java src\Main.java
 *
 * Run:
 *   java -cp out Main
 */
public class Main {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            // AppWindow calls setVisible(true) internally after building the frame
            new AppWindow();
        });
    }
}
