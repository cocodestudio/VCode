package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a folding range request matching LSP 3.17 FoldingRangeParams.
 */
public final class FoldingRangeParams {

    public final TextDocumentIdentifier textDocument;

    public FoldingRangeParams(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public FoldingRangeParams(String uri) {
        this(new TextDocumentIdentifier(uri));
    }

    @NonNull
    @Override
    public String toString() {
        return "FoldingRangeParams{" + textDocument + '}';
    }
}
