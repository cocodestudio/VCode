package com.cocode.vcode.ide.ui.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.template.FileTemplate;
import com.cocode.vcode.ide.core.template.FileTemplateManager;
import com.cocode.vcode.ide.databinding.DialogEditTemplateBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog for creating or editing file template metadata (name, extension)
 * and providing direct access to the code editor for authoring template content.
 */
public class EditTemplateDialog extends Dialog {

    public interface OnTemplateSavedListener {
        void onTemplateSaved();
    }

    public interface OnOpenInEditorListener {
        void onOpenInEditor(@NonNull FileTemplate template);
    }

    private final FileTemplate template;
    private final OnTemplateSavedListener savedListener;
    private final OnOpenInEditorListener openListener;
    private final FileTemplateManager templateManager;
    private DialogEditTemplateBinding binding;

    public EditTemplateDialog(@NonNull Context context,
                              @Nullable FileTemplate template,
                              @Nullable OnTemplateSavedListener savedListener,
                              @Nullable OnOpenInEditorListener openListener) {
        super(context);
        this.template = template;
        this.savedListener = savedListener;
        this.openListener = openListener;
        this.templateManager = FileTemplateManager.getInstance(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        binding = DialogEditTemplateBinding.inflate(LayoutInflater.from(getContext()));
        setContentView(binding.getRoot());

        applyWindowBounds();
        setupDesign();
        setupInitialState();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyWindowBounds();
    }

    private void applyWindowBounds() {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            int maxWidth = getContext().getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.88f), maxWidth);
            window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private void setupDesign() {
        Context ctx = getContext();
        FontManager fm = FontManager.getInstance();

        binding.tvDialogTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvDialogSubtitle.setTypeface(fm.getUiFont(ctx));
        binding.tvTemplateNameLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etTemplateName.setTypeface(fm.getUiMedium(ctx));
        binding.tvTemplateExtensionLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etTemplateExtension.setTypeface(fm.getUiMedium(ctx));
        binding.tvPlaceholdersHint.setTypeface(fm.getUiFont(ctx));
        binding.btnOpenInEditor.setTypeface(fm.getUiMedium(ctx));
        binding.btnCancel.setTypeface(fm.getUiMedium(ctx));
        binding.btnSave.setTypeface(fm.getUiSemiBold(ctx));

        int radius = UiUtils.dpToPx(ctx, 10);
        int strokeWidth = UiUtils.dpToPx(ctx, 1);
        int bgColor = ContextCompat.getColor(ctx, R.color.vcode_bg_deep);
        int normalStrokeColor = ContextCompat.getColor(ctx, R.color.vcode_outline_variant);
        int focusedStrokeColor = ContextCompat.getColor(ctx, R.color.vcode_accent_primary);

        UiUtils.setInputRounded(binding.etTemplateName, radius, bgColor, strokeWidth, normalStrokeColor, focusedStrokeColor);
        UiUtils.setInputRounded(binding.etTemplateExtension, radius, bgColor, strokeWidth, normalStrokeColor, focusedStrokeColor);
        UiUtils.applyAccentToEditText(binding.etTemplateName);
        UiUtils.applyAccentToEditText(binding.etTemplateExtension);
    }

    private void setupInitialState() {
        if (template != null) {
            String title = getContext().getString(R.string.vcode_edit_template) + ": " + template.getName();
            binding.tvDialogTitle.setText(title);
            binding.etTemplateName.setText(template.getName());
            binding.etTemplateExtension.setText(template.getExtension());
            binding.btnDelete.setVisibility(View.VISIBLE);
        } else {
            binding.tvDialogTitle.setText(R.string.vcode_new_template);
            binding.btnDelete.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnCancel.setOnClickListener(v -> dismiss());

        binding.btnOpenInEditor.setOnClickListener(v -> {
            FileTemplate target = validateAndBuildTemplate();
            if (target != null) {
                templateManager.addOrUpdateTemplate(target);
                templateManager.syncTemplateFileToDisk(target);
                if (savedListener != null) {
                    savedListener.onTemplateSaved();
                }
                dismiss();
                if (openListener != null) {
                    openListener.onOpenInEditor(target);
                }
            }
        });

        binding.btnDelete.setOnClickListener(v -> {
            if (template != null) {
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.vcode_delete_template)
                        .setMessage(getContext().getString(R.string.vcode_delete_template_confirm, template.getName()))
                        .setNegativeButton(R.string.vcode_cancel, null)
                        .setPositiveButton(R.string.vcode_delete_template, (dialog, which) -> {
                            templateManager.deleteTemplate(template.getId());
                            if (savedListener != null) {
                                savedListener.onTemplateSaved();
                            }
                            dismiss();
                        })
                        .show();
            }
        });

        binding.btnSave.setOnClickListener(v -> {
            FileTemplate target = validateAndBuildTemplate();
            if (target != null) {
                templateManager.addOrUpdateTemplate(target);
                templateManager.syncTemplateFileToDisk(target);
                if (savedListener != null) {
                    savedListener.onTemplateSaved();
                }
                dismiss();
            }
        });

        clearErrorsOnType();
    }

    private void clearErrorsOnType() {
        binding.etTemplateName.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.etTemplateName.setError(null);
            }
        });
        binding.etTemplateExtension.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.etTemplateExtension.setError(null);
            }
        });
    }

    @Nullable
    private FileTemplate validateAndBuildTemplate() {
        String name = binding.etTemplateName.getText() != null ? binding.etTemplateName.getText().toString().trim() : "";
        String rawExt = binding.etTemplateExtension.getText() != null ? binding.etTemplateExtension.getText().toString().trim() : "";

        boolean valid = true;

        if (name.isEmpty()) {
            binding.etTemplateName.setError(getContext().getString(R.string.vcode_template_name_required));
            valid = false;
        }

        if (rawExt.isEmpty()) {
            binding.etTemplateExtension.setError(getContext().getString(R.string.vcode_template_extension_required));
            valid = false;
        } else if (FileTemplateManager.isBinaryExtension(rawExt)) {
            binding.etTemplateExtension.setError(getContext().getString(R.string.vcode_binary_template_not_supported));
            valid = false;
        }

        if (!valid) {
            return null;
        }

        String normalizedExt = FileTemplate.normalizeExtension(rawExt);
        String id;
        boolean isBuiltIn = false;
        String content = "";

        if (template != null) {
            id = template.getId();
            isBuiltIn = template.isBuiltIn();
            content = template.getContent();
        } else {
            id = normalizedExt + "_" + System.currentTimeMillis();
        }

        return new FileTemplate(id, name, normalizedExt, content, isBuiltIn);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}
    }
}
