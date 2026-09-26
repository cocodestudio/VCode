package com.cocode.vcode.ide.ui.sheets.git;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetGitFetchReviewBinding;
import com.cocode.vcode.ide.git.adapters.CommitHistoryAdapter;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsActivity;
import com.cocode.vcode.ide.ui.commitdetails.CommitFilesAdapter;
import com.cocode.vcode.ide.ui.diff.DiffViewerActivity;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet dialog displaying incoming commits and file differences after a git fetch.
 * Allows developers to review what teammates pushed, inspect commit logs, and diff
 * code before integrating it into their active working branch.
 */
public class GitFetchReviewBottomSheet extends BaseBottomSheetDialogFragment
        implements CommitHistoryAdapter.CommitHistoryListener, CommitFilesAdapter.OnFileSelectedListener {

    private static final String TAG = "GitFetchReviewBottomSheet";
    private static final String KEY_LOCAL_BRANCH = "local_branch";
    private static final String KEY_REMOTE_BRANCH = "remote_branch";

    private BottomSheetGitFetchReviewBinding binding;
    private GitRepository repository;
    private String localBranch = "main";
    private String remoteBranch = "origin/main";
    private Runnable onPullRequested;

    private CommitHistoryAdapter commitsAdapter;
    private CommitFilesAdapter filesAdapter;

    private boolean isFilesTabActive = false;

    public static void show(FragmentManager fm, GitRepository repository,
                            String localBranch, String remoteBranch,
                            @Nullable Runnable onPullRequested) {
        GitFetchReviewBottomSheet sheet = new GitFetchReviewBottomSheet();
        Bundle args = new Bundle();
        args.putString(KEY_LOCAL_BRANCH, localBranch);
        args.putString(KEY_REMOTE_BRANCH, remoteBranch);
        sheet.setArguments(args);
        sheet.repository = repository;
        sheet.onPullRequested = onPullRequested;
        sheet.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String lb = getArguments().getString(KEY_LOCAL_BRANCH);
            if (lb != null && !lb.isEmpty()) localBranch = lb;
            String rb = getArguments().getString(KEY_REMOTE_BRANCH);
            if (rb != null && !rb.isEmpty()) remoteBranch = rb;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGitFetchReviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupTypefaces();
        setupRecyclerViews();
        setupListeners();
        updateTabState(isFilesTabActive);
        loadComparisonData();
    }

    private void setupTypefaces() {
        Context context = requireContext();
        FontManager fm = FontManager.getInstance();
        binding.tvReviewTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvReviewBranchesSubtitle.setTypeface(fm.getUiMedium(context));
        binding.tvSyncStatusBadge.setTypeface(fm.getUiSemiBold(context));
        binding.btnTabCommits.setTypeface(fm.getUiSemiBold(context));
        binding.btnTabFiles.setTypeface(fm.getUiSemiBold(context));
        binding.tvEmptyCommitsTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvEmptyCommitsDesc.setTypeface(fm.getUiMedium(context));
        binding.tvEmptyFilesTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvEmptyFilesDesc.setTypeface(fm.getUiMedium(context));
        binding.btnCloseReview.setTypeface(fm.getUiSemiBold(context));
        binding.btnPullAndMerge.setTypeface(fm.getUiSemiBold(context));
    }

    private void setupRecyclerViews() {
        commitsAdapter = new CommitHistoryAdapter(this);
        binding.rvIncomingCommits.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvIncomingCommits.setAdapter(commitsAdapter);

        filesAdapter = new CommitFilesAdapter(this);
        binding.rvChangedFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChangedFiles.setAdapter(filesAdapter);
    }

    private void setupListeners() {
        binding.btnTabCommits.setOnClickListener(v -> switchTab(false));
        binding.btnTabFiles.setOnClickListener(v -> switchTab(true));

        binding.btnCloseReview.setOnClickListener(v -> dismiss());

        binding.btnPullAndMerge.setOnClickListener(v -> {
            dismiss();
            if (onPullRequested != null) {
                onPullRequested.run();
            }
        });
    }

    private void switchTab(boolean filesTab) {
        isFilesTabActive = filesTab;
        updateTabState(filesTab);
    }

    private void updateTabState(boolean filesTab) {
        Context context = getContext();
        if (context == null || binding == null) return;

        int activeBg = getThemeColor(com.google.android.material.R.attr.colorPrimaryContainer);
        int activeText = getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer);
        int inactiveBg = Color.TRANSPARENT;
        int inactiveText = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);

        if (filesTab) {
            binding.containerCommits.setVisibility(View.GONE);
            binding.containerFiles.setVisibility(View.VISIBLE);

            binding.btnTabFiles.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            binding.btnTabFiles.setTextColor(activeText);
            binding.btnTabFiles.setIconTint(ColorStateList.valueOf(activeText));

            binding.btnTabCommits.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
            binding.btnTabCommits.setTextColor(inactiveText);
            binding.btnTabCommits.setIconTint(ColorStateList.valueOf(inactiveText));
        } else {
            binding.containerCommits.setVisibility(View.VISIBLE);
            binding.containerFiles.setVisibility(View.GONE);

            binding.btnTabCommits.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            binding.btnTabCommits.setTextColor(activeText);
            binding.btnTabCommits.setIconTint(ColorStateList.valueOf(activeText));

            binding.btnTabFiles.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
            binding.btnTabFiles.setTextColor(inactiveText);
            binding.btnTabFiles.setIconTint(ColorStateList.valueOf(inactiveText));
        }
    }

    private int getThemeColor(int attrRes) {
        Context context = getContext();
        if (context == null) return 0;
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void loadComparisonData() {
        if (repository == null) return;

        binding.progressReviewLoading.setVisibility(View.VISIBLE);
        binding.tvReviewBranchesSubtitle.setText(String.format("%s ➔ %s", remoteBranch, localBranch));

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                GitRepository.BranchComparison comparison = repository.getBranchComparison(localBranch, remoteBranch);
                List<CommitItem> incomingCommits = repository.getIncomingCommits(localBranch, remoteBranch);

                String oldRef = "refs/heads/" + localBranch;
                String newRef = remoteBranch.startsWith("refs/") ? remoteBranch : "refs/remotes/" + remoteBranch;
                List<GitFileItem> changedFiles = repository.getChangedFilesBetweenRefs(oldRef, newRef);

                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.progressReviewLoading.setVisibility(View.GONE);

                    // Update sync status pill
                    if (comparison.getBehindCount() > 0) {
                        binding.tvSyncStatusBadge.setText(getString(R.string.vcode_incoming_commits_badge, comparison.getBehindCount()));
                        binding.tvSyncStatusBadge.setVisibility(View.VISIBLE);
                    } else if (comparison.isUpToDate()) {
                        binding.tvSyncStatusBadge.setText(R.string.vcode_sync_up_to_date);
                        binding.tvSyncStatusBadge.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvSyncStatusBadge.setVisibility(View.GONE);
                    }

                    // Update Tab button labels with counts
                    binding.btnTabCommits.setText(getString(R.string.vcode_tab_commits_count, incomingCommits.size()));
                    binding.btnTabFiles.setText(getString(R.string.vcode_tab_diff_count, changedFiles.size()));

                    // Commits empty state
                    if (incomingCommits.isEmpty()) {
                        binding.rvIncomingCommits.setVisibility(View.GONE);
                        binding.layoutEmptyCommits.setVisibility(View.VISIBLE);
                        binding.tvEmptyCommitsDesc.setText(getString(R.string.vcode_no_incoming_commits_desc, remoteBranch));
                    } else {
                        binding.rvIncomingCommits.setVisibility(View.VISIBLE);
                        binding.layoutEmptyCommits.setVisibility(View.GONE);
                        commitsAdapter.submitList(incomingCommits);
                    }

                    // Files empty state
                    if (changedFiles.isEmpty()) {
                        binding.rvChangedFiles.setVisibility(View.GONE);
                        binding.layoutEmptyFiles.setVisibility(View.VISIBLE);
                    } else {
                        binding.rvChangedFiles.setVisibility(View.VISIBLE);
                        binding.layoutEmptyFiles.setVisibility(View.GONE);
                        filesAdapter.submitList(changedFiles);
                    }
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.progressReviewLoading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), getString(R.string.vcode_failed_to_compare_branch, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // --- CommitHistoryAdapter Listener ---
    @Override
    public void onCommitClick(CommitItem item) {
        if (repository == null || repository.getRepoDir() == null) return;
        Intent inspectIntent = new Intent(getContext(), CommitDetailsActivity.class);
        inspectIntent.putExtra("project_path", repository.getRepoDir().getAbsolutePath());
        inspectIntent.putExtra("commit_sha", item.getSha());
        inspectIntent.putExtra("commit_msg", item.getMessage());
        inspectIntent.putExtra("commit_author", item.getAuthor());
        inspectIntent.putExtra("commit_time", item.getTimestamp());
        inspectIntent.putExtra(CommitDetailsActivity.EXTRA_READ_ONLY, true);
        startActivity(inspectIntent);
    }

    @Override
    public void onOverflowClick(CommitItem item, View anchor) {
        ClipboardManager cb = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("SHA", item.getSha()));
            Toast.makeText(getContext(), R.string.vcode_commit_sha_copied, Toast.LENGTH_SHORT).show();
        }
    }

    // --- CommitFilesAdapter Listener ---
    @Override
    public void onFileClick(GitFileItem item) {
        if (repository == null || repository.getRepoDir() == null) return;
        String oldRef = "refs/heads/" + localBranch;
        String newRef = remoteBranch.startsWith("refs/") ? remoteBranch : "refs/remotes/" + remoteBranch;

        startActivity(DiffViewerActivity.newIntent(
                requireContext(),
                repository.getRepoDir().getAbsolutePath(),
                oldRef,
                newRef,
                item
        ));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
