package com.cocode.vcode.ide.core.lsp.model;

/**
 * Symbol tags are extra annotations that tweak the rendering of a symbol.
 * Matches LSP 3.17 SymbolTag.
 */
public enum SymbolTag {
    /**
     * Render a symbol as obsolete, usually using a strike-out.
     */
    DEPRECATED(1);

    private final int value;

    SymbolTag(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SymbolTag fromValue(int value) {
        if (value == 1) return DEPRECATED;
        return null;
    }
}
