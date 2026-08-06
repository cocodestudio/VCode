package com.cocode.vcode.ide.core.lsp;

/**
 * A code completion suggestion returned by an {@link LspServer}.
 * <p>
 * Mirrors the essential fields of the LSP specification's {@code CompletionItem} type
 * without pulling in the full lsp4j transport layer.
 */
public final class LspCompletionItem {

    // -------------------------------------------------------------------------
    // Completion item kinds — mirrors LSP CompletionItemKind values
    // -------------------------------------------------------------------------
    public static final int KIND_TEXT = 1;
    public static final int KIND_METHOD = 2;
    public static final int KIND_FUNCTION = 3;
    public static final int KIND_CONSTRUCTOR = 4;
    public static final int KIND_FIELD = 5;
    public static final int KIND_VARIABLE = 6;
    public static final int KIND_CLASS = 7;
    public static final int KIND_INTERFACE = 8;
    public static final int KIND_MODULE = 9;
    public static final int KIND_PROPERTY = 10;
    public static final int KIND_UNIT = 11;
    public static final int KIND_VALUE = 12;
    public static final int KIND_ENUM = 13;
    public static final int KIND_KEYWORD = 14;
    public static final int KIND_SNIPPET = 15;
    public static final int KIND_COLOR = 16;
    public static final int KIND_FILE = 17;
    public static final int KIND_REFERENCE = 18;
    public static final int KIND_FOLDER = 19;

    /**
     * The label shown in the autocomplete popup list.
     */
    public final String label;

    /**
     * Text inserted into the document when this item is selected.
     * If null, {@link #label} is inserted instead.
     */
    public final String insertText;

    /**
     * Item kind constant (one of KIND_* constants above).
     */
    public final int kind;

    /**
     * Optional detail shown to the right of the label (e.g. type signature, source file).
     */
    public final String detail;

    /**
     * Optional documentation shown below the item.
     */
    public final String documentation;

    /**
     * Sort key — lower sort text sorts earlier in the list.
     */
    public final String sortText;

    /**
     * Whether inserting this item should trigger a re-request for completions (e.g. after a dot).
     */
    public final boolean commitTriggerReRequest;

    public LspCompletionItem(String label, String insertText, int kind, String detail, String documentation) {
        this.label = label;
        this.insertText = insertText != null ? insertText : label;
        this.kind = kind;
        this.detail = detail;
        this.documentation = documentation;
        this.sortText = label;
        this.commitTriggerReRequest = false;
    }

    public LspCompletionItem(String label, int kind) {
        this(label, null, kind, null, null);
    }

    public LspCompletionItem(String label, String insertText, int kind) {
        this(label, insertText, kind, null, null);
    }

    /**
     * Returns the text that should be physically inserted into the editor buffer.
     */
    public String getInsertText() {
        return insertText != null ? insertText : label;
    }
}
