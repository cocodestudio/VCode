package com.cocode.vcode.ide.ui.sheets.files;

import android.app.Dialog;
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
import com.cocode.vcode.ide.databinding.BottomSheetNewProjectBinding;
import com.cocode.vcode.ide.databinding.LayoutChooseTemplateBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * NewProjectBottomSheet provides a wizard-like interface for creating new projects.
 * It allows users to name their project, select a starter template (Blank, HTML, or Full Stack),
 * and optionally initialize a local Git repository.
 */
public class NewProjectBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetNewProjectBinding binding;
    private NewProjectListener listener;

    /**
     * The currently selected template name.
     */
    private String selectedTemplate = "Blank";

    /**
     * Static helper to instantiate and show the project creation sheet.
     */
    public static void show(FragmentManager manager, NewProjectListener listener) {
        NewProjectBottomSheet sheet = new NewProjectBottomSheet();
        sheet.setListener(listener);
        sheet.show(manager, "NewProjectBottomSheet");
    }

    public void setListener(NewProjectListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetNewProjectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // Expand the sheet to full height to accommodate the template grid and form
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            View bottomSheetInternal = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheetInternal != null) {
                bottomSheetInternal.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheetInternal.requestLayout();

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        setupTemplates();
        setupListeners();
    }

    /**
     * Configures the visual template selector cards.
     */
    private void setupTemplates() {
        // Initialize template cards with labels and descriptive icons
        setupTemplateCard(binding.template1, "Blank", R.drawable.ic_file_lines);
        setupTemplateCard(binding.template2, "HTML", R.drawable.ic_file_code);
        setupTemplateCard(binding.template3, "HTML+CSS+JS", R.drawable.ic_code);

        // Set click listeners to handle the selection highlighting logic
        binding.template1.getRoot().setOnClickListener(v -> selectTemplate(1));
        binding.template2.getRoot().setOnClickListener(v -> selectTemplate(2));
        binding.template3.getRoot().setOnClickListener(v -> selectTemplate(3));

        // Set 'Blank' as the default project archetype
        selectTemplate(1);
    }

    /**
     * Helper to set content for an individual template card.
     */
    private void setupTemplateCard(LayoutChooseTemplateBinding templateBinding, String title, int iconRes) {
        templateBinding.title.setText(title);
        templateBinding.icon.setImageResource(iconRes);
    }

    /**
     * Updates the selection stroke and the internal template state.
     *
     * @param index The 1-based index of the template card.
     */
    private void selectTemplate(int index) {
        // Clear all previous selections
        binding.template1.getRoot().setStrokeWidth(0);
        binding.template2.getRoot().setStrokeWidth(0);
        binding.template3.getRoot().setStrokeWidth(0);

        int strokeWidth = UiUtils.dpToPx(requireContext(), 3);

        // Highlight the newly selected card and update state
        if (index == 1) {
            binding.template1.getRoot().setStrokeWidth(strokeWidth);
            selectedTemplate = "Blank";
        } else if (index == 2) {
            binding.template2.getRoot().setStrokeWidth(strokeWidth);
            selectedTemplate = "HTML";
        } else if (index == 3) {
            binding.template3.getRoot().setStrokeWidth(strokeWidth);
            selectedTemplate = "HTML+CSS+JS";
        }
    }

    /**
     * Applies branding fonts and rounded corners to the UI.
     */
    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvCreateNewProject.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvConfigureWorkspace.setTypeface(fm.getUiMedium(ctx));
        binding.tvProjectNameLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etProjectName.setTypeface(fm.getUiMedium(ctx));

        binding.tvProjectTemplateLabel.setTypeface(fm.getUiMedium(ctx));
        binding.template1.title.setTypeface(fm.getUiMedium(ctx));
        binding.template2.title.setTypeface(fm.getUiMedium(ctx));
        binding.template3.title.setTypeface(fm.getUiMedium(ctx));
        binding.tvInitGitLabel.setTypeface(fm.getUiMedium(ctx));
        binding.tvInitGitDesc.setTypeface(fm.getUiFont(ctx));
        binding.btnCreateProject.setTypeface(fm.getUiSemiBold(ctx));

        UiUtils.setViewRounded(binding.etProjectName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    /**
     * Attaches interaction listeners with input validation.
     */
    private void setupListeners() {
        // Clear hint on focus to provide a cleaner typing area
        binding.etProjectName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.etProjectName.setHint("");
            }
        });

        binding.initGitRepoBtn.setOnClickListener(v -> binding.switchGit.setChecked(!binding.switchGit.isChecked()));

        binding.btnCreateProject.setOnClickListener(v -> {
            String name = binding.etProjectName.getText() != null ? binding.etProjectName.getText().toString().trim() : "";

            if (name.isEmpty()) {
                binding.etProjectName.setError("Project name is required");
                binding.etProjectName.requestFocus();
                return;
            }

            // Default entry point for web projects
            String mainFile = "index.html";
            boolean initGit = binding.switchGit.isChecked();

            if (listener != null) {
                listener.onCreateProject(name, mainFile, selectedTemplate, initGit);
            }

            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Clean up binding to prevent memory leaks
    }

    /**
     * Callback interface for handling the project creation parameters.
     */
    public interface NewProjectListener {
        void onCreateProject(String name, String mainFile, String templateChoice, boolean initGit);
    }
}