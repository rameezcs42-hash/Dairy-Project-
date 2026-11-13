package com.dairyflow.app.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * DairyFlow single-file database (dairyflow.db).
 * Journal mode kept as default (rollback journal) so a plain file copy
 * is always a consistent backup. Foreign keys enforced.
 */
public final class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "dairyflow.db";
    public static final int DB_VERSION = 1;

    private volatile static DatabaseHelper instance;

    public static DatabaseHelper get(Context ctx) {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) instance = new DatabaseHelper(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "phone TEXT,"
                + "shop_name TEXT,"
                + "address TEXT,"
                + "area TEXT,"
                + "milk_type TEXT NOT NULL DEFAULT 'buffalo',"
                + "default_rate REAL NOT NULL DEFAULT 0,"
                + "opening_balance REAL NOT NULL DEFAULT 0,"
                + "morning_qty REAL NOT NULL DEFAULT 0,"
                + "evening_qty REAL NOT NULL DEFAULT 0,"
                + "weekly_off INTEGER NOT NULL DEFAULT 0,"
                + "session_pref TEXT NOT NULL DEFAULT 'both',"
                + "note TEXT,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_customers_area ON customers(area)");
        db.execSQL("CREATE INDEX idx_customers_active ON customers(active)");

        db.execSQL("CREATE TABLE deliveries ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,"
                + "date TEXT NOT NULL,"
                + "session TEXT NOT NULL,"
                + "litres REAL NOT NULL DEFAULT 0,"
                + "rate REAL NOT NULL DEFAULT 0,"
                + "amount REAL NOT NULL DEFAULT 0,"
                + "skipped INTEGER NOT NULL DEFAULT 0,"
                + "note TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(customer_id, date, session))");
        db.execSQL("CREATE INDEX idx_deliveries_date ON deliveries(date)");
        db.execSQL("CREATE INDEX idx_deliveries_customer ON deliveries(customer_id, date)");

        db.execSQL("CREATE TABLE payments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,"
                + "date TEXT NOT NULL,"
                + "amount REAL NOT NULL,"
                + "method TEXT NOT NULL DEFAULT 'cash',"
                + "note TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_payments_customer ON payments(customer_id, date)");
        db.execSQL("CREATE INDEX idx_payments_date ON payments(date)");

        db.execSQL("CREATE TABLE purchases ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT NOT NULL,"
                + "supplier TEXT,"
                + "milk_type TEXT NOT NULL DEFAULT 'buffalo',"
                + "litres REAL NOT NULL,"
                + "rate REAL NOT NULL,"
                + "amount REAL NOT NULL,"
                + "fat REAL,"
                + "note TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_purchases_date ON purchases(date)");

        db.execSQL("CREATE TABLE expenses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT NOT NULL,"
                + "category TEXT NOT NULL DEFAULT 'misc',"
                + "amount REAL NOT NULL,"
                + "note TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_expenses_date ON expenses(date)");

        db.execSQL("CREATE TABLE adjustments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT NOT NULL,"
                + "litres REAL NOT NULL,"
                + "reason TEXT NOT NULL,"
                + "note TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE milk_rates ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kind TEXT NOT NULL,"
                + "milk_type TEXT NOT NULL DEFAULT 'buffalo',"
                + "rate REAL NOT NULL,"
                + "effective_from TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_milk_rates ON milk_rates(kind, milk_type, effective_from)");

        db.execSQL("CREATE TABLE holidays ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,"
                + "date TEXT NOT NULL,"
                + "note TEXT,"
                + "UNIQUE(customer_id, date))");

        db.execSQL("CREATE TABLE bills ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,"
                + "month TEXT NOT NULL,"
                + "opening REAL NOT NULL DEFAULT 0,"
                + "litres REAL NOT NULL DEFAULT 0,"
                + "delivery_total REAL NOT NULL DEFAULT 0,"
                + "payments_total REAL NOT NULL DEFAULT 0,"
                + "closing REAL NOT NULL DEFAULT 0,"
                + "skipped_days INTEGER NOT NULL DEFAULT 0,"
                + "delivery_days INTEGER NOT NULL DEFAULT 0,"
                + "pdf_path TEXT,"
                + "generated_at TEXT,"
                + "UNIQUE(customer_id, month))");

        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 is the first schema; future migrations go here.
    }

    @Override public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Never destroy data on downgrade.
    }
}
