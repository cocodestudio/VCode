package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Completion parameters matching LSP 3.17 CompletionParams.
 */
public final class CompletionParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;
    public final CompletionContext context;

    public CompletionParams(TextDocumentIdentifier textDocument, LspPosition position, CompletionContext context) {
        this.textDocument = textDocument;
        this.position = position;
        this.context = context != null ? context : CompletionContext.DEFAULT;
    }

    public CompletionParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this(textDocument, position, null);
    }

    public CompletionParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position, null);
    }

    public CompletionParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character), null);
    }

    @NonNull
    @Override
    public String toString() {
        return "CompletionParams{" + textDocument + " at " + position + '}';
    }
}
