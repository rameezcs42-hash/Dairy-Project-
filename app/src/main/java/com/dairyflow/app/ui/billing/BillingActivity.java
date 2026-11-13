package com.dairyflow.app.ui.billing;

import android.app.Activity;
import android.app.AlertDialog;
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
import com.dairyflow.app.data.dao.SettingsDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.repository.BillingRepository;
import com.dairyflow.app.pdf.PdfGenerator;
import com.dairyflow.app.provider.ShareProvider;
import com.dairyflow.app.util.Format;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Month-end billing desk: per-customer statements for the selected month,
 * one-tap invoice PDF per row, or generate every invoice in one go.
 * Invoices are also saved into app-external Documents/Invoices for safekeeping.
 */
public class BillingActivity extends Activity {

    private BillingRepository repo;
    private SettingsDao settings;
    private String month = Format.currentMonth();

    private TextView billingMonth, billingSummary, billingEmpty;
    private ListView billingList;
    private List<BillRow> rows = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing);
        repo = new BillingRepository(this);
        settings = new SettingsDao(this);

        billingMonth = findViewById(R.id.billing_month);
        billingSummary = findViewById(R.id.billing_summary);
        billingEmpty = findViewById(R.id.billing_empty);
        billingList = findViewById(R.id.billing_list);

        findViewById(R.id.billing_prev_month).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.billing_next_month).setOnClickListener(v -> shiftMonth(1));
        findViewById(R.id.btn_generate_all).setOnClickListener(v -> confirmGenerateAll());
        findViewById(R.id.btn_summary_pdf).setOnClickListener(v -> generateSummaryPdf());
        load();
    }

    private void shiftMonth(int delta) {
        month = Format.monthShift(month, delta);
        load();
    }

    private void load() {
        billingMonth.setText(Format.monthLong(month));
        rows = new ArrayList<>();
        double litres = 0, billed = 0, collected = 0;
        int billedCount = 0;
        for (Customer c : repo.customers().list(false)) {
            BillingEngine.Statement st = repo.statement(c.id, month);
            if (st.litresTotal <= 0 && st.paymentsTotal <= 0 && st.lines.isEmpty()) continue; // hide empty customers
            BillRow r = new BillRow();
            r.customer = c;
            r.statement = st;
            rows.add(r);
            litres += st.litresTotal;
            billed += st.deliveryTotal;
            collected += st.paymentsTotal;
            billedCount++;
        }
        rows.sort((a, b) -> Double.compare(Math.abs(b.statement.closing), Math.abs(a.statement.closing)));

        billingSummary.setText(billedCount + " customers • " + Format.litres(litres) + " L • Rs "
                + Format.money0(billed) + " billed • Rs " + Format.money0(collected) + " collected");

        if (rows.isEmpty()) {
            billingList.setVisibility(View.GONE);
            billingEmpty.setVisibility(View.VISIBLE);
        } else {
            billingEmpty.setVisibility(View.GONE);
            billingList.setVisibility(View.VISIBLE);
            billingList.setAdapter(new BillAdapter(this, rows, this::generateOne));
        }
    }

    private PdfGenerator.InvoiceParams paramsFor(Customer c, BillingEngine.Statement st) {
        PdfGenerator.InvoiceParams p = new PdfGenerator.InvoiceParams();
        p.shopName = settings.get("shop_name", "My Dairy");
        p.shopTagline = settings.get("shop_tagline", "");
        p.shopPhone = settings.get("shop_phone", "");
        p.shopAddress = settings.get("shop_address", "");
        p.currency = settings.get("currency", "Rs");
        p.billingMonth = month;
        p.billNo = "DF-" + month.replace("-", "") + "-" + c.id;
        p.customerName = c.name;
        p.customerArea = c.area;
        p.customerPhone = c.phone;
        p.customerMilkType = c.milkType;
        p.openingBalance = st.opening;
        p.deliveries = st.lines;
        p.payments = st.payments;
        p.footerNote = settings.get("invoice_note", "");
        return p;
    }

    private void generateOne(BillRow r) {
        new Thread(() -> {
            try {
                File pdf = PdfGenerator.generateInvoice(this, paramsFor(r.customer, r.statement));
                sharePdf(pdf, "Invoice — " + r.customer.name);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Invoice failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "invoice-one").start();
    }

    private void confirmGenerateAll() {
        if (rows.isEmpty()) { Toast.makeText(this, "Nothing to bill for " + Format.monthLong(month), Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Generate " + rows.size() + " invoices?")
                .setMessage("PDFs will be saved to Documents/Invoices and can be shared from the share sheet.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Generate", (d, which) -> generateAll())
                .show();
    }

    private void generateAll() {
        new Thread(() -> {
            int ok = 0, fail = 0;
            File lastFile = null;
            for (BillRow r : rows) {
                try {
                    lastFile = PdfGenerator.generateInvoice(this, paramsFor(r.customer, r.statement));
                    ok++;
                } catch (Exception e) {
                    fail++;
                }
            }
            final int okF = ok, failF = fail;
            final File last = lastFile;
            runOnUiThread(() -> {
                Toast.makeText(this, okF + " invoices generated" + (failF > 0 ? " (" + failF + " failed)" : ""), Toast.LENGTH_LONG).show();
                if (last != null) sharePdf(last, "Last invoice");
            });
        }, "invoice-all").start();
    }

    private void generateSummaryPdf() {
        new Thread(() -> {
            try {
                com.dairyflow.app.pdf.ReportPdf.Table t = new com.dairyflow.app.pdf.ReportPdf.Table();
                t.title = "Monthly Billing Summary";
                t.subtitle = Format.monthLong(month);
                t.shopName = settings.get("shop_name", "My Dairy");
                t.currency = settings.get("currency", "Rs");
                t.columns = new String[]{"Customer", "Litres", "Billed", "Paid", "Balance"};
                t.columnWeights = new float[]{2.4f, 1.1f, 1.3f, 1.3f, 1.3f};
                t.rightAlign = new boolean[]{false, true, true, true, true};
                t.rows = new ArrayList<>();
                double litres = 0, billed = 0, paid = 0, closing = 0;
                for (BillRow r : rows) {
                    BillingEngine.Statement st = r.statement;
                    t.rows.add(new String[]{
                            r.customer.name,
                            Format.litres(st.litresTotal),
                            Format.money(st.deliveryTotal),
                            Format.money(st.paymentsTotal),
                            Format.money(st.closing)});
                    litres += st.litresTotal; billed += st.deliveryTotal; paid += st.paymentsTotal; closing += st.closing;
                }
                t.summary = new String[]{
                        "Total litres: " + Format.litres(litres) + " L",
                        "Total billed: Rs " + Format.money(billed),
                        "Total collected: Rs " + Format.money(paid),
                        "Net closing balance: Rs " + Format.money(closing)};
                File pdf = com.dairyflow.app.pdf.ReportPdf.generate(this, t);
                sharePdf(pdf, "Billing summary");
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Summary failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "summary-pdf").start();
    }

    private void sharePdf(File pdf, String title) {
        android.net.Uri uri = ShareProvider.uriFor(pdf);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        runOnUiThread(() -> startActivity(Intent.createChooser(share, title)));
    }

    // ---------- rows ----------

    static final class BillRow {
        Customer customer;
        BillingEngine.Statement statement;
    }

    private interface OnPdfClick { void onPdf(BillRow row); }

    private static final class BillAdapter extends BaseAdapter {
        private final Activity ctx;
        private final List<BillRow> items;
        private final OnPdfClick onPdf;

        BillAdapter(Activity ctx, List<BillRow> items, OnPdfClick onPdf) {
            this.ctx = ctx;
            this.items = items;
            this.onPdf = onPdf;
        }

        @Override public int getCount() { return items.size(); }
        @Override public BillRow getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_billing, parent, false);
            final BillRow r = items.get(position);
            BillingEngine.Statement st = r.statement;

            ((TextView) row.findViewById(R.id.row_bill_avatar)).setText(initials(r.customer.name));
            ((TextView) row.findViewById(R.id.row_bill_name)).setText(r.customer.name);
            ((TextView) row.findViewById(R.id.row_bill_meta)).setText(
                    Format.litres(st.litresTotal) + " L • Rs " + Format.money0(st.deliveryTotal)
                            + " • paid Rs " + Format.money0(st.paymentsTotal));
            TextView bal = row.findViewById(R.id.row_bill_balance);
            if (st.closing > 0.009) {
                bal.setText(Format.pkr(st.closing) + " due");
                bal.setTextColor(0xFFDC2626);
            } else if (st.closing < -0.009) {
                bal.setText(Format.pkr(-st.closing) + " advance");
                bal.setTextColor(0xFF047857);
            } else {
                bal.setText("settled");
                bal.setTextColor(0xFF7A8B82);
            }
            row.findViewById(R.id.row_bill_pdf).setOnClickListener(v -> onPdf.onPdf(r));
            row.setOnClickListener(v -> onPdf.onPdf(r));
            return row;
        }

        private static String initials(String name) {
            if (name == null || name.trim().isEmpty()) return "?";
            String[] parts = name.trim().split("\\s+");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < parts.length && b.length() < 2; i++) {
                if (!parts[i].isEmpty()) b.append(Character.toUpperCase(parts[i].charAt(0)));
            }
            return b.toString();
        }
    }
}
