package com.cocode.vcode.ide.core.lsp;

/**
 * A diagnostic (error or warning) produced by an {@link LspServer}.
 * <p>
 * Mirrors the essential fields of the LSP specification's {@code Diagnostic} type.
 */
public final class LspDiagnostic {

    // -------------------------------------------------------------------------
    // Severity constants — mirrors LSP DiagnosticSeverity
    // -------------------------------------------------------------------------
    public static final int SEVERITY_ERROR = 1;
    public static final int SEVERITY_WARNING = 2;
    public static final int SEVERITY_INFORMATION = 3;
    public static final int SEVERITY_HINT = 4;

    /**
     * The range within the document to which this diagnostic applies.
     */
    public final LspRange range;

    /**
     * Severity level (one of SEVERITY_* constants above).
     */
    public final int severity;

    /**
     * The human-readable description of the problem.
     */
    public final String message;

    /**
     * Optional diagnostic code (e.g. {@code "W001"}, {@code "no-unused-vars"}).
     * May be null.
     */
    public final String code;

    /**
     * Optional source label identifying which server produced this diagnostic
     * (e.g. {@code "html"}, {@code "eslint"}).
     */
    public final String source;

    public LspDiagnostic(LspRange range, int severity, String message, String code, String source) {
        this.range = range;
        this.severity = severity;
        this.message = message;
        this.code = code;
        this.source = source;
    }

    public LspDiagnostic(LspRange range, int severity, String message) {
        this(range, severity, message, null, null);
    }

    public boolean isError() {
        return severity == SEVERITY_ERROR;
    }

    public boolean isWarning() {
        return severity == SEVERITY_WARNING;
    }
}
