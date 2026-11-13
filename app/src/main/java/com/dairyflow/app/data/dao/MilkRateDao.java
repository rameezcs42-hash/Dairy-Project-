package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;
import com.dairyflow.app.data.model.MilkRate;

import java.util.ArrayList;
import java.util.List;

public final class MilkRateDao {
    private final SQLiteDatabase db;

    public MilkRateDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    public long insert(MilkRate r) {
        ContentValues v = new ContentValues();
        v.put("kind", r.kind);
        v.put("milk_type", r.milkType);
        v.put("rate", r.rate);
        v.put("effective_from", r.effectiveFrom);
        return db.insertOrThrow("milk_rates", null, v);
    }

    public void delete(long id) { db.delete("milk_rates", "id=?", new String[]{String.valueOf(id)}); }

    public List<MilkRate> history(String kind, String milkType) {
        List<MilkRate> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT * FROM milk_rates WHERE kind=? AND milk_type=? ORDER BY effective_from DESC, id DESC",
                new String[]{kind, milkType})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    public List<MilkRate> all() {
        List<MilkRate> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM milk_rates ORDER BY kind, milk_type, effective_from DESC", null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    /** Latest rate whose effective_from <= date; null when none. */
    public Double rateOn(String kind, String milkType, String date) {
        try (Cursor c = db.rawQuery(
                "SELECT rate FROM milk_rates WHERE kind=? AND milk_type=? AND effective_from<=?"
                        + " ORDER BY effective_from DESC, id DESC LIMIT 1",
                new String[]{kind, milkType, date})) {
            return c.moveToFirst() ? c.getDouble(0) : null;
        }
    }

    private static MilkRate read(Cursor c) {
        MilkRate r = new MilkRate();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        r.milkType = c.getString(c.getColumnIndexOrThrow("milk_type"));
        r.rate = c.getDouble(c.getColumnIndexOrThrow("rate"));
        r.effectiveFrom = c.getString(c.getColumnIndexOrThrow("effective_from"));
        return r;
    }
}
