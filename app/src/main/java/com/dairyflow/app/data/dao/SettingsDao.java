package com.dairyflow.app.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;

public final class SettingsDao {
    private final SQLiteDatabase db;

    public SettingsDao(Context ctx) { db = DatabaseHelper.get(ctx).getWritableDatabase(); }

    public String get(String key, String fallback) {
        try (Cursor c = db.rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }

    public void set(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        db.insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean getBool(String key, boolean fallback) {
        String v = get(key, fallback ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    public void setBool(String key, boolean value) { set(key, value ? "1" : "0"); }

    public double getDouble(String key, double fallback) {
        try { return Double.parseDouble(get(key, String.valueOf(fallback))); }
        catch (NumberFormatException e) { return fallback; }
    }
}
