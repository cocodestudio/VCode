package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Parameters for a DocumentColor request matching LSP 3.17 DocumentColorParams.
 */
public final class DocumentColorParams {

    public final TextDocumentIdentifier textDocument;

    public DocumentColorParams(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public DocumentColorParams(String uri) {
        this(new TextDocumentIdentifier(uri));
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentColorParams{" + textDocument + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentColorParams)) return false;
        DocumentColorParams that = (DocumentColorParams) o;
        return Objects.equals(textDocument, that.textDocument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textDocument);
    }
}
