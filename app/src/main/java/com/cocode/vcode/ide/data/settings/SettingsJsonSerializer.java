package com.cocode.vcode.ide.data.settings;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * SettingsJsonSerializer manages serialization, deserialization, and schema validation
 * for VCode's VS Code-compatible settings.json format.
 *
 * It supports both clean hierarchical domain objects and flat dot-notation keys,
 * ensuring backwards compatibility and future extensibility.
 */
public final class SettingsJsonSerializer {

    public static final String SETTINGS_FILE_NAME = "settings.json";

    private SettingsJsonSerializer() {
    }

    /**
     * Serializes only the active, configurable application settings into a structured JSONObject.
     */
    @NonNull
    public static JSONObject serialize(@NonNull AppSettings s) throws JSONException {
        JSONObject root = new JSONObject();

        // Editor settings
        JSONObject editor = new JSONObject();
        editor.put("fontSize", s.fontSize);
        editor.put("showLineNumbers", s.showLineNumbers);
        editor.put("autoCloseBrackets", s.autoCloseBrackets);
        editor.put("autoCloseQuotes", s.autoCloseQuotes);
        editor.put("autoCloseHtmlTags", s.autoCloseHtmlTags);
        editor.put("wordWrap", s.wordWrap);
        editor.put("autoIndent", s.autoIndent);
        editor.put("enableDiagnostics", s.enableDiagnostics);
        editor.put("showSquigglyLines", s.showSquigglyLines);
        editor.put("deleteMatchingPairs", s.deleteMatchingPairs);
        editor.put("forceLargeFileHighlighting", s.forceLargeFileHighlighting);
        root.put("editor", editor);

        // Appearance settings
        JSONObject appearance = new JSONObject();
        appearance.put("theme", s.theme != null ? s.theme.name() : AppSettings.Theme.SYSTEM.name());
        root.put("appearance", appearance);

        // Git settings
        JSONObject git = new JSONObject();
        git.put("defaultBranch", s.gitDefaultBranch != null ? s.gitDefaultBranch : "main");
        git.put("confirmHardReset", s.gitConfirmHardReset);
        git.put("authorName", s.gitAuthorName != null ? s.gitAuthorName : "");
        git.put("authorEmail", s.gitAuthorEmail != null ? s.gitAuthorEmail : "");
        root.put("git", git);

        // General settings
        JSONObject general = new JSONObject();
        general.put("openPreviewInApp", s.openPreviewInApp);
        general.put("autoSave", s.autoSave);
        root.put("general", general);

        return root;
    }

