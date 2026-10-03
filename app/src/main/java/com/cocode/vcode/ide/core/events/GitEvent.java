package com.cocode.vcode.ide.core.events;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Base event class for Git lifecycle operations dispatched across the IDE.
 */
public abstract class GitEvent implements IdeEvent {

    private final File repoRoot;
    private final long timestamp;

    public GitEvent(@NonNull File repoRoot) {
        this.repoRoot = repoRoot;
        this.timestamp = System.currentTimeMillis();
    }

    @NonNull
    public File getRepoRoot() {
        return repoRoot;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Dispatched before a commit is created.
     */
    public static class GitPreCommitEvent extends GitEvent {
        private final String commitMessage;

        public GitPreCommitEvent(@NonNull File repoRoot, @NonNull String commitMessage) {
            super(repoRoot);
            this.commitMessage = commitMessage;
        }

        @NonNull
        public String getCommitMessage() {
            return commitMessage;
        }
    }

    /**
     * Dispatched after a commit is successfully created.
     */
    public static class GitPostCommitEvent extends GitEvent {
        private final String commitId;
        private final String commitMessage;

        public GitPostCommitEvent(@NonNull File repoRoot, @NonNull String commitId, @NonNull String commitMessage) {
            super(repoRoot);
            this.commitId = commitId;
            this.commitMessage = commitMessage;
        }

        @NonNull
        public String getCommitId() {
            return commitId;
        }

        @NonNull
        public String getCommitMessage() {
            return commitMessage;
        }
    }

    /**
     * Dispatched before a pull operation begins.
     */
    public static class GitPrePullEvent extends GitEvent {
        public GitPrePullEvent(@NonNull File repoRoot) {
            super(repoRoot);
        }
    }

    /**
     * Dispatched after a pull operation completes.
     */
    public static class GitPostPullEvent extends GitEvent {
        private final boolean successful;
        private final List<String> updatedFiles;

        public GitPostPullEvent(@NonNull File repoRoot, boolean successful, @NonNull List<String> updatedFiles) {
            super(repoRoot);
            this.successful = successful;
            this.updatedFiles = Collections.unmodifiableList(updatedFiles);
        }

        public boolean isSuccessful() {
            return successful;
        }

        @NonNull
        public List<String> getUpdatedFiles() {
            return updatedFiles;
        }
    }

    /**
     * Dispatched after checking out a branch.
     */
    public static class GitPostCheckoutEvent extends GitEvent {
        private final String branch;

        public GitPostCheckoutEvent(@NonNull File repoRoot, @NonNull String branch) {
            super(repoRoot);
            this.branch = branch;
        }

        @NonNull
        public String getBranch() {
            return branch;
        }
    }
}
