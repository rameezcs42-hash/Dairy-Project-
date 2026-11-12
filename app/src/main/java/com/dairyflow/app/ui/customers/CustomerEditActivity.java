package com.dairyflow.app.ui.customers;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.util.Format;

import java.util.Arrays;
import java.util.List;

/** Add or edit a customer. Extras: customer_id (long) for edit mode. */
public class CustomerEditActivity extends Activity {

    private static final String[] MILK_TYPES = {"buffalo", "cow", "mixed"};
    private static final String[] SESSION_PREFS = {"both", "morning", "evening"};
    private static final String[] WEEKLY_OFF = {"none", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private long editId = -1;
    private CustomerDao customers;
    private EditText inputName, inputPhone, inputArea, inputAddress, inputRate, inputMorning, inputEvening, inputOpening, inputNote;
    private Spinner spinnerMilk, spinnerSession, spinnerOff;
    private Button btnDelete;
    private TextView formTitle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_edit);

        customers = new CustomerDao(this);
        editId = getIntent().getLongExtra("customer_id", -1);

        formTitle = findViewById(R.id.form_title);
        inputName = findViewById(R.id.input_name);
        inputPhone = findViewById(R.id.input_phone);
        inputArea = findViewById(R.id.input_area);
        inputAddress = findViewById(R.id.input_address);
        inputRate = findViewById(R.id.input_rate);
        inputMorning = findViewById(R.id.input_morning);
        inputEvening = findViewById(R.id.input_evening);
        inputOpening = findViewById(R.id.input_opening);
        inputNote = findViewById(R.id.input_note);
        spinnerMilk = findViewById(R.id.spinner_milk);
        spinnerSession = findViewById(R.id.spinner_session);
        spinnerOff = findViewById(R.id.spinner_off);
        btnDelete = findViewById(R.id.btn_delete_customer);

        spinnerMilk.setAdapter(spinnerAdapter(MILK_TYPES));
        spinnerSession.setAdapter(spinnerAdapter(SESSION_PREFS));
        spinnerOff.setAdapter(spinnerAdapter(WEEKLY_OFF));

        if (editId > 0) {
            Customer c = customers.getById(editId);
            if (c == null) {
                Toast.makeText(this, "Customer not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            formTitle.setText("Edit Customer");
            inputName.setText(c.name);
            inputPhone.setText(c.phone);
            inputArea.setText(c.area);
            inputAddress.setText(c.address);
            inputRate.setText(c.defaultRate > 0 ? trimDouble(c.defaultRate) : "");
            inputMorning.setText(c.morningQty > 0 ? trimDouble(c.morningQty) : "");
            inputEvening.setText(c.eveningQty > 0 ? trimDouble(c.eveningQty) : "");
            inputOpening.setText(c.openingBalance != 0 ? trimDouble(c.openingBalance) : "");
            inputNote.setText(c.note);
            spinnerMilk.setSelection(indexOf(MILK_TYPES, c.milkType, 0));
            spinnerSession.setSelection(indexOf(SESSION_PREFS, c.sessionPref, 0));
            spinnerOff.setSelection(c.weeklyOff >= 1 && c.weeklyOff <= 7 ? c.weeklyOff : 0);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            formTitle.setText("Add Customer");
            inputRate.setText("130");
            inputMorning.setText("2");
        }

        btnDelete.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btn_save_customer).setOnClickListener(v -> save());
    }

    private void save() {
        String name = inputName.getText().toString().trim();
        if (name.isEmpty()) {
            inputName.setError("Name is required");
            inputName.requestFocus();
            return;
        }
        double rate = Format.parseDouble(inputRate.getText().toString(), 0);
        if (rate <= 0) {
            inputRate.setError("Enter rate per litre");
            inputRate.requestFocus();
            return;
        }
        Customer c;
        if (editId > 0) {
            c = customers.getById(editId);
            if (c == null) c = new Customer();
            c.id = editId;
        } else {
            c = new Customer();
        }
        c.name = name;
        c.phone = Format.blankToNull(inputPhone.getText().toString());
        c.area = Format.blankToNull(inputArea.getText().toString());
        c.address = Format.blankToNull(inputAddress.getText().toString());
        c.milkType = MILK_TYPES[spinnerMilk.getSelectedItemPosition()];
        c.defaultRate = rate;
        c.morningQty = Format.parseDouble(inputMorning.getText().toString(), 0);
        c.eveningQty = Format.parseDouble(inputEvening.getText().toString(), 0);
        c.openingBalance = Format.parseDouble(inputOpening.getText().toString(), 0);
        c.note = Format.blankToNull(inputNote.getText().toString());
        c.sessionPref = SESSION_PREFS[spinnerSession.getSelectedItemPosition()];
        c.weeklyOff = spinnerOff.getSelectedItemPosition(); // 0=none, 1..7=Mon..Sun
        c.active = true;

        if (editId > 0) {
            customers.update(c);
            Toast.makeText(this, "Customer updated", Toast.LENGTH_SHORT).show();
        } else {
            long id = customers.insert(c);
            Toast.makeText(this, "Customer added", Toast.LENGTH_SHORT).show();
        }
        hideKeyboard();
        finish();
    }

    private void confirmDelete() {
        final Customer c = customers.getById(editId);
        if (c == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete " + c.name + "?")
                .setMessage("All deliveries, payments and bills for this customer will also be deleted. This cannot be undone.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    customers.delete(editId);
                    Toast.makeText(this, "Customer deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(items));
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private static int indexOf(String[] items, String value, int fallback) {
        if (value == null) return fallback;
        for (int i = 0; i < items.length; i++) if (items[i].equalsIgnoreCase(value)) return i;
        return fallback;
    }

    private static String trimDouble(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }
}
