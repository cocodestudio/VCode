package com.cocode.vcode.ide.ui.diff;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;

/**
 * DiffViewerViewModel manages the loading of Git diffs asynchronously on a background thread.
 * It provides observable LiveData streams for diff content, loading status, and error states.
 */
public class DiffViewerViewModel extends AndroidViewModel {

    private final GitRepository repository;
    private final MutableLiveData<String> diffContent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public DiffViewerViewModel(@NonNull Application application) {
        super(application);
        this.repository = new GitRepository();
    }

    public LiveData<String> getDiffContent() {
        return diffContent;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Loads the unified diff for either a workspace modification or a specific historical commit.
     */
    public void loadDiff(String projectPath, String filePath, boolean isStaged, String commitSha) {
        loadDiff(projectPath, filePath, isStaged, commitSha, null, null);
    }

    /**
     * Loads the unified diff for a workspace modification, commit delta, or ref-to-ref branch comparison.
     *
     * @param projectPath The root directory of the Git project.
     * @param filePath    The relative path of the file to diff.
     * @param isStaged    True if checking staged workspace changes.
     * @param commitSha   Commit SHA if inspecting a historical commit; null/empty otherwise.
     * @param oldRef      Baseline reference (e.g. HEAD, refs/heads/main) for ref-to-ref comparison.
     * @param newRef      Target reference (e.g. refs/remotes/origin/main) for ref-to-ref comparison.
     */
    public void loadDiff(String projectPath, String filePath, boolean isStaged, String commitSha, String oldRef, String newRef) {
        if (projectPath == null || filePath == null) {
            errorMessage.setValue(getApplication().getString(R.string.vcode_failed_to_load_diff));
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                repository.openRepository(new File(projectPath));
                String diff;
                if (oldRef != null && !oldRef.trim().isEmpty() && newRef != null && !newRef.trim().isEmpty()) {
                    diff = repository.getDiffBetweenRefsForFile(oldRef.trim(), newRef.trim(), filePath);
                } else if (commitSha != null && !commitSha.trim().isEmpty()) {
                    diff = repository.getCommitFileDiff(commitSha.trim(), filePath);
                } else {
                    diff = repository.getFileDiff(filePath, isStaged);
                }
                diffContent.postValue(diff);
                isLoading.postValue(false);
            } catch (Exception e) {
                errorMessage.postValue(getApplication().getString(R.string.vcode_failed_to_load_diff));
                isLoading.postValue(false);
            }
        });
    }
}
