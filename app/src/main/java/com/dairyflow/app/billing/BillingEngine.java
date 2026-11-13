package com.dairyflow.app.billing;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * DairyFlow billing engine — pure Java, no Android dependencies, fully unit-testable.
 *
 * Rules (spec):
 *  - Deliveries are priced with the rate snapshot stored on each delivery row
 *    (captured at entry time from the historical rate effective on that date).
 *  - Skipped/holiday rows carry 0 litres and 0 amount but are listed on statements.
 *  - Balance = openingBalance + deliveriesTotal - paymentsTotal.
 *    Negative balance = customer has an ADVANCE with the shop.
 *  - Rate history resolves by effective_from <= delivery date (latest wins).
 */
public final class BillingEngine {

    private BillingEngine() {}

    public static final int SKIP_NONE = 0;
    public static final int SKIP_WEEKLY_OFF = 1;
    public static final int SKIP_CUSTOM_DATE = 2;
    public static final int SKIP_MANUAL = 3;

    // ---------------- Inputs ----------------

    public static final class DeliveryLine {
        public final String date;      // yyyy-MM-dd
        public final String session;   // morning | evening
        public final double litres;
        public final double rate;
        public final double amount;
        public final boolean skipped;

        public DeliveryLine(String date, String session, double litres, double rate, double amount, boolean skipped) {
            this.date = date;
            this.session = session;
            this.litres = litres;
            this.rate = rate;
            this.amount = amount;
            this.skipped = skipped;
        }
    }

    public static final class PaymentLine {
        public final String date;
        public final double amount;
        public final String method;

        public PaymentLine(String date, double amount, String method) {
            this.date = date;
            this.amount = amount;
            this.method = method;
        }
    }

    /** A historical rate record. kind/milkType are matched by caller; engine only sees kind via list. */
    public static final class RateRecord {
        public final double rate;
        public final String effectiveFrom;

        public RateRecord(double rate, String effectiveFrom) {
            this.rate = rate;
            this.effectiveFrom = effectiveFrom;
        }
    }

    // ---------------- Output ----------------

    public static final class Statement {
        public long customerId;
        public String month;                 // yyyy-MM
        public double opening;               // balance carried into the month
        public double litresTotal;
        public double deliveryTotal;
        public double paymentsTotal;
        public double closing;               // opening + deliveryTotal - paymentsTotal
        public int deliveryDays;             // distinct days with litres > 0
        public int skippedDays;              // distinct days skipped
        public double averageRate;
        public boolean advance;              // true when closing < 0 (customer in advance)

        public final List<DeliveryLine> lines = new ArrayList<>();
        public final List<PaymentLine> payments = new ArrayList<>();
    }

    // ---------------- Core math ----------------

    /** yyyy-MM-dd comparison is lexicographic — safe for ISO strings. */
    public static boolean isValidDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        f.setLenient(false);
        try {
            f.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    public static String monthOf(String date) {
        if (!isValidDate(date)) throw new IllegalArgumentException("Bad date: " + date);
        return date.substring(0, 7);
    }

    /** Monday=1 .. Sunday=7 (0/absent when invalid). */
    public static int weekdayOf(String date) {
        // 1970-01-01 was a Thursday.
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        f.setLenient(false);
        try {
            cal.setTime(f.parse(date));
        } catch (ParseException e) {
            return -1;
        }
        int wd = cal.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        return (wd + 5) % 7 + 1;                // Mon=1..Sun=7
    }

    public static boolean sameMonth(String date, String month) {
        return date != null && date.startsWith(month);
    }

    /**
     * Resolve the rate effective on {@code date}.
     * Latest record with effectiveFrom <= date wins; if none is <= date, the earliest
     * known record applies (shop predates the rate register); otherwise fallbackRate.
     */
    public static double resolveRate(List<RateRecord> history, String date, double fallbackRate) {
        if (history == null || history.isEmpty()) return fallbackRate;
        List<RateRecord> sorted = new ArrayList<>(history);
        Collections.sort(sorted, Comparator.comparing(r -> r.effectiveFrom));
        RateRecord best = null;
        for (RateRecord r : sorted) {
            if (r.effectiveFrom.compareTo(date) <= 0) best = r; else break;
        }
        if (best == null) best = sorted.get(0);
        return best.rate;
    }

    /** Build the monthly statement for a customer. */
    public static Statement buildStatement(long customerId, String month, double openingBalance,
                                           List<DeliveryLine> deliveries, List<PaymentLine> payments) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("month must be yyyy-MM");
        }
        Statement s = new Statement();
        s.customerId = customerId;
        s.month = month;
        s.opening = round2(openingBalance);

        double litres = 0, deliveriesTotal = 0, paymentsTotal = 0;
        List<String> deliveryDays = new ArrayList<>();
        List<String> skippedDays = new ArrayList<>();

        if (deliveries != null) {
            List<DeliveryLine> sorted = new ArrayList<>(deliveries);
            Collections.sort(sorted, (a, b) -> {
                int d = a.date.compareTo(b.date);
                return d != 0 ? d : a.session.compareTo(b.session);
            });
            for (DeliveryLine l : sorted) {
                if (!sameMonth(l.date, month)) continue;
                s.lines.add(l);
                if (l.skipped) {
                    if (!skippedDays.contains(l.date)) skippedDays.add(l.date);
                } else {
                    litres += l.litres;
                    deliveriesTotal += l.amount;
                    if (l.litres > 0 && !deliveryDays.contains(l.date)) deliveryDays.add(l.date);
                }
            }
        }
        if (payments != null) {
            List<PaymentLine> sorted = new ArrayList<>(payments);
            Collections.sort(sorted, Comparator.comparing(p -> p.date));
            for (PaymentLine p : sorted) {
                if (!sameMonth(p.date, month)) continue;
                s.payments.add(p);
                paymentsTotal += p.amount;
            }
        }

        s.litresTotal = round2(litres);
        s.deliveryTotal = round2(deliveriesTotal);
        s.paymentsTotal = round2(paymentsTotal);
        s.closing = round2(s.opening + s.deliveryTotal - s.paymentsTotal);
        s.deliveryDays = deliveryDays.size();
        s.skippedDays = skippedDays.size();
        s.averageRate = litres > 0 ? round2(deliveriesTotal / litres) : 0;
        s.advance = s.closing < 0;
        return s;
    }

    /**
     * Balance carried into {@code month}: opening from customer record + everything
     * before the month (deliveries - payments).
     */
    public static double openingForMonth(double openingBalance, List<DeliveryLine> allDeliveries,
                                         List<PaymentLine> allPayments, String month) {
        double d = 0, p = 0;
        if (allDeliveries != null) {
            for (DeliveryLine l : allDeliveries) {
                if (!l.skipped && l.date.compareTo(month + "-01") < 0) d += l.amount;
            }
        }
        if (allPayments != null) {
            for (PaymentLine pay : allPayments) {
                if (pay.date.compareTo(month + "-01") < 0) p += pay.amount;
            }
        }
        return round2(openingBalance + d - p);
    }

    /** Duplicate-delivery guard used by the entry screen and tests. */
    public static boolean isDuplicate(String existingDate, String existingSession,
                                      String newDate, String newSession) {
        return existingDate.equals(newDate) && existingSession.equalsIgnoreCase(newSession);
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
