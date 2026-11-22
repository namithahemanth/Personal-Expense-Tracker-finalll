import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * ExpenseFormDialog.java
 *
 * Modal JDialog for ADD and EDIT expense operations.
 * Single Category dropdown (flat list — no sub-categories).
 *
 * Usage:
 *   Add:  new ExpenseFormDialog(parent, manager)
 *   Edit: new ExpenseFormDialog(parent, manager, existingExpense)
 *
 * Call showDialog() — blocks until save/cancel, returns true if saved.
 */
public class ExpenseFormDialog extends JDialog {

    private final ExpenseManager manager;
    private final Expense        editTarget; // null = add mode

    // Form fields
    private JTextField        tfDate, tfDescription, tfAmount;
    private JComboBox<String> cbCategory;
    private JLabel            lblError;

    private boolean saved = false;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------
    public ExpenseFormDialog(Frame parent, ExpenseManager manager) {
        this(parent, manager, null);
    }

    public ExpenseFormDialog(Frame parent, ExpenseManager manager, Expense editTarget) {
        super(parent, editTarget == null ? "Add Expense" : "Edit Expense", true);
        this.manager    = manager;
        this.editTarget = editTarget;
        buildUI();
        if (editTarget != null) populateFields();
        pack();
        setMinimumSize(new Dimension(460, 0));
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.PAGE_BG);
        setContentPane(root);

        // ---- Header ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.SIDEBAR_BG);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JLabel titleLbl = new JLabel(editTarget == null ? "Add New Expense" : "Edit Expense");
        titleLbl.setFont(AppTheme.FONT_H2);
        titleLbl.setForeground(AppTheme.TEXT_WHITE);
        header.add(titleLbl, BorderLayout.WEST);

        JLabel subLbl = new JLabel(editTarget == null ? "Fill in the details below" : "Update the expense details");
        subLbl.setFont(AppTheme.FONT_SMALL);
        subLbl.setForeground(AppTheme.SIDEBAR_MUTED);
        header.add(subLbl, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ---- Form body ----
        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(AppTheme.PAGE_BG);
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 8, 24));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets  = new Insets(0, 0, 14, 0);
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx   = 0;

        Dimension fieldSize = new Dimension(410, 34);

        // Date
        gc.gridy = 0;
        tfDate = AppTheme.textField("e.g. 2024-06-15");
        tfDate.setPreferredSize(fieldSize);
        body.add(buildFieldBlock("Date  (YYYY-MM-DD)", tfDate), gc);

        // Category — flat dropdown
        gc.gridy = 1;
        cbCategory = AppTheme.comboBox(Expense.CATEGORIES);
        cbCategory.setPreferredSize(fieldSize);
        body.add(buildFieldBlock("Category", cbCategory), gc);

        // Description
        gc.gridy = 2;
        tfDescription = AppTheme.textField("e.g. Lunch at college canteen");
        tfDescription.setPreferredSize(fieldSize);
        body.add(buildFieldBlock("Description", tfDescription), gc);

        // Amount
        gc.gridy = 3;
        tfAmount = AppTheme.textField("e.g. 250.00");
        tfAmount.setPreferredSize(fieldSize);
        body.add(buildFieldBlock("Amount  (₹)", tfAmount), gc);

        // Error label
        gc.gridy = 4;
        lblError = new JLabel(" ");
        lblError.setFont(AppTheme.FONT_SMALL);
        lblError.setForeground(AppTheme.DANGER);
        body.add(lblError, gc);

        root.add(body, BorderLayout.CENTER);

        // ---- Button bar ----
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnBar.setBackground(AppTheme.PAGE_BG);
        btnBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
            BorderFactory.createEmptyBorder(14, 20, 18, 20)
        ));

        JButton btnCancel = AppTheme.secondaryButton("Cancel");
        JButton btnSave   = AppTheme.primaryButton(editTarget == null ? "Add Expense" : "Save Changes");

        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e   -> onSave());

        getRootPane().setDefaultButton(btnSave);
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        btnBar.add(btnCancel);
        btnBar.add(btnSave);
        root.add(btnBar, BorderLayout.SOUTH);
    }

    /** Wraps label + field vertically. */
    private JPanel buildFieldBlock(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(AppTheme.fieldLabel(labelText), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    // -------------------------------------------------------------------------
    // Populate when editing
    // -------------------------------------------------------------------------
    private void populateFields() {
        tfDate.setText(editTarget.getDate().toString());
        tfDescription.setText(editTarget.getDescription());
        tfAmount.setText(String.valueOf(editTarget.getAmount()));
        selectCategory(editTarget.getCategory());
    }

    private void selectCategory(String cat) {
        for (int i = 0; i < cbCategory.getItemCount(); i++) {
            if (cbCategory.getItemAt(i).equalsIgnoreCase(cat)) {
                cbCategory.setSelectedIndex(i);
                return;
            }
        }
        // Category not in list (e.g. a truly unknown legacy value) — add it temporarily
        cbCategory.addItem(cat);
        cbCategory.setSelectedItem(cat);
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------
    private void onSave() {
        lblError.setText(" ");

        String dateStr   = tfDate.getText().trim();
        String category  = (String) cbCategory.getSelectedItem();
        String desc      = tfDescription.getText().trim();
        String amountStr = tfAmount.getText().trim();

        try {
            if (editTarget == null) {
                manager.addExpense(dateStr, category, desc, amountStr);
            } else {
                manager.editExpense(editTarget.getId(), dateStr, category, desc, amountStr);
            }
            saved = true;
            dispose();
        } catch (IllegalArgumentException ex) {
            lblError.setText(ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    public boolean isSaved()    { return saved; }
    public boolean showDialog() { setVisible(true); return saved; }
}
