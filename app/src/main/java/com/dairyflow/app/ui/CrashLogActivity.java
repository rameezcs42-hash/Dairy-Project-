package com.dairyflow.app.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.dairyflow.app.MainActivity;
import com.dairyflow.app.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Post-mortem viewer for the crash recorded by DairyFlowApp's uncaught-exception handler.
 * The process is killed after a crash; on the next launch SplashActivity detects
 * filesDir/last_crash.txt and routes here so the user can screenshot/copy the exact error.
 * Deliberately touches nothing (no DB, no repositories) so it cannot crash itself.
 */
public class CrashLogActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        TextView versionView = findViewById(R.id.crash_version);
        TextView traceView = findViewById(R.id.crash_trace);
        Button copyBtn = findViewById(R.id.crash_copy);
        Button continueBtn = findViewById(R.id.crash_continue);
        Button closeBtn = findViewById(R.id.crash_close);

        String versionName = "?";
        int versionCode = 0;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (Exception ignored) {}
        versionView.setText("DairyFlow " + versionName + " (build " + versionCode + ")");

        File crashFile = new File(getFilesDir(), "last_crash.txt");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(crashFile))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Exception e) {
            sb.append("(could not read crash log: ").append(e).append(')');
        }
        String trace = sb.toString().trim();
        traceView.setText(trace.isEmpty() ? "(empty crash log)" : trace);

        copyBtn.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("dairyflow-crash", trace));
                copyBtn.setText("Copied ✓");
            } catch (Throwable ignored) { /* clipboard unavailable */ }
        });

        continueBtn.setOnClickListener(v -> {
            try { // user has (hopefully) captured it; clear marker and try the app again
                //noinspection ResultOfMethodCallIgnored
                crashFile.delete();
            } catch (Throwable ignored) {}
            startActivity(new Intent(CrashLogActivity.this, MainActivity.class));
            finish();
        });

        closeBtn.setOnClickListener(v -> {
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}
