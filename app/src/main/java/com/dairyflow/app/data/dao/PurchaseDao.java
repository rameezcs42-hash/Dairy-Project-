package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Purchase;

import java.util.ArrayList;
import java.util.List;

public final class PurchaseDao {
    private final SQLiteDatabase db;

    public PurchaseDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    private static Purchase read(Cursor c) {
        Purchase p = new Purchase();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.date = c.getString(c.getColumnIndexOrThrow("date"));
        p.supplier = c.getString(c.getColumnIndexOrThrow("supplier"));
        p.milkType = c.getString(c.getColumnIndexOrThrow("milk_type"));
        p.litres = c.getDouble(c.getColumnIndexOrThrow("litres"));
        p.rate = c.getDouble(c.getColumnIndexOrThrow("rate"));
        p.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        int f = c.getColumnIndex("fat");
        if (f >= 0 && !c.isNull(f)) p.fat = c.getDouble(f);
        p.note = c.getString(c.getColumnIndexOrThrow("note"));
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return p;
    }

    private ContentValues values(Purchase p) {
        ContentValues v = new ContentValues();
        v.put("date", p.date);
        v.put("supplier", p.supplier);
        v.put("milk_type", p.milkType != null ? p.milkType : "buffalo");
        v.put("litres", p.litres);
        v.put("rate", p.rate);
        v.put("amount", DeliveryDao.round2(p.litres * p.rate));
        if (p.fat != null) v.put("fat", p.fat); else v.putNull("fat");
        v.put("note", p.note);
        return v;
    }

    public long insert(Purchase p) {
        ContentValues v = values(p);
        v.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("purchases", null, v);
    }

    public void update(Purchase p) { db.update("purchases", values(p), "id=?", new String[]{String.valueOf(p.id)}); }

    public void delete(long id) { db.delete("purchases", "id=?", new String[]{String.valueOf(id)}); }

    public List<Purchase> range(String from, String to) {
        List<Purchase> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM purchases WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC",
                new String[]{from, to})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public double litresTotal(String from, String to) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(litres),0) FROM purchases WHERE date BETWEEN ? AND ?",
                new String[]{from, to})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double amountTotal(String from, String to) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM purchases WHERE date BETWEEN ? AND ?",
                new String[]{from, to})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double litresAllTime() {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(litres),0) FROM purchases", null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
}
