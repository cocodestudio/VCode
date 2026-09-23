package com.cocode.vcode.ide.ui.sheets.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.databinding.BottomSheetTemplateOptionsBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Modal bottom sheet providing overflow actions for File Templates:
 * New Template, Reset all to defaults, export templates.json, import templates.json,
 * and edit templates.json in editor.
 */
public class TemplateOptionsBottomSheet extends BaseBottomSheetDialogFragment {

    public interface TemplateOptionsListener {
        void onNewTemplate();
        void onResetAllToDefaults();
        void onExportTemplates();
        void onImportTemplates();
        void onEditTemplatesInEditor();
    }

    private BottomSheetTemplateOptionsBinding binding;
    private TemplateOptionsListener listener;

    public static TemplateOptionsBottomSheet newInstance(@Nullable TemplateOptionsListener listener) {
        TemplateOptionsBottomSheet sheet = new TemplateOptionsBottomSheet();
        sheet.listener = listener;
        return sheet;
    }

    public void setListener(@Nullable TemplateOptionsListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetTemplateOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FontManager fm = FontManager.getInstance();
        binding.tvSheetTitle.setTypeface(fm.getUiMedium(requireContext()));

        binding.tvNewTemplateTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvNewTemplateDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvResetTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvResetDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvExportTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvExportDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvImportTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvImportDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvEditTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvEditDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.opNewTemplate.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onNewTemplate();
            }
        });

        binding.opResetTemplates.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onResetAllToDefaults();
            }
        });

        binding.opExportTemplates.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onExportTemplates();
            }
        });

        binding.opImportTemplates.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onImportTemplates();
            }
        });

        binding.opEditTemplates.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onEditTemplatesInEditor();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
