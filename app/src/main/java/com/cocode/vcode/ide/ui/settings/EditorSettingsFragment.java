package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.databinding.FragmentSettingsEditorBinding;
import com.cocode.vcode.ide.ui.dialogs.RemoveBootstrapConfirmDialog;
import com.cocode.vcode.ide.ui.sheets.settings.CustomizeQuickSymbolsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.settings.DesktopTerminalInstallBottomSheet;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.MessageFormat;

/**
 * EditorSettingsFragment manages code editor preferences:
 * line numbers, font size, bracket/quote/tag auto-closing, word wrap, and indentation.
 */
public class EditorSettingsFragment extends Fragment {

    private FragmentSettingsEditorBinding binding;
    private SettingsViewModel viewModel;
    private boolean isUpdatingUi = false;

    public static EditorSettingsFragment newInstance() {
        return new EditorSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        setupDesign();
        setupListeners();
        setupObservers();
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(requireContext()));

        binding.tvShowLineNumbers.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvShowLineNumbersDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvFontSize.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvFontSizeValue.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoCloseBrackets.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoCloseBracketsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoCloseQuotes.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoCloseQuotesDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoCloseTags.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoCloseTagsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvWordWrap.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvWordWrapDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoIndent.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoIndentDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvRainbowBrackets.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvRainbowBracketsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvBracketHighlighting.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvBracketHighlightingDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvEnableDiagnostics.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvEnableDiagnosticsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvShowSquigglyLines.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvShowSquigglyLinesDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvDeleteMatchingPairs.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvDeleteMatchingPairsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvForceLargeFileHighlighting.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvForceLargeFileHighlightingDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvQuickSymbolBar.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvQuickSymbolBarDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvCustomizeQuickSymbols.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvCustomizeQuickSymbolsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvDesktopTerminal.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvDesktopTerminalDesc.setTypeface(fm.getUiFont(requireContext()));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.opShowLineNumbers.setOnClickListener(_view ->
                binding.switchLineNumbers.setChecked(!binding.switchLineNumbers.isChecked()));
        binding.opAutoCloseBrackets.setOnClickListener(_view ->
                binding.switchAutoClose.setChecked(!binding.switchAutoClose.isChecked()));
        binding.opAutoCloseQuotes.setOnClickListener(_view ->
                binding.switchAutoCloseQuotes.setChecked(!binding.switchAutoCloseQuotes.isChecked()));
        binding.opAutoCloseTags.setOnClickListener(_view ->
                binding.switchAutoCloseTags.setChecked(!binding.switchAutoCloseTags.isChecked()));
        binding.opWordWrap.setOnClickListener(_view ->
                binding.switchWordWrap.setChecked(!binding.switchWordWrap.isChecked()));
        binding.opAutoIndent.setOnClickListener(_view ->
                binding.switchAutoIndent.setChecked(!binding.switchAutoIndent.isChecked()));
        binding.opRainbowBrackets.setOnClickListener(_view ->
                binding.switchRainbowBrackets.setChecked(!binding.switchRainbowBrackets.isChecked()));
        binding.opBracketHighlighting.setOnClickListener(_view ->
                binding.switchBracketHighlighting.setChecked(!binding.switchBracketHighlighting.isChecked()));
        binding.opEnableDiagnostics.setOnClickListener(_view ->
                binding.switchEnableDiagnostics.setChecked(!binding.switchEnableDiagnostics.isChecked()));
        binding.opShowSquigglyLines.setOnClickListener(_view -> {
            if (binding.switchShowSquigglyLines.isEnabled()) {
                binding.switchShowSquigglyLines.setChecked(!binding.switchShowSquigglyLines.isChecked());
            }
        });
        binding.opDeleteMatchingPairs.setOnClickListener(_view ->
                binding.switchDeleteMatchingPairs.setChecked(!binding.switchDeleteMatchingPairs.isChecked()));
        binding.opForceLargeFileHighlighting.setOnClickListener(_view ->
                binding.switchForceLargeFileHighlighting.setChecked(!binding.switchForceLargeFileHighlighting.isChecked()));
        binding.opQuickSymbolBar.setOnClickListener(_view ->
                binding.switchQuickSymbolBar.setChecked(!binding.switchQuickSymbolBar.isChecked()));
        binding.opCustomizeQuickSymbols.setOnClickListener(_view -> {
            if (binding.switchQuickSymbolBar.isChecked()) {
                CustomizeQuickSymbolsBottomSheet sheet = CustomizeQuickSymbolsBottomSheet.newInstance();
                sheet.show(getChildFragmentManager(), "CustomizeQuickSymbolsBottomSheet");
            }
        });

