package com.dairyflow.app.ui;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.dairyflow.app.MainActivity;
import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.dao.PaymentDao;
import com.dairyflow.app.data.dao.SettingsDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.model.Payment;
import com.dairyflow.app.data.repository.BillingRepository;
import com.dairyflow.app.ui.billing.BillingActivity;
import com.dairyflow.app.ui.customers.CustomerEditActivity;
import com.dairyflow.app.ui.deliveries.DeliveryEntryActivity;
import com.dairyflow.app.ui.payments.PaymentEditActivity;
import com.dairyflow.app.ui.reports.ReportsActivity;
import com.dairyflow.app.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Premium emerald dashboard: outstanding, today's stats, month strip, quick actions, recent payments. */
public class DashboardFragment extends Fragment implements MainActivity.Refreshable {

    private BillingRepository repo;
    private TextView dashShop, dashDate, dashOutstanding, monthLitres, monthAmount, monthPayments;
    private View lowStockBanner;
    private TextView lowStockText, recentEmpty;
    private ListView recentPayments;
    private View statLitres, statSales, statPayments, statStock;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new BillingRepository(getActivity());
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override public void onViewCreated(View root, Bundle savedInstanceState) {
        dashShop = root.findViewById(R.id.dash_shop);
        dashDate = root.findViewById(R.id.dash_date);
        dashOutstanding = root.findViewById(R.id.dash_outstanding);
        monthLitres = root.findViewById(R.id.month_litres);
        monthAmount = root.findViewById(R.id.month_amount);
        monthPayments = root.findViewById(R.id.month_payments);
        lowStockBanner = root.findViewById(R.id.low_stock_banner);
        lowStockText = root.findViewById(R.id.low_stock_text);
        recentPayments = root.findViewById(R.id.recent_payments);
        recentEmpty = root.findViewById(R.id.recent_empty);
        statLitres = root.findViewById(R.id.stat_litres);
        statSales = root.findViewById(R.id.stat_sales);
        statPayments = root.findViewById(R.id.stat_payments);
        statStock = root.findViewById(R.id.stat_stock);

        root.findViewById(R.id.dash_outstanding_card).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ReportsActivity.class)));
        root.findViewById(R.id.qa_entry).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), DeliveryEntryActivity.class)));
        root.findViewById(R.id.qa_payment).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), PaymentEditActivity.class)));
        root.findViewById(R.id.qa_customer).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CustomerEditActivity.class)));
        root.findViewById(R.id.qa_billing).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), BillingActivity.class)));

        bindStat(statLitres, "TODAY'S MILK", "0 L", "litres delivered");
        bindStat(statSales, "SALES TODAY", "Rs 0", "at current rates");
        bindStat(statPayments, "COLLECTED TODAY", "Rs 0", "cash & online");
        bindStat(statStock, "STOCK IN HAND", "0 L", "purchases − sales");
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void refresh() {
        if (recentPayments == null) return; // view not ready yet
        SettingsDao settings = new SettingsDao(getActivity());
        dashShop.setText(settings.get("shop_name", "My Dairy"));
        dashDate.setText(Format.prettyLong(Format.todayIso()));

        CustomerDao customers = repo.customers();
        PaymentDao paymentDao = repo.payments();

        double outstanding = customers.outstandingTotal();
        dashOutstanding.setText(Format.pkr(outstanding));

        String today = Format.todayIso();
        String month = Format.currentMonth();
        bindStat(statLitres, "TODAY'S MILK", Format.litres(repo.todayLitres()) + " L",
                Format.weekdayOfDate(today));
        bindStat(statSales, "SALES TODAY", Format.pkr(repo.todayAmount()),
                "delivery value");
        bindStat(statPayments, "COLLECTED TODAY", Format.pkr(repo.todayPayments()),
                countLine(paymentDao, today));
        double stock = repo.stockLitres();
        double lowStockLimit = settings.getDouble("low_stock_litres", 25);
        bindStat(statStock, "STOCK IN HAND", Format.litres(stock) + " L",
                stock < lowStockLimit ? "below reorder level" : "looking healthy");

        if (stock < lowStockLimit) {
            lowStockBanner.setVisibility(View.VISIBLE);
            lowStockText.setText("Low stock — only " + Format.litres(stock)
                    + " L left (reorder level " + Format.litres(lowStockLimit) + " L). Time to buy from the union.");
        } else {
            lowStockBanner.setVisibility(View.GONE);
        }

        monthLitres.setText(Format.litres(repo.monthLitres()) + " L");
        monthAmount.setText(Format.pkr(repo.monthAmount()));
        monthPayments.setText(Format.pkr(repo.monthPayments()));

        List<Payment> recent = paymentDao.recent(6);
        if (recent.isEmpty()) {
            recentPayments.setVisibility(View.GONE);
            recentEmpty.setVisibility(View.VISIBLE);
        } else {
            recentEmpty.setVisibility(View.GONE);
            recentPayments.setVisibility(View.VISIBLE);
            recentPayments.setAdapter(new RecentPaymentsAdapter(getActivity(), recent));
        }
    }

    private String countLine(PaymentDao paymentDao, String today) {
        List<Payment> todays = paymentDao.range(today, today);
        return todays.size() + (todays.size() == 1 ? " payment today" : " payments today");
    }

    private void bindStat(View card, String label, String value, String sub) {
        if (card == null) return;
        TextView l = card.findViewById(R.id.stat_label);
        TextView v = card.findViewById(R.id.stat_value);
        TextView s = card.findViewById(R.id.stat_sub);
        if (l != null) l.setText(label);
        if (v != null) v.setText(value);
        if (s != null) s.setText(sub);
    }

    /** Simple adapter for the dashboard's recent-payments card (fixed 6 rows, no recycling needed). */
    static final class RecentPaymentsAdapter extends BaseAdapter {
        private final android.content.Context ctx;
        private final List<Payment> items;

        RecentPaymentsAdapter(android.content.Context ctx, List<Payment> items) {
            this.ctx = ctx;
            this.items = new ArrayList<>(items);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Payment getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View row = convertView;
            if (row == null) row = android.view.LayoutInflater.from(ctx).inflate(R.layout.row_payment, parent, false);
            Payment p = items.get(position);
            ((TextView) row.findViewById(R.id.row_pay_customer)).setText(p.customerName != null ? p.customerName : "Customer #" + p.customerId);
            ((TextView) row.findViewById(R.id.row_pay_meta)).setText(Format.pretty(p.date) + " • " + p.method);
            ((TextView) row.findViewById(R.id.row_pay_amount)).setText(Format.pkr(p.amount));
            ((TextView) row.findViewById(R.id.row_method)).setText(methodGlyph(p.method));
            return row;
        }

        private static String methodGlyph(String method) {
            if (method == null) return "₨";
            switch (method) {
                case "cash": return "₨";
                case "easypaisa": return "EP";
                case "jazzcash": return "JC";
                case "bank": return "BK";
                default: return "₨";
            }
        }
    }
}
