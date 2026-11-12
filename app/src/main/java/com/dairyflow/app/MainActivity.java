package com.dairyflow.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dairyflow.app.ui.DashboardFragment;
import com.dairyflow.app.ui.CustomersFragment;
import com.dairyflow.app.ui.PaymentsFragment;
import com.dairyflow.app.ui.MoreFragment;
import com.dairyflow.app.ui.deliveries.DeliveryEntryActivity;

/** Hosts the bottom-navigation tabs: Home, Customers, Entry(launcher), Payments, More. */
public class MainActivity extends Activity {

    private static final int TAB_HOME = 0, TAB_CUSTOMERS = 1, TAB_PAYMENTS = 3, TAB_MORE = 4;

    private LinearLayout tabHome, tabCustomers, tabPayments, tabMore;
    private int currentTab = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tabHome = findTab(R.id.tab_home, ic("home"), "Home");
        tabCustomers = findTab(R.id.tab_customers, ic("customers"), "Customers");
        tabPayments = findTab(R.id.tab_payments, ic("payments"), "Payments");
        tabMore = findTab(R.id.tab_more, ic("more"), "More");
        FrameLayout tabEntry = findViewById(R.id.tab_entry);

        tabHome.setOnClickListener(v -> showTab(TAB_HOME));
        tabCustomers.setOnClickListener(v -> showTab(TAB_CUSTOMERS));
        tabPayments.setOnClickListener(v -> showTab(TAB_PAYMENTS));
        tabMore.setOnClickListener(v -> showTab(TAB_MORE));
        tabEntry.setOnClickListener(v -> startActivity(new Intent(this, DeliveryEntryActivity.class)));

        if (savedInstanceState == null) showTab(TAB_HOME);
    }

    @Override protected void onResume() {
        super.onResume();
        // Refresh whichever tab is visible when returning from an activity.
        android.app.Fragment f = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof Refreshable) ((Refreshable) f).refresh();
    }

    private LinearLayout findTab(int id, int iconRes, String label) {
        View include = findViewById(id);
        ImageView icon = include.findViewById(R.id.nav_icon);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.parseColor("#42554B"), PorterDuff.Mode.SRC_IN);
        TextView labelView = include.findViewById(R.id.nav_label);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#42554B"));
        return (LinearLayout) include;
    }

    private void styleTab(LinearLayout tab, boolean selected) {
        int color = selected ? Color.parseColor("#065F46") : Color.parseColor("#42554B");
        ImageView icon = (ImageView) tab.getChildAt(0);
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        TextView label = (TextView) tab.getChildAt(1);
        label.setTextColor(color);
        tab.setSelected(selected);
    }

    private void showTab(int tab) {
        if (tab == currentTab) return;
        currentTab = tab;
        android.app.Fragment fragment;
        switch (tab) {
            case TAB_CUSTOMERS: fragment = new CustomersFragment(); break;
            case TAB_PAYMENTS:  fragment = new PaymentsFragment(); break;
            case TAB_MORE:      fragment = new MoreFragment(); break;
            case TAB_HOME:
            default:            fragment = new DashboardFragment(); break;
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        styleTab(tabHome, tab == TAB_HOME);
        styleTab(tabCustomers, tab == TAB_CUSTOMERS);
        styleTab(tabPayments, tab == TAB_PAYMENTS);
        styleTab(tabMore, tab == TAB_MORE);
    }

    /** Fragments that reload their data when the host resumes. */
    public interface Refreshable { void refresh(); }

    private int ic(String name) {
        switch (name) {
            case "home": return R.drawable.ic_home;
            case "customers": return R.drawable.ic_customers;
            case "payments": return R.drawable.ic_payments;
            case "more": return R.drawable.ic_more;
            default: return R.drawable.ic_home;
        }
    }
}
