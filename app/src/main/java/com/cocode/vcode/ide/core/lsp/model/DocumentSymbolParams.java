package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters of the document symbol request matching LSP 3.17 DocumentSymbolParams.
 */
public final class DocumentSymbolParams {

    public final TextDocumentIdentifier textDocument;

    public DocumentSymbolParams(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public DocumentSymbolParams(String uri) {
        this(new TextDocumentIdentifier(uri));
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentSymbolParams{" + textDocument + '}';
    }
}
