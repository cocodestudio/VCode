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

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.FragmentGitBranchBinding;
import com.cocode.vcode.ide.git.adapters.BranchAdapter;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.ui.dialogs.MergeConfirmDialog;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.RenameBottomSheet;
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
    }

    @Override
    public void onBranchClick(BranchItem item) {
        // Trigger a checkout if the user clicks a non-active branch
        if (!item.isActive()) viewModel.checkoutBranch(item.getName());
    }

    @Override
    public void onOverflowClick(BranchItem item, View anchor) {
        // Build and display a context menu for branch-specific actions
        GitOptionsBottomSheet optionsSheet = GitOptionsBottomSheet.newInstance("Branch " + item.getName());

        if (item.isRemote()) {
            optionsSheet.addOption("Checkout as local", R.drawable.ic_code_branch, () -> viewModel.checkoutRemoteAsBranch(item.getName()));
            optionsSheet.show(getChildFragmentManager(), "BranchOptionsSheet");
            return;
        }

        if (!item.isActive()) {
            optionsSheet.addOption("Checkout Branch", R.drawable.ic_right_from_bracket, () -> viewModel.checkoutBranch(item.getName()));
        }

        optionsSheet.addOption("Merge into current", R.drawable.ic_code_merge, () -> {
            String currentActiveHeadBranch = viewModel.getCurrentBranch().getValue();
            if (currentActiveHeadBranch == null || currentActiveHeadBranch.trim().isEmpty()) {
                currentActiveHeadBranch = "active head pointer";
            }

            // Confirm merge operation before execution
            MergeConfirmDialog.show(
                    requireContext(),
                    item.getName(),
                    currentActiveHeadBranch,
                    () -> viewModel.mergeBranch(item.getName())
            );
        });
        optionsSheet.addOption("Rename Branch", R.drawable.ic_pen, () -> showRenameDialog(item));

        if (!item.isActive()) {
            optionsSheet.addOption("Delete Branch", R.drawable.ic_trash, () -> showDeleteConfirm(item));
        }

        optionsSheet.show(getChildFragmentManager(), "BranchOptionsSheet");
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