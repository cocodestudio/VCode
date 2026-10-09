package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a color presentation matching LSP 3.17 ColorPresentation.
 */
public final class ColorPresentation {

    public final String label;
    public final TextEdit textEdit;
    public final List<TextEdit> additionalTextEdits;

    public ColorPresentation(String label, TextEdit textEdit, List<TextEdit> additionalTextEdits) {
        this.label = label != null ? label : "";
        this.textEdit = textEdit;
        this.additionalTextEdits = additionalTextEdits != null
                ? Collections.unmodifiableList(new ArrayList<>(additionalTextEdits))
                : Collections.emptyList();
    }

    public ColorPresentation(String label, TextEdit textEdit) {
        this(label, textEdit, null);
    }

    public ColorPresentation(String label) {
        this(label, null, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "ColorPresentation{label='" + label + '\'' + ", textEdit=" + textEdit + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColorPresentation)) return false;
        ColorPresentation that = (ColorPresentation) o;
        return Objects.equals(label, that.label) &&
                Objects.equals(textEdit, that.textEdit) &&
                Objects.equals(additionalTextEdits, that.additionalTextEdits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, textEdit, additionalTextEdits);
    }
}
