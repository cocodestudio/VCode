package com.cocode.vcode.ide.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.settings.SettingsJsonSerializer;
import com.cocode.vcode.ide.databinding.FragmentSettingsHomeBinding;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.sheets.settings.SettingsOptionsBottomSheet;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.cocode.vcode.ide.views.VCodeSnackbar;

import java.io.File;

/**
 * SettingsHomeFragment displays the top-level settings categories (Editor, Appearance, Git, General)
 * and provides options to import and export settings via a three-dot menu.
 */
public class SettingsHomeFragment extends Fragment {

    private FragmentSettingsHomeBinding binding;
    private SettingsViewModel viewModel;

    private final ActivityResultLauncher<String[]> pickSettingsFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handleImportSettings(uri);
                }
            });

    public static SettingsHomeFragment newInstance() {
        return new SettingsHomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        setupDesign();
        setupListeners();
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(requireContext()));

        binding.tvCategoryEditorTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvCategoryEditorDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvCategoryAppearanceTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvCategoryAppearanceDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvCategoryGitTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvCategoryGitDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvCategoryKeyboardTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvCategoryKeyboardDesc.setTypeface(fm.getUiFont(requireContext()));

        binding.tvCategoryGeneralTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvCategoryGeneralDesc.setTypeface(fm.getUiFont(requireContext()));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().finish());

        binding.btnMenu.setOnClickListener(v -> showSettingsOptionsMenu());

        binding.cardCategoryEditor.setOnClickListener(v ->
                navigateTo(EditorSettingsFragment.newInstance()));

        binding.cardCategoryAppearance.setOnClickListener(v ->
                navigateTo(AppearanceSettingsFragment.newInstance()));

        binding.cardCategoryGit.setOnClickListener(v ->
                navigateTo(GitSettingsFragment.newInstance()));

        binding.cardCategoryKeyboard.setOnClickListener(v ->
                navigateTo(KeyboardSettingsFragment.newInstance()));

        binding.cardCategoryGeneral.setOnClickListener(v ->
                navigateTo(GeneralSettingsFragment.newInstance()));
    }

    private void showSettingsOptionsMenu() {
        SettingsOptionsBottomSheet sheet = SettingsOptionsBottomSheet.newInstance(new SettingsOptionsBottomSheet.SettingsOptionListener() {
            @Override
            public void onImportSettings() {
                pickSettingsFileLauncher.launch(new String[]{"application/json", "text/*", "*/*"});
            }

            @Override
            public void onExportSettings() {
                handleExportSettings();
            }

            @Override
            public void onEditSettingsInEditor() {
                openSettingsInEditor();
            }
        });
        sheet.show(getChildFragmentManager(), "SettingsOptionsBottomSheet");
    }

    private void handleImportSettings(@NonNull Uri uri) {
        viewModel.importSettings(uri, result -> {
            if (!isAdded()) return;

            if (result.isValid && result.settings != null) {
                if (result.settings.theme != null) {
                    switch (result.settings.theme) {
                        case LIGHT:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                            break;
                        case DARK:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                            break;
                        default:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            break;
                    }
                }
                VCodeSnackbar.success(binding.getRoot(), R.string.vcode_settings_import_success).show();
            } else {
                showImportErrorDialog(result.errorMessage);
            }
        });
    }

    private void showImportErrorDialog(@Nullable String errorMessage) {
        if (!isAdded()) return;
        String message = (errorMessage != null && !errorMessage.trim().isEmpty())
                ? errorMessage
                : getString(R.string.vcode_settings_import_error_msg);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vcode_settings_import_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void handleExportSettings() {
        viewModel.exportSettings(new SettingsViewModel.ExportCallback() {
            @Override
            public void onSuccess(File exportedFile) {
                if (!isAdded()) return;
                String path = exportedFile.getAbsolutePath();
                String message = getString(R.string.vcode_settings_export_success, path);

                VCodeSnackbar.success(binding.getRoot(), message)
                        .setAction(R.string.vcode_settings_copy_path, v -> {
                            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                ClipData clip = ClipData.newPlainText("Settings Path", path);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(requireContext(), R.string.vcode_settings_path_copied, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) return;
                String errorMsg = error != null && error.getLocalizedMessage() != null
                        ? getString(R.string.vcode_settings_export_error, error.getLocalizedMessage())
                        : getString(R.string.vcode_settings_export_error, "Unknown error");
                VCodeSnackbar.error(binding.getRoot(), errorMsg).show();
            }
        });
    }

    private void openSettingsInEditor() {
        File projectsDir = FileUtils.getProjectsDirectory();
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        File settingsFile = SettingsJsonSerializer.getProjectsExportFile();
        if (!settingsFile.exists()) {
            viewModel.exportSettings(new SettingsViewModel.ExportCallback() {
                @Override
                public void onSuccess(File exportedFile) {
                    launchEditorWithFile(projectsDir, exportedFile);
                }

                @Override
                public void onError(Exception error) {
                    if (!isAdded()) return;
                    String errorMsg = error != null && error.getLocalizedMessage() != null
                            ? getString(R.string.vcode_settings_export_error, error.getLocalizedMessage())
                            : getString(R.string.vcode_settings_export_error, "Unknown error");
                    VCodeSnackbar.error(binding.getRoot(), errorMsg).show();
                }
            });
            return;
        }

        launchEditorWithFile(projectsDir, settingsFile);
    }

    private void launchEditorWithFile(@NonNull File projectsDir, @NonNull File file) {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_PATH, projectsDir.getAbsolutePath());
        intent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, "VCodeProjects");
        intent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    private void navigateTo(Fragment fragment) {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).navigateToCategory(fragment);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
