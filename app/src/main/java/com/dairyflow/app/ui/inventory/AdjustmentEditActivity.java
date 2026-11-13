package com.dairyflow.app.ui.inventory;

import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.data.dao.AdjustmentDao;
import com.dairyflow.app.data.model.Adjustment;
import com.dairyflow.app.util.Format;

import java.util.Arrays;

/** Record a stock correction: spoilage (negative litres), returns, recounts. */
public class AdjustmentEditActivity extends Activity {

    private static final String[] REASONS = {"spoil", "wastage", "return", "count"};
    private static final String[] REASON_LABELS = {"Spoilage (sour/spoiled)", "Wastage (dropped/spilled)", "Customer return (+)", "Stock recount"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_edit);

        final EditText inputDate = findViewById(R.id.input_adj_date);
        final EditText inputLitres = findViewById(R.id.input_adj_litres);
        final Spinner spinnerReason = findViewById(R.id.spinner_adj_reason);
        final EditText inputNote = findViewById(R.id.input_adj_note);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(REASON_LABELS));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(adapter);
        inputDate.setText(Format.todayIso());

        findViewById(R.id.btn_save_adjustment).setOnClickListener(v -> {
            String date = inputDate.getText().toString().trim();
            if (!BillingEngine.isValidDate(date)) { inputDate.setError("Use yyyy-MM-dd"); return; }
            String litresRaw = inputLitres.getText().toString().trim();
            double litres = Format.parseDouble(litresRaw, Double.NaN);
            if (Double.isNaN(litres) || litres == 0) {
                inputLitres.setError("Enter litres (use negative for loss)");
                inputLitres.requestFocus();
                return;
            }
            Adjustment a = new Adjustment();
            a.date = date;
            a.litres = litres;
            a.reason = REASONS[spinnerReason.getSelectedItemPosition()];
            a.note = Format.blankToNull(inputNote.getText().toString());
            new AdjustmentDao(this).insert(a);
            Toast.makeText(this, "Adjustment saved — stock updated", Toast.LENGTH_SHORT).show();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            finish();
        });
    }
}