    /**
     * Deserializes a JSONObject into an AppSettings instance, preserving existing values
     * for any settings omitted in the JSON.
     */
    @NonNull
    public static AppSettings deserialize(@NonNull JSONObject root, @Nullable AppSettings current) {
        AppSettings s = current != null ? current : new AppSettings();

        // 1. Hierarchical: Editor
        if (root.has("editor")) {
            JSONObject ed = root.optJSONObject("editor");
            if (ed != null) {
                if (ed.has("fontSize")) s.fontSize = ed.optInt("fontSize", s.fontSize);
                if (ed.has("showLineNumbers")) s.showLineNumbers = ed.optBoolean("showLineNumbers", s.showLineNumbers);
                if (ed.has("autoCloseBrackets")) s.autoCloseBrackets = ed.optBoolean("autoCloseBrackets", s.autoCloseBrackets);
                if (ed.has("autoCloseQuotes")) s.autoCloseQuotes = ed.optBoolean("autoCloseQuotes", s.autoCloseQuotes);
                if (ed.has("autoCloseHtmlTags")) s.autoCloseHtmlTags = ed.optBoolean("autoCloseHtmlTags", s.autoCloseHtmlTags);
                if (ed.has("wordWrap")) s.wordWrap = ed.optBoolean("wordWrap", s.wordWrap);
                if (ed.has("autoIndent")) s.autoIndent = ed.optBoolean("autoIndent", s.autoIndent);
                if (ed.has("enableDiagnostics")) s.enableDiagnostics = ed.optBoolean("enableDiagnostics", s.enableDiagnostics);
                if (ed.has("showSquigglyLines")) s.showSquigglyLines = ed.optBoolean("showSquigglyLines", s.showSquigglyLines);
                if (ed.has("deleteMatchingPairs")) s.deleteMatchingPairs = ed.optBoolean("deleteMatchingPairs", s.deleteMatchingPairs);
                if (ed.has("forceLargeFileHighlighting")) s.forceLargeFileHighlighting = ed.optBoolean("forceLargeFileHighlighting", s.forceLargeFileHighlighting);
            }
        }

        // 2. Hierarchical: Appearance
        if (root.has("appearance")) {
            JSONObject app = root.optJSONObject("appearance");
            if (app != null && app.has("theme")) {
                parseTheme(app.optString("theme"), s);
            }
        }

        // 3. Hierarchical: Git
        if (root.has("git")) {
            JSONObject git = root.optJSONObject("git");
            if (git != null) {
                if (git.has("defaultBranch")) s.gitDefaultBranch = git.optString("defaultBranch", s.gitDefaultBranch);
                if (git.has("confirmHardReset")) s.gitConfirmHardReset = git.optBoolean("confirmHardReset", s.gitConfirmHardReset);
                if (git.has("authorName")) s.gitAuthorName = git.optString("authorName", s.gitAuthorName);
                if (git.has("authorEmail")) s.gitAuthorEmail = git.optString("authorEmail", s.gitAuthorEmail);
            }
        }

        // 4. Hierarchical: General
        if (root.has("general")) {
            JSONObject gen = root.optJSONObject("general");
            if (gen != null) {
                if (gen.has("openPreviewInApp")) s.openPreviewInApp = gen.optBoolean("openPreviewInApp", s.openPreviewInApp);
                if (gen.has("autoSave")) s.autoSave = gen.optBoolean("autoSave", s.autoSave);
            }
        }

        // 5. Flat dot-notation fallbacks (compatibility)
        if (root.has("editor.fontSize")) s.fontSize = root.optInt("editor.fontSize", s.fontSize);
        if (root.has("editor.showLineNumbers")) s.showLineNumbers = root.optBoolean("editor.showLineNumbers", s.showLineNumbers);
        if (root.has("editor.autoCloseBrackets")) s.autoCloseBrackets = root.optBoolean("editor.autoCloseBrackets", s.autoCloseBrackets);
        if (root.has("editor.autoCloseQuotes")) s.autoCloseQuotes = root.optBoolean("editor.autoCloseQuotes", s.autoCloseQuotes);
        if (root.has("editor.autoCloseHtmlTags")) s.autoCloseHtmlTags = root.optBoolean("editor.autoCloseHtmlTags", s.autoCloseHtmlTags);
        if (root.has("editor.wordWrap")) s.wordWrap = root.optBoolean("editor.wordWrap", s.wordWrap);
        if (root.has("editor.autoIndent")) s.autoIndent = root.optBoolean("editor.autoIndent", s.autoIndent);
        if (root.has("editor.enableDiagnostics")) s.enableDiagnostics = root.optBoolean("editor.enableDiagnostics", s.enableDiagnostics);
        if (root.has("editor.showSquigglyLines")) s.showSquigglyLines = root.optBoolean("editor.showSquigglyLines", s.showSquigglyLines);
        if (root.has("editor.deleteMatchingPairs")) s.deleteMatchingPairs = root.optBoolean("editor.deleteMatchingPairs", s.deleteMatchingPairs);
        if (root.has("editor.forceLargeFileHighlighting")) s.forceLargeFileHighlighting = root.optBoolean("editor.forceLargeFileHighlighting", s.forceLargeFileHighlighting);

        if (root.has("appearance.theme")) parseTheme(root.optString("appearance.theme"), s);
        if (root.has("theme")) parseTheme(root.optString("theme"), s);

        if (root.has("git.defaultBranch")) s.gitDefaultBranch = root.optString("git.defaultBranch", s.gitDefaultBranch);
        if (root.has("git.confirmHardReset")) s.gitConfirmHardReset = root.optBoolean("git.confirmHardReset", s.gitConfirmHardReset);
        if (root.has("git.authorName")) s.gitAuthorName = root.optString("git.authorName", s.gitAuthorName);
        if (root.has("git.authorEmail")) s.gitAuthorEmail = root.optString("git.authorEmail", s.gitAuthorEmail);

        if (root.has("general.openPreviewInApp")) s.openPreviewInApp = root.optBoolean("general.openPreviewInApp", s.openPreviewInApp);
        if (root.has("general.autoSave")) s.autoSave = root.optBoolean("general.autoSave", s.autoSave);

        if (!s.enableDiagnostics) {
            s.showSquigglyLines = false;
        }

        return s;
    }

