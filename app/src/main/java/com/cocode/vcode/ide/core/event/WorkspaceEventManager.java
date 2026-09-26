package com.cocode.vcode.ide.core.event;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decoupled event bus for notifying open editors and workspace components
 * about external file and repository modifications (e.g. Git pull, merge, revert,
 * checkout, reset, stash, or external file sync).
 */
public class WorkspaceEventManager {

    public interface WorkspaceChangeListener {
        void onWorkspaceFilesChanged(File projectRoot);
    }

    private static final WorkspaceEventManager INSTANCE = new WorkspaceEventManager();

    private final List<WorkspaceChangeListener> listeners = new CopyOnWriteArrayList<>();

    private WorkspaceEventManager() {
    }

    public static WorkspaceEventManager getInstance() {
        return INSTANCE;
    }

    public void addListener(WorkspaceChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(WorkspaceChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Broadcasts a workspace file change event across the application.
     *
     * @param projectRoot The root directory of the affected workspace/repository.
     */
    public void notifyWorkspaceFilesChanged(File projectRoot) {
        for (WorkspaceChangeListener listener : listeners) {
            try {
                listener.onWorkspaceFilesChanged(projectRoot);
            } catch (Throwable ignored) {
            }
        }
    }
}
