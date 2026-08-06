package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.databinding.ActivitySettingsBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.sheets.git.GitAuthorInfoBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.SshKeyBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.text.MessageFormat;

/**
 * SettingsActivity provides a user interface for configuring global application preferences.
 * This includes editor behavior, Git credentials, theme selection, and preview options.
 * Changes made here are persisted via the SettingsViewModel and immediately reflected across the app.
 */
public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;

    /**
     * Flag to prevent infinite recursive updates when the UI is being populated from the model.
     */
    private boolean isUpdatingUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply standard edge-to-edge system bar padding
        UiUtils.applySystemBarInsets(binding.getRoot());

        // Initialize ViewModel using a factory for dependency injection
        viewModel = new ViewModelProvider(this, new SettingsViewModel.Factory(this)).get(SettingsViewModel.class);

        setupListeners();
        designUI();
        setupObserver();
    }

    /**
     * Connects UI interaction listeners (switches, buttons, text inputs) to ViewModel update methods.
     */
    private void setupListeners() {
        // Handle row clicks by toggling their associated switches
        binding.opShowLineNumbers.setOnClickListener(_view -> binding.switchLineNumbers.setChecked(!binding.switchLineNumbers.isChecked()));
        binding.opAutoCloseBrackets.setOnClickListener(_view -> binding.switchAutoClose.setChecked(!binding.switchAutoClose.isChecked()));
        binding.opAutoCloseQuotes.setOnClickListener(_view -> binding.switchAutoCloseQuotes.setChecked(!binding.switchAutoCloseQuotes.isChecked()));
        binding.opAutoCloseTags.setOnClickListener(_view -> binding.switchAutoCloseTags.setChecked(!binding.switchAutoCloseTags.isChecked()));
        binding.opWordWrap.setOnClickListener(_view -> binding.switchWordWrap.setChecked(!binding.switchWordWrap.isChecked()));
        binding.opAutoIndent.setOnClickListener(_view -> binding.switchAutoIndent.setChecked(!binding.switchAutoIndent.isChecked()));
        binding.opConfirmHardReset.setOnClickListener(_view -> binding.switchConfirmReset.setChecked(!binding.switchConfirmReset.isChecked()));
        binding.opShowInAppPreview.setOnClickListener(_view -> binding.switchInAppPreview.setChecked(!binding.switchInAppPreview.isChecked()));
        binding.opAutoSave.setOnClickListener(_view -> binding.switchAutoSave.setChecked(!binding.switchAutoSave.isChecked()));

        // Launch the Git author info editor sheet
        binding.opGitCredentials.setOnClickListener(_view -> {
            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            if (settings != null) {
                String name = settings.gitAuthorName != null ? settings.gitAuthorName : "";
                String email = settings.gitAuthorEmail != null ? settings.gitAuthorEmail : "";
                String buttonText = (name.isEmpty() && email.isEmpty()) ? "Save" : "Edit";
                GitAuthorInfoBottomSheet sheet = GitAuthorInfoBottomSheet.newInstance(
                        name,
                        email,
                        buttonText
                );
                sheet.setListener((n, e) -> viewModel.updateGitCredentials(n, e));
                sheet.show(getSupportFragmentManager(), "GitAuthorInfoBottomSheet");
            }
        });

        // Launch SSH Key sheet
        binding.opSshKey.setOnClickListener(_view -> {
            SshKeyBottomSheet sheet = SshKeyBottomSheet.newInstance();
            sheet.show(getSupportFragmentManager(), "SshKeyBottomSheet");
        });

        // Font size adjustment listeners with boundary checks
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

        // Map switch changes to persistent settings updates
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


        binding.switchInAppPreview.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateOpenPreviewInApp(isChecked);
        });

        binding.switchAutoSave.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoSave(isChecked);
        });

        binding.switchConfirmReset.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateConfirmHardReset(isChecked);
        });

        // Theme selection logic
        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (isUpdatingUi) return;

            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

            if (checkedId == R.id.radio_theme_system) {
                viewModel.updateTheme(AppSettings.Theme.SYSTEM);
            } else if (checkedId == R.id.radio_theme_dark) {
                viewModel.updateTheme(AppSettings.Theme.DARK);
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else if (checkedId == R.id.radio_theme_light) {
                viewModel.updateTheme(AppSettings.Theme.LIGHT);
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            }

            // Immediately apply the theme change to the application
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        // Default branch name configuration
        binding.etDefaultBranchValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingUi) viewModel.updateDefaultBranch(s.toString().trim());
            }
        });
    }

    /**
     * Sets up the LiveData observer to keep the UI in sync with the persisted settings.
     */
    private void setupObserver() {
        viewModel.getSettingsLiveData().observe(this, settings -> {
            if (settings != null) {
                isUpdatingUi = true; // Suspend listener triggers while populating

                binding.switchLineNumbers.setChecked(settings.isShowLineNumbers());
                binding.switchAutoClose.setChecked(settings.isAutoCloseBrackets());
                binding.switchAutoCloseQuotes.setChecked(settings.autoCloseQuotes);
                binding.switchAutoCloseTags.setChecked(settings.autoCloseHtmlTags);
                binding.switchWordWrap.setChecked(settings.wordWrap);
                binding.switchAutoIndent.setChecked(settings.autoIndent);
                binding.switchInAppPreview.setChecked(settings.openPreviewInApp);
                binding.switchAutoSave.setChecked(settings.autoSave);
                binding.switchConfirmReset.setChecked(settings.gitConfirmHardReset);

                binding.tvFontSizeValue.setText(MessageFormat.format("{0}px", settings.getFontSize()));

                // Update text fields only if they have diverged from the model
                if (!binding.etDefaultBranchValue.getText().toString().equals(settings.gitDefaultBranch)) {
                    binding.etDefaultBranchValue.setText(settings.gitDefaultBranch);
                }

                // Display a summary of the configured Git author
                if (settings.gitAuthorName != null && !settings.gitAuthorName.trim().isEmpty()) {
                    binding.tvGitCredentialsDesc.setText(settings.gitAuthorName + " <" + settings.gitAuthorEmail + ">");
                } else {
                    binding.tvGitCredentialsDesc.setText(R.string.vcode_not_configured);
                }

                // Sync the theme radio group selection
                switch (settings.getTheme()) {
                    case SYSTEM:
                        binding.rgTheme.check(R.id.radio_theme_system);
                        break;
                    case DARK:
                        binding.rgTheme.check(R.id.radio_theme_dark);
                        break;
                    case LIGHT:
                        binding.rgTheme.check(R.id.radio_theme_light);
                        break;
                }

                isUpdatingUi = false;
            }
        });
    }

    /**
     * Builds the syntax theme picker row programmatically.
     * Each theme gets a rounded swatch showing its editor background + keyword + string colors.
     * The active theme gets a white border ring.
     * /**
     * Applies the design system fonts to all textual components in the activity.
     */
    private void designUI() {
        FontManager fm = FontManager.getInstance();

        // Style section headers
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(this));
        binding.tvEditorSettings.setTypeface(fm.getUiMedium(this));
        binding.tvGitSettings.setTypeface(fm.getUiMedium(this));
        binding.tvThemeSettings.setTypeface(fm.getUiMedium(this));
        binding.tvGeneralSetting.setTypeface(fm.getUiMedium(this));

        // Style editor preference rows
        binding.tvShowLineNumbers.setTypeface(fm.getUiMedium(this));
        binding.tvShowLineNumbersDesc.setTypeface(fm.getUiFont(this));
        binding.tvFontSize.setTypeface(fm.getUiMedium(this));
        binding.tvFontSizeValue.setTypeface(fm.getUiFont(this));
        binding.tvAutoCloseBrackets.setTypeface(fm.getUiMedium(this));
        binding.tvAutoCloseBracketsDesc.setTypeface(fm.getUiFont(this));
        binding.tvAutoCloseQuotes.setTypeface(fm.getUiMedium(this));
        binding.tvAutoCloseQuotesDesc.setTypeface(fm.getUiFont(this));
        binding.tvAutoCloseTags.setTypeface(fm.getUiMedium(this));
        binding.tvAutoCloseTagsDesc.setTypeface(fm.getUiFont(this));
        binding.tvWordWrap.setTypeface(fm.getUiMedium(this));
        binding.tvWordWrapDesc.setTypeface(fm.getUiFont(this));
        binding.tvAutoIndent.setTypeface(fm.getUiMedium(this));
        binding.tvAutoIndentDesc.setTypeface(fm.getUiFont(this));

        // Style Git preference rows
        binding.tvDefaultBranch.setTypeface(fm.getUiMedium(this));
        binding.etDefaultBranchValue.setTypeface(fm.getCodeFont(this));
        binding.tvHardReset.setTypeface(fm.getUiMedium(this));
        binding.tvHardResetDesc.setTypeface(fm.getUiFont(this));
        binding.tvGitCredentials.setTypeface(fm.getUiMedium(this));
        binding.tvGitCredentialsDesc.setTypeface(fm.getUiFont(this));
        binding.tvSshKey.setTypeface(fm.getUiMedium(this));
        binding.tvSshKeyDesc.setTypeface(fm.getUiFont(this));

        // Style theme selection options
        binding.radioThemeSystem.setTypeface(fm.getUiFont(this));
        binding.radioThemeDark.setTypeface(fm.getUiFont(this));
        binding.radioThemeLight.setTypeface(fm.getUiFont(this));

        // Style general preference rows
        binding.tvShowInAppPreview.setTypeface(fm.getUiMedium(this));
        binding.tvShowInAppPreviewDesc.setTypeface(fm.getUiFont(this));
        binding.tvAutoSave.setTypeface(fm.getUiMedium(this));
        binding.tvAutoSaveDesc.setTypeface(fm.getUiFont(this));
    }
}