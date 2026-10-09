package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a prepare rename request matching LSP 3.17 PrepareRenameParams.
 */
public final class PrepareRenameParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;

    public PrepareRenameParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this.textDocument = textDocument;
        this.position = position;
    }

    public PrepareRenameParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position);
    }

    public PrepareRenameParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character));
    }

    @NonNull
    @Override
    public String toString() {
        return "PrepareRenameParams{" + textDocument + " at " + position + '}';
    }
}
