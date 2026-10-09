package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a type definition request matching LSP 3.17 TypeDefinitionParams.
 */
public final class TypeDefinitionParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;

    public TypeDefinitionParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this.textDocument = textDocument;
        this.position = position;
    }

    public TypeDefinitionParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position);
    }

    public TypeDefinitionParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character));
    }

    @NonNull
    @Override
    public String toString() {
        return "TypeDefinitionParams{" + textDocument + " at " + position + '}';
    }
}
