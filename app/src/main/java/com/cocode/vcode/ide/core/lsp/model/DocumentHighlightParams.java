package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for the document highlight request matching LSP 3.17 DocumentHighlightParams.
 */
public final class DocumentHighlightParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;

    public DocumentHighlightParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this.textDocument = textDocument;
        this.position = position;
    }

    public DocumentHighlightParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position);
    }

    public DocumentHighlightParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character));
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentHighlightParams{" + textDocument + " at " + position + '}';
    }
}
