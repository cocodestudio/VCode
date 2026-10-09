package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a hover request matching LSP 3.17 HoverParams.
 */
public final class HoverParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;

    public HoverParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this.textDocument = textDocument;
        this.position = position;
    }

    public HoverParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position);
    }

    public HoverParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character));
    }

    @NonNull
    @Override
    public String toString() {
        return "HoverParams{" + textDocument + " at " + position + '}';
    }
}
