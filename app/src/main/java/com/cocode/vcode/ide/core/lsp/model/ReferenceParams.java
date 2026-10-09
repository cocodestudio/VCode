package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a references request matching LSP 3.17 ReferenceParams.
 */
public final class ReferenceParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;
    public final ReferenceContext context;

    public ReferenceParams(TextDocumentIdentifier textDocument, LspPosition position, ReferenceContext context) {
        this.textDocument = textDocument;
        this.position = position;
        this.context = context != null ? context : ReferenceContext.INCLUDE_DECLARATION;
    }

    public ReferenceParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this(textDocument, position, null);
    }

    public ReferenceParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position, null);
    }

    public ReferenceParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character), null);
    }

    @NonNull
    @Override
    public String toString() {
        return "ReferenceParams{" + textDocument + " at " + position + '}';
    }
}
