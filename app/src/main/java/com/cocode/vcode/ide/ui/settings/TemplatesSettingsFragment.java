package com.cocode.vcode.ide.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.template.FileTemplate;
import com.cocode.vcode.ide.core.template.FileTemplateManager;
import com.cocode.vcode.ide.databinding.FragmentSettingsTemplatesBinding;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.sheets.settings.TemplateOptionsBottomSheet;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.views.VCodeSnackbar;

import java.io.File;
import java.util.List;

/**
 * Settings category fragment for managing, customizing, creating, importing, exporting,
 * and editing file templates in the code editor.
 */
public class TemplatesSettingsFragment extends Fragment implements FileTemplateManager.OnTemplatesChangedListener {

    private FragmentSettingsTemplatesBinding binding;
    private FileTemplateManager templateManager;
    private TemplatesSettingsAdapter adapter;

    public static TemplatesSettingsFragment newInstance() {
        return new TemplatesSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsTemplatesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        templateManager = FileTemplateManager.getInstance(requireContext());
        templateManager.addListener(this);

        setupDesign();
        setupListeners();
        setupRecyclerView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Automatically sync any edits made to template files while EditorActivity was open
        if (templateManager != null) {
            templateManager.syncAllTemplatesFromDisk();
            updateList();
        }
    }

    @Override
    public void onTemplatesChanged() {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(this::updateList);
        }
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(requireContext()));
        binding.etSearch.setTypeface(fm.getUiFont(requireContext()));
        binding.tvEmpty.setTypeface(fm.getUiMedium(requireContext()));
    }

    private void setupRecyclerView() {
        binding.rvTemplates.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTemplates.setHasFixedSize(true);

        List<FileTemplate> templates = templateManager.getAllTemplates();
        adapter = new TemplatesSettingsAdapter(
                requireContext(),
                templates,
                this::showEditTemplateDialog
        );
        binding.rvTemplates.setAdapter(adapter);

        String query = binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "";
        if (!query.isEmpty()) {
            adapter.filter(query);
            binding.layoutEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.btnMenu.setOnClickListener(v -> showOverflowMenu());

        binding.fabAddTemplate.setOnClickListener(v -> showEditTemplateDialog(null));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (adapter != null) {
                    adapter.filter(query);
                    binding.layoutEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));
    }

    private void updateList() {
        if (adapter != null && templateManager != null) {
            adapter.setTemplates(templateManager.getAllTemplates());
            binding.layoutEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void showEditTemplateDialog(@Nullable FileTemplate template) {
        EditTemplateDialog dialog = new EditTemplateDialog(
                requireContext(),
                template,
                this::updateList,
                this::openTemplateInEditor
        );
        dialog.show();
    }

    private void openTemplateInEditor(@NonNull FileTemplate template) {
        File templateFile = templateManager.syncTemplateFileToDisk(template);
        File templatesDir = templateManager.getTemplatesDirectory();

        Intent intent = new Intent(requireContext(), EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_PATH, templatesDir.getAbsolutePath());
        intent.putExtra(EditorActivity.EXTRA_PROJECT_ID, "templates");
        intent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, "templates");
        intent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, templateFile.getAbsolutePath());
        startActivity(intent);
    }

    private void showOverflowMenu() {
        TemplateOptionsBottomSheet sheet = TemplateOptionsBottomSheet.newInstance(new TemplateOptionsBottomSheet.TemplateOptionsListener() {
            @Override
            public void onNewTemplate() {
                showEditTemplateDialog(null);
            }

            @Override
            public void onResetAllToDefaults() {
                resetAllToDefaults();
            }

            @Override
            public void onExportTemplates() {
                exportTemplates();
            }

            @Override
            public void onImportTemplates() {
                importTemplates();
            }

            @Override
            public void onEditTemplatesInEditor() {
                openTemplatesJsonInEditor();
            }
        });
        sheet.show(getChildFragmentManager(), "TemplateOptionsBottomSheet");
    }

    private void resetAllToDefaults() {
        templateManager.resetToDefaults();
        updateList();
        Toast.makeText(requireContext(), R.string.vcode_templates_reset_success, Toast.LENGTH_SHORT).show();
    }

    private void exportTemplates() {
        File projectsDir = FileUtils.getProjectsDirectory();
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        File targetFile = new File(projectsDir, FileTemplateManager.TEMPLATES_FILE_NAME);
        boolean ok = templateManager.exportToFile(targetFile);

        if (ok) {
            String path = targetFile.getAbsolutePath();
            String message = getString(R.string.vcode_templates_export_success, path);
            VCodeSnackbar.success(binding.getRoot(), message)
                    .setAction(R.string.vcode_settings_copy_path, v -> {
                        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("Templates Path", path);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(requireContext(), R.string.vcode_settings_path_copied, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        } else {
            Toast.makeText(requireContext(), R.string.vcode_templates_export_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void importTemplates() {
        File projectsDir = FileUtils.getProjectsDirectory();
        File sourceFile = new File(projectsDir, FileTemplateManager.TEMPLATES_FILE_NAME);
        if (!sourceFile.exists()) {
            Toast.makeText(requireContext(), R.string.vcode_templates_file_not_found, Toast.LENGTH_LONG).show();
            return;
        }

        boolean ok = templateManager.importFromFile(sourceFile);
        if (ok) {
            updateList();
            Toast.makeText(requireContext(), R.string.vcode_templates_import_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.vcode_templates_import_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openTemplatesJsonInEditor() {
        File projectsDir = FileUtils.getProjectsDirectory();
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        File templatesFile = new File(projectsDir, FileTemplateManager.TEMPLATES_FILE_NAME);
        if (!templatesFile.exists()) {
            templateManager.exportToFile(templatesFile);
        }

        Intent intent = new Intent(requireContext(), EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_PATH, projectsDir.getAbsolutePath());
        intent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, "VCodeProjects");
        intent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, templatesFile.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (templateManager != null) {
            templateManager.removeListener(this);
        }
        binding = null;
    }
}
