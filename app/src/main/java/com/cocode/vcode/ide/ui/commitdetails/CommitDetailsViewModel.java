package com.cocode.vcode.ide.ui.commitdetails;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.git.model.GitFileItem;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CommitDetailsViewModel manages the state and logic for inspecting a single commit.
 * It handles the retrieval of modified files in a commit and coordinates
 * destructive Git actions like reverts and resets.
 */
public class CommitDetailsViewModel extends AndroidViewModel {

    private final GitRepository repository;
    private final GitCredentialStore credentialStore;

    /**
     * Sequential executor for thread-safe repository operations.
     */
    private final ExecutorService gitExecutor;

    // Observable metadata fields
    private final MutableLiveData<String> commitSha = new MutableLiveData<>();
    private final MutableLiveData<String> commitMessage = new MutableLiveData<>();
    private final MutableLiveData<String> commitAuthor = new MutableLiveData<>();
    private final MutableLiveData<String> commitTimestamp = new MutableLiveData<>();

    // Reactive lists and status flags
    private final MutableLiveData<List<GitFileItem>> commitChanges = new MutableLiveData<>();
    private final MutableLiveData<Boolean> commitChangesLoadError = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> actionCompleted = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public CommitDetailsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new GitRepository();
        this.credentialStore = new GitCredentialStore();
        this.gitExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Sets the initial state of the ViewModel and opens the repository.
     */
    public void initialize(String projectPath, String sha, String msg, String author, String time) {
        commitSha.setValue(sha);
        commitMessage.setValue(msg);
        commitAuthor.setValue(author);
        commitTimestamp.setValue(time);

        if (projectPath != null) {
            gitExecutor.execute(() -> {
                try {
                    repository.openRepository(new File(projectPath));
                    if (sha != null) {
                        loadFilesInCommit(sha);
                    }
                } catch (Exception e) {
                    commitChangesLoadError.postValue(true);
                }
            });
        }
    }

    /**
     * Retrieves the list of files modified in the specified commit from JGit.
     */
    public void loadFilesInCommit(String sha) {
        try {
            List<GitFileItem> files = repository.getFilesInCommit(sha);
            commitChanges.postValue(files);
        } catch (Exception e) {
            commitChangesLoadError.postValue(true);
        }
    }

    /**
     * Executes a revert operation for the current commit.
     * Automatically resolves author credentials from the local store or GitHub state.
     */
    public void revertCommit() {
        String sha = commitSha.getValue();
        if (sha == null) return;

        gitExecutor.execute(() -> {
            try {
                Context ctx = getApplication();
                String resolvedName;
                String resolvedEmail;

                // Priority 1: GitHub authenticated user
                if (credentialStore.hasCredentials(ctx)) {
                    resolvedName = credentialStore.getUsername(ctx);
                    if (resolvedName == null || resolvedName.trim().isEmpty()) {
                        resolvedName = "GitHub User";
                    }
                    resolvedEmail = resolvedName.toLowerCase().replaceAll("\\s+", "") + "@users.noreply.github.com";
                } else {
                    // Priority 2: Locally configured author metadata
                    resolvedName = credentialStore.getLocalAuthorName(ctx);
                    resolvedEmail = credentialStore.getLocalAuthorEmail(ctx);
                }

                repository.revertCommit(sha, resolvedName, resolvedEmail);
                actionCompleted.postValue(true);
            } catch (Exception e) {
                errorMessage.postValue("Revert failed: " + e.getMessage());
            }
        });
    }

    public void softReset(String commitRef) {
        gitExecutor.execute(() -> {
            try {
                repository.softReset(commitRef);
                actionCompleted.postValue(true);
            } catch (Exception e) {
                errorMessage.postValue("Soft reset failed: " + e.getMessage());
            }
        });
    }

    public void mixedReset(String commitRef) {
        gitExecutor.execute(() -> {
            try {
                repository.mixedReset(commitRef);
                actionCompleted.postValue(true);
            } catch (Exception e) {
                errorMessage.postValue("Mixed reset failed: " + e.getMessage());
            }
        });
    }

    public void hardReset(String commitRef) {
        gitExecutor.execute(() -> {
            try {
                repository.hardReset(commitRef);
                actionCompleted.postValue(true);
            } catch (Exception e) {
                errorMessage.postValue("Hard reset failed: " + e.getMessage());
            }
        });
    }

    public GitRepository getRepository() {
        return repository;
    }

    // --- Getters for reactive data ---
    public LiveData<String> getCommitSha() {
        return commitSha;
    }

    public LiveData<String> getCommitMessage() {
        return commitMessage;
    }

    public LiveData<String> getCommitAuthor() {
        return commitAuthor;
    }

    public LiveData<String> getCommitTimestamp() {
        return commitTimestamp;
    }

    public LiveData<List<GitFileItem>> getCommitChanges() {
        return commitChanges;
    }

    public LiveData<Boolean> getCommitChangesLoadError() {
        return commitChangesLoadError;
    }

    public LiveData<Boolean> getActionCompleted() {
        return actionCompleted;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (!gitExecutor.isShutdown()) {
            gitExecutor.shutdownNow();
        }
    }
}