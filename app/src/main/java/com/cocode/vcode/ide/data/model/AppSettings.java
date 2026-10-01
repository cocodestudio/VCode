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
    public boolean rainbowBrackets = true;
    public boolean bracketHighlighting = true;
    public boolean enableDiagnostics = true;
    public boolean showSquigglyLines = true;
    public boolean deleteMatchingPairs = true;
    public boolean forceLargeFileHighlighting = false;
    public boolean showQuickSymbolBar = true;
    public java.util.List<String> quickSymbols = null;

    public static final java.util.List<String> DEFAULT_QUICK_SYMBOLS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList("Tab", "{", "}", "(", ")", "[", "]", ";", ":", "\"", "'", "=", ">", "<", "!", "/", "->", "=>")
    );

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

    public boolean isShowQuickSymbolBar() {
        return showQuickSymbolBar;
    }

    public void setShowQuickSymbolBar(boolean show) {
        this.showQuickSymbolBar = show;
    }

    public java.util.List<String> getQuickSymbols() {
        return quickSymbols;
    }

    public void setQuickSymbols(java.util.List<String> symbols) {
        this.quickSymbols = symbols != null ? new java.util.ArrayList<>(symbols) : null;
    }

    public java.util.List<String> getQuickSymbolsList() {
        if (quickSymbols == null || quickSymbols.isEmpty()) {
            return new java.util.ArrayList<>(DEFAULT_QUICK_SYMBOLS);
        }
        return new java.util.ArrayList<>(quickSymbols);
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

    public boolean isEnableDiagnostics() {
        return enableDiagnostics;
    }

    public void setEnableDiagnostics(boolean enable) {
        this.enableDiagnostics = enable;
        if (!enable) {
            this.showSquigglyLines = false;
        }
    }

    public boolean isShowSquigglyLines() {
        return enableDiagnostics && showSquigglyLines;
    }

    public void setShowSquigglyLines(boolean show) {
        this.showSquigglyLines = enableDiagnostics && show;
    }

    public boolean isRainbowBrackets() {
        return rainbowBrackets;
    }

    public void setRainbowBrackets(boolean rainbowBrackets) {
        this.rainbowBrackets = rainbowBrackets;
    }

    public boolean isBracketHighlighting() {
        return bracketHighlighting;
    }

    public void setBracketHighlighting(boolean bracketHighlighting) {
        this.bracketHighlighting = bracketHighlighting;
        this.matchBrackets = bracketHighlighting;
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
