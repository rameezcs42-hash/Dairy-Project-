package com.dairyflow.app.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.MilkRateDao;
import com.dairyflow.app.data.model.MilkRate;
import com.dairyflow.app.util.Format;

import java.util.Arrays;
import java.util.List;

/** Effective-dated milk rate management: view current rates, add new ones, browse history. */
public class RatesActivity extends Activity {

    private static final String[] MILK_TYPES = {"buffalo", "cow", "mixed"};
    private static final String[] KINDS = {"sale", "purchase"};

    private MilkRateDao rates;
    private LinearLayoutContainer currentRates;
    private ListView historyList;
    private Spinner spinnerMilk, spinnerType;
    private EditText inputPrice, inputEffective;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rates);

        rates = new MilkRateDao(this);
        currentRates = new LinearLayoutContainer(findViewById(R.id.current_rates));
        historyList = findViewById(R.id.rate_history_list);
        spinnerMilk = findViewById(R.id.spinner_rate_milk);
        spinnerType = findViewById(R.id.spinner_rate_type);
        inputPrice = findViewById(R.id.input_rate_price);
        inputEffective = findViewById(R.id.input_rate_effective);

        spinnerMilk.setAdapter(spinnerAdapter(MILK_TYPES));
        spinnerType.setAdapter(spinnerAdapter(KINDS));
        inputEffective.setText(Format.todayIso());

        findViewById(R.id.btn_save_rate).setOnClickListener(v -> saveRate());
        load();
    }

    private void saveRate() {
        double price = Format.parseDouble(inputPrice.getText().toString(), 0);
        if (price <= 0) {
            inputPrice.setError("Enter rate");
            inputPrice.requestFocus();
            return;
        }
        String effective = inputEffective.getText().toString().trim();
        if (!com.dairyflow.app.billing.BillingEngine.isValidDate(effective)) {
            inputEffective.setError("Use yyyy-MM-dd");
            inputEffective.requestFocus();
            return;
        }
        MilkRate r = new MilkRate();
        r.kind = KINDS[spinnerType.getSelectedItemPosition()];
        r.milkType = MILK_TYPES[spinnerMilk.getSelectedItemPosition()];
        r.rate = price;
        r.effectiveFrom = effective;
        rates.insert(r);
        inputPrice.setText("");
        Toast.makeText(this, "Rate saved — applies from " + Format.pretty(effective), Toast.LENGTH_SHORT).show();
        load();
    }

    private void load() {
        // Current effective sale rates per milk type (purchase shown in history)
        currentRates.clear();
        for (String milk : MILK_TYPES) {
            Double sale = rates.rateOn("sale", milk, Format.todayIso());
            Double purchase = rates.rateOn("purchase", milk, Format.todayIso());
            currentRates.add(milk, sale != null ? "Rs " + Format.money0(sale) + "/L sale" : "sale rate not set",
                    purchase != null ? "Rs " + Format.money0(purchase) + "/L purchase" : "purchase rate not set");
        }

        List<MilkRate> history = rates.all();
        history.sort((a, b) -> b.effectiveFrom.compareTo(a.effectiveFrom));
        historyList.setAdapter(new RateAdapter(this, history));
    }

    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(items));
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    /** Tiny wrapper so the current-rates container can hold dynamically created rows. */
    private final class LinearLayoutContainer {
        private final android.widget.LinearLayout box;
        LinearLayoutContainer(View v) { box = (android.widget.LinearLayout) v; }
        void clear() { box.removeAllViews(); }
        void add(String milk, String sale, String purchase) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(RatesActivity.this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(18, 12, 18, 12);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView milkView = new TextView(RatesActivity.this);
            milkView.setText(Character.toUpperCase(milk.charAt(0)) + milk.substring(1));
            milkView.setTextColor(0xFF10231B);
            milkView.setTextSize(15f);
            milkView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(milkView, lp);

            TextView right = new TextView(RatesActivity.this);
            right.setText(sale + "\n" + purchase);
            right.setTextColor(0xFF42554B);
            right.setTextSize(12f);
            row.addView(right);
            box.addView(row);

            android.view.View divider = new android.view.View(RatesActivity.this);
            divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFD8E9E0);
            box.addView(divider);
        }
    }

    private static final class RateAdapter extends BaseAdapter {
        private final RatesActivity ctx;
        private final List<MilkRate> items;

        RateAdapter(RatesActivity ctx, List<MilkRate> items) { this.ctx = ctx; this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public MilkRate getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_rate, parent, false);
            final MilkRate r = items.get(position);
            ((TextView) row.findViewById(R.id.row_rate_type)).setText(
                    Character.toUpperCase(r.kind.charAt(0)) + r.kind.substring(1) + " / "
                            + Character.toUpperCase(r.milkType.charAt(0)) + r.milkType.substring(1));
            ((TextView) row.findViewById(R.id.row_rate_date)).setText(Format.pretty(r.effectiveFrom));
            ((TextView) row.findViewById(R.id.row_rate_price)).setText("Rs " + Format.money0(r.rate));
            row.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(ctx)
                        .setTitle("Delete rate?")
                        .setMessage("This rate record will be removed. Deliveries already priced with it keep their prices.")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            ctx.rates.delete(r.id);
                            ctx.load();
                        })
                        .show();
                return true;
            });
            return row;
        }
    }
}
