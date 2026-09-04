package com.cocode.vcode.ide.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread execution provider managing background thread pools and main thread dispatchers for the IDE.
 * Segregates sequential disk I/O operations from parallel CPU-intensive tasks.
 */
public class ExecutorProvider {

    private static volatile ExecutorProvider instance;

    private final ExecutorService ioExecutor;
    private final ExecutorService cpuExecutor;
    private final ExecutorService diagnosticExecutor;
    private final Handler mainHandler;

    private ExecutorProvider() {
        // Single-threaded executor guarantees sequential, deterministic file operations
        ioExecutor = Executors.newSingleThreadExecutor();
        cpuExecutor = Executors.newFixedThreadPool(2);
        // Dedicated bounded worker pool for CPU-bound diagnostic/linting work.
        // Isolated from ioExecutor so diagnostics are never queued behind auto-saves.
        // Threads run with lower priority so UI typing is never starved on low-end devices.
        int diagThreads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        diagnosticExecutor = Executors.newFixedThreadPool(diagThreads, r -> {
            Thread t = new Thread(r, "vcode-diagnostic-worker");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.setDaemon(true);
            return t;
        });
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Returns the singleton instance of ExecutorProvider.
     */
    public static ExecutorProvider getInstance() {
        if (instance == null) {
            synchronized (ExecutorProvider.class) {
                if (instance == null) {
                    instance = new ExecutorProvider();
                }
            }
        }
        return instance;
    }

    /**
     * Executes a task on the single-threaded I/O executor.
     */
    public void runOnIo(Runnable r) {
        if (r != null) ioExecutor.execute(r);
    }

    /**
     * Executes a task on the dedicated single-threaded diagnostic executor.
     * Use this for CPU-bound linting/diagnostic work to keep it isolated from
     * disk I/O operations on {@link #ioExecutor}.
     */
    public void runOnDiagnostic(Runnable r) {
        if (r != null) diagnosticExecutor.execute(r);
    }

    /**
     * Executes a computational task on the multi-threaded CPU executor pool.
     */
    public void runOnCpu(Runnable r) {
        if (r != null) cpuExecutor.execute(r);
    }

    /**
     * Dispatches a runnable to the Android main (UI) thread.
     */
    public void runOnMain(Runnable r) {
        if (r != null) mainHandler.post(r);
    }

    public Handler getMainHandler() {
        return mainHandler;
    }

    /**
     * Shuts down all background thread executors.
     */
    public void shutdown() {
        ioExecutor.shutdown();
        cpuExecutor.shutdown();
        diagnosticExecutor.shutdown();
    }
}