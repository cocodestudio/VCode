package com.cocode.vcode.ide.ui.settings;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.repository.SettingsRepository;
import com.cocode.vcode.ide.data.settings.SettingsJsonSerializer;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;

/**
 * SettingsViewModel manages the state and persistence of application-wide preferences.
 * It provides a reactive interface for updating editor behavior, theme selection,
 * Git credentials, and preview configurations.
 */
public class SettingsViewModel extends ViewModel {

    private final SettingsRepository settingsRepo;
    private final MutableLiveData<AppSettings> settingsLiveData = new MutableLiveData<>();

    public SettingsViewModel(SettingsRepository settingsRepo) {
        this.settingsRepo = settingsRepo;
        loadSettings();
    }

    public LiveData<AppSettings> getSettingsLiveData() {
        return settingsLiveData;
    }

    /**
     * Loads the current settings from the repository asynchronously.
     */
    public void loadSettings() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            AppSettings settings = settingsRepo.loadSettings();
            settingsLiveData.postValue(settings);
        });
    }

    /**
     * Persists the provided settings model to disk asynchronously.
     */
    public void saveSettings(AppSettings newSettings) {
        if (newSettings == null) return;
        ExecutorProvider.getInstance().runOnIo(() -> {
            settingsRepo.saveSettings(newSettings);
            settingsLiveData.postValue(newSettings);
        });
    }

    /**
     * Updates the editor font size.
     */
    public void updateFontSize(int size) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.fontSize = size;
            saveSettings(current);
        }
    }

    /**
     * Toggles the visibility of line numbers in the editor.
     */
    public void updateLineNumbers(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.showLineNumbers = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles the automatic closing of brackets and quotes.
     */
    public void updateAutoCloseBrackets(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.autoCloseBrackets = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles automatic indentation for new lines.
     */
    public void updateAutoIndent(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.autoIndent = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles word wrap in the editor.
     */
    public void updateWordWrap(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.wordWrap = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles auto-closing of HTML tags.
     */
    public void updateAutoCloseHtmlTags(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.autoCloseHtmlTags = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles auto-closing of quote characters.
     */
    public void updateAutoCloseQuotes(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.autoCloseQuotes = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles background code diagnostics and diagnostic bar visibility.
     * When diagnostics are disabled, squiggly lines are automatically disabled.
     */
    public void updateEnableDiagnostics(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.enableDiagnostics = value;
            if (!value) {
                current.showSquigglyLines = false;
            }
            saveSettings(current);
        }
    }

    /**
     * Toggles rendering of wavy squiggly underlines on syntax errors and warnings.
     * Cannot be enabled if diagnostics are disabled.
     */
    public void updateShowSquigglyLines(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            if (!current.enableDiagnostics && value) {
                return;
            }
            current.showSquigglyLines = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles deletion of matching delimiter pairs on backspace.
     */
    public void updateDeleteMatchingPairs(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.deleteMatchingPairs = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles syntax highlighting on large files (> 10k lines).
     */
    public void updateForceLargeFileHighlighting(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.forceLargeFileHighlighting = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles rainbow bracket coloring.
     */
    public void updateRainbowBrackets(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.rainbowBrackets = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles stroked boundary highlighting around matching brackets.
     */
    public void updateBracketHighlighting(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.bracketHighlighting = value;
            current.matchBrackets = value;
            saveSettings(current);
        }
    }


    /**
     * Updates the global application theme (Light, Dark, or System).
     */
    public void updateTheme(AppSettings.Theme theme) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.theme = theme;
            saveSettings(current);
        }
    }

    /**
     * Updates the default branch name used for new Git repositories.
     */
    public void updateDefaultBranch(String value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.gitDefaultBranch = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles the confirmation requirement for destructive Hard resets.
     */
    public void updateConfirmHardReset(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.gitConfirmHardReset = value;
            saveSettings(current);
        }
    }

    /**
     * Updates the Git user identification (Name and Email) used for commits.
     */
    public void updateGitCredentials(String name, String email) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.gitAuthorName = name;
            current.gitAuthorEmail = email;
            saveSettings(current);
        }
    }

    /**
     * Toggles whether the web preview opens inside the app or in an external browser.
     */
    public void updateOpenPreviewInApp(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.openPreviewInApp = value;
            saveSettings(current);
        }
    }

    /**
     * Toggles the background auto-save mechanism for the code editor.
     */
    public void updateAutoSave(boolean value) {
        AppSettings current = settingsLiveData.getValue();
        if (current != null) {
            current.autoSave = value;
            saveSettings(current);
        }
    }

    public interface ImportCallback {
        void onResult(SettingsJsonSerializer.ValidationResult result);
    }

    public interface ExportCallback {
        void onSuccess(File exportedFile);
        void onError(Exception error);
    }

    /**
     * Imports and applies settings from a user-selected JSON file URI.
     */
    public void importSettings(Uri uri, ImportCallback callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            SettingsJsonSerializer.ValidationResult result = settingsRepo.importSettingsFromUri(uri);
            if (result.isValid && result.settings != null) {
                settingsLiveData.postValue(result.settings);
            }
            if (callback != null) {
                ExecutorProvider.getInstance().runOnMain(() -> callback.onResult(result));
            }
        });
    }

    /**
     * Exports current settings to VCodeProjects/settings.json.
     */
    public void exportSettings(ExportCallback callback) {
        AppSettings current = settingsLiveData.getValue();
        if (current == null) {
            current = settingsRepo.loadSettings();
        }
        final AppSettings settingsToExport = current;
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File exported = settingsRepo.exportSettingsToProjects(settingsToExport);
                if (callback != null) {
                    ExecutorProvider.getInstance().runOnMain(() -> callback.onSuccess(exported));
                }
            } catch (Exception e) {
                if (callback != null) {
                    ExecutorProvider.getInstance().runOnMain(() -> callback.onError(e));
                }
            }
        });
    }

    /**
     * Factory class for creating instances of {@link SettingsViewModel} with a {@link SettingsRepository}.
     */
    public static class Factory implements ViewModelProvider.Factory {
        private final Context appContext;

        public Factory(Context context) {
            this.appContext = context.getApplicationContext();
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
                return (T) new SettingsViewModel(new SettingsRepository(appContext));
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}