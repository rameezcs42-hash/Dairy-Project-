package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Expense;

import java.util.ArrayList;
import java.util.List;

public final class ExpenseDao {
    private final SQLiteDatabase db;

    public ExpenseDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    private static Expense read(Cursor c) {
        Expense e = new Expense();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.date = c.getString(c.getColumnIndexOrThrow("date"));
        e.category = c.getString(c.getColumnIndexOrThrow("category"));
        e.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        e.note = c.getString(c.getColumnIndexOrThrow("note"));
        e.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return e;
    }

    public long insert(Expense e) {
        ContentValues v = new ContentValues();
        v.put("date", e.date);
        v.put("category", e.category != null ? e.category : "misc");
        v.put("amount", e.amount);
        v.put("note", e.note);
        v.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("expenses", null, v);
    }

    public void update(Expense e) {
        ContentValues v = new ContentValues();
        v.put("date", e.date);
        v.put("category", e.category);
        v.put("amount", e.amount);
        v.put("note", e.note);
        db.update("expenses", v, "id=?", new String[]{String.valueOf(e.id)});
    }

    public void delete(long id) { db.delete("expenses", "id=?", new String[]{String.valueOf(id)}); }

    public List<Expense> range(String from, String to) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM expenses WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC",
                new String[]{from, to})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public double total(String from, String to) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date BETWEEN ? AND ?",
                new String[]{from, to})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
}
