package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * An item to transfer a text document from the client to the server.
 * Matches LSP 3.17 TextDocumentItem.
 */
public final class TextDocumentItem {

    public final String uri;
    public final String languageId;
    public final int version;
    public final String text;

    public TextDocumentItem(String uri, String languageId, int version, String text) {
        this.uri = uri != null ? uri : "";
        this.languageId = languageId != null ? languageId : "";
        this.version = version;
        this.text = text != null ? text : "";
    }

    @NonNull
    @Override
    public String toString() {
        return "TextDocumentItem{" + uri + ", lang=" + languageId + ", v=" + version + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextDocumentItem)) return false;
        TextDocumentItem that = (TextDocumentItem) o;
        return version == that.version &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(languageId, that.languageId) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, languageId, version, text);
    }
}
