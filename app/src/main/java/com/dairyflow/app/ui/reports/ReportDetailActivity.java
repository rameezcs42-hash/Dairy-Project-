package com.dairyflow.app.ui.reports;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.dao.DeliveryDao;
import com.dairyflow.app.data.dao.ExpenseDao;
import com.dairyflow.app.data.dao.MilkRateDao;
import com.dairyflow.app.data.dao.PaymentDao;
import com.dairyflow.app.data.dao.PurchaseDao;
import com.dairyflow.app.data.dao.SettingsDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.model.Delivery;
import com.dairyflow.app.data.model.MilkRate;
import com.dairyflow.app.data.model.Payment;
import com.dairyflow.app.data.repository.BillingRepository;
import com.dairyflow.app.pdf.ReportPdf;
import com.dairyflow.app.provider.ShareProvider;
import com.dairyflow.app.util.Format;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic report renderer with prev/next period navigation and a one-tap PDF export.
 * Report type comes from {@link ReportsActivity#EXTRA_REPORT}.
 */
public class ReportDetailActivity extends Activity {

    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private BillingRepository repo;
    private SettingsDao settings;
    private String reportType;

    // Period state
    private String day = Format.todayIso();       // daily
    private String month = Format.currentMonth(); // monthly / customers / pnl

    private TextView reportTitle, reportPeriod, reportSummary, reportNext, reportPrev;
    private ListView list;

    private final List<String[]> rows = new ArrayList<>();
    private String[] currentColumns;
    private String[] currentSummary;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);
        repo = new BillingRepository(this);
        settings = new SettingsDao(this);
        reportType = getIntent().getStringExtra(ReportsActivity.EXTRA_REPORT);
        if (reportType == null) reportType = ReportsActivity.DAILY;

        reportTitle = findViewById(R.id.report_title);
        reportPeriod = findViewById(R.id.report_period);
        reportSummary = findViewById(R.id.report_summary);
        reportPrev = findViewById(R.id.report_prev);
        reportNext = findViewById(R.id.report_next);
        list = findViewById(R.id.report_list);

        boolean daily = ReportsActivity.DAILY.equals(reportType);
        if (ReportsActivity.RATES.equals(reportType)) {
            reportPrev.setVisibility(View.GONE);
            reportNext.setVisibility(View.GONE);
        } else {
            reportPrev.setOnClickListener(v -> shift(-1));
            reportNext.setOnClickListener(v -> shift(1));
        }

        findViewById(R.id.report_export).setOnClickListener(v -> exportPdf());
        load();
    }

    private void shift(int delta) {
        if (ReportsActivity.DAILY.equals(reportType)) {
            try {
                Calendar cal = Calendar.getInstance();
                cal.setTime(ISO.parse(day));
                cal.add(Calendar.DAY_OF_MONTH, delta);
                day = ISO.format(cal.getTime());
            } catch (Exception e) { /* keep day */ }
        } else {
            month = Format.monthShift(month, delta);
        }
        load();
    }

    private void load() {
        rows.clear();
        switch (reportType) {
            case ReportsActivity.DAILY: loadDaily(); break;
            case ReportsActivity.MONTHLY: loadMonthly(); break;
            case ReportsActivity.CUSTOMERS: loadCustomers(); break;
            case ReportsActivity.OUTSTANDING: loadOutstanding(); break;
            case ReportsActivity.PNL: loadPnl(); break;
            case ReportsActivity.RATES: loadRates(); break;
            default: loadDaily(); break;
        }
        list.setAdapter(new ReportAdapter(this, rows));
    }

    // ---------------- report builders ----------------

    private void loadDaily() {
        reportTitle.setText("Daily Sales Report");
        reportPeriod.setText(Format.prettyLong(day));
        DeliveryDao deliveries = repo.deliveries();
        PaymentDao payments = repo.payments();
        List<Delivery> dRows = deliveries.forDate(day);
        List<Payment> pRows = payments.range(day, day);

        currentColumns = new String[]{"Customer", "Detail", "Amount"};
        double litres = 0, billed = 0, collected = 0;
        int skipped = 0;
        Set<Long> counted = new HashSet<>();
        for (Delivery d : dRows) {
            if (d.skipped) {
                skipped++;
                rows.add(new String[]{d.customerName, "Skipped (" + d.session + ")", "—"});
            } else {
                litres += d.litres;
                billed += d.amount;
                if (!counted.contains(d.customerId)) counted.add(d.customerId);
                rows.add(new String[]{d.customerName,
                        Format.litres(d.litres) + " L @ Rs " + Format.money0(d.rate) + " (" + d.session + ")",
                        "Rs " + Format.money0(d.amount)});
            }
        }
        for (Payment p : pRows) {
            collected += p.amount;
            rows.add(new String[]{p.customerName, "Payment (" + (p.method == null ? "cash" : p.method) + ")",
                    "Rs " + Format.money0(p.amount)});
        }
        String summary = Format.litres(litres) + " L sold • Rs " + Format.money0(billed) + " billed • Rs "
                + Format.money0(collected) + " collected" + (skipped > 0 ? " • " + skipped + " skipped" : "");
        reportSummary.setText(summary);
        currentSummary = new String[]{summary};
        if (rows.isEmpty()) rows.add(new String[]{"No entries", "for " + day, ""});
    }

    private void loadMonthly() {
        reportTitle.setText("Monthly Summary");
        reportPeriod.setText(Format.monthLong(month));
        DeliveryDao deliveries = repo.deliveries();
        String from = month + "-01", to = month + "-31";
        DeliveryDao.Totals t = deliveries.totals(from, to);
        double collected = repo.payments().total(from, to);
        double purchaseCost = repo.purchases().amountTotal(from, to);
        double expenses = new ExpenseDao(this).total(from, to);
        currentColumns = new String[]{"Metric", "", "Value"};

        rows.add(new String[]{"Litres sold", "", Format.litres(t.litres) + " L"});
        rows.add(new String[]{"Deliveries", "", String.valueOf(t.deliveries)});
        rows.add(new String[]{"Skipped slots", "", String.valueOf(t.skipped)});
        rows.add(new String[]{"Billing", "", "Rs " + Format.money0(t.amount)});
        rows.add(new String[]{"Collected", "", "Rs " + Format.money0(collected)});
        rows.add(new String[]{"Purchase cost", "", "Rs " + Format.money0(purchaseCost)});
        rows.add(new String[]{"Expenses", "", "Rs " + Format.money0(expenses)});
        rows.add(new String[]{"Gross margin", "", "Rs " + Format.money0(t.amount - purchaseCost)});
        String summary = "Rs " + Format.money0(t.amount) + " billed • Rs " + Format.money0(collected)
                + " collected • Rs " + Format.money0(t.amount - purchaseCost - expenses) + " net";
        reportSummary.setText(summary);
        currentSummary = new String[]{summary};
    }

    private void loadCustomers() {
        reportTitle.setText("Customer Report");
        reportPeriod.setText(Format.monthLong(month));
        currentColumns = new String[]{"Customer", "Litres", "Balance"};
        double litres = 0, billed = 0, paid = 0;
        for (Customer c : repo.customers().list(false)) {
            BillingEngine.Statement st = repo.statement(c.id, month);
            if (st.litresTotal == 0 && st.paymentsTotal == 0 && st.deliveryTotal == 0) continue;
            litres += st.litresTotal; billed += st.deliveryTotal; paid += st.paymentsTotal;
            rows.add(new String[]{
                    c.name,
                    Format.litres(st.litresTotal) + " L",
                    (st.closing < 0 ? "Adv " : "Rs ") + Format.money0(Math.abs(st.closing))});
        }
        if (rows.isEmpty()) rows.add(new String[]{"No activity", month, ""});
        String summary = Format.litres(litres) + " L • Rs " + Format.money0(billed) + " billed • Rs " + Format.money0(paid) + " paid";
        reportSummary.setText(summary);
        currentSummary = new String[]{summary};
    }

    private void loadOutstanding() {
        reportTitle.setText("Outstanding Dues");
        reportPeriod.setText("As of today");
        reportPrev.setVisibility(View.GONE);
        reportNext.setVisibility(View.GONE);
        currentColumns = new String[]{"Customer", "Phone", "Due"};

        List<Customer> dues = new ArrayList<>();
        double total = 0;
        for (Customer c : repo.customers().list(false)) {
            double bal = repo.balance(c.id);
            c.balance = bal;
            if (bal > 0.009) dues.add(c);
        }
        dues.sort((a, b) -> Double.compare(b.balance, a.balance));
        for (Customer c : dues) {
            total += c.balance;
            rows.add(new String[]{c.name, c.phone != null ? c.phone : "", "Rs " + Format.money0(c.balance)});
        }
        if (rows.isEmpty()) rows.add(new String[]{"All clear!", "no pending dues", ""});
        String summary = dues.size() + " customer(s) owe Rs " + Format.money0(total);
        reportSummary.setText(summary);
        currentSummary = new String[]{summary};
    }

    private void loadPnl() {
        reportTitle.setText("Profit & Loss");
        reportPeriod.setText(Format.monthLong(month));
        String from = month + "-01", to = month + "-31";
        DeliveryDao.Totals t = repo.deliveries().totals(from, to);
        double collected = repo.payments().total(from, to);
        double purchaseCost = repo.purchases().amountTotal(from, to);
        double expenses = new ExpenseDao(this).total(from, to);
        double gross = t.amount - purchaseCost;
        double net = gross - expenses;

        currentColumns = new String[]{"Line item", "", "Amount"};
        rows.add(new String[]{"Sales (delivery value)", "", "Rs " + Format.money0(t.amount)});
        rows.add(new String[]{"— Purchases cost", "", "Rs " + Format.money0(purchaseCost)});
        rows.add(new String[]{"= Gross profit", "", "Rs " + Format.money0(gross)});
        rows.add(new String[]{"— Operating expenses", "", "Rs " + Format.money0(expenses)});
        rows.add(new String[]{"= NET PROFIT", "", "Rs " + Format.money0(net)});
        rows.add(new String[]{"Collected from customers", "", "Rs " + Format.money0(collected)});
        String summary = "Net profit Rs " + Format.money0(net) + " on sales Rs " + Format.money0(t.amount);
        reportSummary.setText(summary);
        currentSummary = new String[]{summary};
    }

    private void loadRates() {
        reportTitle.setText("Rate History");
        reportPeriod.setText("All time");
        reportPrev.setVisibility(View.GONE);
        reportNext.setVisibility(View.GONE);
        currentColumns = new String[]{"Rate", "Effective", "Rs/L"};

        List<MilkRate> all = repo.rates().all();
        all.sort((a, b) -> {
            int k = a.kind.compareTo(b.kind);
            if (k != 0) return k;
            int m = a.milkType.compareTo(b.milkType);
            if (m != 0) return m;
            return b.effectiveFrom.compareTo(a.effectiveFrom);
        });
        for (MilkRate r : all) {
            rows.add(new String[]{
                    cap(r.kind) + " / " + capitalize(r.milkType),
                    Format.pretty(r.effectiveFrom),
                    "Rs " + Format.money0(r.rate)});
        }
        if (rows.isEmpty()) rows.add(new String[]{"No rates set yet", "", ""});
        String summary = all.size() + " rate record(s)";
        reportSummary.setText(summary);
        currentSummary = new String[]{summary};
    }

    private static String cap(String s) { return capitalize(s); }
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------- export ----------------

    private void exportPdf() {
        new Thread(() -> {
            try {
                ReportPdf.Table t = new ReportPdf.Table();
                t.title = reportTitle.getText().toString();
                t.subtitle = reportPeriod.getText().toString();
                t.shopName = settings.get("shop_name", "My Dairy");
                t.currency = settings.get("currency", "Rs");
                t.columns = currentColumns != null ? currentColumns : new String[]{"Report", "", ""};
                t.columnWeights = new float[]{2.2f, 1.4f, 1.4f};
                t.rightAlign = new boolean[]{false, true, true};
                t.rows = new ArrayList<>(rows);
                t.summary = currentSummary;
                File pdf = ReportPdf.generate(this, t);
                android.net.Uri uri = ShareProvider.uriFor(pdf);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/pdf");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(share, "Share report")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "report-pdf").start();
    }

    // ---------------- adapter ----------------

    private static final class ReportAdapter extends BaseAdapter {
        private final Activity ctx;
        private final List<String[]> items;

        ReportAdapter(Activity ctx, List<String[]> items) { this.ctx = ctx; this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public String[] getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_report, parent, false);
            String[] r = items.get(position);
            ((TextView) row.findViewById(R.id.row_report_left)).setText(r.length > 0 ? r[0] : "");
            ((TextView) row.findViewById(R.id.row_report_mid)).setText(r.length > 1 ? r[1] : "");
            ((TextView) row.findViewById(R.id.row_report_right)).setText(r.length > 2 ? r[2] : "");
            return row;
        }
    }
}
