package com.cocode.vcode.ide.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.R;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages background file operations (such as copying or moving large files/directories),
 * publishing live progress to {@link LiveData} and system notifications with cancellation support.
 */
public class FileOperationManager {

    public static final String ACTION_CANCEL_OPERATION = "com.cocode.vcode.ide.ACTION_CANCEL_FILE_OP";
    private static final int NOTIFICATION_ID = 4040;
    private static final String CHANNEL_ID = "vcode_file_ops";
    private static FileOperationManager instance;
    private final Context context;
    private final NotificationManager notificationManager;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final MutableLiveData<ProgressState> progressLiveData = new MutableLiveData<>(new ProgressState(false, 0, 0));
    private BroadcastReceiver cancelReceiver;
    private long lastNotificationTime = 0;

    private FileOperationManager(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    public static synchronized FileOperationManager getInstance(Context context) {
        if (instance == null) {
            instance = new FileOperationManager(context);
        }
        return instance;
    }

    public LiveData<ProgressState> getProgressLiveData() {
        return progressLiveData;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "File Operations",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void startOperation(String title) {
        isCancelled.set(false);
        registerCancelReceiver();

        progressLiveData.postValue(new ProgressState(true, 0, 100));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText("Calculating...")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .addAction(getCancelAction());

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void updateProgress(String title, String text, int max, int progress) {
        if (isCancelled.get()) return;

        progressLiveData.postValue(new ProgressState(true, progress, max));

        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < 300) {
            return; // Throttle notification updates
        }
        lastNotificationTime = now;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(max, progress, false)
                .setOngoing(true)
                .addAction(getCancelAction())
                .setOnlyAlertOnce(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void finishOperation(String title, String resultText, boolean success) {
        unregisterCancelReceiver();

        progressLiveData.postValue(new ProgressState(false, 0, 0));

        if (isCancelled.get()) {
            resultText = "Operation cancelled";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(resultText)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false);

        // Cancel the ongoing notification to bypass Android's rapid-update rate limiting on the same ID
        notificationManager.cancel(NOTIFICATION_ID);
        // Reset throttle
        lastNotificationTime = 0;
        // Notify completion with a separate ID
        notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
    }

    public AtomicBoolean getCancelToken() {
        return isCancelled;
    }

    private NotificationCompat.Action getCancelAction() {
        Intent cancelIntent = new Intent(ACTION_CANCEL_OPERATION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, cancelIntent, flags);
        return new NotificationCompat.Action(0, "Cancel", pendingIntent);
    }

    private void registerCancelReceiver() {
        if (cancelReceiver != null) return;
        cancelReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_CANCEL_OPERATION.equals(intent.getAction())) {
                    isCancelled.set(true);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_CANCEL_OPERATION);
        ContextCompat.registerReceiver(context, cancelReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterCancelReceiver() {
        if (cancelReceiver != null) {
            try {
                context.unregisterReceiver(cancelReceiver);
            } catch (Exception ignored) {
            }
            cancelReceiver = null;
        }
    }

    public static class ProgressState {
        public final boolean isActive;
        public final int progress;
        public final int max;

        public ProgressState(boolean isActive, int progress, int max) {
            this.isActive = isActive;
            this.progress = progress;
            this.max = max;
        }
    }
}
