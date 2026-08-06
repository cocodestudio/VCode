package com.cocode.vcode.ide.ui.git.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.FragmentGitRemoteBinding;
import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.git.github.GitHubApiClient;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.git.CreateGitHubRepoBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitConflictBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitHubLoginBottomSheet;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GitRemoteFragment handles synchronization with remote GitHub repositories.
 * It manages GitHub authentication, remote URL configuration, and provides
 * a push interface with real-time status reporting.
 */
public class GitRemoteFragment extends Fragment {

    private static final String PREFS_NAME = "vcode_git_remote_credentials";
    private FragmentGitRemoteBinding binding;
    private GitViewModel viewModel;
    private GitCredentialStore credentialStore;
    private String projectPath;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGitRemoteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Shared Activity-scoped ViewModel to coordinate Git operations
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);
        credentialStore = new GitCredentialStore();

        // Retrieve the current project path for context-aware remote URL caching
        projectPath = requireActivity().getIntent().getStringExtra("project_path");
        if (projectPath == null) projectPath = "";

        setupTypefaces();
        loadSavedRemoteUrl();
        refreshAccountUIState();
        setupBranchDropdown();
        setupActionListeners();
    }

    /**
     * Applies specialized UI fonts to headers, labels, and action components.
     */
    private void setupTypefaces() {
        Context context = requireContext();
        binding.tvRemoteHeaderTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvActiveOrigin.setTypeface(FontManager.getInstance().getUiMedium(context));

        binding.tvGithubAccount.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvAccountUsername.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        binding.tvLabelRemoteUrl.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.etRemoteUrl.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.tvLabelBranch.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvTargetBranchSelector.setTypeface(FontManager.getInstance().getUiMedium(context));

        binding.btnPull.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnFetch.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnPush.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvStatusMessage.setTypeface(FontManager.getInstance().getUiMedium(context));

        binding.tvEmptyRemoteTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvEmptyRemoteDesc.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnLinkAccount.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnCreateOnGithub.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        com.cocode.vcode.ide.utils.UiUtils.setViewRounded(binding.etRemoteUrl, com.cocode.vcode.ide.utils.UiUtils.dpToPx(context, 10), ContextCompat.getColor(context, R.color.vcode_bg_elevated));
        com.cocode.vcode.ide.utils.UiUtils.setViewRounded(binding.tvTargetBranchSelector, com.cocode.vcode.ide.utils.UiUtils.dpToPx(context, 10), ContextCompat.getColor(context, R.color.vcode_bg_elevated));
    }

    /**
     * Updates the UI based on whether a GitHub account is currently linked.
     * Toggles between the remote configuration form and the login empty state.
     */
    private void refreshAccountUIState() {
        Context context = requireContext();
        boolean hasAuth = credentialStore.hasCredentials(context);

        if (hasAuth) {
            binding.scrollRemoteContent.setVisibility(View.VISIBLE);
            binding.layoutEmptyRemote.setVisibility(View.GONE);

            String username = credentialStore.getUsername(context);
            binding.tvAccountUsername.setText(username != null ? username : "Connected");
        } else {
            binding.scrollRemoteContent.setVisibility(View.GONE);
            binding.layoutEmptyRemote.setVisibility(View.VISIBLE);

            binding.tvAccountUsername.setText(R.string.vcode_not_logged_in);
        }

        if (binding.etRemoteUrl != null && binding.etRemoteUrl.getText() != null) {
            updateCreateButtonVisibility(binding.etRemoteUrl.getText().toString());
        }
    }

    private void updateCreateButtonVisibility(String url) {
        if (binding.btnCreateOnGithub == null) return;
        boolean hasAuth = credentialStore.hasCredentials(requireContext());
        if (hasAuth && (url == null || url.trim().isEmpty())) {
            binding.btnCreateOnGithub.setVisibility(View.VISIBLE);
        } else {
            binding.btnCreateOnGithub.setVisibility(View.GONE);
        }
    }

    /**
     * Loads the remote URL from the repository metadata or local preference cache.
     * Also attaches a listener to persist manual URL changes.
     */
    private void loadSavedRemoteUrl() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String remoteUrl = "";
        if (viewModel.getRepository() != null) {
            remoteUrl = viewModel.getRepository().getRemoteUrl();
        }

        // Fallback to locally cached URL if the repository metadata is missing
        if (remoteUrl == null || remoteUrl.isEmpty()) {
            remoteUrl = prefs.getString(projectPath + "_url", "");
        } else {
            prefs.edit().putString(projectPath + "_url", remoteUrl).apply();
        }

        if (!remoteUrl.isEmpty()) {
            binding.etRemoteUrl.setText(remoteUrl);
        }

        binding.etRemoteUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    String url = s.toString().trim();
                    prefs.edit().putString(projectPath + "_url", url).apply();
                    viewModel.updateRemoteUrl(url); // Sync with the Git repository instance
                    updateCreateButtonVisibility(url);
                }
            }
        });
    }

    /**
     * Populates the target branch dropdown with local branch names.
     * Automatically selects the active branch by default.
     */
    private void setupBranchDropdown() {
        viewModel.getLocalBranches().observe(getViewLifecycleOwner(), branches -> {
            if (branches == null) return;
            List<String> branchNames = new ArrayList<>();
            String currentActive = "";

            for (BranchItem item : branches) {
                branchNames.add(item.getName());
                if (item.isActive()) {
                    currentActive = item.getName();
                }
            }

            // Set initial selection if not already specified by the user
            if (!currentActive.isEmpty() && binding.tvTargetBranchSelector.getText().toString().isEmpty()) {
                binding.tvTargetBranchSelector.setText(currentActive);
                binding.tvActiveOrigin.setText("origin/".concat(currentActive));
            }

            binding.tvTargetBranchSelector.setOnClickListener(v -> showBranchSelectionDialog(branchNames));
        });
    }

    private void showBranchSelectionDialog(List<String> branchNames) {
        String currentSelection = binding.tvTargetBranchSelector.getText().toString();
        com.cocode.vcode.ide.ui.dialogs.CustomListDialog.show(
                requireContext(),
                "Select Target Branch",
                branchNames,
                currentSelection,
                selected -> {
                    binding.tvTargetBranchSelector.setText(selected);
                    binding.tvActiveOrigin.setText("origin/".concat(selected));
                }
        );
    }

    /**
     * Attaches listeners to the GitHub link/disconnect and push buttons.
     */
    private void setupActionListeners() {
        binding.btnDisconnectGithub.setOnClickListener(v -> {
            if (credentialStore.hasCredentials(requireContext())) {
                try {
                    credentialStore.clearCredentials(requireContext());
                    Toast.makeText(requireContext(), R.string.vcode_github_disconnected, Toast.LENGTH_SHORT).show();
                    refreshAccountUIState();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.vcode_github_disconnect_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                openGitHubLoginSheet();
            }
        });

        binding.btnLinkAccount.setOnClickListener(v -> openGitHubLoginSheet());
        binding.btnPush.setOnClickListener(v -> executeRemoteOperation("push"));
        binding.btnPull.setOnClickListener(v -> executeRemoteOperation("pull"));
        binding.btnFetch.setOnClickListener(v -> executeRemoteOperation("fetch"));

        if (binding.btnOpenOnGithub != null) {
            binding.btnOpenOnGithub.setOnClickListener(v -> {
                String url = binding.etRemoteUrl.getText() != null
                        ? binding.etRemoteUrl.getText().toString().trim() : "";
                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.vcode_no_remote_url_configured, Toast.LENGTH_SHORT).show();
                    return;
                }
                String browserUrl = url.replace(".git", "");
                if (browserUrl.startsWith("git@github.com:")) {
                    browserUrl = "https://github.com/" + browserUrl.substring("git@github.com:".length());
                }
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(browserUrl)));
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.vcode_could_not_open_url, Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (binding.btnCreateOnGithub != null) {
            binding.btnCreateOnGithub.setOnClickListener(v -> {
                String name = "new-repo";
                if (projectPath != null && !projectPath.isEmpty()) {
                    java.io.File metaFile = new java.io.File(projectPath, "project_meta.json");
                    if (metaFile.exists()) {
                        try {
                            String metaContent = com.cocode.vcode.ide.utils.FileUtils.readFile(metaFile);
                            org.json.JSONObject metaJson = new org.json.JSONObject(metaContent);
                            name = metaJson.optString("name", "new-repo");
                        } catch (Exception ignored) {
                            name = new java.io.File(projectPath).getName();
                        }
                    } else {
                        name = new java.io.File(projectPath).getName();
                    }
                    // GitHub repos typically don't have spaces, replace with dashes
                    name = name.replace(" ", "-");
                }

                CreateGitHubRepoBottomSheet.show(getChildFragmentManager(), name, (repoName, desc, isPrivate) -> {
                    createGitHubRepo(repoName, desc, isPrivate);
                });
            });
        }
    }

    private void createGitHubRepo(String name, String desc, boolean isPrivate) {
        String token = null;
        try {
            token = credentialStore.getToken(requireContext());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to get token: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (token == null || token.isEmpty()) return;

        final String finalToken = token;

        binding.layoutStatusArea.setVisibility(View.VISIBLE);
        binding.progressIndicator.setVisibility(View.VISIBLE);
        setHUDStatus("Creating GitHub repository...", R.color.vcode_accent_primary);
        toggleFormInputState(false);

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                GitHubApiClient client = new GitHubApiClient(finalToken);
                GitHubApiClient.CreateRepoResult result = client.createRepository(name, desc, isPrivate);

                String cloneUrl = result.getCloneUrl();

                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding != null) {
                        binding.etRemoteUrl.setText(cloneUrl);
                        viewModel.updateRemoteUrl(cloneUrl);
                        updateCreateButtonVisibility(cloneUrl);
                        executeRemoteOperation("push");
                    }
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding != null) {
                        binding.progressIndicator.setVisibility(View.GONE);
                        setHUDStatus("Failed to create repository: " + e.getMessage(), R.color.vcode_accent_error);
                        toggleFormInputState(true);
                    }
                });
            }
        });
    }

    /**
     * Launches the GitHub Login bottom sheet for Personal Access Token authentication.
     */
    private void openGitHubLoginSheet() {
        GitHubLoginBottomSheet.show(getChildFragmentManager(), (token, updater) -> ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                // Validate token and retrieve account details from the GitHub API
                GitHubApiClient client = new GitHubApiClient(token);
                GitHubApiClient.GitHubUser user = client.validateToken();
                String username = user.getLogin();

                Context context = getContext();
                if (context != null) {
                    credentialStore.saveUsername(context, username);
                    credentialStore.saveToken(context, token);
                }

                ExecutorProvider.getInstance().runOnMain(() -> {
                    try {
                        updater.onResult(true, null);
                    } catch (Exception ignored) {
                    }
                    if (isAdded()) {
                        refreshAccountUIState();
                        Toast.makeText(requireContext(), "Signed in as @" + username, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    try {
                        updater.onResult(false, e.getMessage() != null ? e.getMessage() : "Authentication failed.");
                    } catch (Exception ignored) {
                    }
                });
            }
        }));
    }

    /**
     * Orchestrates the Git remote operations (push, pull, fetch).
     * Validates inputs, manages the HUD status UI, and executes asynchronously.
     */
    private void executeRemoteOperation(String operation) {
        Context context = requireContext();

        // Ensure authentication is present before attempting a push
        if (!credentialStore.hasCredentials(context)) {
            Toast.makeText(context, "Connect your GitHub account to " + operation + ".", Toast.LENGTH_SHORT).show();
            openGitHubLoginSheet();
            return;
        }

        String url = binding.etRemoteUrl.getText() != null ? binding.etRemoteUrl.getText().toString().trim() : "";
        String branch = binding.tvTargetBranchSelector.getText().toString().trim();

        // Validate remote URL and branch name
        if (url.isEmpty()) {
            binding.etRemoteUrl.setError("Remote URL cannot be empty");
            binding.etRemoteUrl.requestFocus();
            return;
        }

        if (branch.isEmpty()) {
            binding.tvTargetBranchSelector.setError("Select a tracking operational branch");
            return;
        }

        String globalPatToken = "";
        try {
            globalPatToken = credentialStore.getToken(context);
        } catch (Exception ignored) {
        }

        // Show progress HUD and disable form inputs to prevent concurrent modification
        binding.layoutStatusArea.setVisibility(View.VISIBLE);
        binding.progressIndicator.setVisibility(View.VISIBLE);
        setHUDStatus("Initializing background " + operation + " operations stream...", R.color.vcode_accent_primary);
        toggleFormInputState(false);

        final String finalToken = globalPatToken;
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                final String resultSummary;
                if (operation.equals("push")) {
                    viewModel.getRepository().push(url, finalToken, branch);
                    resultSummary = "Push completed successfully.";
                } else if (operation.equals("pull")) {
                    resultSummary = viewModel.getRepository().pull(url, finalToken, branch);
                } else {
                    resultSummary = viewModel.getRepository().fetch(url, finalToken);
                }

                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding != null) {
                        binding.progressIndicator.setVisibility(View.GONE);
                        setHUDStatus(resultSummary, R.color.vcode_accent_primary);
                        toggleFormInputState(true);
                        viewModel.refreshAll();
                    }
                });

            } catch (GitRepository.GitConflictException e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.progressIndicator.setVisibility(View.GONE);
                    setHUDStatus("Merge conflicts detected — resolve them to complete the pull.", R.color.vcode_accent_warning);
                    toggleFormInputState(true);
                    GitConflictBottomSheet.show(getChildFragmentManager(),
                            viewModel.getRepository(),
                            e.getConflictingFiles(),
                            () -> {
                                if (binding != null)
                                    setHUDStatus("Conflicts resolved. Stage and commit the changes.", R.color.vcode_accent_primary);
                                viewModel.refreshAll();
                            });
                });
            } catch (Exception e) {
                final String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown JGit core connection runtime failure context.";
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding != null) {
                        binding.progressIndicator.setVisibility(View.GONE);
                        setHUDStatus("Operational Error: " + errorMessage, R.color.vcode_accent_error);
                        toggleFormInputState(true);
                    }
                });
            }
        });
    }

    /**
     * Updates the HUD status message from any thread.
     */
    private void postHUDProgressUpdate(String statusReport) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (binding != null) {
                binding.tvStatusMessage.setText(statusReport);
            }
        });
    }

    /**
     * Sets the HUD status message and text color.
     */
    private void setHUDStatus(String statusText, int textColorResId) {
        binding.tvStatusMessage.setText(statusText);
        binding.tvStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), textColorResId));
    }

    /**
     * Toggles the enabled state of all interactive form components.
     */
    private void toggleFormInputState(boolean enabled) {
        binding.etRemoteUrl.setEnabled(enabled);
        binding.tvTargetBranchSelector.setEnabled(enabled);
        binding.btnDisconnectGithub.setEnabled(enabled);
        binding.btnPush.setEnabled(enabled);
        binding.btnPull.setEnabled(enabled);
        binding.btnFetch.setEnabled(enabled);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}