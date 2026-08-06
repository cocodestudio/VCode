package com.cocode.vcode.ide.ui.projects;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.Project;
import com.cocode.vcode.ide.databinding.ActivityProjectsBinding;
import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.core.GitManager;
import com.cocode.vcode.ide.git.github.GitHubApiClient;
import com.cocode.vcode.ide.git.model.CommitInfo;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.settings.SettingsActivity;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.NewProjectBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.RenameBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.CloneRepoBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitHubLoginBottomSheet;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.MarginItemDecorator;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * ProjectsActivity is the entry point of the VCode IDE.
 * It provides a workspace overview, allows users to manage projects (create, rename, delete),
 * and facilitates cloning repositories from GitHub.
 */
public class ProjectsActivity extends BaseActivity {

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1001;
    private ActivityProjectsBinding binding;
    private ProjectsViewModel viewModel;
    private final android.content.BroadcastReceiver cloneCompleteReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if ("com.cocode.vcode.ide.ACTION_CLONE_COMPLETE".equals(intent.getAction()) && viewModel != null) {
                binding.getRoot().post(() -> {
                    viewModel.loadProjects();
                });
            }
        }
    };
    private ProjectsAdapter adapter;
    /**
     * Cached list of all projects for local filtering/searching.
     */
    private List<Project> allProjects = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityProjectsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Standard edge-to-edge system bar padding
        UiUtils.applySystemBarInsets(binding.getRoot());

        // Initialize ViewModel
        ProjectsViewModelFactory factory = new ProjectsViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(ProjectsViewModel.class);

        ProjectsViewModel.onCloneCompleteListener = () -> {
            binding.getRoot().post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    viewModel.loadProjects();
                }
            });
        };

        // Configure the project list adapter with interaction callbacks
        adapter = new ProjectsAdapter(new ProjectsAdapter.ProjectClickListener() {
            @Override
            public void onProjectClick(Project project) {
                // Navigate to the editor for the selected project
                File projectDir = new File(FileUtils.getProjectsDir(ProjectsActivity.this), project.getId());
                openProject(projectDir.getAbsolutePath(), project.getId(), project.getName());
            }

            @Override
            public void onProjectRenameClick(Project project) {
                // Trigger the rename workflow via a bottom sheet
                RenameBottomSheet.show(getSupportFragmentManager(), RenameBottomSheet.RenameType.PROJECT, project.getName(), newName -> {
                    viewModel.renameProject(project, newName);
                    project.setName(newName);
                    // Force an immediate UI update for the specific card
                    adapter.forceItemUpdate(project.getId());
                });
            }

            @Override
            public void onProjectDeleteClick(Project project) {
                // Confirm and execute project deletion
                DeleteBottomSheet.show(getSupportFragmentManager(), DeleteBottomSheet.DeleteType.PROJECT, project.getName(), null, () -> viewModel.deleteProject(project));
            }
        });

        // Setup RecyclerView with layout manager and custom item decorations
        binding.rvProjects.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProjects.setAdapter(adapter);
        binding.rvProjects.addItemDecoration(new MarginItemDecorator(UiUtils.dpToPx(this, 24), UiUtils.dpToPx(this, 24), UiUtils.dpToPx(this, 12)));

        // Auto-close open swipe menus when the user starts scrolling the list
        binding.rvProjects.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    adapter.closeSwipedItem();
                }
            }
        });

        binding.rvProjects.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());

        // Apply UI styling and wire up listeners/observers
        designUI();
        setupListeners();
        setupObservers();

        refreshUIState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-evaluate storage permissions and project list when returning to this screen
        refreshUIState();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(cloneCompleteReceiver, new IntentFilter("com.cocode.vcode.ide.ACTION_CLONE_COMPLETE"), Context.RECEIVER_NOT_EXPORTED);
            }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ProjectsViewModel.onCloneCompleteListener != null) {
            ProjectsViewModel.onCloneCompleteListener = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(cloneCompleteReceiver);
    }

    /**
     * Toggles between the project list and the permission-grant empty state
     * based on current system permissions.
     */
    private void refreshUIState() {
        if (!hasStoragePermission()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.rvProjects.setVisibility(View.GONE);
            binding.cardWorkspaceOverview.setVisibility(View.GONE);
            binding.fabAddProject.setVisibility(View.GONE);
            binding.progressLoading.setVisibility(View.GONE);

            binding.tvNoProjectsYet.setText(R.string.vcode_storage_access_required);
            binding.tvTapPlusToCreate.setText(R.string.vcode_storage_access_desc);
            binding.btnCreateProjectEmpty.setText(R.string.vcode_grant_permission);

            binding.btnCreateProjectEmpty.setOnClickListener(v -> requestStoragePermission());
        } else {
            // Permission granted; show standard loading state and trigger data load
            binding.progressLoading.setVisibility(View.VISIBLE);
            binding.tvNoProjectsYet.setText(R.string.vcode_no_projects_yet);
            binding.tvTapPlusToCreate.setText(R.string.vcode_create_project_hint);
            binding.btnCreateProjectEmpty.setText(R.string.vcode_action_create_project);

            binding.btnCreateProjectEmpty.setOnClickListener(v -> showNewProjectSheet());

            viewModel.loadProjects();
        }
    }

    /**
     * Checks if the app has permission to manage external storage.
     * Uses the appropriate API check based on the Android version.
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Requests the necessary storage permissions from the user.
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Navigate to the system settings page for "All Files Access"
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            // Standard runtime permission request for older Android versions
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            refreshUIState();
        }
    }

    /**
     * Initializes click and text-change listeners for UI interactivity.
     */
    private void setupListeners() {
        /*
        binding.tvWorkspaceOverview.setOnClickListener(v -> {
            throw new RuntimeException("Error");
        });
         */
        binding.fabAddProject.setOnClickListener(v -> showNewProjectSheet());

        binding.iconSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // Real-time project filtering based on the search query
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProjects(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        // Clone Repository workflow
        binding.iconCloneRepo.setOnClickListener(v -> CloneRepoBottomSheet.show(getSupportFragmentManager(), () -> {
            viewModel.loadProjects();
        }));

        // GitHub Authentication workflow
        binding.iconGithub.setOnClickListener(v -> GitHubLoginBottomSheet.show(getSupportFragmentManager(), (token, updater) -> ExecutorProvider.getInstance().runOnIo(() -> {
                            try {
                                // Validate the provided Personal Access Token against the GitHub API
                                GitHubApiClient client = new GitHubApiClient(token);
                                GitHubApiClient.GitHubUser user = client.validateToken();
                                String username = user.getLogin();

                                // Persist credentials securely in the local store
                                GitCredentialStore store = new GitCredentialStore();
                                store.saveUsername(ProjectsActivity.this, username);
                                store.saveToken(ProjectsActivity.this, token);

                                ExecutorProvider.getInstance().runOnMain(() -> {
                                    try {
                                        updater.onResult(true, null);
                                    } catch (Exception ignored) {
                                    }
                                    Toast.makeText(ProjectsActivity.this, "Signed in as @" + username, Toast.LENGTH_SHORT).show();
                                });
                            } catch (Exception e) {
                                // Notify the UI of authentication failure
                                ExecutorProvider.getInstance().runOnMain(() -> {
                                    try {
                                        updater.onResult(false, e.getMessage() != null ? e.getMessage() : "Authentication failed.");
                                    } catch (Exception ignored) {
                                    }
                                });
                            }
                        })
                )
        );
    }

    /**
     * Connects UI observers to the ViewModel's reactive data streams.
     */
    private void setupObservers() {
        viewModel.getProjectsLiveData().observe(this, result -> {
            if (!hasStoragePermission()) return;

            binding.progressLoading.setVisibility(View.GONE);
            if (result.isSuccess() && result.getData() != null) {
                allProjects = result.getData();

                // Refresh the adapter with the new dataset, maintaining current search filters
                filterProjects(binding.etSearch.getText().toString());

                // Update workspace overview statistics
                binding.tvTotalProjectsCount.setText(String.valueOf(allProjects.size()));
                calculateCommitsToday(allProjects);
            } else {
                Toast.makeText(this, R.string.vcode_failed_to_load_projects, Toast.LENGTH_SHORT).show();
                updateEmptyStateVisibility(true);
            }
        });
    }

    /**
     * Filters the project list based on a case-insensitive name match.
     *
     * @param query The search text.
     */
    private void filterProjects(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setProjects(allProjects);
            updateEmptyStateVisibility(allProjects.isEmpty());
        } else {
            List<Project> filtered = new ArrayList<>();
            for (Project p : allProjects) {
                if (p.getName().toLowerCase().contains(query.toLowerCase())) {
                    filtered.add(p);
                }
            }
            adapter.setProjects(filtered);
            updateEmptyStateVisibility(allProjects.isEmpty());
        }
    }

    /**
     * Manages the visibility and animation of the empty state vs the project list.
     *
     * @param isEmpty Whether the current project list (after filtering) is empty.
     */
    private void updateEmptyStateVisibility(boolean isEmpty) {
        boolean isCurrentlyEmpty = binding.layoutEmptyState.getVisibility() == View.VISIBLE;
        if (isEmpty == isCurrentlyEmpty) return;

        TransitionManager.beginDelayedTransition(binding.getRoot(), new AutoTransition().setDuration(300));

        if (isEmpty) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.rvProjects.setVisibility(View.GONE);
            binding.fabAddProject.setVisibility(View.GONE);
            binding.cardWorkspaceOverview.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.rvProjects.setVisibility(View.VISIBLE);

            // Animate workspace components into view for a polished feel
            binding.cardWorkspaceOverview.setVisibility(View.VISIBLE);
            binding.cardWorkspaceOverview.setAlpha(0f);
            binding.cardWorkspaceOverview.setTranslationY(80f);
            binding.cardWorkspaceOverview.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).setInterpolator(new OvershootInterpolator(1.2f)).start();

            binding.fabAddProject.setVisibility(View.VISIBLE);
            binding.fabAddProject.setScaleX(0f);
            binding.fabAddProject.setScaleY(0f);
            binding.fabAddProject.animate().scaleX(1f).scaleY(1f).setDuration(400).setStartDelay(200).setInterpolator(new OvershootInterpolator(1.5f)).start();
        }
    }

    /**
     * Applies branding fonts and custom styling to UI elements.
     */
    private void designUI() {
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(this));
        binding.yourProjectsText.setTypeface(fm.getUiMedium(this));
        binding.tvEnvActive.setTypeface(fm.getUiMedium(this));
        binding.tvWorkspaceOverview.setTypeface(fm.getUiSemiBold(this));
        binding.tvTotalProjects.setTypeface(fm.getUiFont(this));
        binding.tvTotalCommitsToday.setTypeface(fm.getUiFont(this));
        binding.tvTotalProjectsCount.setTypeface(fm.getUiMedium(this));
        binding.tvTotalCommitsCount.setTypeface(fm.getUiFont(this));
        binding.etSearch.setTypeface(fm.getUiMedium(this));

        binding.tvNoProjectsYet.setTypeface(fm.getUiSemiBold(this));
        binding.tvTapPlusToCreate.setTypeface(fm.getUiMedium(this));
        binding.btnCreateProjectEmpty.setTypeface(fm.getUiSemiBold(this));

        UiUtils.setViewRounded(binding.searchBarLayout, UiUtils.dpToPx(this, 10), ContextCompat.getColor(this, R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(binding.viewMiddleLine, UiUtils.dpToPx(this, 990), ContextCompat.getColor(this, R.color.vcode_bg_elevated));
    }

    /**
     * Asynchronously scans all projects to calculate the total number of Git commits made today.
     *
     * @param projects The list of projects to analyze.
     */
    private void calculateCommitsToday(List<Project> projects) {
        binding.tvTotalCommitsCount.setText(R.string.vcode_empty);

        ExecutorProvider.getInstance().runOnIo(() -> {
            int count = 0;

            // Define the start of the current day for comparison
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.clear(Calendar.MINUTE);
            cal.clear(Calendar.SECOND);
            cal.clear(Calendar.MILLISECOND);
            long startOfDay = cal.getTimeInMillis() / 1000L;

            for (Project p : projects) {
                try {
                    File projectDir = new File(FileUtils.getProjectsDir(this), p.getId());
                    GitManager git = new GitManager(projectDir);

                    if (git.isGitRepo()) {
                        git.open();
                        // Scan the recent commit history for matches within today
                        List<CommitInfo> logs = git.getCommitLog(50, 0);
                        for (CommitInfo commit : logs) {
                            if (commit.getTimestamp() >= startOfDay) {
                                count++;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            final int finalCount = count;
            ExecutorProvider.getInstance().runOnMain(() -> binding.tvTotalCommitsCount.setText(String.valueOf(finalCount)));
        });
    }

    /**
     * Launches the bottom sheet to create a new project.
     */
    private void showNewProjectSheet() {
        NewProjectBottomSheet.show(getSupportFragmentManager(), (name, mainFile, template, initGit) -> {
            viewModel.createProject(name, mainFile, template, initGit);
        });
    }

    /**
     * Transitions to the EditorActivity for the selected project.
     */
    private void openProject(String absolutePath, String projectId, String projectName) {
        viewModel.saveLastProjectId(projectId);
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_PATH, absolutePath);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, projectName);
        startActivity(intent);
    }
}