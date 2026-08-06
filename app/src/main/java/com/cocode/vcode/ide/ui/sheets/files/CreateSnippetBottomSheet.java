package com.cocode.vcode.ide.ui.sheets.files;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.SnippetItem;
import com.cocode.vcode.ide.databinding.BottomSheetCreateSnippetBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * CreateSnippetBottomSheet provides a form for creating or editing reusable code snippets.
 * It handles title and content input, validates required fields, and notifies
 * the listener upon successful submission.
 */
public class CreateSnippetBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetCreateSnippetBinding binding;
    private SnippetSaveListener listener;

    /**
     * The snippet being edited, or null if creating a new one.
     */
    private SnippetItem existingSnippet;

    /**
     * Attaches a listener to handle the save event.
     */
    public void setListener(SnippetSaveListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the snippet to be edited. Switches the sheet to "Edit Mode".
     */
    public void setExistingSnippet(SnippetItem snippet) {
        this.existingSnippet = snippet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCreateSnippetBinding.inflate(inflater, container, false);

        // Ensure the keyboard is visible immediately and the layout adjusts for it
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply rounded backgrounds to input fields
        UiUtils.setViewRounded(binding.etSnippetTitle, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(binding.etSnippetCode, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));

        setupTypefaces();
        setupMode();

        binding.btnCreateSnippet.setOnClickListener(v -> {
            String title = binding.etSnippetTitle.getText() != null ? binding.etSnippetTitle.getText().toString().trim() : "";
            String code = binding.etSnippetCode.getText() != null ? binding.etSnippetCode.getText().toString() : "";

            // Basic validation for snippet metadata
            if (title.isEmpty()) {
                binding.etSnippetTitle.setError("Title is required");
                binding.etSnippetTitle.requestFocus();
                return;
            }
            if (code.isEmpty()) {
                binding.etSnippetCode.setError("Code is required");
                binding.etSnippetCode.requestFocus();
                return;
            }

            if (existingSnippet != null) {
                // Update existing model for Edit Mode
                existingSnippet.setTitle(title);
                existingSnippet.setContent(code);
                if (listener != null) listener.onSaveSnippet(existingSnippet, true);
            } else {
                // Create a new model for Create Mode
                SnippetItem newItem = new SnippetItem(title, code, FileType.TEXT);
                if (listener != null) listener.onSaveSnippet(newItem, false);
            }
            dismiss();
        });
    }

    /**
     * Configures labels and pre-fills data based on whether we are creating or editing.
     */
    private void setupMode() {
        if (existingSnippet != null) {
            // Configure UI for editing an existing snippet
            binding.tvCreateNewSnippet.setText(R.string.vcode_edit_snippet);
            binding.tvEnterSnippetTitleAndCode.setText(R.string.vcode_edit_snippet_title_and_code);
            binding.btnCreateSnippet.setText(R.string.vcode_edit_snippet);

            binding.etSnippetTitle.setText(existingSnippet.getTitle());
            binding.etSnippetCode.setText(existingSnippet.getContent());
        } else {
            // Default configuration for a new snippet
            binding.tvCreateNewSnippet.setText(R.string.vcode_create_snippet);
            binding.tvEnterSnippetTitleAndCode.setText(R.string.vcode_enter_snippet_title_and_code);
        }
    }

    /**
     * Applies specialized UI fonts for improved readability.
     */
    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvCreateNewSnippet.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvEnterSnippetTitleAndCode.setTypeface(fm.getUiMedium(ctx));
        binding.tvSnippetTitleLabel.setTypeface(fm.getUiSemiBold(ctx));
        binding.etSnippetTitle.setTypeface(fm.getUiMedium(ctx));
        binding.tvSnippetCode.setTypeface(fm.getUiSemiBold(ctx));
        binding.etSnippetCode.setTypeface(fm.getUiMedium(ctx));
        binding.btnCreateSnippet.setTypeface(fm.getUiSemiBold(ctx));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Listener interface for returning the saved snippet to the parent component.
     */
    public interface SnippetSaveListener {
        void onSaveSnippet(SnippetItem snippet, boolean isEdit);
    }
}