package com.cocode.vcode.ide.core.lsp.model;

/**
 * Diagnostic tags matching LSP 3.17 DiagnosticTag enum.
 */
public enum DiagnosticTag {
    /**
     * Unused or unnecessary code (e.g. grayed out / faded in UI).
     */
    UNNECESSARY(1),

    /**
     * Deprecated or obsolete code (e.g. strikethrough in UI).
     */
    DEPRECATED(2);

    private final int value;

    DiagnosticTag(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DiagnosticTag fromValue(int value) {
        switch (value) {
            case 1: return UNNECESSARY;
            case 2: return DEPRECATED;
            default: return null;
        }
    }
}
