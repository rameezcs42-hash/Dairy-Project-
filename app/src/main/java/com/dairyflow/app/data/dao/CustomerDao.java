package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Customer;

import java.util.ArrayList;
import java.util.List;

public final class CustomerDao {
    private final DatabaseHelper helper;

    public CustomerDao(Context ctx) { this.helper = DatabaseHelper.get(ctx); }

    private SQLiteDatabase db() { return helper.getWritableDatabase(); }

    private static Customer read(Cursor c) {
        Customer cu = new Customer();
        cu.id = c.getLong(c.getColumnIndexOrThrow("id"));
        cu.name = c.getString(c.getColumnIndexOrThrow("name"));
        cu.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        cu.shopName = c.getString(c.getColumnIndexOrThrow("shop_name"));
        cu.address = c.getString(c.getColumnIndexOrThrow("address"));
        cu.area = c.getString(c.getColumnIndexOrThrow("area"));
        cu.milkType = c.getString(c.getColumnIndexOrThrow("milk_type"));
        cu.defaultRate = c.getDouble(c.getColumnIndexOrThrow("default_rate"));
        cu.openingBalance = c.getDouble(c.getColumnIndexOrThrow("opening_balance"));
        cu.morningQty = c.getDouble(c.getColumnIndexOrThrow("morning_qty"));
        cu.eveningQty = c.getDouble(c.getColumnIndexOrThrow("evening_qty"));
        cu.weeklyOff = c.getInt(c.getColumnIndexOrThrow("weekly_off"));
        cu.sessionPref = c.getString(c.getColumnIndexOrThrow("session_pref"));
        cu.note = c.getString(c.getColumnIndexOrThrow("note"));
        cu.active = c.getInt(c.getColumnIndexOrThrow("active")) == 1;
        cu.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        int bidx = c.getColumnIndex("balance");
        if (bidx >= 0) cu.balance = c.getDouble(bidx); else cu.balance = cu.openingBalance;
        int ml = c.getColumnIndex("month_litres");
        if (ml >= 0) cu.monthLitres = c.getDouble(ml);
        int mt = c.getColumnIndex("month_total");
        if (mt >= 0) cu.monthTotal = c.getDouble(mt);
        return cu;
    }

    private static ContentValues values(Customer cu) {
        ContentValues v = new ContentValues();
        v.put("name", cu.name);
        v.put("phone", cu.phone);
        v.put("shop_name", cu.shopName);
        v.put("address", cu.address);
        v.put("area", cu.area);
        v.put("milk_type", cu.milkType != null ? cu.milkType : "buffalo");
        v.put("default_rate", cu.defaultRate);
        v.put("opening_balance", cu.openingBalance);
        v.put("morning_qty", cu.morningQty);
        v.put("evening_qty", cu.eveningQty);
        v.put("weekly_off", cu.weeklyOff);
        v.put("session_pref", cu.sessionPref != null ? cu.sessionPref : "both");
        v.put("note", cu.note);
        v.put("active", cu.active ? 1 : 0);
        return v;
    }

    public long insert(Customer cu) {
        ContentValues v = values(cu);
        v.put("created_at", System.currentTimeMillis());
        return db().insertOrThrow("customers", null, v);
    }

    public void update(Customer cu) {
        db().update("customers", values(cu), "id=?", new String[]{String.valueOf(cu.id)});
    }

    public void setActive(long id, boolean active) {
        ContentValues v = new ContentValues();
        v.put("active", active ? 1 : 0);
        db().update("customers", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        db().delete("customers", "id=?", new String[]{String.valueOf(id)});
    }

    public Customer getById(long id) {
        try (Cursor c = db().rawQuery("SELECT * FROM customers WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return read(c);
            return null;
        }
    }

    /** All customers with live balance and current-month figures. */
    public List<Customer> list(boolean includeInactive) {
        String sql = "SELECT c.*, "
                + " (c.opening_balance + COALESCE((SELECT SUM(amount) FROM deliveries d WHERE d.customer_id=c.id),0)"
                + "  - COALESCE((SELECT SUM(amount) FROM payments p WHERE p.customer_id=c.id),0)) AS balance,"
                + " COALESCE((SELECT SUM(litres) FROM deliveries d WHERE d.customer_id=c.id AND substr(d.date,1,7)=strftime('%Y-%m','now','localtime') AND skipped=0),0) AS month_litres,"
                + " COALESCE((SELECT SUM(amount) FROM deliveries d WHERE d.customer_id=c.id AND substr(d.date,1,7)=strftime('%Y-%m','now','localtime')),0) AS month_total"
                + " FROM customers c"
                + (includeInactive ? "" : " WHERE c.active=1")
                + " ORDER BY c.active DESC, c.name COLLATE NOCASE ASC";
        List<Customer> out = new ArrayList<>();
        try (Cursor c = db().rawQuery(sql, null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public List<Customer> search(String q, boolean includeInactive) {
        String like = "%" + q + "%";
        String sql = "SELECT c.*, "
                + " (c.opening_balance + COALESCE((SELECT SUM(amount) FROM deliveries d WHERE d.customer_id=c.id),0)"
                + "  - COALESCE((SELECT SUM(amount) FROM payments p WHERE p.customer_id=c.id),0)) AS balance,"
                + " 0 AS month_litres, 0 AS month_total"
                + " FROM customers c WHERE (c.name LIKE ? OR c.phone LIKE ? OR c.area LIKE ? OR c.shop_name LIKE ?)"
                + (includeInactive ? "" : " AND c.active=1")
                + " ORDER BY c.name COLLATE NOCASE ASC";
        List<Customer> out = new ArrayList<>();
        try (Cursor c = db().rawQuery(sql, new String[]{like, like, like, like})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public int countActive() {
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM customers WHERE active=1", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<String> areas() {
        List<String> out = new ArrayList<>();
        try (Cursor c = db().rawQuery(
                "SELECT DISTINCT area FROM customers WHERE active=1 AND area IS NOT NULL AND TRIM(area)!='' ORDER BY area", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    public double outstandingTotal() {
        String sql = "SELECT COALESCE(SUM(bal),0) FROM ("
                + " SELECT (opening_balance + COALESCE((SELECT SUM(amount) FROM deliveries d WHERE d.customer_id=c.id),0)"
                + "  - COALESCE((SELECT SUM(amount) FROM payments p WHERE p.customer_id=c.id),0)) AS bal"
                + " FROM customers c WHERE c.active=1) WHERE bal > 0";
        try (Cursor c = db().rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
}
