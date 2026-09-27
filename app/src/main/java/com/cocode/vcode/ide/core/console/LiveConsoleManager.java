package com.cocode.vcode.ide.core.console;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe singleton repository and LiveData stream for in-editor live console logs.
 */
public final class LiveConsoleManager {

    private static final int MAX_LOG_CAPACITY = 500;
    private static volatile LiveConsoleManager instance;

    private final Object lock = new Object();
    private final List<ConsoleLogEntry> logList = new ArrayList<>();
    private final MutableLiveData<List<ConsoleLogEntry>> logsLiveData = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> errorCountLiveData = new MutableLiveData<>(0);
    private int errorCount = 0;

    private LiveConsoleManager() {
    }

    public static LiveConsoleManager getInstance() {
        if (instance == null) {
            synchronized (LiveConsoleManager.class) {
                if (instance == null) {
                    instance = new LiveConsoleManager();
                }
            }
        }
        return instance;
    }

    /**
     * Appends a log entry to the in-memory buffer and notifies active observers.
     */
    public void addLog(ConsoleLogEntry entry) {
        if (entry == null) return;

        List<ConsoleLogEntry> snapshot;
        int currentErrors;

        synchronized (lock) {
            if (logList.size() >= MAX_LOG_CAPACITY) {
                ConsoleLogEntry removed = logList.remove(0);
                if (removed.getLevel() == ConsoleLogEntry.Level.ERROR && errorCount > 0) {
                    errorCount--;
                }
            }
            logList.add(entry);
            if (entry.getLevel() == ConsoleLogEntry.Level.ERROR) {
                errorCount++;
            }
            snapshot = new ArrayList<>(logList);
            currentErrors = errorCount;
        }

        try {
            ExecutorProvider.getInstance().runOnMain(() -> {
                logsLiveData.setValue(snapshot);
                errorCountLiveData.setValue(currentErrors);
            });
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // Fallback for JVM unit tests where Android Main Looper is not mocked
        }
    }

    /**
     * Clears all recorded console logs and resets error count.
     */
    public void clearLogs() {
        synchronized (lock) {
            logList.clear();
            errorCount = 0;
        }
        try {
            ExecutorProvider.getInstance().runOnMain(() -> {
                logsLiveData.setValue(Collections.emptyList());
                errorCountLiveData.setValue(0);
            });
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // Fallback for JVM unit tests where Android Main Looper is not mocked
        }
    }

    public LiveData<List<ConsoleLogEntry>> getLogs() {
        return logsLiveData;
    }

    public LiveData<Integer> getErrorCount() {
        return errorCountLiveData;
    }

    public List<ConsoleLogEntry> getSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(logList);
        }
    }
}
