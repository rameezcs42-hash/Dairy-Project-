package com.dairyflow.app.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dairyflow.app.R;
import com.dairyflow.app.backup.BackupManager;
import com.dairyflow.app.data.dao.SettingsDao;
import com.dairyflow.app.util.SampleData;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shop settings + backup/restore + demo data.
 * Backup uses SAF (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT) so users can
 * save the .dairyflow file to Downloads, Drive, or WhatsApp.
 * Restore validates magic header + PRAGMA integrity_check + required tables
 * before swapping the live database.
 */
public class SettingsActivity extends Activity {

    private static final int REQ_EXPORT = 4101;
    private static final int REQ_RESTORE = 4102;

    private SettingsDao settings;
    private EditText inputShopName, inputOwnerName, inputShopPhone, inputShopAddress;
    private TextView backupStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        settings = new SettingsDao(this);

        inputShopName = findViewById(R.id.input_shop_name);
        inputOwnerName = findViewById(R.id.input_owner_name);
        inputShopPhone = findViewById(R.id.input_shop_phone);
        inputShopAddress = findViewById(R.id.input_shop_address);
        backupStatus = findViewById(R.id.backup_status);

        inputShopName.setText(settings.get("shop_name", ""));
        inputOwnerName.setText(settings.get("owner_name", ""));
        inputShopPhone.setText(settings.get("shop_phone", ""));
        inputShopAddress.setText(settings.get("shop_address", ""));
        backupStatus.setText(BackupManager.autoBackupStatus(this));

        findViewById(R.id.btn_save_settings).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btn_export_backup).setOnClickListener(v -> exportBackup());
        findViewById(R.id.btn_restore_backup).setOnClickListener(v -> pickRestore());
        findViewById(R.id.btn_load_demo).setOnClickListener(v -> confirmLoadDemo());
        findViewById(R.id.btn_clear_demo).setOnClickListener(v -> confirmClearAll());
    }

    private void saveSettings() {
        String name = inputShopName.getText().toString().trim();
        if (name.isEmpty()) {
            inputShopName.setError("Shop name is required");
            return;
        }
        settings.set("shop_name", name);
        settings.set("owner_name", inputOwnerName.getText().toString().trim());
        settings.set("shop_phone", inputShopPhone.getText().toString().trim());
        settings.set("shop_address", inputShopAddress.getText().toString().trim());
        Toast.makeText(this, "Shop details saved — they will print on invoices", Toast.LENGTH_SHORT).show();
    }

    // ---------- backup / restore ----------

    private void exportBackup() {
        String suggested = "dairyflow-backup-" + com.dairyflow.app.util.Format.todayIso() + ".dairyflow";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, suggested);
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (android.content.ActivityNotFoundException e) {
            // Fallback: save inside app storage and share via the share provider
            try {
                File f = BackupManager.exportToFile(this, new File(getCacheDir(), "backups"), suggested);
                shareFile(f, "DairyFlow backup", "application/octet-stream");
            } catch (Exception ex) {
                Toast.makeText(this, "Backup failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pickRestore() {
        new AlertDialog.Builder(this)
                .setTitle("Restore backup?")
                .setMessage("Your current data will be REPLACED by the backup file. This cannot be undone. A safety copy of the current database is kept in the app's backups folder first.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Choose file…", (d, which) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    try {
                        startActivityForResult(i, REQ_RESTORE);
                    } catch (android.content.ActivityNotFoundException e) {
                        Toast.makeText(this, "No file manager available to pick a backup", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void shareFile(File file, String title, String mime) {
        android.net.Uri uri = com.dairyflow.app.provider.ShareProvider.uriFor(file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, title));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            new Thread(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    long bytes = BackupManager.exportTo(this, out);
                    runOnUiThread(() -> Toast.makeText(this, "Backup exported (" + (bytes / 1024) + " KB)", Toast.LENGTH_LONG).show());
                    runOnUiThread(() -> backupStatus.setText(BackupManager.autoBackupStatus(this)));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }, "backup-export").start();
        } else if (requestCode == REQ_RESTORE) {
            new Thread(() -> {
                File temp = null;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    temp = BackupManager.copyToTemp(this, in, ".dairyflow");
                    BackupManager.ValidationResult result = BackupManager.validate(temp);
                    if (!result.ok) {
                        final String msg = result.message;
                        runOnUiThread(() -> Toast.makeText(this, "Invalid backup: " + msg, Toast.LENGTH_LONG).show());
                        return;
                    }
                    // Safety copy of the live DB before replacing it
                    try {
                        BackupManager.runAutoBackup(this);
                    } catch (Exception ignored) { }
                    BackupManager.restoreFrom(this, temp);
                    final BackupManager.ValidationResult finalResult = result;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Backup restored successfully", Toast.LENGTH_LONG).show();
                        backupStatus.setText(BackupManager.autoBackupStatus(this));
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                } finally {
                    if (temp != null) temp.delete();
                }
            }, "backup-restore").start();
        }
    }

    // ---------- demo data ----------

    private void confirmLoadDemo() {
        new AlertDialog.Builder(this)
                .setTitle("Load demo data?")
                .setMessage("This inserts 8 sample customers with 60 days of deliveries, purchases, payments and expenses, plus realistic rate history (95→100 purchase, 130→135 sale). Existing data is kept.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Load", (d, which) -> {
                    new Thread(() -> {
                        try {
                            SampleData.seed(this);
                            runOnUiThread(() -> Toast.makeText(this, "Demo data loaded", Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "Demo load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }, "seed-demo").start();
                })
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Delete ALL data?")
                .setMessage("Customers, deliveries, payments, purchases, expenses, rates — everything will be permanently deleted. Shop settings are kept. This cannot be undone!")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Delete everything", (d, which) -> {
                    new Thread(() -> {
                        try {
                            SampleData.clearAll(this);
                            runOnUiThread(() -> Toast.makeText(this, "All data cleared", Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "Clear failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }, "clear-all").start();
                })
                .show();
    }
}
