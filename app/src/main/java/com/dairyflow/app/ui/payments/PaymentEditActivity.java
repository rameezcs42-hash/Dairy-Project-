package com.dairyflow.app.ui.payments;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.dao.PaymentDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.model.Payment;
import com.dairyflow.app.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Record or edit a payment. Extras: payment_id (long) edit mode, customer_id (long) preselect. */
public class PaymentEditActivity extends Activity {

    private static final String[] METHODS = {"cash", "easypaisa", "jazzcash", "bank"};

    private long paymentId = -1;
    private long preselectCustomer = -1;
    private CustomerDao customers;
    private PaymentDao payments;
    private List<Customer> spinnerItems = new ArrayList<>();

    private Spinner spinnerCustomer;
    private EditText inputAmount, inputDate, inputNote;
    private RadioGroup methodGroup;
    private RadioButton methodCash, methodEasypaisa, methodJazzcash, methodBank;
    private Button btnDelete;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_edit);

        payments = new PaymentDao(this);
        customers = new CustomerDao(this);
        paymentId = getIntent().getLongExtra("payment_id", -1);
        preselectCustomer = getIntent().getLongExtra("customer_id", -1);

        spinnerCustomer = findViewById(R.id.spinner_customer);
        inputAmount = findViewById(R.id.input_amount);
        inputDate = findViewById(R.id.input_pay_date);
        inputNote = findViewById(R.id.input_pay_note);
        methodGroup = findViewById(R.id.method_group);
        methodCash = findViewById(R.id.method_cash);
        methodEasypaisa = findViewById(R.id.method_easypaisa);
        methodJazzcash = findViewById(R.id.method_jazzcash);
        methodBank = findViewById(R.id.method_bank);
        btnDelete = findViewById(R.id.btn_delete_payment);

        spinnerItems = customers.list(true);
        List<String> names = new ArrayList<>();
        for (Customer c : spinnerItems) names.add(c.name + (c.active ? "" : " (inactive)"));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomer.setAdapter(adapter);

        inputDate.setText(Format.todayIso());

        if (paymentId > 0) {
            Payment p = findPayment(paymentId);
            if (p == null) { Toast.makeText(this, "Payment not found", Toast.LENGTH_SHORT).show(); finish(); return; }
            findViewById(R.id.pay_form_title).setVisibility(View.VISIBLE);
            ((android.widget.TextView) findViewById(R.id.pay_form_title)).setText("Edit Payment");
            inputAmount.setText(trimDouble(p.amount));
            inputDate.setText(p.date);
            inputNote.setText(p.note);
            selectCustomer(p.customerId);
            selectMethod(p.method);
            btnDelete.setVisibility(View.VISIBLE);
        } else if (preselectCustomer > 0) {
            selectCustomer(preselectCustomer);
        }

        btnDelete.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btn_save_payment).setOnClickListener(v -> save());
        inputAmount.requestFocus();
    }

    private Payment findPayment(long id) {
        for (Payment p : payments.range("1970-01-01", "9999-12-31")) if (p.id == id) return p;
        return null;
    }

    private void selectCustomer(long customerId) {
        for (int i = 0; i < spinnerItems.size(); i++) {
            if (spinnerItems.get(i).id == customerId) { spinnerCustomer.setSelection(i); return; }
        }
    }

    private void selectMethod(String method) {
        if (method == null) { methodCash.setChecked(true); return; }
        switch (method) {
            case "easypaisa": methodEasypaisa.setChecked(true); break;
            case "jazzcash": methodJazzcash.setChecked(true); break;
            case "bank": methodBank.setChecked(true); break;
            default: methodCash.setChecked(true); break;
        }
    }

    private String selectedMethod() {
        int checked = methodGroup.getCheckedRadioButtonId();
        if (checked == R.id.method_easypaisa) return "easypaisa";
        if (checked == R.id.method_jazzcash) return "jazzcash";
        if (checked == R.id.method_bank) return "bank";
        return "cash";
    }

    private void save() {
        if (spinnerItems.isEmpty()) {
            Toast.makeText(this, "Add a customer first", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount = Format.parseDouble(inputAmount.getText().toString(), 0);
        if (amount <= 0) {
            inputAmount.setError("Enter amount");
            inputAmount.requestFocus();
            return;
        }
        String date = inputDate.getText().toString().trim();
        if (!com.dairyflow.app.billing.BillingEngine.isValidDate(date)) {
            inputDate.setError("Use yyyy-MM-dd");
            inputDate.requestFocus();
            return;
        }
        long customerId = spinnerItems.get(spinnerCustomer.getSelectedItemPosition()).id;

        Payment p = new Payment();
        p.id = paymentId;
        p.customerId = customerId;
        p.amount = amount;
        p.date = date;
        p.method = selectedMethod();
        p.note = Format.blankToNull(inputNote.getText().toString());

        if (paymentId > 0) {
            payments.update(p);
            Toast.makeText(this, "Payment updated", Toast.LENGTH_SHORT).show();
        } else {
            payments.insert(p);
            Toast.makeText(this, "Payment saved", Toast.LENGTH_SHORT).show();
        }
        hideKeyboard();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete payment?")
                .setMessage("This payment will be removed and the customer balance recalculated.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    payments.delete(paymentId);
                    Toast.makeText(this, "Payment deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
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
