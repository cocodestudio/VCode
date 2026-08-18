package com.cocode.vcode.ide.ui.sheets.editor;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetGoToLineBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

/**
 * GoToLineBottomSheet provides a numeric input interface for navigating to a specific line in the editor.
 * It includes input validation against the total line count and provides dynamic error feedback.
 */
public class GoToLineBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetGoToLineBinding binding;

    /**
     * The total number of lines in the current file, used for range validation.
     */
    private int maxLines = 1;
    private GoToLineListener listener;

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    public void setListener(GoToLineListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGoToLineBinding.inflate(inflater, container, false);

        // Instantly focus the input and show the soft keyboard upon appearance
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply visual styling
        UiUtils.setViewRounded(binding.etLineNumber, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        setupTypefaces();
        resetHelperText();

        binding.etLineNumber.requestFocus();

        // Clear error states as the user types
        binding.etLineNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetHelperText();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnGoTo.setOnClickListener(v -> {
            String text = binding.etLineNumber.getText() != null ? binding.etLineNumber.getText().toString().trim() : "";

            if (text.isEmpty()) {
                showError("Enter a line number");
                return;
            }

            try {
                int line = Integer.parseInt(text);
                // Validate that the requested line exists in the document
                if (line < 1 || line > maxLines) {
                    showError("Line out of range (1 - " + maxLines + ")");
                    return;
                }
                if (listener != null) {
                    listener.onGoToLine(line);
                }
                dismiss();
            } catch (NumberFormatException e) {
                showError("Invalid number format");
            }
        });
    }

    /**
     * Applies branding fonts to all UI components.
     */
    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvGoToLine.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvLineNumberLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etLineNumber.setTypeface(fm.getUiMedium(ctx));
        binding.tvLineHelper.setTypeface(fm.getUiMedium(ctx));
        binding.btnGoTo.setTypeface(fm.getUiSemiBold(ctx));
    }

    /**
     * Resets the helper text to show the valid line range.
     */
    private void resetHelperText() {
        binding.tvLineHelper.setText("1 – ".concat(String.valueOf(maxLines)));
        binding.tvLineHelper.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_text_hint));
    }

    /**
     * Displays a validation error message in the helper text area.
     */
    private void showError(String errorMessage) {
        binding.tvLineHelper.setText(errorMessage);
        binding.tvLineHelper.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_accent_error));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Clean up binding to avoid memory leaks
    }

    /**
     * Callback interface for handling the navigation event.
     */
    public interface GoToLineListener {
        void onGoToLine(int line);
    }
}