package com.cocode.vcode.ide.ui.sheets.files;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.template.FileTemplate;
import com.cocode.vcode.ide.core.template.FileTemplateManager;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.databinding.BottomSheetCreateNewFileBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;

/**
 * NewFileBottomSheet provides a clean, focused workflow for creating a new file.
 * File extensions are automatically matched against configurable templates in FileTemplateManager,
 * populating the file with evaluated boilerplate and dynamic {fileName} and {projectName} placeholders.
 */
public class NewFileBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_PROJECT_NAME = "arg_project_name";
    private static final String ARG_PARENT_PATH = "arg_parent_path";

    private BottomSheetCreateNewFileBinding binding;
    private NewFileListener listener;
    private String projectName;
    private String parentPath;

    public static NewFileBottomSheet newInstance() {
        return new NewFileBottomSheet();
    }

    public static NewFileBottomSheet newInstance(@Nullable String projectName) {
        return newInstance(null, projectName);
    }

    public static NewFileBottomSheet newInstance(@Nullable File parentDir, @Nullable String projectName) {
        NewFileBottomSheet sheet = new NewFileBottomSheet();
        Bundle args = new Bundle();
        if (parentDir != null) {
            args.putString(ARG_PARENT_PATH, parentDir.getAbsolutePath());
        }
        args.putString(ARG_PROJECT_NAME, projectName);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(NewFileListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectName = getArguments().getString(ARG_PROJECT_NAME);
            parentPath = getArguments().getString(ARG_PARENT_PATH);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCreateNewFileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        setupListeners();
    }

    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvCreateNewFile.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvChooseNameExtension.setTypeface(fm.getUiFont(ctx));
        binding.tvFileNameLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etFileName.setTypeface(fm.getUiMedium(ctx));
        binding.btnCreateFile.setTypeface(fm.getUiSemiBold(ctx));

        UiUtils.setViewRounded(binding.etFileName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    private void setupListeners() {
        binding.etFileName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.etFileName.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etFileName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createFile();
                return true;
            }
            return false;
        });

        binding.btnCreateFile.setOnClickListener(v -> createFile());
    }

    private void createFile() {
        String name = binding.etFileName.getText() != null
                ? binding.etFileName.getText().toString().trim()
                : "";

        if (name.isEmpty()) {
            binding.etFileName.setError(getString(R.string.vcode_file_name_cannot_be_empty));
            return;
        }

        String ext = FileTemplateManager.getExtension(name);
        FileTemplate template = FileTemplateManager.getInstance(requireContext()).getTemplateForExtension(ext);
        String initialContent = "";
        if (template != null) {
            String resolvedProject = resolveActualProjectName();
            initialContent = FileTemplateManager.evaluateTemplate(template.getContent(), name, resolvedProject);
        }

        if (listener != null) {
            listener.onCreateFile(name, initialContent);
        }
        dismiss();
    }

    private String resolveActualProjectName() {
        if (parentPath != null) {
            File parentDir = new File(parentPath);
            String fromMeta = ProjectRepository.getProjectName(parentDir);
            if (!fromMeta.isEmpty() && !fromMeta.equals(parentDir.getName())) {
                return fromMeta;
            }
        }
        if (projectName != null && !projectName.trim().isEmpty()) {
            return projectName.trim();
        }
        if (parentPath != null) {
            return ProjectRepository.getProjectName(new File(parentPath));
        }
        return "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface NewFileListener {
        void onCreateFile(String fileName, String initialContent);
    }
}