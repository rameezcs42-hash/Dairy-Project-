package com.dairyflow.app.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Format {
    private Format() {}

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat MONEY0 = new DecimalFormat("#,##0");
    private static final DecimalFormat LITRES = new DecimalFormat("#,##0.##");
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat PRETTY = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private static final SimpleDateFormat PRETTY_LONG = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US);

    public static String money(double v) { return MONEY.format(v); }

    public static String money0(double v) { return MONEY0.format(v); }

    /** "Rs 1,234.00" — symbol comes from settings each call so shops can change it. */
    public static String pkr(double v) { return pkr(v, "Rs"); }

    public static String pkr(double v, String symbol) {
        String sign = v < 0 ? "-" : "";
        return sign + symbol + " " + MONEY.format(Math.abs(v));
    }

    public static String moneySigned(double v, String symbol) {
        if (v < 0) return "-" + symbol + " " + MONEY.format(-v);
        return symbol + " " + MONEY.format(v);
    }

    public static String litres(double v) { return LITRES.format(v); }

    public static String dateIso(Date d) { return ISO.format(d); }

    public static String todayIso() { return ISO.format(new Date()); }

    public static String pretty(String iso) {
        try { return PRETTY.format(ISO.parse(iso)); } catch (ParseException e) { return iso; }
    }

    public static String prettyLong(String iso) {
        try { return PRETTY_LONG.format(ISO.parse(iso)); } catch (ParseException e) { return iso; }
    }

    /** 2026-11 -> "November 2026" */
    public static String monthLong(String month) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM", Locale.US).parse(month);
            return new SimpleDateFormat("MMMM yyyy", Locale.US).format(d);
        } catch (ParseException e) { return month; }
    }

    public static String monthCompact(String month) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM", Locale.US).parse(month);
            return new SimpleDateFormat("MMM yyyy", Locale.US).format(d);
        } catch (ParseException e) { return month; }
    }

    public static String currentMonth() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    public static String monthShift(String month, int delta) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new SimpleDateFormat("yyyy-MM", Locale.US).parse(month));
            cal.add(Calendar.MONTH, delta);
            return new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
        } catch (ParseException e) { return month; }
    }

    /** days in ISO date (01..31) as zero-padded string */
    public static String dayOfMonth(String iso) { return iso != null && iso.length() >= 10 ? iso.substring(8, 10) : iso; }

    public static String weekdayShort(String iso) {
        try {
            return new SimpleDateFormat("EEE", Locale.US).format(ISO.parse(iso));
        } catch (ParseException e) { return ""; }
    }

    public static String weekdayOfDate(String iso) {
        try {
            return new SimpleDateFormat("EEEE", Locale.US).format(ISO.parse(iso));
        } catch (ParseException e) { return ""; }
    }

    /** Parse decimal input tolerantly ("1.5", "1,5"); NaN-safe. */
    public static double parseDouble(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Number words in English for invoice amounts (up to crores). */
    public static String numberToWords(long value) {
        if (value == 0) return "Zero";
        String[] ones = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        StringBuilder sb = new StringBuilder();
        long n = value;
        long crore = n / 10000000; n %= 10000000;
        long lakh = n / 100000; n %= 100000;
        long thousand = n / 1000; n %= 1000;
        long hundred = n / 100; n %= 100;
        if (crore > 0) sb.append(twoDigits(crore, ones, tens)).append(" Crore ");
        if (lakh > 0) sb.append(twoDigits(lakh, ones, tens)).append(" Lakh ");
        if (thousand > 0) sb.append(twoDigits(thousand, ones, tens)).append(" Thousand ");
        if (hundred > 0) sb.append(ones[(int) hundred]).append(" Hundred ");
        if (n > 0) sb.append(twoDigits(n, ones, tens));
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String twoDigits(long n, String[] ones, String[] tens) {
        if (n < 20) return ones[(int) n];
        long t = n / 10, o = n % 10;
        return tens[(int) t] + (o > 0 ? " " + ones[(int) o] : "");
    }
}
