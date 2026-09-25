package com.cocode.vcode.ide.ui.git.tabs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.widget.Toast;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.FragmentGitBranchBinding;
import com.cocode.vcode.ide.git.adapters.BranchAdapter;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.ui.dialogs.MergeConfirmDialog;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.RenameBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitAuthorInfoBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitConflictBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitFetchReviewBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitOptionsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.NewBranchBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * GitBranchFragment manages the local branch list for the repository.
 * It allows users to switch branches, create new ones, and perform operations
 * like merging, renaming, and deleting branches.
 */
public class GitBranchFragment extends Fragment implements BranchAdapter.BranchListener {
    private FragmentGitBranchBinding binding;
    private GitViewModel viewModel;
    private BranchAdapter adapter;
    private BranchAdapter remoteAdapter;
    private boolean isRemoteExpanded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentGitBranchBinding.inflate(i, c, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        // Shared Activity-scoped ViewModel to coordinate Git state
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        adapter = new BranchAdapter(this);
        binding.rvBranches.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvBranches.setAdapter(adapter);

        remoteAdapter = new BranchAdapter(this);
        binding.rvRemoteBranches.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvRemoteBranches.setAdapter(remoteAdapter);

        setupTypefaces();
        setupListeners();
        observeData();
    }

    /**
     * Applies specialized UI fonts to labels and buttons.
     */
    private void setupTypefaces() {
        Context context = requireContext();
        binding.tvCurrentBranchLabel.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvActiveBranchPill.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvBranchesListLabel.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.fabNewBranch.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvRemoteBranchesLabel.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvRemoteBranchCount.setTypeface(FontManager.getInstance().getUiMedium(context));
    }

    /**
     * Initializes click listeners for creating new branches.
     */
    private void setupListeners() {
        binding.fabNewBranch.setOnClickListener(v ->
                new NewBranchBottomSheet().show(getChildFragmentManager(), "new_branch"));

        binding.layoutRemoteHeader.setOnClickListener(v -> {
            isRemoteExpanded = !isRemoteExpanded;
            if (isRemoteExpanded && remoteAdapter.getItemCount() > 0) {
                binding.rvRemoteBranches.setVisibility(View.VISIBLE);
                binding.tvNoRemoteBranches.setVisibility(View.GONE);
            } else if (isRemoteExpanded && remoteAdapter.getItemCount() == 0) {
                binding.rvRemoteBranches.setVisibility(View.GONE);
                binding.tvNoRemoteBranches.setVisibility(View.VISIBLE);
            } else {
                binding.rvRemoteBranches.setVisibility(View.GONE);
                binding.tvNoRemoteBranches.setVisibility(View.GONE);
            }
            binding.ivRemoteChevron.animate().rotation(isRemoteExpanded ? 0f : -90f).setDuration(200).start();
        });
    }

