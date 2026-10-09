package com.cocode.vcode.ide.core.lsp.model;

/**
 * A document highlight kind matching LSP 3.17 DocumentHighlightKind.
 */
public enum DocumentHighlightKind {
    /**
     * A textual occurrence.
     */
    TEXT(1),

    /**
     * Read-access of a symbol, like reading a variable.
     */
    READ(2),

    /**
     * Write-access of a symbol, like writing to a variable.
     */
    WRITE(3);

    private final int value;

    DocumentHighlightKind(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DocumentHighlightKind fromValue(int value) {
        switch (value) {
            case 1: return TEXT;
            case 2: return READ;
            case 3: return WRITE;
            default: return TEXT;
        }
    }
}
