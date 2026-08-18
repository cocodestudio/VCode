package com.cocode.vcode.ide.ui.sheets.git;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.databinding.BottomSheetNewBranchBinding;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * NewBranchBottomSheet provides an interface for creating a new Git branch.
 * It allows users to specify the branch name and select a base branch to branch off from.
 */
public class NewBranchBottomSheet extends BaseBottomSheetDialogFragment {
    private BottomSheetNewBranchBinding binding;
    private GitViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = BottomSheetNewBranchBinding.inflate(i, c, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Bind to Activity-scoped ViewModel to execute the branch creation
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        android.content.Context context = requireContext();
        com.cocode.vcode.ide.utils.FontManager fm = com.cocode.vcode.ide.utils.FontManager.getInstance();
        binding.tvNewBranchTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvNewBranchSubtitle.setTypeface(fm.getUiMedium(context));
        binding.tvLabelBranchName.setTypeface(fm.getUiSemiBold(context));
        binding.tvLabelBaseBranch.setTypeface(fm.getUiSemiBold(context));
        binding.etBranchName.setTypeface(fm.getUiMedium(context));
        binding.tvCreateFromSelector.setTypeface(fm.getUiMedium(context));
        binding.btnCreateBranch.setTypeface(fm.getUiSemiBold(context));

        com.cocode.vcode.ide.utils.UiUtils.setViewRounded(binding.etBranchName, com.cocode.vcode.ide.utils.UiUtils.dpToPx(context, 10), androidx.core.content.ContextCompat.getColor(context, com.cocode.vcode.ide.R.color.vcode_bg_elevated));
        com.cocode.vcode.ide.utils.UiUtils.setViewRounded(binding.tvCreateFromSelector, com.cocode.vcode.ide.utils.UiUtils.dpToPx(context, 10), androidx.core.content.ContextCompat.getColor(context, com.cocode.vcode.ide.R.color.vcode_bg_elevated));

        setupDropdown();

        binding.btnCreateBranch.setOnClickListener(v -> {
            String name = Objects.requireNonNull(binding.etBranchName.getText()).toString().trim();
            String from = binding.tvCreateFromSelector.getText().toString();

            // Validate branch name against standard Git naming conventions (basic regex)
            if (!name.matches("^[a-zA-Z0-9._-]+$")) {
                binding.etBranchName.setError("Invalid name format (alphanumeric, dots, dashes, underscores)");
                binding.etBranchName.requestFocus();
                return;
            }

            viewModel.createBranch(name, from);
            dismiss();
        });
    }

    /**
     * Populates the "Create From" dropdown with the list of existing local branches.
     */
    private void setupDropdown() {
        viewModel.getLocalBranches().observe(getViewLifecycleOwner(), branches -> {
            if (branches == null) return;

            List<String> names = new ArrayList<>();
            for (BranchItem b : branches) names.add(b.getName());

            // Default to the first available branch (usually the current HEAD)
            if (!names.isEmpty() && binding.tvCreateFromSelector.getText().toString().isEmpty()) {
                binding.tvCreateFromSelector.setText(names.get(0));
            }

            binding.tvCreateFromSelector.setOnClickListener(v -> showBranchSelectionDialog(names));
        });
    }

    private void showBranchSelectionDialog(List<String> branchNames) {
        String currentSelection = binding.tvCreateFromSelector.getText().toString();
        com.cocode.vcode.ide.ui.dialogs.CustomListDialog.show(
                requireContext(),
                "Select Base Branch",
                branchNames,
                currentSelection,
                selected -> binding.tvCreateFromSelector.setText(selected)
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}