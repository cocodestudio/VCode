package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a rename request matching LSP 3.17 RenameParams.
 */
public final class RenameParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;
    public final String newName;

    public RenameParams(TextDocumentIdentifier textDocument, LspPosition position, String newName) {
        this.textDocument = textDocument;
        this.position = position;
        this.newName = newName != null ? newName : "";
    }

    public RenameParams(String uri, LspPosition position, String newName) {
        this(new TextDocumentIdentifier(uri), position, newName);
    }

    public RenameParams(String uri, int line, int character, String newName) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character), newName);
    }

    @NonNull
    @Override
    public String toString() {
        return "RenameParams{" + textDocument + " at " + position + " -> \"" + newName + "\"}";
    }
}
