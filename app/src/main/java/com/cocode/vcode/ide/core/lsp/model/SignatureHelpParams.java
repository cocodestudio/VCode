package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for a signature help request matching LSP 3.17 SignatureHelpParams.
 */
public final class SignatureHelpParams {

    public final TextDocumentIdentifier textDocument;
    public final LspPosition position;
    public final SignatureHelpContext context;

    public SignatureHelpParams(TextDocumentIdentifier textDocument,
                               LspPosition position,
                               SignatureHelpContext context) {
        this.textDocument = textDocument;
        this.position = position;
        this.context = context != null ? context : SignatureHelpContext.DEFAULT;
    }

    public SignatureHelpParams(TextDocumentIdentifier textDocument, LspPosition position) {
        this(textDocument, position, null);
    }

    public SignatureHelpParams(String uri, LspPosition position) {
        this(new TextDocumentIdentifier(uri), position, null);
    }

    public SignatureHelpParams(String uri, int line, int character) {
        this(new TextDocumentIdentifier(uri), new LspPosition(line, character), null);
    }

    @NonNull
    @Override
    public String toString() {
        return "SignatureHelpParams{" + textDocument + " at " + position + '}';
    }
}
