package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A text edit applicable to a text document.
 * Matches LSP 3.17 TextEdit specification.
 */
public final class TextEdit {

    public final LspRange range;
    public final String newText;

    public TextEdit(LspRange range, String newText) {
        this.range = range;
        this.newText = newText != null ? newText : "";
    }

    @NonNull
    @Override
    public String toString() {
        return "TextEdit{" + range + " -> \"" + newText + "\"}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextEdit)) return false;
        TextEdit textEdit = (TextEdit) o;
        return Objects.equals(range, textEdit.range) && Objects.equals(newText, textEdit.newText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, newText);
    }
}
