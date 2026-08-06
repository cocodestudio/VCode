package com.cocode.vcode.ide.ui.sheets.files;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetDeleteConfirmationBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * DeleteBottomSheet provides a generic confirmation interface for deleting various project assets.
 * It dynamically adjusts its messaging based on the {@link DeleteType} (Project, File, Folder, Snippet, or Branch).
 */
public class DeleteBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetDeleteConfirmationBinding binding;
    private ConfirmDeleteListener listener;
    private String itemName;
    private String customMessage;

    /**
     * The type of item being deleted, used to determine the visual labels.
     */
    private DeleteType deleteType = DeleteType.PROJECT;

    /**
     * Static helper to instantiate and display the deletion confirmation sheet.
     */
    public static void show(FragmentManager manager, DeleteType type, String itemName, String message, ConfirmDeleteListener listener) {
        DeleteBottomSheet sheet = new DeleteBottomSheet();
        sheet.setDeleteType(type);
        sheet.setItemName(itemName);
        sheet.setCustomMessage(message);
        sheet.setListener(listener);
        sheet.show(manager, "DeleteBottomSheet");
    }

    public void setListener(ConfirmDeleteListener listener) {
        this.listener = listener;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }

    public void setDeleteType(DeleteType type) {
        this.deleteType = type;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDeleteConfirmationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        setupDynamicUI();
        setupListeners();
    }

    /**
     * Configures the textual content of the sheet based on the item type and name.
     */
    private void setupDynamicUI() {
        // Resolve localized strings for the specific deletion target
        if (deleteType == DeleteType.PROJECT) {
            binding.tvDelete.setText(getString(R.string.vcode_delete_project, itemName));
            binding.tvDesc.setText(getString(R.string.vcode_action_cannot_be_undone, "project"));
        } else if (deleteType == DeleteType.FILE) {
            binding.tvDelete.setText(getString(R.string.vcode_delete_file, itemName));
            binding.tvDesc.setText(getString(R.string.vcode_action_cannot_be_undone, "file"));
        } else if (deleteType == DeleteType.FOLDER) {
            binding.tvDelete.setText(getString(R.string.vcode_delete_folder, itemName));
            binding.tvDesc.setText(getString(R.string.vcode_action_cannot_be_undone, "folder"));
        } else if (deleteType == DeleteType.SNIPPET) {
            binding.tvDelete.setText(getString(R.string.vcode_delete_snippet, itemName));
            binding.tvDesc.setText(getString(R.string.vcode_action_cannot_be_undone, "snippet"));
        } else if (deleteType == DeleteType.BRANCH) {
            binding.tvDelete.setText(getString(R.string.vcode_delete_branch, itemName));
            binding.tvDesc.setText(getString(R.string.vcode_action_cannot_be_undone, "branch"));
        } else if (deleteType == DeleteType.DISCARD) {
            binding.tvDelete.setText(itemName != null && !itemName.isEmpty() ? "Discard " + itemName + "?" : "Discard all unstaged changes?");
            binding.tvDesc.setText(R.string.vcode_this_action_cannot_be_undone_2);
            binding.btnDelete.setText(R.string.vcode_discard_3);
        } else if (deleteType == DeleteType.STASH) {
            binding.tvDelete.setText("Drop Stash " + itemName + "?");
            binding.tvDesc.setText(R.string.vcode_this_action_cannot_be_undone);
            binding.btnDelete.setText(R.string.vcode_drop);
        }

        // Apply custom message override if provided
        if (customMessage != null && !customMessage.isEmpty()) {
            binding.tvDesc.setText(customMessage);
        }
    }

    /**
     * Applies specialized UI fonts for a consistent aesthetic.
     */
    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvDelete.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvDesc.setTypeface(fm.getUiMedium(ctx));
        binding.btnDelete.setTypeface(fm.getUiSemiBold(ctx));
    }

    /**
     * Attaches logic to the delete confirmation button.
     */
    private void setupListeners() {
        binding.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteConfirmed();
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
     * Enumeration of supported deletion targets.
     */
    public enum DeleteType {
        PROJECT, FILE, FOLDER, SNIPPET, BRANCH, DISCARD, STASH
    }

    /**
     * Listener interface for confirming the deletion action.
     */
    public interface ConfirmDeleteListener {
        void onDeleteConfirmed();
    }
}