package com.dairyflow.app.backup;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dairyflow.app.data.db.DatabaseHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reliable backup & restore for the single-file database.
 *
 * Export: SQLite is checkpointed, then the dairyflow.db file is copied byte-for-byte.
 * Import: candidate file is validated (magic header + integrity_check + required tables)
 *         BEFORE the live database is replaced — a corrupt backup never wipes real data.
 * Auto:   keeps the last 5 automatic snapshots in filesDir/backups.
 */
public final class BackupManager {

    public static final String[] REQUIRED_TABLES = {
            "customers", "deliveries", "payments", "purchases", "expenses",
            "adjustments", "milk_rates", "bills", "settings", "holidays"
    };

    private BackupManager() {}

    public static File dbFile(Context ctx) {
        return ctx.getDatabasePath(DatabaseHelper.DB_NAME);
    }

    private static void checkpoint(Context ctx) {
        SQLiteDatabase db = DatabaseHelper.get(ctx).getWritableDatabase();
        try {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close();
        } catch (Exception ignored) { /* rollback journal mode: nothing to fold */ }
        try {
            db.rawQuery("PRAGMA optimize", null).close();
        } catch (Exception ignored) { }
    }

    /** Copy the live database into out. Returns bytes written. */
    public static long exportTo(Context ctx, OutputStream out) throws IOException {
        checkpoint(ctx);
        File src = dbFile(ctx);
        try (FileInputStream in = new FileInputStream(src)) {
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.flush();
            return total;
        }
    }

    /** Export to a fresh file inside dir. */
    public static File exportToFile(Context ctx, File dir, String name) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File out = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            exportTo(ctx, fos);
        }
        return out;
    }

    /** Write the live database to the given URI (SAF). Returns bytes written. */
    public static long exportToUri(Context ctx, android.net.Uri uri) throws IOException {
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("Cannot open destination");
            return exportTo(ctx, out);
        }
    }

    // ---------------- validation ----------------

    public static final class ValidationResult {
        public final boolean ok;
        public final String message;
        public ValidationResult(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    /** Validate a candidate backup file (read-only open, no side effects). */
    public static ValidationResult validate(File f) {
        if (f == null || !f.exists()) return new ValidationResult(false, "File not found.");
        if (f.length() < 512) return new ValidationResult(false, "File is too small to be a DairyFlow backup.");
        // 1) magic header
        byte[] head = new byte[16];
        try (FileInputStream in = new FileInputStream(f)) {
            if (in.read(head) != 16) return new ValidationResult(false, "Unreadable file.");
        } catch (IOException e) {
            return new ValidationResult(false, "Cannot read file: " + e.getMessage());
        }
        String magic = new String(head, 0, 15, java.nio.charset.StandardCharsets.US_ASCII);
        if (!magic.startsWith("SQLite format 3")) {
            return new ValidationResult(false, "Not a SQLite database (bad header).");
        }
        // 2) integrity check + required tables, via a temp copy opened read-only
        SQLiteDatabase test = null;
        try {
            test = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try (Cursor c = test.rawQuery("PRAGMA integrity_check", null)) {
                if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) {
                    return new ValidationResult(false, "Database failed integrity check.");
                }
            }
            java.util.HashSet<String> tables = new java.util.HashSet<>();
            try (Cursor c = test.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)) {
                while (c.moveToNext()) tables.add(c.getString(0).toLowerCase(Locale.US));
            }
            for (String req : REQUIRED_TABLES) {
                if (!tables.contains(req)) {
                    return new ValidationResult(false, "Missing table: " + req);
                }
            }
            return new ValidationResult(true, "Valid DairyFlow backup.");
        } catch (Exception e) {
            return new ValidationResult(false, "Cannot open backup: " + e.getMessage());
        } finally {
            if (test != null) try { test.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * Replace the live database with the validated candidate.
     * Caller must recreate/restart the app afterwards.
     */
    public static void restoreFrom(Context ctx, File candidate) throws IOException {
        ValidationResult v = validate(candidate);
        if (!v.ok) throw new IOException(v.message);
        File live = dbFile(ctx);
        // Close the shared helper if open
        try {
            DatabaseHelper helper = DatabaseHelper.get(ctx);
            helper.getWritableDatabase().close();
            helper.close();
        } catch (Exception ignored) { /* will reopen on next get() */ }
        // Remove stray journals so the restored file is used as-is
        deleteQuiet(new File(live.getPath() + "-wal"));
        deleteQuiet(new File(live.getPath() + "-journal"));
        deleteQuiet(new File(live.getPath() + "-shm"));
        try (FileInputStream in = new FileInputStream(candidate);
             FileOutputStream out = new FileOutputStream(live)) {
            FileChannel inCh = in.getChannel();
            FileChannel outCh = out.getChannel();
            inCh.transferTo(0, inCh.size(), outCh);
            out.getFD().sync();
        }
    }

    /** Copy a stream (e.g. SAF-opened backup) into a local temp file. */
    public static File copyToTemp(Context ctx, InputStream in, String suffix) throws IOException {
        File tmp = new File(ctx.getCacheDir(), "restore_tmp" + suffix);
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }
        return tmp;
    }

    // ---------------- auto backups ----------------

    public static File autoBackupDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "backups");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** True if today's auto backup already exists (yyyyMMdd in name). */
    public static boolean autoBackupDoneToday(Context ctx) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        File dir = autoBackupDir(ctx);
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.getName().contains(today)) return true;
        }
        return false;
    }

    /** Write today's snapshot; prune to the newest 5. Returns the file, or null if already done today. */
    public static File runAutoBackup(Context ctx) throws IOException {
        if (autoBackupDoneToday(ctx)) return null;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = exportToFile(ctx, autoBackupDir(ctx), "dairyflow_auto_" + stamp + ".db");
        pruneAutoBackups(ctx, 5);
        return out;
    }

    public static void pruneAutoBackups(Context ctx, int keep) {
        File[] files = autoBackupDir(ctx).listFiles();
        if (files == null || files.length <= keep) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < files.length; i++) //noinspection ResultOfMethodCallIgnored
            files[i].delete();
    }

    public static File[] listAutoBackups(Context ctx) {
        File[] files = autoBackupDir(ctx).listFiles();
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    public static String autoBackupStatus(Context ctx) {
        File[] files = listAutoBackups(ctx);
        if (files.length == 0) return "Never";
        File newest = files[0];
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date(newest.lastModified()))
                + "  •  " + (newest.length() / 1024) + " KB";
    }

    private static void deleteQuiet(File f) {
        if (f != null && f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
