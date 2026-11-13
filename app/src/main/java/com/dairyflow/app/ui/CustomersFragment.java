package com.dairyflow.app.ui;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.dairyflow.app.MainActivity;
import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.ui.customers.CustomerDetailActivity;
import com.dairyflow.app.ui.customers.CustomerEditActivity;
import com.dairyflow.app.util.Format;

import java.util.List;

/** Searchable customer list with per-customer balance and month litres. */
public class CustomersFragment extends Fragment implements MainActivity.Refreshable {

    private CustomerDao customers;
    private ListView list;
    private TextView empty;
    private TextView count;
    private EditText search;
    private String query = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        customers = new CustomerDao(getActivity());
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_customers, container, false);
    }

    @Override public void onViewCreated(View root, Bundle savedInstanceState) {
        list = root.findViewById(R.id.customer_list);
        empty = root.findViewById(R.id.customers_empty);
        count = root.findViewById(R.id.customer_count);
        search = root.findViewById(R.id.search_input);

        root.findViewById(R.id.fab_add_customer).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CustomerEditActivity.class)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim();
                refresh();
            }
        });
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void refresh() {
        if (list == null) return;
        List<Customer> rows = query.isEmpty()
                ? customers.list(false)
                : customers.search(query, false);
        // Compute balances + month litres for the visible page (single-shop scale, fine to do inline).
        BillingUi b = new BillingUi(getActivity());
        for (Customer c : rows) {
            c.balance = b.balance(c.id);
            double[] ml = b.monthLitresAndAmount(c.id);
            c.monthLitres = ml[0];
            c.monthTotal = ml[1];
        }
        count.setText(String.valueOf(customers.countActive()));
        if (rows.isEmpty()) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new CustomerAdapter(getActivity(), rows));
        }
    }

    private static final class BillingUi {
        private final com.dairyflow.app.data.repository.BillingRepository repo;
        BillingUi(android.content.Context ctx) { repo = new com.dairyflow.app.data.repository.BillingRepository(ctx); }
        double balance(long customerId) { return repo.balance(customerId); }
        double[] monthLitresAndAmount(long customerId) {
            String month = Format.currentMonth();
            com.dairyflow.app.billing.BillingEngine.Statement st = repo.statement(customerId, month);
            return new double[]{st.litresTotal, st.deliveryTotal};
        }
    }

    private static final class CustomerAdapter extends BaseAdapter {
        private final android.content.Context ctx;
        private final List<Customer> items;

        CustomerAdapter(android.content.Context ctx, List<Customer> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Customer getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_customer, parent, false);
            Customer c = items.get(position);
            ((TextView) row.findViewById(R.id.row_avatar)).setText(initials(c.name));
            ((TextView) row.findViewById(R.id.row_name)).setText(c.name);
            String meta = (c.area != null && !c.area.isEmpty() ? c.area + " • " : "")
                    + capitalize(c.milkType) + (c.weeklyOff > 0 ? " • off " + weekday(c.weeklyOff) : "");
            ((TextView) row.findViewById(R.id.row_meta)).setText(meta);
            TextView balance = row.findViewById(R.id.row_balance);
            balance.setText(Format.pkr(Math.abs(c.balance)));
            if (c.balance > 0.009) {
                balance.setTextColor(0xFFDC2626);
            } else if (c.balance < -0.009) {
                balance.setTextColor(0xFF047857);
            } else {
                balance.setTextColor(0xFF7A8B82);
            }
            TextView balLabel = row.findViewById(R.id.row_balance_label);
            if (balLabel != null) balLabel.setText(c.balance < -0.009 ? "ADVANCE" : (c.balance > 0.009 ? "DUE" : "SETTLED"));
            row.setOnClickListener(v -> {
                Intent i = new Intent(ctx, CustomerDetailActivity.class);
                i.putExtra("customer_id", c.id);
                ctx.startActivity(i);
            });
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

        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return "";
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        private static String weekday(int weeklyOff) {
            switch (weeklyOff) {
                case 1: return "Mon"; case 2: return "Tue"; case 3: return "Wed"; case 4: return "Thu";
                case 5: return "Fri"; case 6: return "Sat"; case 7: return "Sun"; default: return "";
            }
        }
    }
}
