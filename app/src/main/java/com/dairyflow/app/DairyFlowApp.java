package com.dairyflow.app;

import android.app.Application;

import com.dairyflow.app.backup.BackupManager;
import com.dairyflow.app.data.dao.SettingsDao;

/** Application entry point: default settings + opportunistic daily auto-backup. */
public final class DairyFlowApp extends Application {

    @Override public void onCreate() {
        super.onCreate();
        installCrashRecorder();
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

    /**
     * Record any uncaught crash (with version + stack trace) to filesDir/last_crash.txt.
     * SplashActivity detects the file on next launch and routes to CrashLogActivity,
     * so even a foran-crash shows the exact error on screen — no logcat required.
     */
    private void installCrashRecorder() {
        final String fileName = "last_crash.txt";
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String versionName = "?";
                int versionCode = 0;
                try {
                    android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                    versionName = pi.versionName;
                    versionCode = pi.versionCode;
                } catch (Throwable ignored) { /* keep placeholders */ }
                java.io.StringWriter sw = new java.io.StringWriter();
                throwable.printStackTrace(new java.io.PrintWriter(sw));
                StringBuilder sb = new StringBuilder();
                sb.append("DairyFlow ").append(versionName).append(" (build ").append(versionCode).append(")\n");
                sb.append("Thread: ").append(thread != null ? thread.getName() : "?").append("\n");
                sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                        .append(" · Android ").append(android.os.Build.VERSION.RELEASE)
                        .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");
                sb.append(sw.toString());
                java.io.File out = new java.io.File(getFilesDir(), fileName);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                fos.write(sb.toString().getBytes("UTF-8"));
                fos.close();
            } catch (Throwable ignored) {
                // If even recording fails, fall through to just dying — never mask the original crash.
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
        // 'previous' intentionally unused: default behavior after a recorded crash is process death.
        if (previous == null) { /* nothing extra */ }
    }

    private void seedDefault(SettingsDao s, String key, String value) {
        if (s.get(key, null) == null) s.set(key, value);
    }
}
