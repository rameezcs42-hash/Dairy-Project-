package com.dairyflow.app;

import android.app.Application;

import com.dairyflow.app.backup.BackupManager;
import com.dairyflow.app.data.dao.SettingsDao;

/** Application entry point: default settings + opportunistic daily auto-backup. */
public final class DairyFlowApp extends Application {

    @Override public void onCreate() {
        super.onCreate();
        SettingsDao settings = new SettingsDao(this);
        seedDefault(settings, "shop_name", "My Dairy");
        seedDefault(settings, "shop_tagline", "Pure & Fresh Milk Daily");
        seedDefault(settings, "currency", "Rs");
        seedDefault(settings, "low_stock_litres", "25");
        seedDefault(settings, "default_sale_rate", "120");
        seedDefault(settings, "default_purchase_rate", "95");
        seedDefault(settings, "invoice_note", "");
        seedDefault(settings, "auto_backup", "1");
        seedDefault(settings, "seeded_demo", "0");

        if (settings.getBool("auto_backup", true)) {
            Thread t = new Thread(() -> {
                try {
                    BackupManager.runAutoBackup(DairyFlowApp.this);
                } catch (Exception ignored) { /* auto-backup is best-effort */ }
            }, "auto-backup");
            t.setDaemon(true);
            t.start();
        }
    }

    private void seedDefault(SettingsDao s, String key, String value) {
        if (s.get(key, null) == null) s.set(key, value);
    }
}
