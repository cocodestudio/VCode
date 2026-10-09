package com.cocode.vcode.ide.core.lsp.model;

/**
 * Defines whether the insert text in a completion item should be interpreted as plain text or snippet.
 * Matches LSP 3.17 InsertTextFormat.
 */
public enum InsertTextFormat {
    /**
     * The primary text to be inserted is treated as a plain string.
     */
    PLAIN_TEXT(1),

    /**
     * The primary text to be inserted is treated as a snippet (e.g. $1, ${1:default}).
     */
    SNIPPET(2);

    private final int value;

    InsertTextFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static InsertTextFormat fromValue(int value) {
        if (value == 2) return SNIPPET;
        return PLAIN_TEXT;
    }
}
