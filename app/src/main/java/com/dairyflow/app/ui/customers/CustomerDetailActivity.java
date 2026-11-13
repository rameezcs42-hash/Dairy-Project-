package com.dairyflow.app.ui.customers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import com.dairyflow.app.data.dao.PaymentDao;
import com.dairyflow.app.data.dao.SettingsDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.model.Delivery;
import com.dairyflow.app.data.model.Payment;
import com.dairyflow.app.data.repository.BillingRepository;
import com.dairyflow.app.pdf.PdfGenerator;
import com.dairyflow.app.provider.ShareProvider;
import com.dairyflow.app.ui.payments.PaymentEditActivity;
import com.dairyflow.app.util.Format;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Customer profile: balance hero, contact actions, invoice generation, ledger. */
public class CustomerDetailActivity extends Activity {

    private long customerId;
    private CustomerDao customers;
    private DeliveryDao deliveries;
    private PaymentDao payments;
    private BillingRepository repo;
    private SettingsDao settings;

    private TextView detailAvatar, detailName, detailMeta, detailTags, detailBalance, detailBalanceLabel;
    private TextView detailMonthLitres, detailMonthAmount, ledgerEmpty;
    private ListView ledgerList;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);
        customerId = getIntent().getLongExtra("customer_id", -1);
        if (customerId <= 0) { finish(); return; }

        customers = new CustomerDao(this);
        deliveries = new DeliveryDao(this);
        payments = new PaymentDao(this);
        repo = new BillingRepository(this);
        settings = new SettingsDao(this);

        detailAvatar = findViewById(R.id.detail_avatar);
        detailName = findViewById(R.id.detail_name);
        detailMeta = findViewById(R.id.detail_meta);
        detailTags = findViewById(R.id.detail_tags);
        detailBalance = findViewById(R.id.detail_balance);
        detailBalanceLabel = findViewById(R.id.detail_balance_label);
        detailMonthLitres = findViewById(R.id.detail_month_litres);
        detailMonthAmount = findViewById(R.id.detail_month_amount);
        ledgerList = findViewById(R.id.ledger_list);
        ledgerEmpty = findViewById(R.id.ledger_empty);

        findViewById(R.id.edit_customer).setOnClickListener(v -> {
            Intent i = new Intent(this, CustomerEditActivity.class);
            i.putExtra("customer_id", customerId);
            startActivity(i);
        });
        findViewById(R.id.action_pay).setOnClickListener(v -> {
            Intent i = new Intent(this, PaymentEditActivity.class);
            i.putExtra("customer_id", customerId);
            startActivity(i);
        });
        findViewById(R.id.action_call).setOnClickListener(v -> callCustomer());
        findViewById(R.id.action_whatsapp).setOnClickListener(v -> smsCustomer());
        findViewById(R.id.action_invoice).setOnClickListener(v -> generateInvoice());
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        Customer c = customers.getById(customerId);
        if (c == null) { finish(); return; }

        detailAvatar.setText(initials(c.name));
        detailName.setText(c.name);
        String meta = (c.area != null && !c.area.isEmpty() ? c.area + " • " : "")
                + (c.phone != null && !c.phone.isEmpty() ? c.phone : "no phone");
        detailMeta.setText(meta);
        String tags = capitalize(c.milkType) + " • Rs " + Format.money0(c.defaultRate) + "/L"
                + (c.weeklyOff > 0 ? " • Off: " + weekday(c.weeklyOff) : " • No weekly off")
                + (c.sessionPref != null && !"both".equals(c.sessionPref) ? " • " + c.sessionPref + " only" : "");
        detailTags.setText(tags);

        double balance = repo.balance(customerId);
        detailBalance.setText(Format.pkr(Math.abs(balance)));
        if (balance > 0.009) {
            detailBalanceLabel.setText("DUE — collect this amount");
            detailBalanceLabel.setTextColor(0xFFFCA5A5);
        } else if (balance < -0.009) {
            detailBalanceLabel.setText("ADVANCE — credit with shop");
            detailBalanceLabel.setTextColor(0xFF6EE7B7);
        } else {
            detailBalanceLabel.setText("SETTLED — nothing due");
            detailBalanceLabel.setTextColor(0xFF6EE7B7);
        }

        String month = Format.currentMonth();
        BillingEngine.Statement st = repo.statement(customerId, month);
        detailMonthLitres.setText(Format.litres(st.litresTotal) + " L");
        detailMonthAmount.setText(Format.pkr(st.deliveryTotal) + " billed • " + Format.pkr(st.paymentsTotal) + " paid");

        buildLedger(c);
    }

    private void buildLedger(Customer c) {
        String month = Format.currentMonth();
        String from = month + "-01";
        String to = month + "-31";
        List<Delivery> dRows = deliveries.forCustomer(customerId, from, to);
        List<Payment> pRows = payments.forCustomer(customerId, from, to);

        List<LedgerRow> rows = new ArrayList<>();
        for (Delivery d : dRows) {
            LedgerRow r = new LedgerRow();
            r.date = d.date;
            if (d.skipped) {
                r.title = (dayLabel(d.date)) + " — skipped (" + sessionLabel(d.session) + ")";
                r.amountText = "";
                r.amountColor = 0xFF7A8B82;
                r.kind = 0;
            } else {
                r.title = cap(d.session) + " delivery — " + Format.litres(d.litres) + " L";
                r.amountText = "+ " + Format.pkr(d.amount);
                r.amountColor = 0xFFDC2626;
                r.kind = 0;
            }
            r.meta = Format.pretty(d.date) + " • rate Rs " + Format.money0(d.rate);
            rows.add(r);
        }
        for (Payment p : pRows) {
            LedgerRow r = new LedgerRow();
            r.date = p.date;
            r.title = "Payment received (" + label(p.method) + ")";
            r.meta = Format.pretty(p.date) + (p.note != null && !p.note.isEmpty() ? " • " + p.note : "");
            r.amountText = "− " + Format.pkr(p.amount);
            r.amountColor = 0xFF047857;
            r.kind = 1;
            rows.add(r);
        }
        rows.sort((a, b) -> b.date.compareTo(a.date)); // newest first

        if (rows.isEmpty()) {
            ledgerList.setVisibility(View.GONE);
            ledgerEmpty.setVisibility(View.VISIBLE);
        } else {
            ledgerEmpty.setVisibility(View.GONE);
            ledgerList.setVisibility(View.VISIBLE);
            ledgerList.setAdapter(new LedgerAdapter(this, rows));
        }
    }

    private void callCustomer() {
        Customer c = customers.getById(customerId);
        if (c == null) return;
        if (c.phone == null || c.phone.trim().isEmpty()) {
            Toast.makeText(this, "No phone number saved for this customer", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.phone.trim()))));
    }

    private void smsCustomer() {
        Customer c = customers.getById(customerId);
        if (c == null) return;
        if (c.phone == null || c.phone.trim().isEmpty()) {
            Toast.makeText(this, "No phone number saved for this customer", Toast.LENGTH_SHORT).show();
            return;
        }
        double balance = repo.balance(customerId);
        String body = "Assalam o Alaikum " + c.name + "! Your " + Format.monthLong(Format.currentMonth())
                + " milk bill is Rs " + Format.money0(repo.statement(customerId, Format.currentMonth()).deliveryTotal)
                + ". Total dues: Rs " + Format.money0(Math.max(balance, 0))
                + ". — " + settings.get("shop_name", "DairyFlow");
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + c.phone.trim()));
        i.putExtra("sms_body", body);
        startActivity(i);
    }

    private void generateInvoice() {
        new Thread(() -> {
            try {
                String month = Format.currentMonth();
                BillingEngine.Statement st = repo.statement(customerId, month);
                Customer c = customers.getById(customerId);
                PdfGenerator.InvoiceParams p = new PdfGenerator.InvoiceParams();
                p.shopName = settings.get("shop_name", "My Dairy");
                p.shopTagline = settings.get("shop_tagline", "");
                p.shopPhone = settings.get("shop_phone", "");
                p.shopAddress = settings.get("shop_address", "");
                p.currency = settings.get("currency", "Rs");
                p.billingMonth = month;
                p.billNo = "DF-" + month.replace("-", "") + "-" + customerId;
                p.customerName = c.name;
                p.customerArea = c.area;
                p.customerPhone = c.phone;
                p.customerMilkType = c.milkType;
                p.openingBalance = st.opening;
                p.deliveries = st.lines;
                p.payments = st.payments;
                p.footerNote = settings.get("invoice_note", "");
                File pdf = PdfGenerator.generateInvoice(this, p);
                Uri uri = ShareProvider.uriFor(pdf);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/pdf");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(share, "Share invoice")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not generate invoice: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "invoice-gen").start();
    }

    // ---------- ledger row plumbing ----------

    static final class LedgerRow {
        String date, title, meta, amountText;
        int amountColor;
        int kind; // 0=delivery, 1=payment
    }

    private static final class LedgerAdapter extends BaseAdapter {
        private final Activity ctx;
        private final List<LedgerRow> items;

        LedgerAdapter(Activity ctx, List<LedgerRow> items) { this.ctx = ctx; this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public LedgerRow getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_ledger, parent, false);
            LedgerRow r = items.get(position);
            ((TextView) row.findViewById(R.id.ledger_title)).setText(r.title);
            ((TextView) row.findViewById(R.id.ledger_meta)).setText(r.meta);
            TextView amount = row.findViewById(R.id.ledger_amount);
            amount.setText(r.amountText);
            amount.setTextColor(r.amountColor);
            View dot = row.findViewById(R.id.ledger_dot);
            dot.setBackgroundResource(r.kind == 1 ? R.drawable.bg_dot_bar : R.drawable.bg_dot_emerald);
            return row;
        }
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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String cap(String s) { return capitalize(s); }

    private static String sessionLabel(String s) {
        if ("morning".equals(s)) return "morning";
        if ("evening".equals(s)) return "evening";
        return String.valueOf(s);
    }

    private String dayLabel(String iso) {
        return Format.weekdayShort(iso) + " " + Format.dayOfMonth(iso) + " " + Format.monthCompact(iso.substring(0, 7));
    }

    private static String weekday(int weeklyOff) {
        switch (weeklyOff) {
            case 1: return "Mon"; case 2: return "Tue"; case 3: return "Wed"; case 4: return "Thu";
            case 5: return "Fri"; case 6: return "Sat"; case 7: return "Sun"; default: return "";
        }
    }

    private static String label(String method) {
        if (method == null) return "cash";
        switch (method) {
            case "cash": return "cash";
            case "easypaisa": return "Easypaisa";
            case "jazzcash": return "JazzCash";
            case "bank": return "bank";
            default: return method;
        }
    }
}
