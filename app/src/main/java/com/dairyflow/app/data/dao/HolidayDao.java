package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.Holiday;

import java.util.ArrayList;
import java.util.List;

public final class HolidayDao {
    private final SQLiteDatabase db;

    public HolidayDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    public void add(long customerId, String date, String note) {
        ContentValues v = new ContentValues();
        v.put("customer_id", customerId);
        v.put("date", date);
        v.put("note", note);
        db.insertWithOnConflict("holidays", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void remove(long id) { db.delete("holidays", "id=?", new String[]{String.valueOf(id)}); }

    public void remove(long customerId, String date) {
        db.delete("holidays", "customer_id=? AND date=?", new String[]{String.valueOf(customerId), date});
    }

    public boolean isHoliday(long customerId, String date) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM holidays WHERE customer_id=? AND date=?",
                new String[]{String.valueOf(customerId), date})) {
            return c.moveToFirst();
        }
    }

    public List<Holiday> forCustomer(long customerId) {
        List<Holiday> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM holidays WHERE customer_id=? ORDER BY date DESC",
                new String[]{String.valueOf(customerId)})) {
            while (c.moveToNext()) {
                Holiday h = new Holiday();
                h.id = c.getLong(c.getColumnIndexOrThrow("id"));
                h.customerId = customerId;
                h.date = c.getString(c.getColumnIndexOrThrow("date"));
                h.note = c.getString(c.getColumnIndexOrThrow("note"));
                out.add(h);
            }
        }
        return out;
    }

    public List<Long> customersOffOn(String date) {
        List<Long> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT customer_id FROM holidays WHERE date=?", new String[]{date})) {
            while (c.moveToNext()) out.add(c.getLong(0));
        }
        return out;
    }
}
