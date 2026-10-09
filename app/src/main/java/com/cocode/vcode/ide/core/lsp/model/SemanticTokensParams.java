package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for the semantic tokens request matching LSP 3.17 SemanticTokensParams.
 */
public final class SemanticTokensParams {

    public final TextDocumentIdentifier textDocument;

    public SemanticTokensParams(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public SemanticTokensParams(String uri) {
        this(new TextDocumentIdentifier(uri));
    }

    @NonNull
    @Override
    public String toString() {
        return "SemanticTokensParams{" + textDocument + '}';
    }
}
