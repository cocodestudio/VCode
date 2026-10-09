package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for the textDocument/codeAction request matching LSP 3.17 CodeActionParams.
 */
public final class CodeActionParams {

    public final TextDocumentIdentifier textDocument;
    public final LspRange range;
    public final CodeActionContext context;

    public CodeActionParams(TextDocumentIdentifier textDocument, LspRange range, CodeActionContext context) {
        this.textDocument = textDocument;
        this.range = range;
        this.context = context;
    }

    public CodeActionParams(String uri, LspRange range, CodeActionContext context) {
        this(new TextDocumentIdentifier(uri), range, context);
    }

    @NonNull
    @Override
    public String toString() {
        return "CodeActionParams{" + textDocument + " at " + range + '}';
    }
}
