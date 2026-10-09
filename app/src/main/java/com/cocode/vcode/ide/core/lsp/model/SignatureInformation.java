package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the signature of something callable.
 * Matches LSP 3.17 SignatureInformation.
 */
public final class SignatureInformation {

    public final String label;
    public final MarkupContent documentation;
    public final List<ParameterInformation> parameters;
    public final int activeParameter;

    public SignatureInformation(String label,
                                MarkupContent documentation,
                                List<ParameterInformation> parameters,
                                int activeParameter) {
        this.label = label != null ? label : "";
        this.documentation = documentation;
        this.parameters = parameters != null
                ? Collections.unmodifiableList(new ArrayList<>(parameters))
                : Collections.emptyList();
        this.activeParameter = activeParameter;
    }

    public SignatureInformation(String label, MarkupContent documentation, List<ParameterInformation> parameters) {
        this(label, documentation, parameters, -1);
    }

    public SignatureInformation(String label, List<ParameterInformation> parameters) {
        this(label, null, parameters, -1);
    }

    @NonNull
    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignatureInformation)) return false;
        SignatureInformation that = (SignatureInformation) o;
        return activeParameter == that.activeParameter &&
                Objects.equals(label, that.label) &&
                Objects.equals(documentation, that.documentation) &&
                Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, documentation, parameters, activeParameter);
    }
}
