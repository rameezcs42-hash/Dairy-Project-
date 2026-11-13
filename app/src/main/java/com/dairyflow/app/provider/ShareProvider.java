package com.dairyflow.app.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Minimal read-only ContentProvider so DairyFlow can share PDFs / backup files
 * with other apps without any external libraries (framework FileProvider would
 * require androidx.core, which is not used).
 *
 * Exposes ONLY: cacheDir/Invoices, cacheDir/Reports, filesDir/backups, externalDocs/Invoices|Reports
 */
public class ShareProvider extends ContentProvider {

    public static final String AUTHORITY = "com.dairyflow.app.share";

    private static final int FILE = 1;
    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

    public static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .encodedPath(Uri.encode(file.getAbsolutePath(), "/"))
                .build();
    }

    @Override public boolean onCreate() {
        matcher.addURI(AUTHORITY, "*", FILE);
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("no path");
        File f = new File(path).getAbsoluteFile();
        try {
            String canon = f.getCanonicalPath();
            if (!isAllowed(canon)) throw new FileNotFoundException("path not allowed: " + canon);
            f = new File(canon);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath());
        return f;
    }

    private boolean isAllowed(String canon) {
        Context ctx = getContext();
        if (ctx == null) return false;
        String[] prefixes = {
                new File(ctx.getCacheDir(), "Invoices").getAbsolutePath(),
                new File(ctx.getCacheDir(), "Reports").getAbsolutePath(),
                new File(ctx.getFilesDir(), "backups").getAbsolutePath(),
                new File(ctx.getFilesDir(), "Invoices").getAbsolutePath(),
                new File(ctx.getFilesDir(), "Reports").getAbsolutePath(),
        };
        File ext = ctx.getExternalFilesDir(null);
        String[] extPrefixes = ext != null ? new String[]{
                new File(ext, "Invoices").getAbsolutePath(),
                new File(ext, "Reports").getAbsolutePath(),
                new File(ext, "backups").getAbsolutePath(),
        } : new String[0];
        for (String p : prefixes) {
            if (canon.startsWith(p + File.separator) || canon.equals(p)) return true;
        }
        for (String p : extPrefixes) {
            if (canon.startsWith(p + File.separator) || canon.equals(p)) return true;
        }
        return false;
    }

    @Override public String getType(Uri uri) {
        String path = uri.getPath() != null ? uri.getPath() : "";
        if (path.endsWith(".pdf")) return "application/pdf";
        if (path.endsWith(".db")) return "application/x-sqlite3";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read-only");
        File f = resolve(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
}
