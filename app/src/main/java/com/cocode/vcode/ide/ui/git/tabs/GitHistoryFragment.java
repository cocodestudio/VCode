package com.cocode.vcode.ide.ui.git.tabs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.FragmentGitHistoryBinding;
import com.cocode.vcode.ide.git.adapters.CommitHistoryAdapter;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsActivity;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.git.GitConflictBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitOptionsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.ResetConfirmBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * GitHistoryFragment provides a visual timeline of the repository's commit history.
 * It supports searching commits by message or SHA, and offers context actions like
 * cherry-picking, reverting, and resetting.
 */
public class GitHistoryFragment extends Fragment implements CommitHistoryAdapter.CommitHistoryListener {
    private FragmentGitHistoryBinding binding;
    private GitViewModel viewModel;
    private CommitHistoryAdapter adapter;

    /**
     * Local cache of the full history to enable efficient client-side filtering.
     */
    private List<CommitItem> fullHistory = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentGitHistoryBinding.inflate(i, c, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        // Shared ViewModel ensures synchronization with other Git tabs
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        adapter = new CommitHistoryAdapter(this);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvHistory.setAdapter(adapter);

        // Apply visual styling to the search input
        UiUtils.setViewRounded(binding.searchBarLayout, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));

        setupTypefaces();

        // Implement real-time history filtering
        binding.etSearchHistory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                filterHistory(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        // Observe the commit history stream and update the list or empty state UI
        viewModel.getConflictEvent().observe(getViewLifecycleOwner(), conflict -> {
            if (conflict != null) {
                GitConflictBottomSheet.show(getChildFragmentManager(),
                        viewModel.getRepository(),
                        conflict.getConflictingFiles(),
                        () -> viewModel.refreshAll());
                viewModel.clearConflictEvent();
            }
        });

        viewModel.getCommitHistory().observe(getViewLifecycleOwner(), commits -> {
            fullHistory = commits;
            adapter.submitList(commits);

            if (commits == null || commits.isEmpty()) {
                binding.searchBarLayout.setVisibility(View.GONE);
                binding.rvHistory.setVisibility(View.GONE);
                binding.layoutEmptyHistory.setVisibility(View.VISIBLE);
            } else {
                binding.searchBarLayout.setVisibility(View.VISIBLE);
                binding.rvHistory.setVisibility(View.VISIBLE);
                binding.layoutEmptyHistory.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Refreshes the repository state whenever the fragment is resumed.
     * This ensures the history is up-to-date after external operations or tab switches.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshAll();
        }
    }

    /**
     * Applies branding fonts to textual UI elements.
     */
    private void setupTypefaces() {
        Context context = requireContext();
        binding.etSearchHistory.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.tvEmptyHistoryTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvEmptyHistoryDesc.setTypeface(FontManager.getInstance().getUiMedium(context));
    }

    /**
     * Filters the commit history list locally based on the search query.
     * Searches both commit messages and SHA identifiers.
     */
    private void filterHistory(String query) {
        if (query.isEmpty()) {
            adapter.submitList(fullHistory);
            return;
        }
        List<CommitItem> filtered = new ArrayList<>();
        for (CommitItem item : fullHistory) {
            if (item.getMessage().toLowerCase().contains(query.toLowerCase()) ||
                    item.getSha().contains(query)) {
                filtered.add(item);
            }
        }
        adapter.submitList(filtered);
    }

    @Override
    public void onOverflowClick(CommitItem item, View anchor) {
        // Build and display a context menu for the selected commit
        GitOptionsBottomSheet.newInstance("Commit " + item.getSha())
                .addOption("Revert", R.drawable.ic_rotate_left, () -> viewModel.revertCommit(item.getSha()))
                .addOption("Reset to here", R.drawable.ic_rotate_left, () ->
                        ResetConfirmBottomSheet.newInstance(item).show(getChildFragmentManager(), "reset"))
                .addOption("Copy SHA", R.drawable.ic_copy, () -> {
                    ClipboardManager cb = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) {
                        cb.setPrimaryClip(ClipData.newPlainText("SHA", item.getSha()));
                        Toast.makeText(getContext(), R.string.vcode_commit_sha_copied, Toast.LENGTH_SHORT).show();
                    }
                })
                .show(getChildFragmentManager(), "CommitOptionsSheet");
    }

    @Override
    public void onCommitClick(CommitItem item) {
        // Navigate to the detailed view for the selected commit
        Intent inspectIntent = new Intent(getContext(), CommitDetailsActivity.class);
        inspectIntent.putExtra("project_path", requireActivity().getIntent().getStringExtra("project_path"));
        inspectIntent.putExtra("commit_sha", item.getSha());
        inspectIntent.putExtra("commit_msg", item.getMessage());
        inspectIntent.putExtra("commit_author", item.getAuthor());
        inspectIntent.putExtra("commit_time", item.getTimestamp());
        startActivity(inspectIntent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}