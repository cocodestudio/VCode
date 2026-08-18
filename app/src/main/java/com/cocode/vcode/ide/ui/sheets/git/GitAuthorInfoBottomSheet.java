package com.cocode.vcode.ide.ui.sheets.git;

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
import com.cocode.vcode.ide.databinding.BottomSheetGitAuthorInfoBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

/**
 * GitAuthorInfoBottomSheet provides an interface for configuring Git user metadata (Name and Email).
 * This information is required for creating valid Git commits and attribution in history.
 */
public class GitAuthorInfoBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_NAME = "arg_initial_name";
    private static final String ARG_EMAIL = "arg_initial_email";
    private static final String ARG_BUTTON_TEXT = "arg_button_text";
    private BottomSheetGitAuthorInfoBinding binding;
    private AuthorInfoListener listener;

    /**
     * Creates a new instance of the sheet, pre-filled with existing author details if available.
     */
    public static GitAuthorInfoBottomSheet newInstance(String name, String email) {
        return newInstance(name, email, null);
    }

    public static GitAuthorInfoBottomSheet newInstance(String name, String email, String buttonText) {
        GitAuthorInfoBottomSheet sheet = new GitAuthorInfoBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_EMAIL, email);
        if (buttonText != null) {
            args.putString(ARG_BUTTON_TEXT, buttonText);
        }
        sheet.setArguments(args);
        return sheet;
    }

    /**
     * Attaches a listener to capture the saved author information.
     */
    public void setListener(AuthorInfoListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGitAuthorInfoBinding.inflate(inflater, container, false);

        // Auto-show keyboard and resize layout to prevent overlapping
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Visual styling for input fields
        UiUtils.setViewRounded(binding.etAuthorName, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(binding.etAuthorEmail, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        setupTypefaces();

        // Restore initial values from arguments
        if (getArguments() != null) {
            String initialName = getArguments().getString(ARG_NAME, "");
            String initialEmail = getArguments().getString(ARG_EMAIL, "");
            String buttonText = getArguments().getString(ARG_BUTTON_TEXT);
            binding.etAuthorName.setText(initialName);
            binding.etAuthorEmail.setText(initialEmail);

            if (buttonText != null && !buttonText.isEmpty()) {
                binding.btnSaveAndCommit.setText(buttonText);
            }

            // Show "Clear" button if data exists
            if (!initialName.isEmpty() || !initialEmail.isEmpty()) {
                binding.btnClearCredentials.setVisibility(View.VISIBLE);
            }
        }

        binding.btnSaveAndCommit.setOnClickListener(v -> {
            String authorName = binding.etAuthorName.getText() != null ? binding.etAuthorName.getText().toString().trim() : "";
            String authorEmail = binding.etAuthorEmail.getText() != null ? binding.etAuthorEmail.getText().toString().trim() : "";

            // Validate mandatory fields
            if (authorName.isEmpty()) {
                binding.etAuthorName.setError("Name is required");
                return;
            }

            if (authorEmail.isEmpty()) {
                binding.etAuthorEmail.setError("Email address is required");
                return;
            }

            if (listener != null) {
                listener.onSaveAuthor(authorName, authorEmail);
            }
            dismiss();
        });

        binding.btnClearCredentials.setOnClickListener(v -> {
            // Effectively logs out or resets the local author identity
            if (listener != null) {
                listener.onSaveAuthor("", "");
            }
            dismiss();
        });
    }

    /**
     * Applies specialized UI fonts for professional typography.
     */
    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvGitAuthorInfo.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvRequiredForCommitsToManageHistory.setTypeface(fm.getUiMedium(ctx));
        binding.tvAuthorNameLabel.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvAuthorEmailLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etAuthorEmail.setTypeface(fm.getUiSemiBold(ctx));
        binding.etAuthorName.setTypeface(fm.getUiMedium(ctx));
        binding.btnSaveAndCommit.setTypeface(fm.getUiSemiBold(ctx));
        binding.btnClearCredentials.setTypeface(fm.getUiSemiBold(ctx));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Callback interface for saving author metadata.
     */
    public interface AuthorInfoListener {
        void onSaveAuthor(String name, String email);
    }
}