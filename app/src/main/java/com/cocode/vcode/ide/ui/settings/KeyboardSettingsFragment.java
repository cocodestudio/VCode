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
import com.cocode.vcode.ide.core.keybinding.KeyCommand;
import com.cocode.vcode.ide.core.keybinding.KeybindingManager;
import com.cocode.vcode.ide.databinding.FragmentSettingsKeyboardBinding;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.sheets.settings.KeyboardOptionsBottomSheet;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.views.VCodeSnackbar;

import java.io.File;
import java.util.List;

/**
 * Settings category fragment for discovering, searching, and customizing keyboard shortcuts.
 */
public class KeyboardSettingsFragment extends Fragment {

    private FragmentSettingsKeyboardBinding binding;
    private KeybindingManager keybindingManager;
    private KeyboardSettingsAdapter adapter;

    public static KeyboardSettingsFragment newInstance() {
        return new KeyboardSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsKeyboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        keybindingManager = KeybindingManager.getInstance(requireContext());

        setupDesign();
        setupListeners();
        setupRecyclerView();
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(requireContext()));
        binding.etSearch.setTypeface(fm.getUiFont(requireContext()));
        binding.tvEmpty.setTypeface(fm.getUiMedium(requireContext()));
    }

    private void setupRecyclerView() {
        binding.rvKeybindings.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvKeybindings.setHasFixedSize(true);
        List<KeyCommand> commands = keybindingManager.getAllCommands();
        adapter = new KeyboardSettingsAdapter(requireContext(), commands, this::showEditKeybindingDialog);
        binding.rvKeybindings.setAdapter(adapter);

        String query = binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "";
        if (!query.isEmpty()) {
            adapter.filter(query);
            binding.layoutEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.btnMenu.setOnClickListener(this::showOverflowMenu);

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

    private void showEditKeybindingDialog(@NonNull KeyCommand command) {
        EditKeybindingDialog dialog = new EditKeybindingDialog(requireContext(), command, () -> {
            if (adapter != null) {
                adapter.refresh();
            }
        });
        dialog.show();
    }

    private void showOverflowMenu(View anchor) {
        KeyboardOptionsBottomSheet sheet = KeyboardOptionsBottomSheet.newInstance(new KeyboardOptionsBottomSheet.KeyboardOptionsListener() {
            @Override
            public void onResetAllToDefaults() {
                resetAllToDefaults();
            }

            @Override
            public void onExportKeybindings() {
                exportKeybindings();
            }

            @Override
            public void onImportKeybindings() {
                importKeybindings();
            }

            @Override
            public void onEditKeybindingsInEditor() {
                openKeybindingsInEditor();
            }
        });
        sheet.show(getChildFragmentManager(), "KeyboardOptionsBottomSheet");
    }

    private void resetAllToDefaults() {
        keybindingManager.resetToDefaults();
        if (adapter != null) {
            adapter.refresh();
        }
        Toast.makeText(requireContext(), R.string.vcode_keybindings_reset_success, Toast.LENGTH_SHORT).show();
    }

    private void exportKeybindings() {
        File projectsDir = FileUtils.getProjectsDirectory();
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        File targetFile = new File(projectsDir, "keybindings.json");
        boolean ok = keybindingManager.exportToFile(targetFile);

        if (ok) {
            String path = targetFile.getAbsolutePath();
            String message = getString(R.string.vcode_keybindings_export_success, path);
            VCodeSnackbar.success(binding.getRoot(), message)
                    .setAction(R.string.vcode_settings_copy_path, v -> {
                        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("Keybindings Path", path);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(requireContext(), R.string.vcode_settings_path_copied, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        } else {
            Toast.makeText(requireContext(), R.string.vcode_keybindings_export_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void importKeybindings() {
        File projectsDir = FileUtils.getProjectsDirectory();
        File sourceFile = new File(projectsDir, "keybindings.json");
        if (!sourceFile.exists()) {
            Toast.makeText(requireContext(), R.string.vcode_keybindings_file_not_found, Toast.LENGTH_LONG).show();
            return;
        }

        KeybindingManager.ImportResult result = keybindingManager.importFromFile(sourceFile);
        if (result.isSuccess()) {
            if (adapter != null) {
                adapter.refresh();
            }
            if (result.getConflictCount() > 0) {
                String msg = getString(R.string.vcode_keybindings_import_success_conflicts, result.getConflictCount());
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), R.string.vcode_keybindings_import_success, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), R.string.vcode_keybindings_import_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openKeybindingsInEditor() {
        File projectsDir = FileUtils.getProjectsDirectory();
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        File keybindingsFile = new File(projectsDir, "keybindings.json");
        if (!keybindingsFile.exists()) {
            keybindingManager.exportToFile(keybindingsFile);
        }

        Intent intent = new Intent(requireContext(), EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_PATH, projectsDir.getAbsolutePath());
        intent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, "VCodeProjects");
        intent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, keybindingsFile.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
