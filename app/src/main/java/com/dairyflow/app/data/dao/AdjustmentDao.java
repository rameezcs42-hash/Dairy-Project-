package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Adjustment;

import java.util.ArrayList;
import java.util.List;

public final class AdjustmentDao {
    private final SQLiteDatabase db;

    public AdjustmentDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    public long insert(Adjustment a) {
        ContentValues v = new ContentValues();
        v.put("date", a.date);
        v.put("litres", a.litres);
        v.put("reason", a.reason);
        v.put("note", a.note);
        v.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("adjustments", null, v);
    }

    public void delete(long id) { db.delete("adjustments", "id=?", new String[]{String.valueOf(id)}); }

    public List<Adjustment> range(String from, String to) {
        List<Adjustment> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM adjustments WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC",
                new String[]{from, to})) {
            while (c.moveToNext()) {
                Adjustment a = new Adjustment();
                a.id = c.getLong(c.getColumnIndexOrThrow("id"));
                a.date = c.getString(c.getColumnIndexOrThrow("date"));
                a.litres = c.getDouble(c.getColumnIndexOrThrow("litres"));
                a.reason = c.getString(c.getColumnIndexOrThrow("reason"));
                a.note = c.getString(c.getColumnIndexOrThrow("note"));
                a.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                out.add(a);
            }
        }
        return out;
    }

    public double netLitresAllTime() {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(litres),0) FROM adjustments", null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
}
