package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters of the document diagnostic request matching LSP 3.17 DocumentDiagnosticParams.
 */
public final class DocumentDiagnosticParams {

    public final TextDocumentIdentifier textDocument;

    public DocumentDiagnosticParams(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public DocumentDiagnosticParams(String uri) {
        this(new TextDocumentIdentifier(uri));
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentDiagnosticParams{" + textDocument + '}';
    }
}
