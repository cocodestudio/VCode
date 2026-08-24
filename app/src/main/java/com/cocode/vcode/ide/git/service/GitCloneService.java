package com.cocode.vcode.ide.git.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.ui.projects.ProjectsActivity;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Foreground service for running background Git clone operations with notification progress updates.
 */
public class GitCloneService extends Service {

    public static final String ACTION_START_CLONE = "com.cocode.vcode.ide.action.START_CLONE";
    public static final String EXTRA_REPO_URL = "extra_repo_url";
    public static final String EXTRA_PROJECT_NAME = "extra_project_name";
    public static final String EXTRA_TARGET_DIR = "extra_target_dir";
    public static final String EXTRA_GIT_USER = "extra_git_user";
    public static final String EXTRA_GIT_TOKEN = "extra_git_token";
    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private static final String CHANNEL_ID = "git_clone_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static java.lang.ref.WeakReference<CloneListener> sListenerRef;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private static CloneListener getListener() {
        return sListenerRef != null ? sListenerRef.get() : null;
    }

    public static void setListener(CloneListener listener) {
        sListenerRef = new java.lang.ref.WeakReference<>(listener);
    }

    public static String parseGitError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();

        if (msg.contains("not authorized") || msg.contains("Auth fail") || msg.contains("Authentication is required")) {
            return "Authentication Failed: Invalid token or repository is private.";
        }
        if (msg.contains("not found") || msg.contains("cannot open git-upload-pack")) {
            return "Repository not found. Check the URL.";
        }
        if (msg.contains("already exists")) {
            return "Destination folder already exists.";
        }
        if (msg.contains("Unknown I/O disruption")) {
            return "Filesystem Error: Could not write project files.";
        }
        if (msg.contains("Connection timed out") || msg.contains("UnknownHostException")) {
            return "Network Error: Could not connect to GitHub.";
        }

        // Strip out noisy Java class names like org.eclipse.jgit.api.errors.TransportException:
        msg = msg.replaceAll("^[a-zA-Z0-9_.]+(Exception|Error):\\s*", "");

        // Limit length
        if (msg.length() > 80) {
            msg = msg.substring(0, 77) + "...";
        }
        return msg;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_CLONE.equals(intent.getAction())) {
            String repoUrl = intent.getStringExtra(EXTRA_REPO_URL);
            String projectName = intent.getStringExtra(EXTRA_PROJECT_NAME);
            String targetDir = intent.getStringExtra(EXTRA_TARGET_DIR);
            String gitUser = intent.getStringExtra(EXTRA_GIT_USER);
            String gitToken = intent.getStringExtra(EXTRA_GIT_TOKEN);
            String projectId = intent.getStringExtra(EXTRA_PROJECT_ID);

            if (repoUrl != null && targetDir != null) {
                startForegroundNotification();
                performClone(repoUrl, projectName, new File(targetDir), gitUser, gitToken, projectId);
            } else {
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Git Clone Operations",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows progress for background repository cloning");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startForegroundNotification() {
        Intent notificationIntent = new Intent(this, ProjectsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cloning Repository")
                .setContentText("Initializing...")
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateNotification(String task, int done, int total) {
        int percentage;
        if (total > 0) {
            percentage = (int) (((float) done / total) * 100);
            notificationBuilder.setProgress(100, percentage, false);
            notificationBuilder.setContentText(task + " (" + percentage + "%)");
        } else {
            notificationBuilder.setProgress(0, 0, true);
            notificationBuilder.setContentText(task);
        }
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void showCompletionNotification(boolean success, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(success ? "Clone Successful" : "Clone Failed")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false);

        notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }

        Intent completeIntent = new Intent("com.cocode.vcode.ide.ACTION_CLONE_COMPLETE");
        completeIntent.setPackage(getPackageName());
        sendBroadcast(completeIntent);

        if (com.cocode.vcode.ide.ui.projects.ProjectsViewModel.onCloneCompleteListener != null) {
            com.cocode.vcode.ide.ui.projects.ProjectsViewModel.onCloneCompleteListener.run();
        }

        stopSelf();
    }

    private void performClone(String repoUrl, String projectName, File targetProjectDirectory, String gitUser, String gitToken, String projectId) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            var result = GitRepository.cloneRepo(this, repoUrl, targetProjectDirectory, gitUser, gitToken, new GitRepository.CloneProgressCallback() {
                @Override
                public void onProgress(String task, int done, int total) {
                    int percentage = total > 0 ? (int) (((float) done / total) * 100) : 0;
                    updateNotification(task, done, total);
                    CloneListener listener = getListener();
                    if (listener != null) {
                        listener.onProgress(task, done, total, percentage);
                    }
                }

                @Override
                public void onUpdate(int completed) {
                    CloneListener listener = getListener();
                    if (listener != null) {
                        listener.onUpdate(completed);
                    }
                }

                @Override
                public void onTaskDone() {
                    // Ignored for service
                }
            });

            if (result.isSuccess()) {
                try {
                    // Assemble the project metadata layer post-clone
                    long timestamp = System.currentTimeMillis();
                    JSONObject metadata = new JSONObject();
                    metadata.put("id", projectId);
                    metadata.put("name", projectName);
                    metadata.put("createdAt", timestamp);
                    metadata.put("lastModifiedAt", timestamp);

                    // Auto-detect the primary entry point file
                    String mainFile = getMainFile(targetProjectDirectory);
                    metadata.put("mainFile", mainFile);
                    metadata.put("fileCount", FileUtils.countFilesInDir(targetProjectDirectory));

                    // Persist metadata to disk
                    File metaFile = com.cocode.vcode.ide.data.repository.ProjectRepository.getProjectMetaFile(targetProjectDirectory);
                    if (metaFile.getParentFile() != null) {
                        metaFile.getParentFile().mkdirs();
                    }
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metaFile), StandardCharsets.UTF_8))) {
                        writer.write(metadata.toString(2));
                    }

                    // Cache the remote URL for future sync operations
                    getSharedPreferences("vcode_git_remote_credentials", Context.MODE_PRIVATE)
                            .edit()
                            .putString(targetProjectDirectory.getAbsolutePath() + "_url", repoUrl)
                            .apply();

                    CloneListener listener = getListener();
                    if (listener != null) listener.onSuccess();
                    showCompletionNotification(true, projectName + " cloned successfully.");
                } catch (Exception e) {
                    android.util.Log.e("VCode", "Error during clone post-processing", e);
                    String traceMessage = parseGitError(e);
                    FileUtils.deleteRecursive(targetProjectDirectory);
                    CloneListener listener = getListener();
                    if (listener != null) listener.onFailure(traceMessage);
                    showCompletionNotification(false, traceMessage);
                }
            } else {
                String errMsg = result.getMessage() != null ? parseGitError(new Exception(result.getMessage())) : "Unknown critical failure.";
                FileUtils.deleteRecursive(targetProjectDirectory);
                CloneListener listener = getListener();
                if (listener != null) listener.onFailure(errMsg);
                showCompletionNotification(false, errMsg);
            }
        });
    }

    private String getMainFile(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return "";
        File[] files = dir.listFiles();
        if (files == null) return "";

        String[] priorities = {"index.html", "main.js", "app.js", "index.js", "package.json", "README.md"};
        for (String target : priorities) {
            for (File f : files) {
                if (f.getName().equalsIgnoreCase(target)) {
                    return f.getName();
                }
            }
        }
        for (File f : files) {
            if (f.isFile() && !f.isHidden()) return f.getName();
        }
        return "";
    }

    public interface CloneListener {
        void onProgress(String task, int done, int total, int percentage);

        void onUpdate(int completed);

        void onSuccess();

        void onFailure(String error);
    }
}
