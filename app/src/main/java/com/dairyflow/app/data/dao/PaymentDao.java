package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Payment;

import java.util.ArrayList;
import java.util.List;

public final class PaymentDao {
    private final SQLiteDatabase db;

    public PaymentDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    private static Payment read(Cursor c) {
        Payment p = new Payment();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
        p.date = c.getString(c.getColumnIndexOrThrow("date"));
        p.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        p.method = c.getString(c.getColumnIndexOrThrow("method"));
        int n = c.getColumnIndex("note");
        p.note = n >= 0 ? c.getString(n) : null;
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        int cn = c.getColumnIndex("customer_name");
        if (cn >= 0) p.customerName = c.getString(cn);
        return p;
    }

    public long insert(Payment p) {
        ContentValues v = new ContentValues();
        v.put("customer_id", p.customerId);
        v.put("date", p.date);
        v.put("amount", p.amount);
        v.put("method", p.method != null ? p.method : "cash");
        v.put("note", p.note);
        v.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("payments", null, v);
    }

    public void update(Payment p) {
        ContentValues v = new ContentValues();
        v.put("customer_id", p.customerId);
        v.put("date", p.date);
        v.put("amount", p.amount);
        v.put("method", p.method);
        v.put("note", p.note);
        db.update("payments", v, "id=?", new String[]{String.valueOf(p.id)});
    }

    public void delete(long id) { db.delete("payments", "id=?", new String[]{String.valueOf(id)}); }

    private List<Payment> query(String sql, String[] args) {
        List<Payment> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public List<Payment> forCustomer(long customerId, String from, String to) {
        return query("SELECT p.*, c.name AS customer_name FROM payments p JOIN customers c ON c.id=p.customer_id"
                + " WHERE p.customer_id=? AND p.date BETWEEN ? AND ? ORDER BY p.date DESC, p.id DESC",
                new String[]{String.valueOf(customerId), from, to});
    }

    public List<Payment> range(String from, String to) {
        return query("SELECT p.*, c.name AS customer_name FROM payments p JOIN customers c ON c.id=p.customer_id"
                + " WHERE p.date BETWEEN ? AND ? ORDER BY p.date DESC, p.id DESC",
                new String[]{from, to});
    }

    public double total(String from, String to) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM payments WHERE date BETWEEN ? AND ?",
                new String[]{from, to})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double totalForDate(String date) { return total(date, date); }

    public List<Payment> recent(int limit) {
        List<Payment> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT p.*, c.name AS customer_name FROM payments p JOIN customers c ON c.id=p.customer_id"
                        + " ORDER BY p.date DESC, p.id DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }
}
