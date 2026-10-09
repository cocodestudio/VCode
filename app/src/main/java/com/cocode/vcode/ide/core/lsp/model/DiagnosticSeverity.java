package com.cocode.vcode.ide.core.lsp.model;

/**
 * Diagnostic severity levels matching LSP 3.17 DiagnosticSeverity enum.
 */
public enum DiagnosticSeverity {
    ERROR(1),
    WARNING(2),
    INFORMATION(3),
    HINT(4);

    private final int value;

    DiagnosticSeverity(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DiagnosticSeverity fromValue(int value) {
        switch (value) {
            case 1: return ERROR;
            case 2: return WARNING;
            case 3: return INFORMATION;
            case 4: return HINT;
            default: return ERROR;
        }
    }
}
