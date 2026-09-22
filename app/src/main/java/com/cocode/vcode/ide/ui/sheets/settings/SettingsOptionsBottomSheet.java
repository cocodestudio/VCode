package com.cocode.vcode.ide.ui.sheets.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.databinding.BottomSheetSettingsMenuBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * SettingsOptionsBottomSheet provides options to import and export settings.json
 * in a clean Material Design bottom sheet.
 */
public class SettingsOptionsBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetSettingsMenuBinding binding;
    private SettingsOptionListener listener;

    public static SettingsOptionsBottomSheet newInstance(SettingsOptionListener listener) {
        SettingsOptionsBottomSheet sheet = new SettingsOptionsBottomSheet();
        sheet.listener = listener;
        return sheet;
    }

    public void setListener(SettingsOptionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSettingsMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FontManager fm = FontManager.getInstance();
        binding.tvSheetTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvImportTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvImportDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvExportTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvExportDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvEditTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvEditDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.opImportSettings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onImportSettings();
            }
        });

        binding.opExportSettings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onExportSettings();
            }
        });

        binding.opEditSettings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onEditSettingsInEditor();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface SettingsOptionListener {
        void onImportSettings();

        void onExportSettings();

        void onEditSettingsInEditor();
    }
}
