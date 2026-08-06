package com.cocode.vcode.ide.ui.sheets.git;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.repository.SettingsRepository;
import com.cocode.vcode.ide.databinding.BottomSheetResetConfirmBinding;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsActivity;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsViewModel;
import com.cocode.vcode.ide.ui.dialogs.HardResetConfirmDialog;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * ResetConfirmBottomSheet allows users to perform Git resets to a specific commit.
 * It supports Soft, Mixed, and Hard reset modes, and includes a mandatory safety
 * confirmation dialog for destructive Hard resets.
 */
public class ResetConfirmBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetResetConfirmBinding binding;

    /**
     * The target commit to reset to.
     */
    private CommitItem commit;

    /**
     * Creates a new instance of the sheet targeting a specific commit.
     */
    public static ResetConfirmBottomSheet newInstance(CommitItem item) {
        ResetConfirmBottomSheet s = new ResetConfirmBottomSheet();
        s.commit = item;
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = BottomSheetResetConfirmBinding.inflate(i, c, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();

        setupTypefaces(context);

        // Populate commit metadata
        binding.tvCommitSha.setText(commit.getSha());
        binding.tvCommitMsg.setText(commit.getMessage());

        // Toggle warning visibility based on the selected reset mode
        binding.rgResetMode.setOnCheckedChangeListener((group, id) -> {
            boolean isHard = id == R.id.rb_hard;
            binding.tvHardWarning.setVisibility(isHard ? View.VISIBLE : View.GONE);
            binding.btnConfirmReset.setAlpha(isHard ? 0.8f : 1.0f);
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());

        binding.btnConfirmReset.setOnClickListener(v -> {
            int id = binding.rgResetMode.getCheckedRadioButtonId();

            if (id == R.id.rb_soft) {
                executeSoftReset();
                dismiss();
            } else if (id == R.id.rb_mixed) {
                executeMixedReset();
                dismiss();
            } else if (id == R.id.rb_hard) {
                // Hard resets require an extra layer of confirmation if enabled in settings
                SettingsRepository settingsRepo = new SettingsRepository(context);
                AppSettings settings = settingsRepo.loadSettings();

                if (settings != null && settings.gitConfirmHardReset) {
                    HardResetConfirmDialog.show(context, () -> {
                        executeHardReset();
                        dismiss();
                    });
                } else {
                    executeHardReset();
                    dismiss();
                }
            }
        });
    }

    /**
     * Executes a Soft reset (keeps changes staged).
     */
    private void executeSoftReset() {
        if (getActivity() instanceof CommitDetailsActivity) {
            new ViewModelProvider(requireActivity()).get(CommitDetailsViewModel.class).softReset(commit.getSha());
        } else {
            new ViewModelProvider(requireActivity()).get(GitViewModel.class).softReset(commit.getSha());
        }
    }

    /**
     * Executes a Mixed reset (keeps changes unstaged).
     */
    private void executeMixedReset() {
        if (getActivity() instanceof CommitDetailsActivity) {
            new ViewModelProvider(requireActivity()).get(CommitDetailsViewModel.class).mixedReset(commit.getSha());
        } else {
            new ViewModelProvider(requireActivity()).get(GitViewModel.class).mixedReset(commit.getSha());
        }
    }

    /**
     * Executes a Hard reset (discards all uncommitted changes).
     */
    private void executeHardReset() {
        if (getActivity() instanceof CommitDetailsActivity) {
            new ViewModelProvider(requireActivity()).get(CommitDetailsViewModel.class).hardReset(commit.getSha());
        } else {
            new ViewModelProvider(requireActivity()).get(GitViewModel.class).hardReset(commit.getSha());
        }
    }

    /**
     * Applies branding fonts to all UI elements.
     */
    private void setupTypefaces(Context context) {
        FontManager fm = FontManager.getInstance();
        binding.tvResetCommit.setTypeface(fm.getUiSemiBold(context));
        binding.tvCommitSha.setTypeface(fm.getUiFont(context));
        binding.tvCommitMsg.setTypeface(fm.getUiMedium(context));
        binding.tvHardWarning.setTypeface(fm.getUiMedium(context));
        binding.btnCancel.setTypeface(fm.getUiSemiBold(context));
        binding.btnConfirmReset.setTypeface(fm.getUiSemiBold(context));

        binding.rbSoft.setTypeface(fm.getUiMedium(context));
        binding.rbMixed.setTypeface(fm.getUiMedium(context));
        binding.rbHard.setTypeface(fm.getUiMedium(context));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}