        binding.opDesktopTerminal.setOnClickListener(_view ->
                binding.switchDesktopTerminal.setChecked(!binding.switchDesktopTerminal.isChecked()));

        binding.switchDesktopTerminal.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) {
                if (isChecked) {
                    handleEnableDesktopTerminal();
                } else {
                    handleDisableDesktopTerminal();
                }
            }
        });

        binding.btnFontIncrease.setOnClickListener(v -> {
            AppSettings current = viewModel.getSettingsLiveData().getValue();
            if (current != null && current.getFontSize() < 32) {
                viewModel.updateFontSize(current.getFontSize() + 1);
            }
        });

        binding.btnFontDecrease.setOnClickListener(v -> {
            AppSettings current = viewModel.getSettingsLiveData().getValue();
            if (current != null && current.getFontSize() > 8) {
                viewModel.updateFontSize(current.getFontSize() - 1);
            }
        });

        binding.switchLineNumbers.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateLineNumbers(isChecked);
        });

        binding.switchAutoClose.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoCloseBrackets(isChecked);
        });

        binding.switchAutoCloseQuotes.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoCloseQuotes(isChecked);
        });

        binding.switchAutoCloseTags.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoCloseHtmlTags(isChecked);
        });

        binding.switchWordWrap.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateWordWrap(isChecked);
        });

        binding.switchAutoIndent.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoIndent(isChecked);
        });

        binding.switchRainbowBrackets.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateRainbowBrackets(isChecked);
        });

        binding.switchBracketHighlighting.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateBracketHighlighting(isChecked);
        });

        binding.switchEnableDiagnostics.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) {
                updateSquigglyLinesUi(isChecked, isChecked && binding.switchShowSquigglyLines.isChecked());
                viewModel.updateEnableDiagnostics(isChecked);
            }
        });

        binding.switchShowSquigglyLines.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) {
                if (!binding.switchEnableDiagnostics.isChecked() && isChecked) {
                    btn.setChecked(false);
                    return;
                }
                viewModel.updateShowSquigglyLines(isChecked);
            }
        });

        binding.switchDeleteMatchingPairs.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateDeleteMatchingPairs(isChecked);
        });

        binding.switchForceLargeFileHighlighting.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateForceLargeFileHighlighting(isChecked);
        });

        binding.switchQuickSymbolBar.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) {
                updateCustomizeSymbolsRowUi(isChecked);
                viewModel.updateShowQuickSymbolBar(isChecked);
            }
        });
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                isUpdatingUi = true;
                binding.switchLineNumbers.setChecked(settings.isShowLineNumbers());
                binding.switchAutoClose.setChecked(settings.isAutoCloseBrackets());
                binding.switchAutoCloseQuotes.setChecked(settings.autoCloseQuotes);
                binding.switchAutoCloseTags.setChecked(settings.autoCloseHtmlTags);
                binding.switchWordWrap.setChecked(settings.wordWrap);
                binding.switchAutoIndent.setChecked(settings.autoIndent);
                binding.switchRainbowBrackets.setChecked(settings.rainbowBrackets);
                binding.switchBracketHighlighting.setChecked(settings.bracketHighlighting);
                binding.switchEnableDiagnostics.setChecked(settings.enableDiagnostics);
                updateSquigglyLinesUi(settings.enableDiagnostics, settings.showSquigglyLines);
                binding.switchDeleteMatchingPairs.setChecked(settings.deleteMatchingPairs);
                binding.switchForceLargeFileHighlighting.setChecked(settings.forceLargeFileHighlighting);
                binding.switchQuickSymbolBar.setChecked(settings.showQuickSymbolBar);
                updateCustomizeSymbolsRowUi(settings.showQuickSymbolBar);
                binding.switchDesktopTerminal.setChecked(settings.desktopTerminalEnabled &&
                        UserlandBootstrapManager.getInstance().isInstalled(requireContext()));
                binding.tvFontSizeValue.setText(MessageFormat.format("{0}px", settings.getFontSize()));
                isUpdatingUi = false;
            }
        });
    }

    private void updateCustomizeSymbolsRowUi(boolean isEnabled) {
        binding.opCustomizeQuickSymbols.setEnabled(isEnabled);
        binding.opCustomizeQuickSymbols.setAlpha(isEnabled ? 1.0f : 0.4f);
    }

    private void updateSquigglyLinesUi(boolean diagnosticsEnabled, boolean squigglyChecked) {
        binding.switchShowSquigglyLines.setEnabled(diagnosticsEnabled);
        binding.opShowSquigglyLines.setEnabled(diagnosticsEnabled);
        binding.opShowSquigglyLines.setAlpha(diagnosticsEnabled ? 1.0f : 0.4f);
        binding.switchShowSquigglyLines.setChecked(diagnosticsEnabled && squigglyChecked);
    }

    private void handleEnableDesktopTerminal() {
        UserlandBootstrapManager manager = UserlandBootstrapManager.getInstance();
        if (manager.isInstalled(requireContext())) {
            viewModel.updateDesktopTerminalEnabled(true);
            return;
        }

        // Keep switch visual state false until installation succeeds
        isUpdatingUi = true;
        binding.switchDesktopTerminal.setChecked(false);
        isUpdatingUi = false;

        DesktopTerminalInstallBottomSheet.show(getParentFragmentManager(),
                new DesktopTerminalInstallBottomSheet.InstallListener() {
                    @Override
                    public void onInstallSuccess() {
                        viewModel.updateDesktopTerminalEnabled(true);
                    }

                    @Override
                    public void onInstallCancelled() {
                        if (isAdded()) {
                            isUpdatingUi = true;
                            binding.switchDesktopTerminal.setChecked(false);
                            isUpdatingUi = false;
                        }
                    }
                });
    }

    private void handleDisableDesktopTerminal() {
        UserlandBootstrapManager manager = UserlandBootstrapManager.getInstance();
        if (!manager.isInstalled(requireContext())) {
            viewModel.updateDesktopTerminalEnabled(false);
            return;
        }

        // Keep switch visual state true until user confirms removal
        isUpdatingUi = true;
        binding.switchDesktopTerminal.setChecked(true);
        isUpdatingUi = false;

        RemoveBootstrapConfirmDialog.show(requireContext(), new RemoveBootstrapConfirmDialog.RemoveBootstrapListener() {
            @Override
            public void onConfirmed() {
                ExecutorProvider.getInstance().runOnIo(() -> {
                    manager.uninstallBootstrap(requireContext().getApplicationContext());
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        viewModel.updateDesktopTerminalEnabled(false);
                        if (isAdded()) {
                            isUpdatingUi = true;
                            binding.switchDesktopTerminal.setChecked(false);
                            isUpdatingUi = false;
                            android.widget.Toast.makeText(requireContext(), R.string.vcode_desktop_terminal_removed, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }

            @Override
            public void onCancelled() {
                if (isAdded()) {
                    isUpdatingUi = true;
                    binding.switchDesktopTerminal.setChecked(true);
                    isUpdatingUi = false;
                }
            }
        });
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
