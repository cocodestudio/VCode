package com.cocode.vcode.ide.data.model;

/**
 * Data model for global and project-level settings (editor preferences, Git defaults, appearance, preview, and auto-save).
 */
public class AppSettings {

    // Editor Configurations
    public int fontSize = 14;
    public int tabSize = 2;
    public boolean showLineNumbers = true;
    public boolean autoCloseBrackets = true;
    public boolean autoCloseHtmlTags = true;
    public boolean autoCloseQuotes = true;
    public boolean wordWrap = false;
    public boolean highlightCurrentLine = true;
    public boolean autoIndent = true;
    public boolean matchBrackets = true;

    // JSON Validation & Formatting
    public int jsonIndentSize = 2;
    public boolean jsonFormatOnSave = false;

    // Git Integration Settings
    public String gitAuthorName = "";
    public String gitAuthorEmail = "";
    public String gitDefaultBranch = "main";
    public boolean gitAutoFetch = false;
    public boolean gitConfirmPush = true;
    public boolean gitConfirmHardReset = true;
    public boolean gitShowFileTreeStatus = true;
    public String gitDefaultRemote = "origin";

    // Appearance Settings
    public Theme theme = Theme.SYSTEM;

    // Live Preview Preferences
    public boolean openPreviewInApp = true;
    public boolean autoRefreshPreview = false;

    // Auto-Save Settings
    public boolean autoSave = false;
    public int autoSaveDelay = 2; // seconds

    // Safety and Confirmation Dialogs
    public boolean confirmOnTabClose = true;
    public boolean confirmOnProjectDelete = true;

    public AppSettings() {
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int size) {
        this.fontSize = size;
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
    }

    public boolean isAutoCloseBrackets() {
        return autoCloseBrackets;
    }

    public void setAutoCloseBrackets(boolean auto) {
        this.autoCloseBrackets = auto;
    }

    /**
     * Resolves the configured branch destination metadata rule.
     */
    public String getDefaultBranch() {
        return gitDefaultBranch;
    }

    /**
     * Updates the default target remote branch destination assignment marker.
     */
    public void setDefaultBranch(String branch) {
        this.gitDefaultBranch = branch;
    }

    /**
     * Checks verification rules for deletion and reset confirmations.
     */
    public boolean isConfirmHardReset() {
        return gitConfirmHardReset;
    }

    /**
     * Configures the enforcement prompt checks before permanent item teardowns.
     */
    public void setConfirmHardReset(boolean confirm) {
        this.gitConfirmHardReset = confirm;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    /**
     * Resolves the preferred baseline layout step block by stacking horizontal tab keys.
     * Used by formatter and auto-indent engines to create baseline padding lines.
     */
    public String getIndent() {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < tabSize; i++) {
            indent.append(" ");
        }
        return indent.toString();
    }

    /**
     * Available UI visual style modes supported by the workspace presentation layer.
     */
    public enum Theme {DARK, LIGHT, SYSTEM}
}
