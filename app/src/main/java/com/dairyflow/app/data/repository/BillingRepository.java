package com.dairyflow.app.data.repository;

import android.content.Context;

import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.data.dao.AdjustmentDao;
import com.dairyflow.app.data.dao.CustomerDao;
import com.dairyflow.app.data.dao.DeliveryDao;
import com.dairyflow.app.data.dao.HolidayDao;
import com.dairyflow.app.data.dao.PaymentDao;
import com.dairyflow.app.data.dao.PurchaseDao;
import com.dairyflow.app.data.dao.MilkRateDao;
import com.dairyflow.app.data.model.Adjustment;
import com.dairyflow.app.data.model.Customer;
import com.dairyflow.app.data.model.Delivery;
import com.dairyflow.app.data.model.MilkRate;
import com.dairyflow.app.data.model.Payment;
import com.dairyflow.app.data.model.Purchase;
import com.dairyflow.app.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Repository: combines DAOs with the pure billing engine. */
public final class BillingRepository {

    private final CustomerDao customers;
    private final DeliveryDao deliveries;
    private final PaymentDao payments;
    private final PurchaseDao purchases;
    private final AdjustmentDao adjustments;
    private final MilkRateDao rates;
    private final HolidayDao holidays;

    public BillingRepository(Context ctx) {
        customers = new CustomerDao(ctx);
        deliveries = new DeliveryDao(ctx);
        payments = new PaymentDao(ctx);
        purchases = new PurchaseDao(ctx);
        adjustments = new AdjustmentDao(ctx);
        rates = new MilkRateDao(ctx);
        holidays = new HolidayDao(ctx);
    }

    public CustomerDao customers() { return customers; }
    public DeliveryDao deliveries() { return deliveries; }
    public PaymentDao payments() { return payments; }
    public PurchaseDao purchases() { return purchases; }
    public AdjustmentDao adjustments() { return adjustments; }
    public MilkRateDao rates() { return rates; }
    public HolidayDao holidays() { return holidays; }

    // ---------------- billing ----------------

    private List<BillingEngine.DeliveryLine> engineLines(List<Delivery> rows, String beforeMonthExclusive) {
        List<BillingEngine.DeliveryLine> out = new ArrayList<>();
        for (Delivery d : rows) {
            out.add(new BillingEngine.DeliveryLine(d.date, d.session, d.litres, d.rate, d.amount, d.skipped));
        }
        return out;
    }

    private List<BillingEngine.PaymentLine> engineLinesP(List<Payment> rows) {
        List<BillingEngine.PaymentLine> out = new ArrayList<>();
        for (Payment p : rows) out.add(new BillingEngine.PaymentLine(p.date, p.amount, p.method));
        return out;
    }

    /** Full statement for a customer/month including carried opening balance. */
    public BillingEngine.Statement statement(long customerId, String month) {
        Customer c = customers.getById(customerId);
        if (c == null) throw new IllegalStateException("Customer not found: " + customerId);
        // Pull history once (older months + this month) — dataset is a single dairy, this is small.
        List<Delivery> all = deliveries.forCustomer(customerId, "1970-01-01", month + "-31");
        List<Payment> allP = payments.forCustomer(customerId, "1970-01-01", month + "-31");
        double opening = BillingEngine.openingForMonth(c.openingBalance,
                engineLines(all, month), engineLinesP(allP), month);
        List<Delivery> thisMonth = new ArrayList<>();
        for (Delivery d : all) if (d.date.startsWith(month)) thisMonth.add(d);
        List<Payment> thisMonthP = new ArrayList<>();
        for (Payment p : allP) if (p.date.startsWith(month)) thisMonthP.add(p);
        return BillingEngine.buildStatement(customerId, month, opening,
                engineLines(thisMonth, month), engineLinesP(thisMonthP));
    }

    /** Balance as of today (opening + everything − payments). */
    public double balance(long customerId) {
        Customer c = customers.getById(customerId);
        if (c == null) return 0;
        List<Delivery> all = deliveries.forCustomer(customerId, "1970-01-01", "9999-12-31");
        List<Payment> allP = payments.forCustomer(customerId, "1970-01-01", "9999-12-31");
        double dSum = 0, pSum = 0;
        for (Delivery d : all) if (!d.skipped) dSum += d.amount;
        for (Payment p : allP) pSum += p.amount;
        return BillingEngine.round2(c.openingBalance + dSum - pSum);
    }

    // ---------------- inventory ----------------

    /** Net litres in stock = purchases − deliveries + adjustments. */
    public double stockLitres() {
        double purchased = purchases.litresAllTime();
        DeliveryDao.Totals all = deliveries.totals("1970-01-01", "9999-12-31");
        double adj = adjustments.netLitresAllTime();
        return BillingEngine.round2(purchased - all.litres + adj);
    }

    /** Rate lookup with fallback chain: sale rate history → customer default. */
    public double saleRateOn(String milkType, String date, double customerDefault) {
        Double r = rates.rateOn("sale", milkType, date);
        if (r != null) return r;
        r = rates.rateOn("sale", "mixed", date);
        return r != null ? r : customerDefault;
    }

    public double purchaseRateOn(String milkType, String date) {
        Double r = rates.rateOn("purchase", milkType, date);
        if (r != null) return r;
        r = rates.rateOn("purchase", "mixed", date);
        return r != null ? r : 0;
    }

    // ---------------- convenience totals ----------------

    public double todayLitres() { return deliveries.litresForDate(Format.todayIso()); }
    public double todayAmount() { return deliveries.amountForDate(Format.todayIso()); }
    public double todayPayments() { return payments.totalForDate(Format.todayIso()); }

    public double monthLitres() {
        String m = Format.currentMonth();
        DeliveryDao.Totals t = deliveries.totals(m + "-01", m + "-31");
        return t.litres;
    }

    public double monthAmount() {
        String m = Format.currentMonth();
        return deliveries.totals(m + "-01", m + "-31").amount;
    }

    public double monthPayments() {
        String m = Format.currentMonth();
        return payments.total(m + "-01", m + "-31");
    }
}