    /**
     * Connects UI observers to the ViewModel's branch-related streams.
     */
    private void observeData() {
        // Observe the current HEAD branch name for the header display
        viewModel.getCurrentBranch().observe(getViewLifecycleOwner(), branchName -> {
            if (branchName != null && !branchName.trim().isEmpty()) {
                binding.tvActiveBranchPill.setText(branchName);
            }
        });

        // Observe the list of local branches to populate the recycler view
        viewModel.getLocalBranches().observe(getViewLifecycleOwner(), branches -> {
            if (branches != null) {
                adapter.submitList(branches);
            }
        });

        viewModel.getRemoteBranches().observe(getViewLifecycleOwner(), branches -> {
            if (branches != null) {
                remoteAdapter.submitList(branches);
                binding.tvRemoteBranchCount.setText("(" + branches.size() + ")");
                if (isRemoteExpanded) {
                    if (branches.isEmpty()) {
                        binding.rvRemoteBranches.setVisibility(View.GONE);
                        binding.tvNoRemoteBranches.setVisibility(View.VISIBLE);
                    } else {
                        binding.rvRemoteBranches.setVisibility(View.VISIBLE);
                        binding.tvNoRemoteBranches.setVisibility(View.GONE);
                    }
                } else {
                    binding.rvRemoteBranches.setVisibility(View.GONE);
                    binding.tvNoRemoteBranches.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getConflictEvent().observe(getViewLifecycleOwner(), conflict -> {
            if (conflict != null) {
                GitConflictBottomSheet.show(getChildFragmentManager(),
                        viewModel.getRepository(),
                        conflict.getConflictingFiles(),
                        () -> viewModel.refreshAll());
                viewModel.clearConflictEvent();
            }
        });
    }

    @Override
    public void onBranchClick(BranchItem item) {
        if (item.isRemote()) {
            viewModel.checkoutRemoteAsBranch(item.getName());
            Toast.makeText(requireContext(), getString(R.string.vcode_branch_switched_to, item.getName()), Toast.LENGTH_SHORT).show();
        } else if (!item.isActive()) {
            viewModel.checkoutBranch(item.getName());
        }
    }

    @Override
    public void onOverflowClick(BranchItem item, View anchor) {
        GitOptionsBottomSheet optionsSheet = GitOptionsBottomSheet.newInstance(
                getString(R.string.vcode_branch_options_title, item.getName()));

        if (item.isRemote()) {
            optionsSheet.addOption(getString(R.string.vcode_checkout_as_local), R.drawable.ic_code_branch, () -> {
                viewModel.checkoutRemoteAsBranch(item.getName());
                Toast.makeText(requireContext(), getString(R.string.vcode_branch_switched_to, item.getName()), Toast.LENGTH_SHORT).show();
            });
            optionsSheet.addOption(getString(R.string.vcode_inspect_commits), R.drawable.ic_rotate_left, () -> {
                showRemoteBranchReview(item.getName());
            });
            optionsSheet.addOption(getString(R.string.vcode_diff_with_current), R.drawable.ic_file_lines, () -> {
                showRemoteBranchReview(item.getName());
            });
            optionsSheet.addOption(getString(R.string.vcode_merge_into_current), R.drawable.ic_code_merge, () -> {
                String currentActiveHead = viewModel.getCurrentBranch().getValue();
                if (currentActiveHead == null || currentActiveHead.trim().isEmpty()) {
                    currentActiveHead = "active branch";
                }
                MergeConfirmDialog.show(
                        requireContext(),
                        item.getName(),
                        currentActiveHead,
                        () -> performMerge(item.getName())
                );
            });
            optionsSheet.show(getChildFragmentManager(), "BranchOptionsSheet");
            return;
        }

        if (!item.isActive()) {
            optionsSheet.addOption(getString(R.string.vcode_checkout_branch), R.drawable.ic_right_from_bracket, () -> viewModel.checkoutBranch(item.getName()));
        }

        // Only allow merging if the item is NOT active AND there are at least 2 local branches
        if (!item.isActive() && adapter.getItemCount() > 1) {
            optionsSheet.addOption(getString(R.string.vcode_merge_into_current), R.drawable.ic_code_merge, () -> {
                String currentActiveHeadBranch = viewModel.getCurrentBranch().getValue();
                if (currentActiveHeadBranch == null || currentActiveHeadBranch.trim().isEmpty()) {
                    currentActiveHeadBranch = "active head pointer";
                }

                // Confirm merge operation before execution
                MergeConfirmDialog.show(
                        requireContext(),
                        item.getName(),
                        currentActiveHeadBranch,
                        () -> performMerge(item.getName())
                );
            });
        }
        optionsSheet.addOption(getString(R.string.vcode_rename_branch), R.drawable.ic_pen, () -> showRenameDialog(item));

        if (!item.isActive()) {
            optionsSheet.addOption(getString(R.string.vcode_action_delete_branch), R.drawable.ic_trash, () -> showDeleteConfirm(item));
        }

        optionsSheet.show(getChildFragmentManager(), "BranchOptionsSheet");
    }

    private void showRemoteBranchReview(String remoteBranchName) {
        String activeLocal = viewModel.getCurrentBranch().getValue();
        if (activeLocal == null || activeLocal.isEmpty()) {
            activeLocal = "main";
        }
        GitFetchReviewBottomSheet.show(
                getChildFragmentManager(),
                viewModel.getRepository(),
                activeLocal,
                remoteBranchName,
                () -> performMerge(remoteBranchName)
        );
    }

    /**
     * Checks if author details are configured before performing a merge.
     * If neither GitHub credentials nor local author metadata exists, prompts with GitAuthorInfoBottomSheet.
     */
    private void performMerge(String branchName) {
        if (viewModel.shouldPromptForAuthor()) {
            GitAuthorInfoBottomSheet sheet = GitAuthorInfoBottomSheet.newInstance(
                    "", "", getString(R.string.vcode_btn_save_and_continue));
            sheet.setListener((name, email) -> {
                viewModel.saveLocalAuthor(name, email);
                viewModel.mergeBranch(branchName);
            });
            sheet.show(getChildFragmentManager(), "GitAuthorInfoBottomSheet");
        } else {
            viewModel.mergeBranch(branchName);
        }
    }

    /**
     * Launches the rename dialog for a specific branch.
     */
    private void showRenameDialog(BranchItem item) {
        RenameBottomSheet.show(
                getChildFragmentManager(),
                RenameBottomSheet.RenameType.BRANCH,
                item.getName(),
                newName -> viewModel.renameBranch(item.getName(), newName)
        );
    }

    /**
     * Launches the deletion confirmation dialog for a specific branch.
     */
    private void showDeleteConfirm(BranchItem item) {
        DeleteBottomSheet.show(
                getChildFragmentManager(),
                DeleteBottomSheet.DeleteType.BRANCH,
                item.getName(),
                null,
                () -> viewModel.deleteBranch(item.getName())
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}