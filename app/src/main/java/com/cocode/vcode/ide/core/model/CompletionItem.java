package com.cocode.vcode.ide.core.model;

/**
 * Model representing an autocomplete suggestion item in the editor popup.
 */
public class CompletionItem {

    private final String label;
    private final String insertText;
    private final int cursorOffset;
    private String detail;
    private Type type;
    private int replaceLength = -1;
    private int replaceAfterLength = 0;
    private int sortScore = 0;
    /**
     * Absolute URI of the file this completion came from. Used by
     * cross-file completion to remember the source
     * of an export so the import-statement auto-insert can compute
     * the relative path. {@code null} for non-cross-file completions.
     */
    private String sourceUri;

    public CompletionItem(String label, String insertText, String detail, Type type, int cursorOffset) {
        this.label = label;
        this.insertText = insertText;
        this.detail = detail;
        this.type = type;
        this.cursorOffset = cursorOffset;
    }

    /**
     * Constructor with source-URI attached. Use for cross-file
     * completions so the import-statement auto-insert
     * can compute the relative path.
     */
    public CompletionItem(String label, String insertText, String detail, Type type, int cursorOffset, String sourceUri) {
        this.label = label;
        this.insertText = insertText;
        this.detail = detail;
        this.type = type;
        this.cursorOffset = cursorOffset;
        this.sourceUri = sourceUri;
    }

    /**
     * Copy constructor.
     */
    public CompletionItem(CompletionItem other) {
        this.label = other.label;
        this.insertText = other.insertText;
        this.detail = other.detail;
        this.type = other.type;
        this.cursorOffset = other.cursorOffset;
        this.replaceLength = other.replaceLength;
        this.replaceAfterLength = other.replaceAfterLength;
        this.sortScore = other.sortScore;
        this.sourceUri = other.sourceUri;
    }

    /**
     * Returns the text to insert, falling back to label if insertText is null or empty.
     */
    public String getEffectiveInsertText() {
        return (insertText != null && !insertText.isEmpty()) ? insertText : label;
    }

    public String getInsertText() {
        return insertText;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getCursorOffset() {
        return cursorOffset;
    }

    public int getReplaceLength() {
        return replaceLength;
    }

    public void setReplaceLength(int replaceLength) {
        this.replaceLength = replaceLength;
    }

    public int getReplaceAfterLength() {
        return replaceAfterLength;
    }

    public void setReplaceAfterLength(int replaceAfterLength) {
        this.replaceAfterLength = replaceAfterLength;
    }

    public int getSortScore() {
        return sortScore;
    }

    public void setSortScore(int sortScore) {
        this.sortScore = sortScore;
    }

    /**
     * Returns the absolute URI of the file this completion came
     * from, or {@code null} for non-cross-file completions.
     */
    public String getSourceUri() {
        return sourceUri;
    }

    /**
     * Sets the absolute URI of the file this completion came from.
     * Used by cross-file completion to remember the export's source
     * so the import-statement auto-insert can compute
     * the relative path.
     */
    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    /**
     * Returns the base priority rank for this item's Type.
     * Mirrors VS Code's CompletionItemKind sort priority: snippets > functions > keywords > values.
     * Emmet/snippets get the highest priority to always appear first.
     */
    public int getTypePriority() {
        if (type == null) return 0;
        switch (type) {
            case SNIPPET:
                return 10;
            case FUNCTION:
                return 6;
            case BUILTIN:
                return 5;
            case KEYWORD:
                return 4;
            case TAG:
                return 3;
            case ATTRIBUTE:
                return 3;
            case CSS_PROPERTY:
                return 3;
            case CSS_VALUE:
                return 2;
            case VALUE:
                return 2;
            case JSON_KEY:
                return 2;
            case FILE:
                return 1;
            case FOLDER:
                return 1;
            default:
                return 0;
        }
    }

    public enum Type {
        TAG,
        ATTRIBUTE,
        VALUE,
        CSS_PROPERTY,
        CSS_VALUE,
        KEYWORD,
        FUNCTION,
        BUILTIN,
        SNIPPET,
        JSON_KEY,
        FILE,
        FOLDER
    }
}