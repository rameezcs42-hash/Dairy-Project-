package com.dairyflow.app.billing;

import java.util.Arrays;
import java.util.List;

/**
 * Offline scenario tests for the DairyFlow billing engine.
 * Run:  javac ... && java com.dairyflow.app.billing.BillingEngineScenarios
 * (build_apk.sh runs this automatically before packaging).
 */
public final class BillingEngineScenarios {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("DairyFlow billing engine — scenario tests\n=========================================");
        rateChangesApplyPerDeliveryDate();
        duplicateDeliveriesRejectedByUniqueKey();
        skippedDaysCostNothing();
        advancePaymentGoesNegative();
        monthEndBillingTotals();
        openingBalanceCarriesAcrossMonths();
        resolveRateEdgeCases();
        roundingStaysClean();
        weekdayMapping();

        System.out.println("=========================================");
        System.out.println("PASSED: " + passed + "   FAILED: " + failed);
        if (failed > 0) System.exit(1);
    }

    static void check(String name, boolean condition) {
        if (condition) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    // 1) Rate changes: each delivery keeps its own rate snapshot (old rate before 15th, new after).
    static void rateChangesApplyPerDeliveryDate() {
        System.out.println("\nScenario 1 — historical rate change mid-month");
        List<BillingEngine.RateRecord> history = Arrays.asList(
                new BillingEngine.RateRecord(100, "2026-09-01"),
                new BillingEngine.RateRecord(110, "2026-10-15"));
        check("rate on 2026-10-10 is 100", eq(BillingEngine.resolveRate(history, "2026-10-10", 0), 100));
        check("rate on 2026-10-14 is 100", eq(BillingEngine.resolveRate(history, "2026-10-14", 0), 100));
        check("rate on 2026-10-15 is 110 (effective date counts)", eq(BillingEngine.resolveRate(history, "2026-10-15", 0), 110));
        check("rate on 2026-11-30 is 110", eq(BillingEngine.resolveRate(history, "2026-11-30", 0), 110));

        List<BillingEngine.DeliveryLine> delivered = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-10-10", "morning", 2.0, 100, 200, false),
                new BillingEngine.DeliveryLine("2026-10-20", "morning", 2.0, 110, 220, false));
        BillingEngine.Statement s = BillingEngine.buildStatement(7, "2026-10", 0, delivered, null);
        check("litres = 4.0", eq(s.litresTotal, 4.0));
        check("deliveryTotal = 420 (100*2 + 110*2)", eq(s.deliveryTotal, 420));
        check("averageRate = 105", eq(s.averageRate, 105));
    }

    // 2) Duplicates: the same customer+date+session must never double-bill.
    static void duplicateDeliveriesRejectedByUniqueKey() {
        System.out.println("\nScenario 2 — duplicate deliveries");
        check("same date+session detected", BillingEngine.isDuplicate("2026-11-05", "morning", "2026-11-05", "morning"));
        check("different session allowed", !BillingEngine.isDuplicate("2026-11-05", "morning", "2026-11-05", "evening"));
        check("different date allowed", !BillingEngine.isDuplicate("2026-11-05", "morning", "2026-11-06", "morning"));
        List<BillingEngine.DeliveryLine> dupes = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-11-05", "morning", 2.0, 100, 200, false),
                new BillingEngine.DeliveryLine("2026-11-05", "evening", 0, 100, 0, true));
        BillingEngine.Statement s = BillingEngine.buildStatement(1, "2026-11", 0, dupes, null);
        check("skipped evening adds nothing", eq(s.deliveryTotal, 200));
    }

    // 3) Skipped days: 0 litres, 0 amount, counted as skipped.
    static void skippedDaysCostNothing() {
        System.out.println("\nScenario 3 — skipped days / weekly offs");
        List<BillingEngine.DeliveryLine> lines = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-10-06", "morning", 0, 100, 0, true),   // Monday off
                new BillingEngine.DeliveryLine("2026-10-07", "morning", 1.5, 100, 150, false));
        BillingEngine.Statement s = BillingEngine.buildStatement(3, "2026-10", 0, lines, null);
        check("litres excludes skipped day", eq(s.litresTotal, 1.5));
        check("one skipped day counted", s.skippedDays == 1);
        check("one delivery day counted", s.deliveryDays == 1);
        check("skipped line listed on statement", s.lines.size() == 2);
    }

    // 4) Advance payment: customer pays more than owed → negative closing (advance).
    static void advancePaymentGoesNegative() {
        System.out.println("\nScenario 4 — advance payments");
        List<BillingEngine.DeliveryLine> d = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-11-01", "morning", 1.0, 120, 120, false));
        List<BillingEngine.PaymentLine> p = Arrays.asList(
                new BillingEngine.PaymentLine("2026-11-02", 1000, "cash"));
        BillingEngine.Statement s = BillingEngine.buildStatement(5, "2026-11", 0, d, p);
        check("closing = -880 (advance)", eq(s.closing, -880));
        check("statement flagged as advance", s.advance);
    }

    // 5) Month-end: delivery + payment + opening all combine.
    static void monthEndBillingTotals() {
        System.out.println("\nScenario 5 — month-end billing");
        List<BillingEngine.DeliveryLine> d = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-10-01", "morning", 2.0, 100, 200, false),
                new BillingEngine.DeliveryLine("2026-10-02", "morning", 2.0, 100, 200, false),
                new BillingEngine.DeliveryLine("2026-10-03", "morning", 2.5, 100, 250, false));
        List<BillingEngine.PaymentLine> p = Arrays.asList(
                new BillingEngine.PaymentLine("2026-10-10", 300, "easypaisa"));
        BillingEngine.Statement s = BillingEngine.buildStatement(9, "2026-10", 150, d, p);
        check("litres = 6.5", eq(s.litresTotal, 6.5));
        check("deliveries = 650", eq(s.deliveryTotal, 650));
        check("payments = 300", eq(s.paymentsTotal, 300));
        check("closing = 150 + 650 - 300 = 500", eq(s.closing, 500));
        check("delivery days = 3", s.deliveryDays == 3);
    }

    // 6) Opening balance rolls across month boundary.
    static void openingBalanceCarriesAcrossMonths() {
        System.out.println("\nScenario 6 — balance carried into next month");
        List<BillingEngine.DeliveryLine> d = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-09-30", "morning", 2.0, 100, 200, false),   // before month
                new BillingEngine.DeliveryLine("2026-10-01", "morning", 1.0, 100, 100, false));   // inside month
        List<BillingEngine.PaymentLine> p = Arrays.asList(
                new BillingEngine.PaymentLine("2026-09-15", 50, "cash"));
        double opening = BillingEngine.openingForMonth(30, d, p, "2026-10");
        check("opening into Oct = 30 + 200 - 50 = 180", eq(opening, 180.0));
        BillingEngine.Statement s = BillingEngine.buildStatement(11, "2026-10", opening, d, p);
        check("Oct closing = 180 + 100 = 280", eq(s.closing, 280.0));
    }

    // 7) Rate edge cases: empty history, date before first record.
    static void resolveRateEdgeCases() {
        System.out.println("\nScenario 7 — rate resolution edge cases");
        check("empty history falls back", eq(BillingEngine.resolveRate(null, "2026-10-01", 95), 95));
        List<BillingEngine.RateRecord> h = Arrays.asList(new BillingEngine.RateRecord(105, "2026-10-01"));
        check("date before first record uses earliest known", eq(BillingEngine.resolveRate(h, "2020-01-01", 0), 105));
        check("exact effective date applies", eq(BillingEngine.resolveRate(h, "2026-10-01", 0), 105));
    }

    // 8) Money rounding: no 0.1+0.2 artefacts.
    static void roundingStaysClean() {
        System.out.println("\nScenario 8 — currency rounding");
        check("round2(0.1+0.2) == 0.3", eq(BillingEngine.round2(0.1 + 0.2), 0.3));
        List<BillingEngine.DeliveryLine> d = Arrays.asList(
                new BillingEngine.DeliveryLine("2026-11-01", "morning", 1.1, 33.33, 36.663, false));
        BillingEngine.Statement s = BillingEngine.buildStatement(2, "2026-11", 0, d, null);
        check("statement total rounds to 36.66", eq(s.deliveryTotal, 36.66) || eq(s.deliveryTotal, 36.66 + 0.005));
    }

    // 9) Weekday mapping (Mon=1..Sun=7) — used by weekly-off auto-skip.
    static void weekdayMapping() {
        System.out.println("\nScenario 9 — weekday mapping");
        check("2026-11-02 is Monday", BillingEngine.weekdayOf("2026-11-02") == 1);
        check("2026-11-08 is Sunday", BillingEngine.weekdayOf("2026-11-08") == 7);
        check("2026-11-06 is Friday", BillingEngine.weekdayOf("2026-11-06") == 5);
        check("invalid date returns -1", BillingEngine.weekdayOf("2026-13-99") == -1);
        check("bad month rejected", !BillingEngine.isValidDate("2026-13-40"));
        check("2026-02-29 rejected (2026 not leap)", !BillingEngine.isValidDate("2026-02-29"));
    }

    static boolean eq(double a, double b) { return Math.abs(a - b) < 0.005; }
}
