package com.dairyflow.app.ui.reports;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.dairyflow.app.R;

/** Report hub — tiles open ReportDetailActivity with the requested report type. */
public class ReportsActivity extends Activity {

    public static final String EXTRA_REPORT = "report_type";

    // Report types
    public static final String DAILY = "daily";
    public static final String MONTHLY = "monthly";
    public static final String CUSTOMERS = "customers";
    public static final String OUTSTANDING = "outstanding";
    public static final String PNL = "pnl";
    public static final String RATES = "rates";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        bindTile(R.id.tile_daily, "Daily Sales Report", "Per-day litres, billing & collections", DAILY);
        bindTile(R.id.tile_monthly, "Monthly Summary", "Month-by-month milk, bills, payments", MONTHLY);
        bindTile(R.id.tile_customers, "Customer Report", "Per-customer litres, billed, paid, balance", CUSTOMERS);
        bindTile(R.id.tile_outstanding, "Outstanding Dues", "Who owes money right now, sorted by amount", OUTSTANDING);
        bindTile(R.id.tile_pnl, "Profit & Loss", "Sales − purchase cost − expenses for the month", PNL);
        bindTile(R.id.tile_rates, "Rate History", "Every sale & purchase rate change with effective dates", RATES);
    }

    private void bindTile(int tileId, String title, String subtitle, final String reportType) {
        View tile = findViewById(tileId);
        ((TextView) tile.findViewById(R.id.tile_title)).setText(title);
        ((TextView) tile.findViewById(R.id.tile_subtitle)).setText(subtitle);
        tile.setOnClickListener(v -> {
            Intent i = new Intent(this, ReportDetailActivity.class);
            i.putExtra(EXTRA_REPORT, reportType);
            startActivity(i);
        });
    }
}
