package com.cocode.vcode.ide.core.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Base event representing lifecycle changes or dependency mutations in the workspace project.
 */
public abstract class ProjectEvent implements IdeEvent {

    private final File projectRoot;
    private final long timestamp;

    public ProjectEvent(@NonNull File projectRoot) {
        this.projectRoot = projectRoot;
        this.timestamp = System.currentTimeMillis();
    }

    @NonNull
    public File getProjectRoot() {
        return projectRoot;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Dispatched when a workspace project is opened or fully loaded.
     */
    public static class ProjectLoadedEvent extends ProjectEvent {
        private final String projectName;

        public ProjectLoadedEvent(@NonNull File projectRoot, @Nullable String projectName) {
            super(projectRoot);
            this.projectName = projectName;
        }

        @Nullable
        public String getProjectName() {
            return projectName;
        }
    }

    /**
     * Dispatched when an active workspace project is closed.
     */
    public static class ProjectClosedEvent extends ProjectEvent {
        public ProjectClosedEvent(@NonNull File projectRoot) {
            super(projectRoot);
        }
    }

    /**
     * Dispatched when dependencies in package.json or node_modules are added, removed, or updated.
     */
    public static class DependencyChangedEvent extends ProjectEvent {

        public enum Action {
            ADDED,
            REMOVED,
            UPDATED
        }

        private final String packageName;
        private final Action action;

        public DependencyChangedEvent(@NonNull File projectRoot, @NonNull String packageName, @NonNull Action action) {
            super(projectRoot);
            this.packageName = packageName;
            this.action = action;
        }

        @NonNull
        public String getPackageName() {
            return packageName;
        }

        @NonNull
        public Action getAction() {
            return action;
        }
    }
}
