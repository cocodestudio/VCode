package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a diagnostic, such as a compiler error or warning.
 * Matches LSP 3.17 Diagnostic specification.
 */
public final class Diagnostic {

    public final LspRange range;
    public final DiagnosticSeverity severity;
    public final String code;
    public final String source;
    public final String message;
    public final List<DiagnosticTag> tags;
    public final Object data;

    public Diagnostic(LspRange range,
                      DiagnosticSeverity severity,
                      String code,
                      String source,
                      String message,
                      List<DiagnosticTag> tags,
                      Object data) {
        this.range = range;
        this.severity = severity != null ? severity : DiagnosticSeverity.ERROR;
        this.code = code != null ? code : "";
        this.source = source != null ? source : "";
        this.message = message != null ? message : "";
        this.tags = tags != null ? Collections.unmodifiableList(new ArrayList<>(tags)) : Collections.emptyList();
        this.data = data;
    }

    public Diagnostic(LspRange range, DiagnosticSeverity severity, String code, String source, String message) {
        this(range, severity, code, source, message, null, null);
    }

    public Diagnostic(LspRange range, DiagnosticSeverity severity, String message) {
        this(range, severity, null, null, message, null, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "Diagnostic{" + severity + " at " + range + " [" + source + ":" + code + "]: " + message + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Diagnostic)) return false;
        Diagnostic that = (Diagnostic) o;
        return Objects.equals(range, that.range) &&
                severity == that.severity &&
                Objects.equals(code, that.code) &&
                Objects.equals(source, that.source) &&
                Objects.equals(message, that.message) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, severity, code, source, message, tags);
    }
}
