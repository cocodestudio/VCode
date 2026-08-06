package com.cocode.vcode.ide.ui.sheets.files;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.databinding.BottomSheetCreateNewFileBinding;
import com.cocode.vcode.ide.databinding.LayoutChooseTemplateBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * NewFileBottomSheet provides a workflow for creating a new code file.
 * It features a template selector (HTML, CSS, JS, etc.) that pre-fills the file
 * extension and initial content based on the selected language.
 */
public class NewFileBottomSheet extends BottomSheetDialogFragment {

    private final String[] extensions = {".html", ".css", ".js", ".json", ".md", ".txt"};

    /**
     * Asset filenames for the initial boilerplate content.
     */
    private final String[] templateFiles = {
            "template_blank.html",
            "template_blank.css",
            "template_blank.js",
            "template_blank.json",
            "template_markdown.md",
            ""
    };

    private BottomSheetCreateNewFileBinding binding;
    private NewFileListener listener;
    private int selectedTemplateIndex = 0;

    /**
     * Creates a new instance of the sheet.
     */
    public static NewFileBottomSheet newInstance() {
        return new NewFileBottomSheet();
    }

    public void setListener(NewFileListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // Force the sheet to expand to full height for a better template selection experience
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
        setupTemplates();
        setupListeners();
    }

    /**
     * Configures the grid of template options with icons and labels.
     */
    private void setupTemplates() {
        setupTemplateItem(binding.template1, R.drawable.ic_html_icon, FileType.HTML, "HTML", 0);
        setupTemplateItem(binding.template2, R.drawable.ic_css_icon, FileType.CSS, "CSS", 1);
        setupTemplateItem(binding.template3, R.drawable.ic_js_icon, FileType.JAVASCRIPT, "JS", 2);
        setupTemplateItem(binding.template4, R.drawable.ic_json_icon, FileType.JSON, "JSON", 3);
        setupTemplateItem(binding.template5, R.drawable.ic_md_icon, FileType.MARKDOWN, "MD", 4);
        setupTemplateItem(binding.template6, R.drawable.ic_file_lines, FileType.TEXT, "TEXT", 5);

        // Default selection: HTML
        selectTemplate(0);
    }

    /**
     * Applies branding fonts and styles to the UI components.
     */
    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvCreateNewFile.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvChooseNameExtension.setTypeface(fm.getUiMedium(ctx));
        binding.tvFileNameLabel.setTypeface(fm.getUiMedium(ctx));
        binding.etFileName.setTypeface(fm.getUiMedium(ctx));

        binding.tvQuickSelectTemplateLabel.setTypeface(fm.getUiMedium(ctx));
        binding.template1.title.setTypeface(fm.getUiMedium(ctx));
        binding.template2.title.setTypeface(fm.getUiMedium(ctx));
        binding.template3.title.setTypeface(fm.getUiMedium(ctx));
        binding.template4.title.setTypeface(fm.getUiMedium(ctx));
        binding.template5.title.setTypeface(fm.getUiMedium(ctx));
        binding.template6.title.setTypeface(fm.getUiMedium(ctx));
        binding.btnCreateFile.setTypeface(fm.getUiSemiBold(ctx));

        UiUtils.setViewRounded(binding.etFileName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    /**
     * Helper to configure an individual template card.
     */
    private void setupTemplateItem(LayoutChooseTemplateBinding itemBinding, int iconRes, FileType fileType, String title, int index) {
        itemBinding.icon.setImageResource(iconRes);
        itemBinding.icon.setColorFilter(
                ContextCompat.getColor(requireContext(), fileType.getColorResId()),
                android.graphics.PorterDuff.Mode.SRC_IN
        );
        itemBinding.title.setText(title);
        itemBinding.getRoot().setOnClickListener(v -> selectTemplate(index));
    }

    /**
     * Updates the UI selection state and auto-appends the correct extension to the filename.
     */
    private void selectTemplate(int index) {
        selectedTemplateIndex = index;
        int strokeWidth = UiUtils.dpToPx(requireContext(), 3);

        // Highlight the selected card using its stroke property
        MaterialCardView[] cards = {
                binding.template1.getRoot(),
                binding.template2.getRoot(),
                binding.template3.getRoot(),
                binding.template4.getRoot(),
                binding.template5.getRoot(),
                binding.template6.getRoot()
        };

        for (int i = 0; i < cards.length; i++) {
            cards[i].setStrokeWidth(i == index ? strokeWidth : 0);
        }

        // Maintain the user's base filename while swapping the extension
        String currentName = binding.etFileName.getText() != null ? binding.etFileName.getText().toString() : "";
        int dotIndex = currentName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? currentName.substring(0, dotIndex) : currentName;

        if (baseName.isEmpty()) baseName = "Untitled";

        binding.etFileName.setText(baseName.concat(extensions[index]));
        binding.etFileName.setSelection(binding.etFileName.getText().length());
    }

    /**
     * Attaches listeners for input validation and file creation.
     */
    private void setupListeners() {
        binding.etFileName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.etFileName.setError(null); // Clear error state on type
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnCreateFile.setOnClickListener(v -> {
            String name = binding.etFileName.getText() != null
                    ? binding.etFileName.getText().toString().trim()
                    : "Untitled" + extensions[selectedTemplateIndex];

            if (name.isEmpty()) {
                binding.etFileName.setError("File name cannot be empty");
                return;
            }

            // Load boilerplate content only if the typed extension matches the active template
            String content = "";
            if (name.toLowerCase().endsWith(extensions[selectedTemplateIndex].toLowerCase())) {
                content = readTemplateFromAssets(templateFiles[selectedTemplateIndex]);
            }

            if (listener != null) {
                listener.onCreateFile(name, content);
            }
            dismiss();
        });
    }

    /**
     * Synchronously reads a template file from the application assets.
     */
    private String readTemplateFromAssets(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        try (InputStream is = requireContext().getAssets().open("templates/" + fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            // Remove trailing newline
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
        } catch (Exception e) {
            return ""; // Return empty string on failure to avoid null content
        }
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Callback interface for the file creation event.
     */
    public interface NewFileListener {
        void onCreateFile(String fileName, String initialContent);
    }
}