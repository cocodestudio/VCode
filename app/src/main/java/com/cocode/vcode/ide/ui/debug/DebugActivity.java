package com.cocode.vcode.ide.ui.debug;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ActivityDebugBinding;
import com.cocode.vcode.ide.ui.projects.ProjectsActivity;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Crash log inspection activity that displays uncaught exception stack traces.
 */
public class DebugActivity extends AppCompatActivity {

    public static final String EXTRA_CRASH_LOG = "extra_crash_log";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityDebugBinding binding = ActivityDebugBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UiUtils.applySystemBarInsets(binding.getRoot());

        // Apply custom fonts
        FontManager fontManager = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fontManager.getUiSemiBold(this));
        binding.tvAnUnexpectedErrorOccurred.setTypeface(fontManager.getUiMedium(this));
        binding.tvCrashLog.setTypeface(fontManager.getCodeFont(this));
        binding.btnCopyError.setTypeface(fontManager.getUiMedium(this));
        binding.btnShareError.setTypeface(fontManager.getUiMedium(this));
        binding.btnRestartApp.setTypeface(fontManager.getUiMedium(this));

        String crashLog = getIntent().getStringExtra(EXTRA_CRASH_LOG);
        if (crashLog == null || crashLog.isEmpty()) {
            crashLog = "No stack trace provided.";
        }

        binding.tvCrashLog.setText(crashLog);

        String finalCrashLog = crashLog;
        binding.btnCopyError.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Crash Log", finalCrashLog);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.vcode_crash_log_copied, Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnShareError.setOnClickListener(v -> {
            String deviceInfo = "Android " + android.os.Build.VERSION.RELEASE
                    + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                    + " | " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
            String versionName = "unknown";
            try {
                versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }

            Intent shareIntent = getIntent(versionName, deviceInfo, finalCrashLog);
            startActivity(Intent.createChooser(shareIntent, "Share crash report"));
        });

        binding.btnRestartApp.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProjectsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Runtime.getRuntime().exit(0);
        });
    }

    @NonNull
    private Intent getIntent(String versionName, String deviceInfo, String finalCrashLog) {
        String issueBody = "## Bug Report\n\n"
                + "**App version:** " + versionName + "\n"
                + "**Device:** " + deviceInfo + "\n\n"
                + "### Steps to reproduce\n"
                + "<!-- Describe what you were doing when the crash occurred -->\n\n"
                + "### Crash log\n"
                + "```\n" + finalCrashLog + "\n```\n";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "[VCode] Crash Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, issueBody);
        return shareIntent;
    }
}
