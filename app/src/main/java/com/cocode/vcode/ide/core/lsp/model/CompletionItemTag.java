package com.cocode.vcode.ide.core.lsp.model;

/**
 * Completion item tags matching LSP 3.17 CompletionItemTag.
 */
public enum CompletionItemTag {
    /**
     * Render a completion as obsolete, usually using a strike-out.
     */
    DEPRECATED(1);

    private final int value;

    CompletionItemTag(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CompletionItemTag fromValue(int value) {
        if (value == 1) return DEPRECATED;
        return null;
    }
}
