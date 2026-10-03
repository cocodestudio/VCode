package com.cocode.vcode.ide.git.core;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.List;

/**
 * Listener interface for Git operation lifecycle events (commits, pulls, checkouts).
 */
public interface GitEventListener {

    /**
     * Invoked immediately before a commit is created.
     */
    default void onPreCommit(@NonNull File repoRoot, @NonNull String commitMessage) {
    }

    /**
     * Invoked immediately after a commit is successfully created.
     */
    default void onPostCommit(@NonNull File repoRoot, @NonNull String commitId, @NonNull String commitMessage) {
    }

    /**
     * Invoked immediately before a pull operation begins.
     */
    default void onPrePull(@NonNull File repoRoot) {
    }

    /**
     * Invoked after a pull operation completes.
     *
     * @param repoRoot the repository working tree root
     * @param successful true if pull merged or fast-forwarded cleanly
     * @param updatedFiles list of files updated during pull
     */
    default void onPostPull(@NonNull File repoRoot, boolean successful, @NonNull List<String> updatedFiles) {
    }

    /**
     * Invoked after a branch checkout completes.
     */
    default void onPostCheckout(@NonNull File repoRoot, @NonNull String targetBranch) {
    }
}
