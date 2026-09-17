package com.cocode.vcode.ide.git.github;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.cocode.vcode.ide.R;

/**
 * BroadcastReceiver triggered by the "Copy Code" action in the GitHub authorization notification.
 */
public class GitHubAuthActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !GitHubAuthNotificationHelper.ACTION_COPY_DEVICE_CODE.equals(intent.getAction())) {
            return;
        }

        String deviceCode = intent.getStringExtra(GitHubAuthNotificationHelper.EXTRA_DEVICE_CODE);
        if (deviceCode != null && !deviceCode.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("GitHub Device Code", deviceCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, R.string.vcode_github_code_copied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
