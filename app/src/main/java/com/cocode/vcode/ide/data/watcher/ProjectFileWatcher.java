package com.cocode.vcode.ide.data.watcher;

import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors the project root directory and subdirectories for filesystem changes
 * (file creation, deletion, modification, and renames) using native {@link FileObserver}.
 * Batches and debounces events before notifying listeners to avoid UI thrashing.
 */
public class ProjectFileWatcher {

    public interface Callback {
        void onFileSystemChanged();
    }

    private static final int WATCH_MASK = FileObserver.CREATE | FileObserver.DELETE
            | FileObserver.DELETE_SELF | FileObserver.MOVED_FROM | FileObserver.MOVED_TO
            | FileObserver.CLOSE_WRITE | FileObserver.MODIFY;

    private static final Set<String> IGNORED_DIR_NAMES = new HashSet<>(Arrays.asList(
            ".git", ".vcode", "node_modules", "build", ".gradle", ".idea", ".vscode", ".android"
    ));

    private static final int MAX_WATCHED_DIRS = 200;
    private static final long DEBOUNCE_DELAY_MS = 250L;

    private final File projectRoot;
    private final Callback callback;
    private final Handler mainHandler;
    private final Map<String, FileObserver> activeObservers = new ConcurrentHashMap<>();
    private final Runnable debounceRunnable;
    private volatile boolean isWatching = false;

    public ProjectFileWatcher(@NonNull File projectRoot, @NonNull Callback callback) {
        this.projectRoot = projectRoot;
        this.callback = callback;
        this.mainHandler = ExecutorProvider.getInstance().getMainHandler();
        this.debounceRunnable = () -> {
            if (isWatching) {
                callback.onFileSystemChanged();
            }
        };
    }

    public synchronized void startWatching() {
        if (isWatching) return;
        isWatching = true;
        ExecutorProvider.getInstance().runOnIo(() -> {
            if (isWatching) {
                registerObserversRecursive(projectRoot, 0);
            }
        });
    }

    public synchronized void stopWatching() {
        isWatching = false;
        mainHandler.removeCallbacks(debounceRunnable);
        for (FileObserver observer : activeObservers.values()) {
            try {
                observer.stopWatching();
            } catch (Exception ignored) {
            }
        }
        activeObservers.clear();
    }

    public boolean isWatching() {
        return isWatching;
    }

    private void registerObserversRecursive(File dir, int depth) {
        if (!isWatching || dir == null || !dir.exists() || !dir.isDirectory()) return;
        if (depth > 6 || activeObservers.size() >= MAX_WATCHED_DIRS) return;
        if (IGNORED_DIR_NAMES.contains(dir.getName())) return;

        registerSingleDirectory(dir);

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && !IGNORED_DIR_NAMES.contains(child.getName())) {
                    registerObserversRecursive(child, depth + 1);
                }
            }
        }
    }

    private void registerSingleDirectory(File dir) {
        String path = dir.getAbsolutePath();
        if (activeObservers.containsKey(path)) return;

        FileObserver observer = createObserver(dir, path);
        if (observer != null) {
            try {
                observer.startWatching();
                activeObservers.put(path, observer);
            } catch (Exception ignored) {
            }
        }
    }

    private FileObserver createObserver(File dir, String dirPath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new FileObserver(dir, WATCH_MASK) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    handleEvent(event, path, dirPath);
                }
            };
        } else {
            return new PreQFileObserver(dirPath, WATCH_MASK);
        }
    }

    @SuppressWarnings("deprecation")
    private class PreQFileObserver extends FileObserver {
        private final String observedDirPath;

        public PreQFileObserver(String path, int mask) {
            super(path, mask);
            this.observedDirPath = path;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            handleEvent(event, path, observedDirPath);
        }
    }

    private void handleEvent(int event, @Nullable String path, String dirPath) {
        if (!isWatching) return;
        if ((event & WATCH_MASK) == 0) return;

        // Skip ignored directories based on dirPath
        if (dirPath != null) {
            for (String ignored : IGNORED_DIR_NAMES) {
                if (dirPath.endsWith(File.separator + ignored)
                        || dirPath.contains(File.separator + ignored + File.separator)
                        || dirPath.endsWith("/" + ignored)
                        || dirPath.contains("/" + ignored + "/")) {
                    return;
                }
            }
        }

        // Skip ignored directories or hidden temp files
        if (path != null) {
            for (String ignored : IGNORED_DIR_NAMES) {
                if (path.equals(ignored) || path.startsWith(ignored + File.separator) || path.startsWith(ignored + "/")) {
                    return;
                }
            }
            if (path.endsWith(".swp") || path.endsWith(".tmp") || path.startsWith(".~")) {
                return;
            }
        }

        // Handle directory deletion
        if ((event & FileObserver.DELETE_SELF) != 0) {
            FileObserver obs = activeObservers.remove(dirPath);
            if (obs != null) {
                try {
                    obs.stopWatching();
                } catch (Exception ignored) {
                }
            }
        }

        // Handle newly created subdirectory on background IO thread
        if ((event & FileObserver.CREATE) != 0 && path != null) {
            File child = new File(dirPath, path);
            if (child.isDirectory() && !IGNORED_DIR_NAMES.contains(path)) {
                ExecutorProvider.getInstance().runOnIo(() -> registerObserversRecursive(child, 0));
            }
        }

        // Schedule debounced notification on main thread
        mainHandler.removeCallbacks(debounceRunnable);
        mainHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
    }
}
