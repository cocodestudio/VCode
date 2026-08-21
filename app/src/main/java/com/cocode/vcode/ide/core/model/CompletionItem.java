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
    private int sortScore = 0;

    public CompletionItem(String label, String insertText, String detail, Type type, int cursorOffset) {
        this.label = label;
        this.insertText = insertText;
        this.detail = detail;
        this.type = type;
        this.cursorOffset = cursorOffset;
    }

    /**
     * Returns the text to insert, falling back to label if insertText is null or empty.
     */
    public String getEffectiveInsertText() {
        return (insertText != null && !insertText.isEmpty()) ? insertText : label;
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

    public int getSortScore() {
        return sortScore;
    }

    public void setSortScore(int sortScore) {
        this.sortScore = sortScore;
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