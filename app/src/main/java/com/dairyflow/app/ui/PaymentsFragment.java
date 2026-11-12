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
import com.dairyflow.app.data.dao.PaymentDao;
import com.dairyflow.app.data.model.Payment;
import com.dairyflow.app.ui.payments.PaymentEditActivity;
import com.dairyflow.app.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Recent payments feed with month & today totals. */
public class PaymentsFragment extends Fragment implements MainActivity.Refreshable {

    private PaymentDao payments;
    private ListView list;
    private TextView empty, monthTotal, todayTotal;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        payments = new PaymentDao(getActivity());
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_payments, container, false);
    }

    @Override public void onViewCreated(View root, Bundle savedInstanceState) {
        list = root.findViewById(R.id.payment_list);
        empty = root.findViewById(R.id.payments_empty);
        monthTotal = root.findViewById(R.id.pay_month_total);
        todayTotal = root.findViewById(R.id.pay_today_total);
        root.findViewById(R.id.fab_add_payment).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), PaymentEditActivity.class)));
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void refresh() {
        if (list == null) return;
        String today = Format.todayIso();
        String month = Format.currentMonth();
        todayTotal.setText(Format.pkr(payments.totalForDate(today)));
        monthTotal.setText(Format.pkr(payments.total(month + "-01", month + "-31")));

        List<Payment> rows = payments.recent(100);
        if (rows.isEmpty()) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new PaymentAdapter(getActivity(), rows));
        }
    }

    private static final class PaymentAdapter extends BaseAdapter {
        private final android.content.Context ctx;
        private final List<Payment> items;

        PaymentAdapter(android.content.Context ctx, List<Payment> items) {
            this.ctx = ctx;
            this.items = new ArrayList<>(items);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Payment getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_payment, parent, false);
            Payment p = items.get(position);
            ((TextView) row.findViewById(R.id.row_pay_customer)).setText(p.customerName != null ? p.customerName : "Customer #" + p.customerId);
            ((TextView) row.findViewById(R.id.row_pay_meta)).setText(Format.pretty(p.date) + " • " + label(p.method) + (p.note != null && !p.note.isEmpty() ? " • " + p.note : ""));
            ((TextView) row.findViewById(R.id.row_pay_amount)).setText(Format.pkr(p.amount));
            ((TextView) row.findViewById(R.id.row_method)).setText(glyph(p.method));
            row.setOnClickListener(v -> {
                Intent i = new Intent(ctx, PaymentEditActivity.class);
                i.putExtra("payment_id", p.id);
                ctx.startActivity(i);
            });
            return row;
        }

        private static String glyph(String method) {
            if (method == null) return "₨";
            switch (method) {
                case "cash": return "₨";
                case "easypaisa": return "EP";
                case "jazzcash": return "JC";
                case "bank": return "BK";
                default: return "₨";
            }
        }

        private static String label(String method) {
            if (method == null) return "cash";
            switch (method) {
                case "cash": return "cash";
                case "easypaisa": return "Easypaisa";
                case "jazzcash": return "JazzCash";
                case "bank": return "Bank transfer";
                default: return method;
            }
        }
    }
}
