package com.cocode.vcode.ide.ui.git;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.List;

/**
 * GitViewModel manages the business logic and state for Git operations.
 * It interfaces with the {@link GitRepository} to perform JGit-based actions
 * (staging, committing, branching, etc.) on a background thread and publishes
 * updates to the UI via LiveData.
 */
public class GitViewModel extends AndroidViewModel {

    private final GitRepository repository;
    private final GitCredentialStore credentialStore;

    // Reactive data streams for Git status and history
    private final MutableLiveData<List<GitFileItem>> stagedFiles = new MutableLiveData<>();
    private final MutableLiveData<List<GitFileItem>> unstagedFiles = new MutableLiveData<>();
    private final MutableLiveData<List<CommitItem>> commitHistory = new MutableLiveData<>();
    private final MutableLiveData<List<BranchItem>> localBranches = new MutableLiveData<>();
    private final MutableLiveData<List<BranchItem>> remoteBranches = new MutableLiveData<>();
    private final MutableLiveData<String> currentBranch = new MutableLiveData<>();
    private final MutableLiveData<List<com.cocode.vcode.ide.git.model.StashItem>> stashes = new MutableLiveData<>();

    // UI state indicators
    private final MutableLiveData<String> logOutput = new MutableLiveData<>("");
    private final MutableLiveData<GitRepository.GitConflictException> conflictEvent = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNotRepository = new MutableLiveData<>(false);



    public GitViewModel(@NonNull Application application) {
        super(application);
        this.repository = new GitRepository();
        this.credentialStore = new GitCredentialStore();
    }

