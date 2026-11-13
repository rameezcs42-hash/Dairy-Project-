package com.dairyflow.app.ui.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.data.dao.PurchaseDao;
import com.dairyflow.app.data.model.Purchase;
import com.dairyflow.app.util.Format;

import java.util.Arrays;
import java.util.List;

/** Log a milk purchase (union / supplier / farm). Extras: purchase_id (long) for edit mode. */
public class PurchaseEditActivity extends Activity {

    private static final String[] MILK_TYPES = {"buffalo", "cow", "mixed"};

    private long purchaseId = -1;
    private PurchaseDao purchases;

    private EditText inputDate, inputSupplier, inputLitres, inputRate, inputFat, inputNote;
    private Spinner spinnerType;
    private TextView purchaseTotal;
    private Button btnDelete;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_edit);

        purchases = new PurchaseDao(this);
        purchaseId = getIntent().getLongExtra("purchase_id", -1);

        inputDate = findViewById(R.id.input_purchase_date);
        inputSupplier = findViewById(R.id.input_supplier);
        inputLitres = findViewById(R.id.input_purchase_litres);
        inputRate = findViewById(R.id.input_purchase_rate);
        inputFat = findViewById(R.id.input_purchase_fat);
        inputNote = findViewById(R.id.input_purchase_note);
        spinnerType = findViewById(R.id.spinner_purchase_type);
        purchaseTotal = findViewById(R.id.purchase_total);
        btnDelete = findViewById(R.id.btn_delete_purchase);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(MILK_TYPES));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);

        inputDate.setText(Format.todayIso());
        // Default purchase rate from settings
        inputRate.setText(trimDouble(new com.dairyflow.app.data.dao.SettingsDao(this).getDouble("default_purchase_rate", 95)));

        TextWatcher recalc = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateTotal(); }
        };
        inputLitres.addTextChangedListener(recalc);
        inputRate.addTextChangedListener(recalc);

        if (purchaseId > 0) {
            Purchase p = findPurchase(purchaseId);
            if (p == null) { Toast.makeText(this, "Purchase not found", Toast.LENGTH_SHORT).show(); finish(); return; }
            ((TextView) findViewById(R.id.purchase_form_title)).setText("Edit Purchase");
            inputDate.setText(p.date);
            inputSupplier.setText(p.supplier);
            inputLitres.setText(trimDouble(p.litres));
            inputRate.setText(trimDouble(p.rate));
            if (p.fat != null) inputFat.setText(trimDouble(p.fat));
            inputNote.setText(p.note);
            spinnerType.setSelection(indexOf(MILK_TYPES, p.milkType, 0));
            btnDelete.setVisibility(View.VISIBLE);
            updateTotal();
        }

        btnDelete.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btn_save_purchase).setOnClickListener(v -> save());
    }

    private Purchase findPurchase(long id) {
        for (Purchase p : purchases.range("1970-01-01", "9999-12-31")) if (p.id == id) return p;
        return null;
    }

    private void updateTotal() {
        double litres = Format.parseDouble(inputLitres.getText().toString(), 0);
        double rate = Format.parseDouble(inputRate.getText().toString(), 0);
        purchaseTotal.setText("Rs " + Format.money(litres * rate));
    }

    private void save() {
        String date = inputDate.getText().toString().trim();
        if (!BillingEngine.isValidDate(date)) { inputDate.setError("Use yyyy-MM-dd"); return; }
        double litres = Format.parseDouble(inputLitres.getText().toString(), 0);
        if (litres <= 0) { inputLitres.setError("Enter litres"); inputLitres.requestFocus(); return; }
        double rate = Format.parseDouble(inputRate.getText().toString(), 0);
        if (rate <= 0) { inputRate.setError("Enter rate"); inputRate.requestFocus(); return; }

        Purchase p = new Purchase();
        p.id = purchaseId;
        p.date = date;
        p.supplier = Format.blankToNull(inputSupplier.getText().toString());
        p.milkType = MILK_TYPES[spinnerType.getSelectedItemPosition()];
        p.litres = litres;
        p.rate = rate;
        p.amount = BillingEngine.round2(litres * rate);
        String fat = inputFat.getText().toString().trim();
        p.fat = fat.isEmpty() ? null : Format.parseDouble(fat, 0);
        p.note = Format.blankToNull(inputNote.getText().toString());

        if (purchaseId > 0) {
            purchases.update(p);
            Toast.makeText(this, "Purchase updated", Toast.LENGTH_SHORT).show();
        } else {
            purchases.insert(p);
            Toast.makeText(this, "Purchase saved — stock updated", Toast.LENGTH_SHORT).show();
        }
        hideKeyboard();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete purchase?")
                .setMessage("Stock will be reduced by this quantity.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    purchases.delete(purchaseId);
                    Toast.makeText(this, "Purchase deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
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
