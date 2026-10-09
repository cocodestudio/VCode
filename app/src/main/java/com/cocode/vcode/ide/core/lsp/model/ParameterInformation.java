package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Represents a parameter of a callable-signature.
 * Matches LSP 3.17 ParameterInformation.
 */
public final class ParameterInformation {

    public final String label;
    public final MarkupContent documentation;

    public ParameterInformation(String label, MarkupContent documentation) {
        this.label = label != null ? label : "";
        this.documentation = documentation;
    }

    public ParameterInformation(String label, String documentationMarkdown) {
        this(label, documentationMarkdown != null ? MarkupContent.markdown(documentationMarkdown) : null);
    }

    public ParameterInformation(String label) {
        this(label, (MarkupContent) null);
    }

    @NonNull
    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParameterInformation)) return false;
        ParameterInformation that = (ParameterInformation) o;
        return Objects.equals(label, that.label) && Objects.equals(documentation, that.documentation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, documentation);
    }
}
