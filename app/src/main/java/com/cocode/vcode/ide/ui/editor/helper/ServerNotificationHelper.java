package com.cocode.vcode.ide.ui.editor.helper;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.utils.ExecutorProvider;

/**
 * Helper to display and manage ongoing status notifications for the embedded live web server.
 */
public class ServerNotificationHelper {

    public static final String CHANNEL_ID = "vcode_channel_server";
    public static final int NOTIFICATION_ID = 2001;
    public static final String ACTION_STOP_SERVER = "com.cocode.vcode.ide.ACTION_STOP_SERVER";

    private static volatile Runnable activeStopAction;

    /**
     * Ensures the notification channel exists on API 26+.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.vcode_notification_channel_server),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(context.getString(R.string.vcode_notification_channel_server_desc));
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Displays an ongoing notification for the running live server with a Stop action.
     */
    public static void showServerNotification(Context context, int port, String projectName, Runnable onStopAction) {
        activeStopAction = onStopAction;
        createNotificationChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent contentIntent = new Intent(context, EditorActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingContentIntent = PendingIntent.getActivity(context, 0, contentIntent, flags);

        Intent stopIntent = new Intent(context, ServerActionReceiver.class);
        stopIntent.setAction(ACTION_STOP_SERVER);
        PendingIntent pendingStopIntent = PendingIntent.getBroadcast(context, 0, stopIntent, flags);

        String displayProjectName = projectName != null ? projectName : "";
        String contentText = context.getString(R.string.vcode_server_running_content, port, displayProjectName);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.vcode_server_running_title))
                .setContentText(contentText)
                .setOngoing(true)
                .setContentIntent(pendingContentIntent)
                .addAction(R.drawable.ic_stop, context.getString(R.string.vcode_server_action_stop), pendingStopIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Cancels the active live server notification.
     */
    public static void cancelServerNotification(Context context) {
        activeStopAction = null;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    /**
     * Handles the stop server broadcast from notification action.
     */
    public static void handleStopServer(Context context) {
        Runnable stopAction = activeStopAction;
        cancelServerNotification(context);
        if (stopAction != null) {
            ExecutorProvider.getInstance().runOnMain(stopAction);
        } else {
            Toast.makeText(context.getApplicationContext(), R.string.vcode_server_stopped, Toast.LENGTH_SHORT).show();
        }
    }
}
