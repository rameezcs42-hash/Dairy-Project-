package com.dairyflow.app.ui.inventory;

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
import com.dairyflow.app.data.dao.PurchaseDao;
import com.dairyflow.app.data.model.Purchase;
import com.dairyflow.app.data.repository.BillingRepository;
import com.dairyflow.app.util.Format;

import java.util.List;

/** Inventory screen: current stock, month purchases, spoilage action, FAB to add a purchase. */
public class InventoryActivity extends Activity {

    private PurchaseDao purchases;
    private BillingRepository repo;
    private ListView list;
    private TextView empty, stockLitres, stockSpent;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        purchases = new PurchaseDao(this);
        repo = new BillingRepository(this);

        list = findViewById(R.id.purchase_list);
        empty = findViewById(R.id.purchase_empty);
        stockLitres = findViewById(R.id.stock_litres);
        stockSpent = findViewById(R.id.stock_spent);

        findViewById(R.id.fab_add_purchase).setOnClickListener(v ->
                startActivity(new Intent(this, PurchaseEditActivity.class)));
        findViewById(R.id.btn_spoilage).setOnClickListener(v ->
                startActivity(new Intent(this, AdjustmentEditActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String month = Format.currentMonth();
        String from = month + "-01", to = month + "-31";

        stockLitres.setText(Format.litres(repo.stockLitres()) + " L");
        stockSpent.setText(Format.pkr(purchases.amountTotal(from, to)));

        List<Purchase> rows = purchases.range(from, to);
        if (rows.isEmpty()) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new PurchaseAdapter(this, rows));
        }
    }

    private static final class PurchaseAdapter extends BaseAdapter {
        private final Activity ctx;
        private final List<Purchase> items;

        PurchaseAdapter(Activity ctx, List<Purchase> items) { this.ctx = ctx; this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public Purchase getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(ctx).inflate(R.layout.row_purchase, parent, false);
            Purchase p = items.get(position);
            ((TextView) row.findViewById(R.id.row_purchase_supplier)).setText(p.supplier != null ? p.supplier : "Purchase");
            ((TextView) row.findViewById(R.id.row_purchase_meta)).setText(
                    Format.pretty(p.date) + " • " + p.milkType + " • Rs " + Format.money0(p.rate) + "/L"
                            + (p.fat != null ? " • fat " + p.fat + "%" : ""));
            ((TextView) row.findViewById(R.id.row_purchase_litres)).setText("+" + Format.litres(p.litres) + " L");
            ((TextView) row.findViewById(R.id.row_purchase_amount)).setText(Format.pkr(p.amount));
            row.setOnClickListener(v -> {
                Intent i = new Intent(ctx, PurchaseEditActivity.class);
                i.putExtra("purchase_id", p.id);
                ctx.startActivity(i);
            });
            return row;
        }
    }
}
