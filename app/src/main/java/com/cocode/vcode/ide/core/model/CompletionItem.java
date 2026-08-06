package com.cocode.vcode.ide.core.model;

/**
 * Represents a single suggestion entry in the editor's autocomplete dropdown.
 * It holds structural data required to display the suggestion, decide what text
 * to insert, manage the final cursor positioning after selection, and sort items
 * intelligently based on relevance score — mirroring VS Code's IntelliSense ranking.
 */
public class CompletionItem {

    private final String label;       // The text displayed to the user in the popup list
    private final String insertText;  // The actual snippet or text injected into the document
    private final int cursorOffset;   // Relative cursor positioning adjustments post-insertion
    private String detail;            // Optional context info (e.g., "Method", "Tag", description)
    private Type type;                // The syntactic category of this token
    private int replaceLength = -1;   // Explicit number of chars to remove before insertion (-1 for default)
    private int sortScore = 0;        // Higher score = higher in the list; set by the fuzzy scorer

    /**
     * Constructs a fully initialized completion item.
     */
    public CompletionItem(String label, String insertText, String detail, Type type, int cursorOffset) {
        this.label = label;
        this.insertText = insertText;
        this.detail = detail;
        this.type = type;
        this.cursorOffset = cursorOffset;
    }

    /**
     * Resolves the actual text to be safely injected into the editor.
     * Reverts back to using the visual label if a specific insertion text isn't provided.
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

    /**
     * Categorizes suggestions to aid the UI Layer in rendering distinct icons or styles.
     * Aligned with VS Code's CompletionItemKind vocabulary.
     */
    public enum Type {
        TAG,            // HTML structural tags
        ATTRIBUTE,      // XML/HTML tag attributes
        VALUE,          // Generic property values / document words
        CSS_PROPERTY,   // CSS style rules (e.g., "margin", "display")
        CSS_VALUE,      // Valid CSS metrics or descriptors (e.g., "block", "pointer")
        KEYWORD,        // Core language statements (e.g., "if", "const")
        FUNCTION,       // Call signatures and custom subroutines
        BUILTIN,        // Platform-standard symbols (e.g., "console.log")
        SNIPPET,        // Expandable structural boilerplate blocks
        JSON_KEY,       // JSON field property strings
        FILE,           // Local workspace file
        FOLDER          // Local workspace directory
    }
}