package com.dairyflow.app.ui;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dairyflow.app.MainActivity;
import com.dairyflow.app.R;
import com.dairyflow.app.data.dao.SettingsDao;
import com.dairyflow.app.ui.billing.BillingActivity;
import com.dairyflow.app.ui.expenses.ExpensesActivity;
import com.dairyflow.app.ui.inventory.InventoryActivity;
import com.dairyflow.app.ui.reports.ReportsActivity;
import com.dairyflow.app.ui.settings.RatesActivity;
import com.dairyflow.app.ui.settings.SettingsActivity;

/** Premium grid of secondary destinations. */
public class MoreFragment extends Fragment implements MainActivity.Refreshable {

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_more, container, false);
    }

    @Override public void onViewCreated(View root, Bundle savedInstanceState) {
        bind(root.findViewById(R.id.more_inventory), "Milk Inventory", "Stock, purchases & spoilage", v ->
                startActivity(new Intent(getActivity(), InventoryActivity.class)));
        bind(root.findViewById(R.id.more_rates), "Milk Rates", "Effective-dated sale & purchase rates", v ->
                startActivity(new Intent(getActivity(), RatesActivity.class)));
        bind(root.findViewById(R.id.more_expenses), "Shop Expenses", "Rent, salary, transport, ice…", v ->
                startActivity(new Intent(getActivity(), ExpensesActivity.class)));
        bind(root.findViewById(R.id.more_reports), "Business Reports", "Daily, monthly, P&L, outstanding", v ->
                startActivity(new Intent(getActivity(), ReportsActivity.class)));
        bind(root.findViewById(R.id.more_billing), "Monthly Billing", "Generate invoices for every customer", v ->
                startActivity(new Intent(getActivity(), BillingActivity.class)));
        bind(root.findViewById(R.id.more_backup), "Backup & Restore", "Export .dairyflow backup, restore safely", v ->
                startActivity(new Intent(getActivity(), SettingsActivity.class)));
        bind(root.findViewById(R.id.more_settings), "Shop Settings", "Name, address, phone shown on invoices", v ->
                startActivity(new Intent(getActivity(), SettingsActivity.class)));

        SettingsDao settings = new SettingsDao(getActivity());
        ((TextView) root.findViewById(R.id.more_shop_name)).setText(settings.get("shop_name", "My Dairy"));
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void refresh() {
        if (getView() == null) return;
        SettingsDao settings = new SettingsDao(getActivity());
        ((TextView) getView().findViewById(R.id.more_shop_name)).setText(settings.get("shop_name", "My Dairy"));
    }

    private void bind(View row, String title, String subtitle, View.OnClickListener onClick) {
        ((TextView) row.findViewById(R.id.more_title)).setText(title);
        ((TextView) row.findViewById(R.id.more_subtitle)).setText(subtitle);
        row.setOnClickListener(onClick);
    }
}
