package com.dairyflow.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.dairyflow.app.R;
import com.dairyflow.app.ui.CrashLogActivity;

import java.io.File;

/** Brief branded splash (logo + wordmark), then MainActivity. */
public class SplashActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If the last run died with an uncaught exception, show the recorded crash first.
        File crashFile = new File(getFilesDir(), "last_crash.txt");
        if (crashFile.exists()) {
            startActivity(new Intent(this, CrashLogActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_splash);
        FrameLayout root = findViewById(R.id.splash_root);
        ImageView logo = findViewById(R.id.splash_logo);
        if (Build.VERSION.SDK_INT >= 21) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        root.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1100);
    }
}
