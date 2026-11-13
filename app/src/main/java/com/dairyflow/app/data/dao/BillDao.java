package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Bill;

import java.util.ArrayList;
import java.util.List;

public final class BillDao {
    private final SQLiteDatabase db;

    public BillDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    public void upsert(Bill b) {
        ContentValues v = new ContentValues();
        v.put("customer_id", b.customerId);
        v.put("month", b.month);
        v.put("opening", b.opening);
        v.put("litres", b.litres);
        v.put("delivery_total", b.deliveryTotal);
        v.put("payments_total", b.paymentsTotal);
        v.put("closing", b.closing);
        v.put("skipped_days", b.skippedDays);
        v.put("delivery_days", b.deliveryDays);
        v.put("pdf_path", b.pdfPath);
        v.put("generated_at", java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()));
        db.insertWithOnConflict("bills", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Bill get(long customerId, String month) {
        try (Cursor c = db.rawQuery("SELECT * FROM bills WHERE customer_id=? AND month=?",
                new String[]{String.valueOf(customerId), month})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public List<Bill> forMonth(String month) {
        List<Bill> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT b.*, c.name AS customer_name, c.area AS customer_area FROM bills b"
                        + " JOIN customers c ON c.id=b.customer_id WHERE b.month=? ORDER BY c.name COLLATE NOCASE",
                new String[]{month})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static Bill read(Cursor c) {
        Bill b = new Bill();
        b.id = c.getLong(c.getColumnIndexOrThrow("id"));
        b.customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
        b.month = c.getString(c.getColumnIndexOrThrow("month"));
        b.opening = c.getDouble(c.getColumnIndexOrThrow("opening"));
        b.litres = c.getDouble(c.getColumnIndexOrThrow("litres"));
        b.deliveryTotal = c.getDouble(c.getColumnIndexOrThrow("delivery_total"));
        b.paymentsTotal = c.getDouble(c.getColumnIndexOrThrow("payments_total"));
        b.closing = c.getDouble(c.getColumnIndexOrThrow("closing"));
        b.skippedDays = c.getInt(c.getColumnIndexOrThrow("skipped_days"));
        b.deliveryDays = c.getInt(c.getColumnIndexOrThrow("delivery_days"));
        int p = c.getColumnIndex("pdf_path");
        b.pdfPath = p >= 0 ? c.getString(p) : null;
        int g = c.getColumnIndex("generated_at");
        b.generatedAt = g >= 0 ? c.getString(g) : null;
        int cn = c.getColumnIndex("customer_name");
        if (cn >= 0) b.customerName = c.getString(cn);
        int ca = c.getColumnIndex("customer_area");
        if (ca >= 0) b.customerArea = c.getString(ca);
        return b;
    }
}
