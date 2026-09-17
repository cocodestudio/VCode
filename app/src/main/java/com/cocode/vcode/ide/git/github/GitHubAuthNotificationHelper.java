package com.cocode.vcode.ide.git.github;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;

/**
 * Helper to display and manage notifications for the GitHub OAuth device flow authorization.
 */
public class GitHubAuthNotificationHelper {

    public static final String CHANNEL_ID = "vcode_channel_github_auth";
    public static final int NOTIFICATION_ID = 2002;
    public static final String ACTION_COPY_DEVICE_CODE = "com.cocode.vcode.ide.ACTION_COPY_DEVICE_CODE";
    public static final String EXTRA_DEVICE_CODE = "extra_device_code";

    /**
     * Ensures the GitHub Auth notification channel is created.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.vcode_notification_channel_github_auth),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(context.getString(R.string.vcode_notification_channel_github_auth_desc));
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Displays an ongoing notification containing the GitHub device code with Copy and Open GitHub actions.
     */
    public static void showDeviceCodeNotification(Context context, String userCode, String verificationUri) {
        createNotificationChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent openAppIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pendingContentIntent = null;
        if (openAppIntent != null) {
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pendingContentIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags);
        }

        Intent copyIntent = new Intent(context, GitHubAuthActionReceiver.class);
        copyIntent.setAction(ACTION_COPY_DEVICE_CODE);
        copyIntent.putExtra(EXTRA_DEVICE_CODE, userCode);
        PendingIntent pendingCopyIntent = PendingIntent.getBroadcast(context, 0, copyIntent, flags);

        PendingIntent pendingBrowserIntent = null;
        if (verificationUri != null && !verificationUri.isEmpty()) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri));
            pendingBrowserIntent = PendingIntent.getActivity(context, 1, browserIntent, flags);
        }

        String title = context.getString(R.string.vcode_github_auth_notification_title, userCode);
        String contentText = context.getString(R.string.vcode_github_auth_notification_content);
        String bigText = context.getString(R.string.vcode_github_auth_notification_big_text, userCode);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(R.drawable.ic_copy, context.getString(R.string.vcode_github_action_copy_code), pendingCopyIntent);

        if (pendingContentIntent != null) {
            builder.setContentIntent(pendingContentIntent);
        }

        if (pendingBrowserIntent != null) {
            builder.addAction(R.drawable.ic_github, context.getString(R.string.vcode_github_action_open_page), pendingBrowserIntent);
        }

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Displays a completion notification when GitHub authorization succeeds.
     */
    public static void showSuccessNotification(Context context, String username) {
        createNotificationChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent openAppIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pendingContentIntent = null;
        if (openAppIntent != null) {
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pendingContentIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags);
        }

        String title = context.getString(R.string.vcode_github_auth_success_title);
        String content = context.getString(R.string.vcode_github_auth_success_content, username != null ? username : "");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (pendingContentIntent != null) {
            builder.setContentIntent(pendingContentIntent);
        }

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Displays a status notification when GitHub authorization expires, is denied, or fails.
     */
    public static void showStatusNotification(Context context, String title, String message) {
        createNotificationChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Cancels the GitHub Auth notification.
     */
    public static void cancelNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }
}
