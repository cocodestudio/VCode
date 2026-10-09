package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * TextDocumentIdentifier identifying a document using a URI.
 * Matches LSP 3.17 TextDocumentIdentifier.
 */
public class TextDocumentIdentifier {

    public final String uri;

    public TextDocumentIdentifier(String uri) {
        this.uri = uri != null ? uri : "";
    }

    @NonNull
    @Override
    public String toString() {
        return "TextDocumentIdentifier{" + uri + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextDocumentIdentifier)) return false;
        TextDocumentIdentifier that = (TextDocumentIdentifier) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }
}
