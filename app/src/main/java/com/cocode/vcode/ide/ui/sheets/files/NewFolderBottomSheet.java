package com.cocode.vcode.ide.ui.sheets.files;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetCreateFolderBinding;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

import java.io.File;

/**
 * NewFolderBottomSheet provides an interface for creating a new directory in the workspace.
 * It displays the creation context via a breadcrumb and ensures the keyboard is focused for input.
 */
public class NewFolderBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_PARENT_DIR = "arg_parent_dir";

    private BottomSheetCreateFolderBinding binding;
    private EditorViewModel viewModel;

    /**
     * The parent directory where the new folder will be created.
     */
    private File parentDir;

    /**
     * Creates a new instance of the sheet for a specific parent directory.
     */
    public static NewFolderBottomSheet newInstance(File parentDir) {
        NewFolderBottomSheet sheet = new NewFolderBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PARENT_DIR, parentDir);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Extract the parent directory from the argument bundle
        if (getArguments() != null) {
            parentDir = (File) getArguments().getSerializable(ARG_PARENT_DIR);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCreateFolderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        // Bind to Activity-scoped EditorViewModel for file operations
        viewModel = new ViewModelProvider(requireActivity()).get(EditorViewModel.class);

        // Apply visual styling
        UiUtils.setViewRounded(binding.etFolderName, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        setupTypefaces(context);
        setupBreadcrumb();
        setupInitialState();
        setupListeners();
    }

    /**
     * Applies branding fonts to textual components.
     */
    private void setupTypefaces(Context context) {
        FontManager fm = FontManager.getInstance();
        binding.tvCreateProject.setTypeface(fm.getUiSemiBold(context));
        binding.tvFolderNameLabel.setTypeface(fm.getUiMedium(context));
        binding.etFolderName.setTypeface(fm.getUiMedium(context));
        binding.btnCreateProject.setTypeface(fm.getUiSemiBold(context));
    }

    /**
     * Configures the breadcrumb display to show the target creation path.
     */
    private void setupBreadcrumb() {
        if (parentDir != null && viewModel != null) {
            String relativePath = getRelativePath(parentDir);
            binding.breadcrumb.setPath(viewModel.getProjectName(), relativePath);
        }
    }

    /**
     * Focuses the input field and explicitly requests the soft keyboard.
     */
    private void setupInitialState() {
        binding.etFolderName.requestFocus();
        binding.etFolderName.postDelayed(() -> {
            if (getContext() != null) {
                com.cocode.vcode.ide.utils.UiUtils.showKeyboard(binding.etFolderName);
            }
        }, 200);
    }

    /**
     * Attaches logic to the create folder button with basic validation.
     */
    private void setupListeners() {
        binding.btnCreateProject.setOnClickListener(v -> {
            String folderName = binding.etFolderName.getText() != null ? binding.etFolderName.getText().toString().trim() : "";

            if (folderName.isEmpty()) {
                binding.etFolderName.setError("Folder name is required");
                binding.etFolderName.requestFocus();
                return;
            }

            if (parentDir != null && viewModel != null) {
                // Delegate folder creation to the ViewModel
                viewModel.createDirectory(parentDir, folderName);
                dismiss();
            } else {
                Toast.makeText(requireContext(), R.string.vcode_unable_to_create_folder_please, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Resolves the workspace-relative path for the parent directory.
     */
    private String getRelativePath(File file) {
        File projectRoot = viewModel.getProjectRoot();
        if (projectRoot == null) return file.getName();
        String rootPath = projectRoot.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            String rel = filePath.substring(rootPath.length());
            if (rel.startsWith(File.separator)) {
                rel = rel.substring(1);
            }
            return rel.replace('\\', '/');
        }
        return file.getName();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}