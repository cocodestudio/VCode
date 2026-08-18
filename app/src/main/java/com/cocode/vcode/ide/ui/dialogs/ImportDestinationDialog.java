package com.cocode.vcode.ide.ui.dialogs;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.DialogImportDestinationBinding;
import com.cocode.vcode.ide.ui.filetree.DestinationAdapter;
import com.cocode.vcode.ide.utils.FontManager;

import java.io.File;
import java.util.List;

public class ImportDestinationDialog extends DialogFragment {

    private File projectRoot;
    private String projectName;
    private List<FileNode> fileTree;
    private OnDestinationSelectedListener listener;
    private File selectedFile;

    public static ImportDestinationDialog newInstance(File projectRoot, String projectName, List<FileNode> fileTree) {
        ImportDestinationDialog dialog = new ImportDestinationDialog();
        dialog.projectRoot = projectRoot;
        dialog.projectName = projectName;
        dialog.fileTree = fileTree;
        return dialog;
    }

    public void setListener(OnDestinationSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogImportDestinationBinding binding = DialogImportDestinationBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(binding.getRoot())
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        binding.tvDialogTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));

        binding.rvDestinationFolders.setLayoutManager(new LinearLayoutManager(getContext()));

        selectedFile = projectRoot;

        DestinationAdapter destAdapter = new DestinationAdapter(file -> {
            selectedFile = file;
        }, 16, getResources().getDisplayMetrics().density);

        binding.rvDestinationFolders.setAdapter(destAdapter);
        destAdapter.setTree(projectRoot, projectName, fileTree);

        binding.btnCancel.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        binding.btnConfirm.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelected(selectedFile);
            }
            dismiss();
        });

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int screenWidth = requireContext().getResources().getDisplayMetrics().widthPixels;
            int maxWidth = requireContext().getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public interface OnDestinationSelectedListener {
        void onSelected(File destination);
    }
}
