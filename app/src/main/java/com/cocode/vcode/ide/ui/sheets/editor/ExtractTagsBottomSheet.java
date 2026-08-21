package com.cocode.vcode.ide.ui.sheets.editor;

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
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.TagExtractor;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Bottom sheet dialog for viewing HTML tag statistics and structure extracted from the active document.
 */
public class ExtractTagsBottomSheet extends BaseBottomSheetDialogFragment {

    public interface ExtractListener {
        void onExtract(String targetFilename, TagExtractor.Type type);
    }

    private BottomSheetRenameBinding binding;
    private ExtractListener listener;
    private TagExtractor.Type extractType;

    public static void show(FragmentManager manager, TagExtractor.Type type, ExtractListener listener) {
        ExtractTagsBottomSheet sheet = new ExtractTagsBottomSheet();
        sheet.setExtractType(type);
        sheet.setListener(listener);
        sheet.show(manager, "ExtractTagsBottomSheet");
    }

    public void setExtractType(TagExtractor.Type type) {
        this.extractType = type;
    }

    public void setListener(ExtractListener listener) {
        this.listener = listener;
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

    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvRenameProject.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProjectNameLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etProjectName.setTypeface(fm.getUiMedium(ctx));
        binding.btnRenameProject.setTypeface(fm.getUiSemiBold(ctx));

        UiUtils.setViewRounded(binding.etProjectName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    private void setupDynamicUI() {
        Context context = requireContext();
        if (extractType == TagExtractor.Type.STYLE) {
            binding.tvRenameProject.setText("Extract Inline Styles");
            binding.etProjectName.setHint("e.g. styles.css");
        } else {
            binding.tvRenameProject.setText("Extract Inline Scripts");
            binding.etProjectName.setHint("e.g. script.js");
        }
        binding.tvProjectNameLabel.setText("Target File Name");
        binding.btnRenameProject.setText("Extract");
    }

    private void setupInitialState() {
        if (extractType == TagExtractor.Type.STYLE) {
            binding.etProjectName.setText("styles.css");
        } else {
            binding.etProjectName.setText("script.js");
        }
        binding.etProjectName.selectAll();
        binding.etProjectName.requestFocus();

        binding.etProjectName.postDelayed(() -> {
            if (getContext() != null) {
                com.cocode.vcode.ide.utils.UiUtils.showKeyboard(binding.etProjectName);
            }
        }, 200);
    }

    private void setupListeners() {
        binding.btnRenameProject.setOnClickListener(v -> {
            String filename = binding.etProjectName.getText() != null ? binding.etProjectName.getText().toString().trim() : "";

            if (filename.isEmpty()) {
                binding.etProjectName.setError("File name is required");
                binding.etProjectName.requestFocus();
                return;
            }

            if (listener != null) {
                listener.onExtract(filename, extractType);
            }
            dismiss();
        });
    }
}
