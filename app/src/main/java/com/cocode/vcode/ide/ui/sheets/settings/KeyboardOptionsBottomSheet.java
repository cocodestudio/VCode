package com.cocode.vcode.ide.ui.sheets.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.databinding.BottomSheetKeyboardOptionsBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Modal bottom sheet providing actions for keyboard shortcuts:
 * Reset all to defaults, export keybindings.json, import keybindings.json,
 * and edit keybindings.json in editor.
 */
public class KeyboardOptionsBottomSheet extends BaseBottomSheetDialogFragment {

    public interface KeyboardOptionsListener {
        void onResetAllToDefaults();
        void onExportKeybindings();
        void onImportKeybindings();
        void onEditKeybindingsInEditor();
    }

    private BottomSheetKeyboardOptionsBinding binding;
    private KeyboardOptionsListener listener;

    public static KeyboardOptionsBottomSheet newInstance(@Nullable KeyboardOptionsListener listener) {
        KeyboardOptionsBottomSheet sheet = new KeyboardOptionsBottomSheet();
        sheet.listener = listener;
        return sheet;
    }

    public void setListener(@Nullable KeyboardOptionsListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetKeyboardOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FontManager fm = FontManager.getInstance();
        binding.tvSheetTitle.setTypeface(fm.getUiMedium(requireContext()));

        binding.tvResetTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvResetDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvExportTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvExportDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvImportTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvImportDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvEditTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvEditDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.opResetKeybindings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onResetAllToDefaults();
            }
        });

        binding.opExportKeybindings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onExportKeybindings();
            }
        });

        binding.opImportKeybindings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onImportKeybindings();
            }
        });

        binding.opEditKeybindings.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onEditKeybindingsInEditor();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
