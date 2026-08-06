package com.cocode.vcode.ide.ui.commitdetails;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ActivityCommitDetailsBinding;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.sheets.git.DiffViewerBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.ResetConfirmBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * CommitDetailsActivity displays granular information about a specific Git commit.
 * It shows the author, timestamp, message, and a list of all files modified in the commit.
 * It also provides operational controls to revert the commit or reset the repository to its state.
 */
public class CommitDetailsActivity extends BaseActivity {

    private ActivityCommitDetailsBinding binding;
    private CommitDetailsViewModel viewModel;
    private CommitFilesAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCommitDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply edge-to-edge system UI padding
        UiUtils.applySystemBarInsets(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(CommitDetailsViewModel.class);

        setupTypefaces();
        setupRecyclerView();
        setupObservers();

        // Initialize the ViewModel with commit metadata passed via Intent
        if (savedInstanceState == null) {
            viewModel.initialize(
                    getIntent().getStringExtra("project_path"),
                    getIntent().getStringExtra("commit_sha"),
                    getIntent().getStringExtra("commit_msg"),
                    getIntent().getStringExtra("commit_author"),
                    getIntent().getStringExtra("commit_time")
            );
        }

        setupOperationalButtons();
        binding.btnBack.setOnClickListener(v -> finish());
    }

    /**
     * Connects UI elements to reactive data streams in the ViewModel.
     */
    private void setupObservers() {
        viewModel.getCommitSha().observe(this, sha -> binding.tvCommitShaValue.setText(sha));
        viewModel.getCommitMessage().observe(this, msg -> binding.tvMessageValue.setText(msg));
        viewModel.getCommitAuthor().observe(this, author -> binding.tvAuthorValue.setText(author));
        viewModel.getCommitTimestamp().observe(this, time -> binding.tvTimestampValue.setText(time));

        // Display the list of changed files in the recycler view
        viewModel.getCommitChanges().observe(this, files -> {
            if (files != null) {
                adapter.submitList(files);
            }
        });

        // Notify user if commit file metadata fails to load
        viewModel.getCommitChangesLoadError().observe(this, failed -> {
            if (Boolean.TRUE.equals(failed)) {
                Toast.makeText(this, R.string.vcode_failed_to_load_commit_changes, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        // Close activity upon successful execution of a destructive Git action (e.g., revert)
        viewModel.getActionCompleted().observe(this, completed -> {
            if (Boolean.TRUE.equals(completed)) {
                finish();
            }
        });
    }

    /**
     * Applies the branding fonts to labels and values for a polished UI.
     */
    private void setupTypefaces() {
        Context context = this;
        FontManager fm = FontManager.getInstance();

        binding.appBarTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvCommitShaLabel.setTypeface(fm.getUiSemiBold(context));
        binding.tvCommitShaValue.setTypeface(fm.getUiFont(context));
        binding.tvAuthorLabel.setTypeface(fm.getUiSemiBold(context));
        binding.tvAuthorValue.setTypeface(fm.getUiSemiBold(context));
        binding.tvTimestampLabel.setTypeface(fm.getUiSemiBold(context));
        binding.tvTimestampValue.setTypeface(fm.getUiSemiBold(context));
        binding.tvMessageLabel.setTypeface(fm.getUiSemiBold(context));
        binding.tvMessageValue.setTypeface(fm.getUiMedium(context));
        binding.tvChangedFilesLabel.setTypeface(fm.getUiSemiBold(context));
        binding.btnRevertCommit.setTypeface(fm.getUiSemiBold(context));
        binding.btnResetHere.setTypeface(fm.getUiSemiBold(context));
    }

    /**
     * Initializes the RecyclerView for modification tracking.
     * Tapping a file launches the visual Diff viewer.
     */
    private void setupRecyclerView() {
        adapter = new CommitFilesAdapter(item ->
                DiffViewerBottomSheet.newInstance(viewModel.getCommitSha().getValue(), item)
                        .show(getSupportFragmentManager(), "DiffViewerBottomSheet")
        );
        binding.rvCommitFiles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCommitFiles.setAdapter(adapter);
    }

    /**
     * Attaches logic to the Revert and Reset buttons.
     */
    private void setupOperationalButtons() {
        binding.btnRevertCommit.setOnClickListener(v -> {
            viewModel.revertCommit();
        });

        binding.btnResetHere.setOnClickListener(v -> {
            String sha = viewModel.getCommitSha().getValue();
            String msg = viewModel.getCommitMessage().getValue();
            if (sha != null) {
                // Wrap commit info in a mock item for the confirmation dialog
                CommitItem mockItem = new CommitItem(sha, sha.substring(0, 7), msg != null ? msg : "", "", "");
                ResetConfirmBottomSheet.newInstance(mockItem).show(getSupportFragmentManager(), "ResetConfirmSheet");
            }
        });
    }
}