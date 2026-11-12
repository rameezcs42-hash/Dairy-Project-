package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Delivery;

import java.util.ArrayList;
import java.util.List;

public final class DeliveryDao {
    private final SQLiteDatabase db;

    public DeliveryDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    private static Delivery read(Cursor c) {
        Delivery d = new Delivery();
        d.id = c.getLong(c.getColumnIndexOrThrow("id"));
        d.customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
        d.date = c.getString(c.getColumnIndexOrThrow("date"));
        d.session = c.getString(c.getColumnIndexOrThrow("session"));
        d.litres = c.getDouble(c.getColumnIndexOrThrow("litres"));
        d.rate = c.getDouble(c.getColumnIndexOrThrow("rate"));
        d.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        d.skipped = c.getInt(c.getColumnIndexOrThrow("skipped")) == 1;
        int n = c.getColumnIndex("note");
        d.note = n >= 0 ? c.getString(n) : null;
        d.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        int cn = c.getColumnIndex("customer_name");
        if (cn >= 0) d.customerName = c.getString(cn);
        return d;
    }

    /**
     * Insert or update the delivery for (customer, date, session).
     * Returns true if inserted, false if updated. Relies on the UNIQUE index
     * so duplicates can never silently create two rows.
     */
    public boolean upsert(Delivery d) {
        ContentValues v = new ContentValues();
        v.put("customer_id", d.customerId);
        v.put("date", d.date);
        v.put("session", d.session);
        v.put("litres", d.litres);
        v.put("rate", d.rate);
        v.put("amount", d.skipped ? 0 : round2(d.litres * d.rate));
        v.put("skipped", d.skipped ? 1 : 0);
        v.put("note", d.note);
        v.put("created_at", d.createdAt > 0 ? d.createdAt : System.currentTimeMillis());
        long id = db.insertWithOnConflict("deliveries", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) return true;
        db.update("deliveries", v, "customer_id=? AND date=? AND session=?",
                new String[]{String.valueOf(d.customerId), d.date, d.session});
        return false;
    }

    public void delete(long id) { db.delete("deliveries", "id=?", new String[]{String.valueOf(id)}); }

    public boolean exists(long customerId, String date, String session) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM deliveries WHERE customer_id=? AND date=? AND session=?",
                new String[]{String.valueOf(customerId), date, session})) {
            return c.moveToFirst();
        }
    }

    public List<Delivery> forDate(String date) {
        return query("SELECT d.*, c.name AS customer_name FROM deliveries d JOIN customers c ON c.id=d.customer_id"
                + " WHERE d.date=? ORDER BY c.area, c.name COLLATE NOCASE, d.session", new String[]{date});
    }

    public List<Delivery> forDateSession(String date, String session) {
        return query("SELECT d.*, c.name AS customer_name FROM deliveries d JOIN customers c ON c.id=d.customer_id"
                + " WHERE d.date=? AND d.session=? ORDER BY c.area, c.name COLLATE NOCASE", new String[]{date, session});
    }

    public List<Delivery> forCustomer(long customerId, String from, String to) {
        return query("SELECT d.*, c.name AS customer_name FROM deliveries d JOIN customers c ON c.id=d.customer_id"
                + " WHERE d.customer_id=? AND d.date BETWEEN ? AND ? ORDER BY d.date, d.session",
                new String[]{String.valueOf(customerId), from, to});
    }

    public List<Delivery> forMonth(String month) {  // month = yyyy-MM
        return query("SELECT d.*, c.name AS customer_name FROM deliveries d JOIN customers c ON c.id=d.customer_id"
                + " WHERE substr(d.date,1,7)=? ORDER BY d.date, c.area, c.name COLLATE NOCASE, d.session",
                new String[]{month});
    }

    public List<Delivery> range(String from, String to) {
        return query("SELECT d.*, c.name AS customer_name FROM deliveries d JOIN customers c ON c.id=d.customer_id"
                + " WHERE d.date BETWEEN ? AND ? ORDER BY d.date DESC, d.customer_id, d.session",
                new String[]{from, to});
    }

    private List<Delivery> query(String sql, String[] args) {
        List<Delivery> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    /** Aggregates for dashboards/reports. */
    public static final class Totals {
        public double litres; public double amount; public int skipped; public int deliveries;
    }

    public Totals totals(String from, String to) {
        Totals t = new Totals();
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(litres),0), COALESCE(SUM(amount),0),"
                        + " COALESCE(SUM(skipped),0), COUNT(*) FROM deliveries WHERE date BETWEEN ? AND ?",
                new String[]{from, to})) {
            if (c.moveToFirst()) {
                t.litres = c.getDouble(0); t.amount = c.getDouble(1);
                t.skipped = c.getInt(2); t.deliveries = c.getInt(3);
            }
        }
        return t;
    }

    public double litresForDate(String date) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(litres),0) FROM deliveries WHERE date=?", new String[]{date})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double amountForDate(String date) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM deliveries WHERE date=?", new String[]{date})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