    /**
     * Resolves the default branch name from shared preferences.
     */
    private String resolveDefaultBranchFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        return prefs.getString("gitDefaultBranch", prefs.getString("git_default_branch", "main"));
    }

    /**
     * Initializes the Git repository for the specified directory.
     *
     * @param projectDir            The root directory of the project.
     * @param explicitDefaultBranch Optional branch name to use as default.
     */
    public void initRepo(File projectDir, String explicitDefaultBranch) {
        isLoading.setValue(true);
        String fallbackBranch = (explicitDefaultBranch != null && !explicitDefaultBranch.isEmpty())
                ? explicitDefaultBranch : resolveDefaultBranchFromPreferences();
        repository.setConfiguredDefaultBranch(fallbackBranch);

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File gitFolder = new File(projectDir, ".git");
                if (!gitFolder.exists()) {
                    isNotRepository.postValue(true);
                    isLoading.postValue(false);
                    return;
                }
                repository.openRepository(projectDir);
                isNotRepository.postValue(false);
                refreshAll();
            } catch (Exception e) {
                postError("Initialization failed: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Forcefully initializes a new Git repository in the specified directory.
     */
    public void executeExplicitRepoInitialization(File projectDir) {
        isLoading.setValue(true);
        repository.setConfiguredDefaultBranch(resolveDefaultBranchFromPreferences());

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                repository.openRepository(projectDir);
                isNotRepository.postValue(false);
                refreshAll();
            } catch (Exception e) {
                postError("Failed to instantiate standard repository context layout: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public LiveData<Boolean> getIsNotRepository() {
        return isNotRepository;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    /**
     * Refreshes all Git data (staged/unstaged files, history, branches) from the repository.
     */
    public void refreshAll() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                List<GitFileItem> staged = repository.getStagedFiles();
                List<GitFileItem> unstaged = repository.getUnstagedFiles();
                List<CommitItem> history = repository.getCommitHistory();
                List<BranchItem> locals = repository.getBranches(false);
                List<BranchItem> remotes = repository.getBranches(true);

                // Handle the case of an empty repository with an unborn branch
                if (locals != null && locals.isEmpty()) {
                    String unbornBranchName = repository.getCurrentBranchName();
                    locals.add(new BranchItem(unbornBranchName, true, false, "No commits yet"));
                }

                stagedFiles.postValue(staged);
                unstagedFiles.postValue(unstaged);
                commitHistory.postValue(history);
                localBranches.postValue(locals);
                remoteBranches.postValue(remotes);

                java.util.Collection<org.eclipse.jgit.revwalk.RevCommit> stashCommits = repository.stashList();
                List<com.cocode.vcode.ide.git.model.StashItem> stashItems = new java.util.ArrayList<>();
                int idx = 0;
                if (stashCommits != null) {
                    for (org.eclipse.jgit.revwalk.RevCommit commit : stashCommits) {
                        stashItems.add(new com.cocode.vcode.ide.git.model.StashItem(
                                idx++,
                                "stash@{" + (idx - 1) + "}",
                                commit.getShortMessage(),
                                com.cocode.vcode.ide.utils.DateUtils.formatDate(commit.getAuthorIdent().getWhen())
                        ));
                    }
                }
                stashes.postValue(stashItems);

                // Update the current branch name based on the repository state
                boolean foundActive = false;
                if (locals != null) {
                    for (BranchItem branch : locals) {
                        if (branch.isActive()) {
                            currentBranch.postValue(branch.getName());
                            foundActive = true;
                            break;
                        }
                    }
                }
                if (!foundActive) {
                    currentBranch.postValue(repository.getCurrentBranchName());
                }
            } catch (Exception e) {
                postError("State synchronization refresh failed: " + e.getMessage());
            }
        });
    }

    /**
     * Determines if the user needs to configure their Git name and email.
     */
    public boolean shouldPromptForAuthor() {
        android.content.Context ctx = getApplication();
        return !credentialStore.hasCredentials(ctx) && !credentialStore.hasLocalAuthor(ctx);
    }

    /**
     * Persists the Git author information locally.
     */
    public void saveLocalAuthor(String name, String email) {
        credentialStore.saveLocalAuthor(getApplication(), name, email);
    }

    /**
     * Commits the currently staged changes with the provided message.
     */
    public void commit(String message) {
        runAction(() -> {
            android.content.Context ctx = getApplication();
            String resolvedName;
            String resolvedEmail;

            // Resolve author info prioritizing GitHub credentials over local author
            if (credentialStore.hasCredentials(ctx)) {
                resolvedName = credentialStore.getUsername(ctx);
                if (resolvedName == null || resolvedName.trim().isEmpty()) {
                    resolvedName = "GitHub User";
                }
                resolvedEmail = resolvedName.toLowerCase().replaceAll("\\s+", "") + "@users.noreply.github.com";
            } else {
                resolvedName = credentialStore.getLocalAuthorName(ctx);
                resolvedEmail = credentialStore.getLocalAuthorEmail(ctx);
            }

            repository.commit(message, resolvedName, resolvedEmail);
        });
    }

    public void stageAll() {
        runAction(repository::stageAll);
    }

    public void unstageAll() {
        runAction(repository::unstageAll);
    }

    public void unstageFile(String path) {
        runAction(() -> repository.unstageFile(path));
    }

    public void amendCommit(String message) {
        runAction(() -> repository.amendCommit(message));
    }

    public GitRepository getRepository() {
        return repository;
    }

    public LiveData<List<com.cocode.vcode.ide.git.model.StashItem>> getStashes() {
        return stashes;
    }

    public void stashCreate() {
        runAction(() -> repository.stashCreate());
    }

    public void stashCreate(String message) {
        runAction(() -> repository.stashCreate(message));
    }

    public void stashApply(int id) {
        runAction(() -> repository.stashApply(id));
    }

    public void stashDrop(int id) {
        runAction(() -> repository.stashDrop(id));
    }

    public void pull(String remoteUrl, String pat, String branch) {
        runAction(() -> repository.pull(remoteUrl, pat, branch));
    }

    public void fetch(String remoteUrl, String pat) {
        runAction(() -> repository.fetch(remoteUrl, pat));
    }

    public void cherryPick(String commitSha) {
        runAction(() -> {
            try {
                repository.cherryPick(commitSha);
            } catch (GitRepository.GitConflictException e) {
                conflictEvent.postValue(e);
            }
        });
    }

    public void revertCommit(String commitSha) {
        runAction(() -> {
            android.content.Context ctx = getApplication();
            String resolvedName;
            String resolvedEmail;

            if (credentialStore.hasCredentials(ctx)) {
                resolvedName = credentialStore.getUsername(ctx);
                if (resolvedName == null || resolvedName.trim().isEmpty()) {
                    resolvedName = "GitHub User";
                }
                resolvedEmail = resolvedName.toLowerCase().replaceAll("\\s+", "") + "@users.noreply.github.com";
            } else {
                resolvedName = credentialStore.getLocalAuthorName(ctx);
                resolvedEmail = credentialStore.getLocalAuthorEmail(ctx);
            }

            try {
                repository.revertCommit(commitSha, resolvedName, resolvedEmail);
            } catch (GitRepository.GitConflictException e) {
                conflictEvent.postValue(e);
            }
        });
    }

    public void revertCommit(String commitSha, String authorName, String authorEmail) {
        runAction(() -> {
            try {
                repository.revertCommit(commitSha, authorName, authorEmail);
            } catch (GitRepository.GitConflictException e) {
                conflictEvent.postValue(e);
            }
        });
    }

    public void createBranch(String name, String from) {
        runAction(() -> repository.createBranch(name, from));
    }

    public void checkoutBranch(String name) {
        runAction(() -> repository.checkoutBranch(name));
    }

    public void checkoutRemoteAsBranch(String remoteName) {
        runAction(() -> repository.checkoutRemoteBranchAsLocal(remoteName));
    }

    public void mergeBranch(String branchName) {
        runAction(() -> repository.mergeBranch(branchName));
    }

    public void renameBranch(String oldName, String newName) {
        runAction(() -> repository.renameBranch(oldName, newName));
    }

    public void deleteBranch(String name) {
        runAction(() -> repository.deleteBranch(name));
    }

    public void softReset(String commitRef) {
        runAction(() -> repository.softReset(commitRef));
    }

    public void mixedReset(String commitRef) {
        runAction(() -> repository.mixedReset(commitRef));
    }

    public void hardReset(String commitRef) {
        runAction(() -> repository.hardReset(commitRef));
    }

    public void stageFile(String path) {
        runAction(() -> repository.stageFile(path));
    }

    public void discardFile(String path) {
        runAction(() -> repository.discardFile(path));
    }

    public void discardAll() {
        runAction(() -> repository.discardAll());
    }

    /**
     * Internal helper to execute Git actions asynchronously and manage the loading state.
     * Automatically triggers a full refresh upon successful execution.
     */
    private void runAction(GitAction action) {
        isLoading.setValue(true);
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                action.execute();
                refreshAll();
            } catch (Exception e) {
                postError(e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Updates the remote 'origin' URL for the repository.
     */
    public void updateRemoteUrl(String url) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                repository.setRemoteUrl(url);
            } catch (Exception e) {
                postError("Failed to update remote URL configuration: " + e.getMessage());
            }
        });
    }

    /**
     * Posts an error message to the LiveData stream for UI consumption.
     */
    private void postError(String message) {
        errorMessage.postValue(message);
    }

    // --- Getters for reactive data ---
    public LiveData<List<GitFileItem>> getStagedFiles() {
        return stagedFiles;
    }

    public LiveData<List<GitFileItem>> getUnstagedFiles() {
        return unstagedFiles;
    }

    public LiveData<List<CommitItem>> getCommitHistory() {
        return commitHistory;
    }

    public LiveData<List<BranchItem>> getLocalBranches() {
        return localBranches;
    }

    public LiveData<List<BranchItem>> getRemoteBranches() {
        return remoteBranches;
    }

    public LiveData<String> getCurrentBranch() {
        return currentBranch;
    }

    public LiveData<String> getLogOutput() {
        return logOutput;
    }

    public LiveData<GitRepository.GitConflictException> getConflictEvent() {
        return conflictEvent;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Represents a single Git operation that may throw an exception.
     */
    @FunctionalInterface
    private interface GitAction {
        void execute() throws Exception;
    }
}