    private static void parseTheme(String themeStr, AppSettings s) {
        if (themeStr != null && !themeStr.trim().isEmpty()) {
            try {
                s.theme = AppSettings.Theme.valueOf(themeStr.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Validates JSON string content and parses into AppSettings if valid.
     */
    @NonNull
    public static ValidationResult validateAndParse(@Nullable String jsonString, @Nullable AppSettings current) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return ValidationResult.error("The selected file is empty.");
        }

        JSONObject root;
        try {
            root = new JSONObject(jsonString);
        } catch (JSONException e) {
            return ValidationResult.error("The selected file is not valid JSON and could not be parsed.");
        }

        // Domain check: Must contain at least one known section or recognizable VCode setting key
        boolean hasKnownSection = root.has("editor") || root.has("appearance") || root.has("git") || root.has("general");
        boolean hasKnownFlatKey = root.has("editor.fontSize") || root.has("editor.showLineNumbers")
                || root.has("editor.enableDiagnostics") || root.has("editor.showSquigglyLines")
                || root.has("editor.deleteMatchingPairs") || root.has("editor.forceLargeFileHighlighting")
                || root.has("fontSize") || root.has("theme")
                || root.has("git.defaultBranch") || root.has("defaultBranch")
                || root.has("general.openPreviewInApp") || root.has("autoSave");

        if (!hasKnownSection && !hasKnownFlatKey) {
            return ValidationResult.error("This file does not contain recognized VCode settings. Please select a valid settings.json file.");
        }

        AppSettings parsed = deserialize(root, current);
        return ValidationResult.success(parsed);
    }

    /**
     * Returns the app-specific external files location for live settings.json.
     */
    @NonNull
    public static File getExternalSettingsFile(@NonNull Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, SETTINGS_FILE_NAME);
    }

    /**
     * Returns the VCodeProjects root destination for settings export.
     */
    @NonNull
    public static File getProjectsExportFile() {
        File projectsDir = FileUtils.getProjectsDirectory();
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        return new File(projectsDir, SETTINGS_FILE_NAME);
    }

    /**
     * Writes formatted settings JSON to a destination file.
     */
    public static void writeSettingsToFile(@NonNull File targetFile, @NonNull AppSettings settings) throws IOException, JSONException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        JSONObject json = serialize(settings);
        String formatted = json.toString(2);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            writer.write(formatted);
            writer.flush();
        }
    }

    /**
     * Reads and validates settings from a local file.
     */
    @NonNull
    public static ValidationResult readAndValidateFromFile(@NonNull File file, @Nullable AppSettings current) {
        if (!file.exists() || file.length() == 0) {
            return ValidationResult.error("The selected file is empty or does not exist.");
        }

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return validateAndParse(sb.toString(), current);
        } catch (IOException e) {
            return ValidationResult.error("Could not read the selected file: " + e.getLocalizedMessage());
        }
    }

    /**
     * Reads and validates settings from a content URI picked via SAF.
     */
    @NonNull
    public static ValidationResult readAndValidateFromUri(@NonNull Context context, @NonNull Uri uri, @Nullable AppSettings current) {
        try {
            StringBuilder sb = new StringBuilder();
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return ValidationResult.error("Could not open the selected file.");
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
            }
            return validateAndParse(sb.toString(), current);
        } catch (IOException e) {
            return ValidationResult.error("Could not read the selected file: " + e.getLocalizedMessage());
        }
    }

    /**
     * Result container for validation operations.
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final AppSettings settings;

        private ValidationResult(boolean isValid, String errorMessage, AppSettings settings) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.settings = settings;
        }

        public static ValidationResult success(AppSettings settings) {
            return new ValidationResult(true, null, settings);
        }

        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }
    }
}
