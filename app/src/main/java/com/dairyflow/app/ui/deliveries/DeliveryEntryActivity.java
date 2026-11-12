package com.dairyflow.app.ui.deliveries;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.dao.DeliveryDao;
import com.dairyflow.app.data.dao.HolidayDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.model.Delivery;
import com.dairyflow.app.data.repository.BillingRepository;
import com.dairyflow.app.util.Format;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fast daily milk-entry desk: pick a date & session, fill litres per customer,
 * tap OFF to mark skips (weekly off / one-off holiday pre-selected automatically),
 * then Save. UNIQUE(customer,date,session) guarantees saving twice never duplicates.
 */
public class DeliveryEntryActivity extends Activity {

    private BillingRepository repo;
    private DeliveryDao deliveries;
    private CustomerDao customers;
    private HolidayDao holidays;

    private String selectedDate = Format.todayIso();
    private String sessionFilter = "all"; // all | morning | evening

    private TextView entryDate, entryWeekday, entrySummary, entryEmpty;
    private ListView entryList;
    private TextView sessionAll, sessionMorning, sessionEvening;

    private final List<RowState> rows = new ArrayList<>();
    private EntryAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_entry);

        repo = new BillingRepository(this);
        deliveries = repo.deliveries();
        customers = repo.customers();
        holidays = repo.holidays();

        entryDate = findViewById(R.id.entry_date);
        entryWeekday = findViewById(R.id.entry_weekday);
        entrySummary = findViewById(R.id.entry_summary);
        entryEmpty = findViewById(R.id.entry_empty);
        entryList = findViewById(R.id.entry_list);
        sessionAll = findViewById(R.id.session_all);
        sessionMorning = findViewById(R.id.session_morning);
        sessionEvening = findViewById(R.id.session_evening);

        findViewById(R.id.prev_day).setOnClickListener(v -> shiftDate(-1));
        findViewById(R.id.next_day).setOnClickListener(v -> shiftDate(1));
        findViewById(R.id.date_button).setOnClickListener(v -> pickDate());
        sessionAll.setOnClickListener(v -> setSessionFilter("all"));
        sessionMorning.setOnClickListener(v -> setSessionFilter("morning"));
        sessionEvening.setOnClickListener(v -> setSessionFilter("evening"));
        findViewById(R.id.fill_defaults).setOnClickListener(v -> fillDefaults());
        findViewById(R.id.save_entries).setOnClickListener(v -> save());

        String start = getIntent().getStringExtra("date");
        if (start != null && BillingEngine.isValidDate(start)) selectedDate = start;
        setSessionFilter("all");
    }

    private void setSessionFilter(String f) {
        sessionFilter = f;
        sessionAll.setSelected("all".equals(f));
        sessionMorning.setSelected("morning".equals(f));
        sessionEvening.setSelected("evening".equals(f));
        load();
    }

    private void shiftDate(int delta) {
        Calendar cal = parseCalendar(selectedDate);
        cal.add(Calendar.DAY_OF_MONTH, delta);
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        load();
    }

    private void pickDate() {
        Calendar cal = parseCalendar(selectedDate);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth);
            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(picked.getTime());
            load();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static Calendar parseCalendar(String iso) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso));
            return cal;
        } catch (ParseException e) {
            return Calendar.getInstance();
        }
    }

    /** Build the row model for the selected date & filter. Existing rows win; otherwise apply auto-skip. */
    private void load() {
        rows.clear();
        entryDate.setText(Format.pretty(selectedDate));
        entryWeekday.setText(Format.weekdayOfDate(selectedDate) + (isToday() ? "  •  Today" : ""));

        int weekday = BillingEngine.weekdayOf(selectedDate);
        List<Customer> active = customers.list(false);
        Map<String, Delivery> existing = new HashMap<>();
        for (Delivery d : deliveries.forDate(selectedDate)) {
            existing.put(d.customerId + "|" + d.session, d);
        }

        for (Customer c : active) {
            if (c.receivesMorning() && !"evening".equals(sessionFilter)) addRow(c, "morning", weekday, existing);
            if (c.receivesEvening() && !"morning".equals(sessionFilter)) addRow(c, "evening", weekday, existing);
        }

        if (rows.isEmpty()) {
            entryList.setVisibility(View.GONE);
            entryEmpty.setVisibility(View.VISIBLE);
        } else {
            entryEmpty.setVisibility(View.GONE);
            entryList.setVisibility(View.VISIBLE);
        }
        adapter = new EntryAdapter(this, rows);
        entryList.setAdapter(adapter);
        updateSummary();
    }

    private void addRow(Customer c, String session, int weekday, Map<String, Delivery> existing) {
        RowState r = new RowState();
        r.customer = c;
        r.session = session;
        r.rate = repo.saleRateOn(c.milkType, selectedDate, c.defaultRate);

        boolean autoSkip = (c.weeklyOff == weekday && weekday != 0) || holidays.isHoliday(c.id, selectedDate);
        double defQty = "morning".equals(session) ? c.morningQty : c.eveningQty;

        Delivery found = existing.get(c.id + "|" + session);
        if (found != null) {
            r.qty = found.litres;
            r.skipped = found.skipped;
            r.rate = found.rate > 0 ? found.rate : r.rate; // keep the historical snapshot if stored
            r.fromExisting = true;
        } else {
            r.skipped = autoSkip;
            r.qty = r.skipped ? 0 : defQty;
        }
        rows.add(r);
    }

    private void fillDefaults() {
        for (RowState r : rows) {
            if (r.skipped) continue;
            double defQty = "morning".equals(r.session) ? r.customer.morningQty : r.customer.eveningQty;
            r.qty = defQty;
        }
        adapter.notifyDataSetChanged();
        updateSummary();
    }

    private void save() {
        int savedCount = 0, skippedCount = 0;
        for (RowState r : rows) {
            Delivery d = new Delivery();
            d.customerId = r.customer.id;
            d.date = selectedDate;
            d.session = r.session;
            d.litres = r.skipped ? 0 : r.qty;
            d.rate = r.rate;
            d.skipped = r.skipped;
            deliveries.upsert(d);
            savedCount++;
            if (r.skipped) skippedCount++;
        }
        Toast.makeText(this, "Saved " + savedCount + " entries (" + skippedCount + " skipped)", Toast.LENGTH_SHORT).show();
        updateSummary();
    }

    private void updateSummary() {
        int active = 0, skipped = 0;
        double litres = 0;
        for (RowState r : rows) {
            if (r.skipped) skipped++;
            else { active++; litres += r.qty; }
        }
        entrySummary.setText(rows.size() + " entries • " + Format.litres(litres) + " L • " + skipped + " skipped");
    }

    private boolean isToday() { return Format.todayIso().equals(selectedDate); }

    // ---------- row model & adapter ----------

    private final class RowState {
        Customer customer;
        String session;
        double qty;
        double rate;
        boolean skipped;
        boolean fromExisting;
    }

    private static final class EntryAdapter extends BaseAdapter {
        private final Activity ctx;
        private final List<RowState> items;

        EntryAdapter(Activity ctx, List<RowState> items) { this.ctx = ctx; this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public RowState getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            // Never recycle: each row owns an EditText with listeners.
            View row = LayoutInflater.from(ctx).inflate(R.layout.row_delivery_entry, parent, false);
            final RowState r = items.get(position);
            TextView name = row.findViewById(R.id.entry_name);
            TextView meta = row.findViewById(R.id.entry_meta);
            final EditText qty = row.findViewById(R.id.entry_qty);
            final TextView skip = row.findViewById(R.id.entry_skip);

            name.setText(r.customer.name);
            meta.setText((r.customer.area != null && !r.customer.area.isEmpty() ? r.customer.area + " • " : "")
                    + capitalize(r.customer.milkType) + " • Rs " + Format.money0(r.rate)
                    + ("evening".equals(r.session) ? " • EVE" : " • MON"));
            qty.setText(r.qty == 0 ? "" : trimDouble(r.qty));
            applySkipState(skip, qty, r);

            qty.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    r.qty = Format.parseDouble(s == null ? "" : s.toString(), 0);
                    DeliveryEntryActivity host = (DeliveryEntryActivity) ctx;
                    host.updateSummary();
                }
            });

            skip.setOnClickListener(v -> {
                r.skipped = !r.skipped;
                if (r.skipped) {
                    r.qty = 0;
                    qty.setText("");
                } else {
                    double defQty = "morning".equals(r.session) ? r.customer.morningQty : r.customer.eveningQty;
                    r.qty = defQty;
                    qty.setText(defQty == 0 ? "" : trimDouble(defQty));
                }
                applySkipState(skip, qty, r);
                DeliveryEntryActivity host = (DeliveryEntryActivity) ctx;
                host.updateSummary();
            });
            return row;
        }

        private static void applySkipState(TextView skip, EditText qty, RowState r) {
            if (r.skipped) {
                skip.setText("OFF");
                skip.setTextColor(0xFFFFFFFF);
                skip.setBackgroundResource(R.drawable.bg_chip_off);
                qty.setText("");
                qty.setEnabled(false);
                qty.setAlpha(0.35f);
            } else {
                skip.setText("ON");
                skip.setTextColor(0xFF065F46);
                skip.setBackgroundResource(R.drawable.bg_chip_on);
                qty.setEnabled(true);
                qty.setAlpha(1f);
            }
        }

        private static String trimDouble(double v) {
            if (v == Math.floor(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }

        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return "";
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
