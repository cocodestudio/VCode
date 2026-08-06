package com.cocode.vcode.ide.ui.git;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ActivityGitBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.git.tabs.GitBranchFragment;
import com.cocode.vcode.ide.ui.git.tabs.GitChangesFragment;
import com.cocode.vcode.ide.ui.git.tabs.GitHistoryFragment;
import com.cocode.vcode.ide.ui.git.tabs.GitRemoteFragment;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;

/**
 * GitActivity provides a comprehensive interface for Git version control management.
 * It features a tabbed layout (Changes, History, Branches, Remote) to handle
 * various Git workflows like staging, committing, branching, and remote synchronization.
 */
public class GitActivity extends BaseActivity {

    private final String[] tabTitles = {"Changes", "History", "Branches", "Remote", "Stash"};
    private ActivityGitBinding binding;
    private GitViewModel viewModel;
    private File projectDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityGitBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Configure system and IME insets for consistent edge-to-edge layout across all tabs
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Resolve project path and optional default branch from launch intent
        String projectPath = getIntent().getStringExtra("project_path");
        String defaultBranch = getIntent().getStringExtra("default_branch");
        if (projectPath == null) {
            finish(); // Exit if no workspace context is provided
            return;
        }
        projectDirectory = new File(projectPath);

        // Initialize ViewModel and UI components
        viewModel = new ViewModelProvider(this).get(GitViewModel.class);
        setupTypefaces();
        setupViewPager();
        setupReactiveObservers();

        // Initialize the Git repository context; handles non-initialized repos gracefully
        viewModel.initRepo(projectDirectory, defaultBranch);

        // Custom back button logic to warn about uncommitted work
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExitVerificationSequence();
            }
        });
    }

    /**
     * Applies specialized UI fonts to title and empty state elements.
     */
    private void setupTypefaces() {
        Typeface semiBoldTf = FontManager.getInstance().getUiSemiBold(this);

        binding.tvGitWorkspaceTitle.setTypeface(semiBoldTf);
        binding.tvEmptyStateTitle.setTypeface(semiBoldTf);
        binding.tvEmptyStateDescription.setTypeface(semiBoldTf);
        binding.btnInitializeRepo.setTypeface(semiBoldTf);
    }

    /**
     * Initializes the ViewPager2 with the GitPagerAdapter and configures the TabLayout
     * with custom views for better typography control.
     */
    private void setupViewPager() {
        binding.viewPager.setAdapter(new GitPagerAdapter(this));

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            TextView tabTextView = new TextView(this);
            tabTextView.setText(tabTitles[position]);
            tabTextView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            tabTextView.setGravity(android.view.Gravity.CENTER);

            // Apply consistent UI medium font to tab labels
            tabTextView.setTypeface(FontManager.getInstance().getUiMedium(this));

            // Sync text color with the TabLayout's state list
            tabTextView.setTextColor(binding.tabLayout.getTabTextColors());

            tab.setCustomView(tabTextView);
        }).attach();
    }

    /**
     * Connects reactive data streams from the ViewModel to update the Activity's UI.
     */
    private void setupReactiveObservers() {
        // Toggle between the main Git UI and the "Uninitialized Repo" empty state
        viewModel.getIsNotRepository().observe(this, isNotRepo -> {
            if (isNotRepo != null && isNotRepo) {
                binding.layoutGitContent.setVisibility(View.GONE);
                binding.layoutUninitializedEmptyState.setVisibility(View.VISIBLE);
                binding.btnInitializeRepo.setOnClickListener(v ->
                        viewModel.executeExplicitRepoInitialization(projectDirectory));
            } else {
                binding.layoutGitContent.setVisibility(View.VISIBLE);
                binding.layoutUninitializedEmptyState.setVisibility(View.GONE);
            }
        });

        // Display or hide the global loading overlay during Git operations
        viewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                binding.gitLoadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
        });

        // Surface errors via snackbars with an integrated retry mechanism
        viewModel.getErrorMessage().observe(this, errorText -> {
            if (errorText != null && !errorText.trim().isEmpty()) {
                Snackbar.make(binding.getRoot(), errorText, Snackbar.LENGTH_LONG)
                        .setAction("Retry", v -> viewModel.refreshAll())
                        .setActionTextColor(getColor(com.google.android.material.R.color.material_timepicker_button_background))
                        .show();
            }
        });
    }

    /**
     * Verifies if there is uncommitted work before allowing the user to exit the activity.
     * Prompts for confirmation if staged or unstaged changes exist.
     */
    private void handleExitVerificationSequence() {
        boolean hasStagedChanges = viewModel.getStagedFiles().getValue() != null && !viewModel.getStagedFiles().getValue().isEmpty();
        boolean hasUnstagedChanges = viewModel.getUnstagedFiles().getValue() != null && !viewModel.getUnstagedFiles().getValue().isEmpty();

        if (hasStagedChanges || hasUnstagedChanges) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.vcode_uncommitted_changes)
                    .setMessage(R.string.vcode_you_have_uncommitted_modifications_inside)
                    .setPositiveButton(R.string.vcode_leave, (dialog, which) -> finish())
                    .setNegativeButton(R.string.vcode_stay, null)
                    .show();
        } else {
            finish();
        }
    }

    /**
     * Adapter for managing Git-related fragments within the ViewPager.
     */
    private class GitPagerAdapter extends FragmentStateAdapter {
        public GitPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new GitChangesFragment();
                case 1:
                    return new GitHistoryFragment();
                case 2:
                    return new GitBranchFragment();
                case 3:
                    return new GitRemoteFragment();
                default:
                    return new com.cocode.vcode.ide.ui.git.tabs.GitStashFragment();
            }
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }
    }
}