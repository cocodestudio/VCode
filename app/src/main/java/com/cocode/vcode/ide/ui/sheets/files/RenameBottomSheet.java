package com.cocode.vcode.ide.ui.sheets.files;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetRenameBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

/**
 * RenameBottomSheet provides a specialized input form for renaming various project entities.
 * It intelligently selects text to exclude extensions when renaming files and
 * adapts its labels based on the {@link RenameType} (Project, File, Folder, or Branch).
 */
public class RenameBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetRenameBinding binding;
    private RenameListener listener;
    private String currentName;

    /**
     * The type of entity being renamed, used to configure labels and hints.
     */
    private RenameType renameType = RenameType.PROJECT;

    /**
     * Static helper to instantiate and show the rename sheet.
     *
     * @param manager     The FragmentManager to host the sheet.
     * @param type        The type of item being renamed.
     * @param currentName The existing name of the item.
     * @param listener    Callback for the rename event.
     */
    public static void show(FragmentManager manager, RenameType type, String currentName, RenameListener listener) {
        RenameBottomSheet sheet = new RenameBottomSheet();
        sheet.setRenameType(type);
        sheet.setCurrentName(currentName);
        sheet.setListener(listener);
        sheet.show(manager, "RenameBottomSheet");
    }

    public void setListener(RenameListener listener) {
        this.listener = listener;
    }

    public void setCurrentName(String currentName) {
        this.currentName = currentName;
    }

    public void setRenameType(RenameType type) {
        this.renameType = type;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetRenameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        setupDynamicUI();
        setupInitialState();
        setupListeners();
    }

    /**
     * Updates labels and hints based on the RenameType.
     */
    private void setupDynamicUI() {
        Context context = requireContext();
        if (renameType == RenameType.PROJECT) {
            binding.tvRenameProject.setText(context.getString(R.string.vcode_rename_project));
            binding.tvProjectNameLabel.setText(context.getString(R.string.project_name));
            binding.etProjectName.setHint(context.getString(R.string.e_g_my_awesome_project));
        } else if (renameType == RenameType.FILE) {
            binding.tvRenameProject.setText(context.getString(R.string.vcode_rename_file));
            binding.tvProjectNameLabel.setText(context.getString(R.string.file_name));
            binding.etProjectName.setHint(context.getString(R.string.e_g_index_file));
        } else if (renameType == RenameType.FOLDER) {
            binding.tvRenameProject.setText(context.getString(R.string.vcode_rename_folder));
            binding.tvProjectNameLabel.setText(context.getString(R.string.folder_name));
            binding.etProjectName.setHint(context.getString(R.string.e_g_assets));
        } else if (renameType == RenameType.BRANCH) {
            binding.tvRenameProject.setText(context.getString(R.string.vcode_rename_branch));
            binding.tvProjectNameLabel.setText(context.getString(R.string.branch_name));
            binding.etProjectName.setHint(context.getString(R.string.e_g_feature_branch));
        }
    }

    /**
     * Applies branding fonts and rounded corners to the input field.
     */
    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvRenameProject.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProjectNameLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etProjectName.setTypeface(fm.getUiMedium(ctx));
        binding.btnRenameProject.setTypeface(fm.getUiSemiBold(ctx));

        UiUtils.setViewRounded(binding.etProjectName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    /**
     * Populates the input field and manages the selection logic.
     * When renaming files, it only selects the base name (excluding extension) for convenience.
     */
    private void setupInitialState() {
        if (currentName != null && !currentName.isEmpty()) {
            binding.etProjectName.setText(currentName);

            int dotIndex = currentName.lastIndexOf('.');

            // Smart selection: skip the extension if renaming a file
            if (renameType == RenameType.FILE && dotIndex > 0) {
                binding.etProjectName.setSelection(0, dotIndex);
            } else {
                binding.etProjectName.selectAll();
            }
        }

        binding.etProjectName.requestFocus();

        // Show soft keyboard with a slight delay to ensure the window is ready
        binding.etProjectName.postDelayed(() -> {
            if (getContext() != null) {
                com.cocode.vcode.ide.utils.UiUtils.showKeyboard(binding.etProjectName);
            }
        }, 200);
    }

    /**
     * Attaches logic to the rename button with verification for name changes.
     */
    private void setupListeners() {
        binding.btnRenameProject.setOnClickListener(v -> {
            String newName = binding.etProjectName.getText() != null ? binding.etProjectName.getText().toString().trim() : "";

            if (newName.isEmpty()) {
                binding.etProjectName.setError("Name is required");
                binding.etProjectName.requestFocus();
                return;
            }

            // If the name hasn't changed, just close the sheet
            if (newName.equals(currentName)) {
                dismiss();
                return;
            }

            if (listener != null) {
                listener.onRename(newName);
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Enumeration of supported rename operations.
     */
    public enum RenameType {
        PROJECT, FILE, FOLDER, BRANCH
    }

    /**
     * Callback interface for the rename event.
     */
    public interface RenameListener {
        void onRename(String newName);
    }
}