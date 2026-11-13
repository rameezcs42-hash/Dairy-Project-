package com.dairyflow.app.ui.expenses;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.ExpenseDao;
import com.dairyflow.app.data.model.Expense;
import com.dairyflow.app.util.Format;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** Shop expenses with quick-add dialog (no extra screen needed). */
public class ExpensesActivity extends Activity {

    private static final String[] CATEGORIES = {"rent", "salary", "transport", "electricity", "ice", "misc"};
    private static final String[] CATEGORY_LABELS = {"Rent", "Salary", "Transport", "Electricity", "Ice / misc", "Miscellaneous"};

    private ExpenseDao expenses;
    private ListView list;
    private TextView empty, monthTotal;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expenses);

        expenses = new ExpenseDao(this);
        list = findViewById(R.id.expense_list);
        empty = findViewById(R.id.expense_empty);
        monthTotal = findViewById(R.id.expense_month_total);

        findViewById(R.id.fab_add_expense).setOnClickListener(v -> showAddDialog());
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String month = Format.currentMonth();
        String from = month + "-01", to = month + "-31";
        monthTotal.setText(Format.pkr(expenses.total(from, to)));

        List<Expense> rows = expenses.range(from, to);
        if (rows.isEmpty()) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new ExpenseAdapter(this, rows));
        }
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText amountInput = new EditText(this);
        amountInput.setHint("Amount (Rs)");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setTextSize(22f);
        box.addView(amountInput);

        final Spinner category = new Spinner(this);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(CATEGORY_LABELS));
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(catAdapter);
        category.setPadding(0, pad / 2, 0, pad / 2);
        box.addView(category);

        final EditText noteInput = new EditText(this);
        noteInput.setHint("Note (optional)");
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(noteInput);

        new AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    double amount = Format.parseDouble(amountInput.getText().toString(), 0);
                    if (amount <= 0) {
                        Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Expense e = new Expense();
                    e.date = Format.todayIso();
                    e.amount = amount;
                    e.category = CATEGORIES[category.getSelectedItemPosition()];
                    e.note = Format.blankToNull(noteInput.getText().toString());
                    expenses.insert(e);
                    Toast.makeText(this, "Expense saved", Toast.LENGTH_SHORT).show();
                    load();
                })
                .show();
    }

    private static final class ExpenseAdapter extends BaseAdapter {
        private final Activity ctx;
        private final List<Expense> items;

        ExpenseAdapter(Activity ctx, List<Expense> items) { this.ctx = ctx; this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public Expense getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_expense, parent, false);
            final Expense e = items.get(position);
            ((TextView) row.findViewById(R.id.row_expense_category)).setText(categoryLabel(e.category));
            ((TextView) row.findViewById(R.id.row_expense_meta)).setText(
                    Format.pretty(e.date) + (e.note != null && !e.note.isEmpty() ? " • " + e.note : ""));
            ((TextView) row.findViewById(R.id.row_expense_amount)).setText("Rs " + Format.money0(e.amount));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ctx)
                        .setTitle("Delete expense?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            new ExpenseDao(ctx).delete(e.id);
                            Toast.makeText(ctx, "Expense deleted", Toast.LENGTH_SHORT).show();
                            ((ExpensesActivity) ctx).load();
                        })
                        .show();
                return true;
            });
            return row;
        }

        private static String categoryLabel(String cat) {
            if (cat == null) return "Miscellaneous";
            for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(cat)) return CATEGORY_LABELS[i];
            return cat;
        }
    }
}
