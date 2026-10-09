package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a definition request matching LSP 3.17 DefinitionParams.
 */
public final class DefinitionParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;

    public DefinitionParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this.textDocument = textDocument;
        this.position = position;
    }

    public DefinitionParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position);
    }

    public DefinitionParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character));
    }

    @NonNull
    @Override
    public String toString() {
        return "DefinitionParams{" + textDocument + " at " + position + '}';
    }
}
