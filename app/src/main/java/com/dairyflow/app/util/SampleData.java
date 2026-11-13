package com.dairyflow.app.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.data.db.DatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * "Load sample data" for demos/testing: builds a realistic 60-day history.
 * Everything is inserted inside one transaction and can be wiped with clearAll().
 */
public final class SampleData {
    private SampleData() {}

    public static void seed(Context ctx) {
        SQLiteDatabase db = DatabaseHelper.get(ctx).getWritableDatabase();
        Random rnd = new Random(20261112L);
        String[] names = {"Muhammad Iqbal", "Ahmed Sweets", "Bismillah Bakery", "Nawaz Hotel",
                "Mama Bakery", "Saddar Kiryana Store", "Fatima Bibi", "New Al-Noor Restaurant"};
        String[] phones = {"0300-1234567", "0321-7654321", "0333-4567890", "0345-1122334",
                "0322-9988776", "0311-3344556", "0321-5544332", "0300-6677889"};
        String[] areas = {"Saddar", "Tariq Road", "Clifton", "Nazimabad", "PECHS", "Saddar", "Garden", "Tariq Road"};
        String[] types = {"buffalo", "buffalo", "cow", "mixed", "cow", "buffalo", "buffalo", "mixed"};
        double[] rates = {130, 130, 125, 128, 125, 130, 130, 128};
        double[] mQty = {2, 8, 15, 25, 4, 3, 1, 30};
        double[] eQty = {0, 6, 0, 20, 0, 0, 0, 25};
        int[] offs = {0, 0, 7, 0, 0, 0, 1, 0};           // Sunday / Monday offs
        double[] openings = {0, 1200, 0, 4500, 800, 0, 0, 9800};

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();

        db.beginTransaction();
        try {
            // Default purchase rate history: effective 60 days ago 95 Rs, effective 20 days ago 100 Rs (shows rate changes)
            Calendar ago60 = (Calendar) cal.clone();
            ago60.add(Calendar.DAY_OF_YEAR, -60);
            Calendar ago20 = (Calendar) cal.clone();
            ago20.add(Calendar.DAY_OF_YEAR, -20);
            exec(db, "INSERT INTO milk_rates(kind, milk_type, rate, effective_from) VALUES('purchase','buffalo',95,?)", iso.format(ago60.getTime()));
            exec(db, "INSERT INTO milk_rates(kind, milk_type, rate, effective_from) VALUES('purchase','buffalo',100,?)", iso.format(ago20.getTime()));
            exec(db, "INSERT INTO milk_rates(kind, milk_type, rate, effective_from) VALUES('sale','buffalo',130,?)", iso.format(ago60.getTime()));
            exec(db, "INSERT INTO milk_rates(kind, milk_type, rate, effective_from) VALUES('sale','buffalo',135,?)", iso.format(ago20.getTime()));
            exec(db, "INSERT INTO milk_rates(kind, milk_type, rate, effective_from) VALUES('sale','cow',120,?)", iso.format(ago60.getTime()));

            long[] ids = new long[names.length];
            for (int i = 0; i < names.length; i++) {
                Object[] args = {names[i], phones[i], names[i], null, areas[i], types[i], rates[i],
                        openings[i], mQty[i], eQty[i], offs[i], "both", System.currentTimeMillis()};
                db.execSQL("INSERT INTO customers(name, phone, shop_name, address, area, milk_type, default_rate,"
                        + " opening_balance, morning_qty, evening_qty, weekly_off, session_pref, created_at, active)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1)", args);
                ids[i] = lastId(db);
            }

            // Purchases roughly covering sales volume
            double dailyNeed = 0;
            for (double q : mQty) dailyNeed += q;
            for (double q : eQty) dailyNeed += q;
            for (int day = 60; day >= 0; day -= 1) {
                Calendar d = (Calendar) cal.clone();
                d.add(Calendar.DAY_OF_YEAR, -day);
                String date = iso.format(d.getTime());
                // bulk purchase every ~3 days
                if (day % 3 == 0) {
                    double litres = dailyNeed * 3.1;
                    double rate = day > 20 ? 95 : 100;
                    db.execSQL("INSERT INTO purchases(date, supplier, milk_type, litres, rate, amount, fat, note, created_at)"
                                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                            new Object[]{date, day % 6 == 0 ? "City Dairy Union" : "Rehman Farm", "buffalo",
                                    litres, rate, litres * rate, 4.5 + rnd.nextDouble() * 1.5, null, System.currentTimeMillis()});
                }
                // Deliveries
                for (int i = 0; i < names.length; i++) {
                    int wd = weekday(d);
                    if (offs[i] != 0 && wd == offs[i]) {
                        db.execSQL("INSERT INTO deliveries(customer_id, date, session, litres, rate, amount, skipped, note, created_at)"
                                        + " VALUES(?,?,?,?,?,?,1,?,?)",
                                new Object[]{ids[i], date, "morning", 0, currentSaleRate(db, date, rates[i]), 0, "Weekly off", System.currentTimeMillis()});
                        continue;
                    }
                    if (mQty[i] > 0) insertDelivery(db, rnd, ids[i], date, "morning", mQty[i], rates[i], rateOn(db, "sale", date));
                    if (eQty[i] > 0) insertDelivery(db, rnd, ids[i], date, "evening", eQty[i], rates[i], rateOn(db, "sale", date));
                }
                // Payments: each customer pays every ~9 days
                if (day % 9 == 4) {
                    int i = rnd.nextInt(names.length);
                    db.execSQL("INSERT INTO payments(customer_id, date, amount, method, note, created_at) VALUES(?,?,?,?,?,?)",
                            new Object[]{ids[i], date, 1000 + rnd.nextInt(9000),
                                    new String[]{"cash", "easypaisa", "jazzcash", "bank"}[rnd.nextInt(4)], "", System.currentTimeMillis()});
                }
            }
            // A couple of expenses
            for (int k = 0; k < 6; k++) {
                Calendar d = (Calendar) cal.clone();
                d.add(Calendar.DAY_OF_YEAR, -rnd.nextInt(30));
                db.execSQL("INSERT INTO expenses(date, category, amount, note, created_at) VALUES(?,?,?,?,?)",
                        new Object[]{iso.format(d.getTime()),
                                new String[]{"salary", "transport", "electricity", "ice", "rent"}[rnd.nextInt(5)],
                                200 + rnd.nextInt(3800), null, System.currentTimeMillis()});
            }

            db.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('seeded_demo','1')");
            db.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('shop_name','Al-Madina Dairy')");
            db.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('shop_address','Shop 12, Anaj Mandi, Saddar')");
            db.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('shop_phone','0300-7654321')");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void insertDelivery(SQLiteDatabase db, Random rnd, long customerId, String date,
                                       String session, double baseQty, double fallbackRate, Double rateOpt) {
        double qty = baseQty;
        // ±10% variation
        qty = Math.round(qty * (0.9 + rnd.nextDouble() * 0.2) * 10.0) / 10.0;
        double rate = rateOpt != null ? rateOpt : fallbackRate;
        db.execSQL("INSERT INTO deliveries(customer_id, date, session, litres, rate, amount, skipped, note, created_at)"
                        + " VALUES(?,?,?,?,?,?,0,?,?)",
                new Object[]{customerId, date, session, qty, rate, qty * rate, null, System.currentTimeMillis()});
    }

    private static Double rateOn(SQLiteDatabase db, String kind, String date) {
        Cursor c = db.rawQuery("SELECT rate FROM milk_rates WHERE kind=? AND effective_from<=? ORDER BY effective_from DESC, id DESC LIMIT 1",
                new String[]{kind, date});
        Double r = null;
        if (c.moveToFirst()) r = c.getDouble(0);
        c.close();
        return r;
    }

    private static double currentSaleRate(SQLiteDatabase db, String date, double fallback) {
        Double r = rateOn(db, "sale", date);
        return r != null ? r : fallback;
    }

    private static int weekday(Calendar d) {
        int wd = d.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        return (wd + 5) % 7 + 1;              // Mon=1..Sun=7
    }

    private static void exec(SQLiteDatabase db, String sql, String arg) {
        db.execSQL(sql, new Object[]{arg});
    }

    private static long lastId(SQLiteDatabase db) {
        android.database.Cursor c = db.rawQuery("SELECT last_insert_rowid()", null);
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    /** Wipes every business table (settings kept). */
    public static void clearAll(Context ctx) {
        SQLiteDatabase db = DatabaseHelper.get(ctx).getWritableDatabase();
        db.beginTransaction();
        try {
            for (String t : new String[]{"deliveries", "payments", "purchases", "expenses", "adjustments",
                    "bills", "holidays", "customers", "milk_rates"}) {
                db.execSQL("DELETE FROM " + t);
            }
            db.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('deliveries','payments','purchases','expenses','adjustments','bills','holidays','customers','milk_rates')");
            db.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('seeded_demo','0')");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
