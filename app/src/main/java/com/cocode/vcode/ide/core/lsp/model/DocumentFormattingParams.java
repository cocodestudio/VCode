package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for the document formatting request matching LSP 3.17 DocumentFormattingParams.
 */
public final class DocumentFormattingParams {

    public final TextDocumentIdentifier textDocument;
    public final FormattingOptions options;

    public DocumentFormattingParams(TextDocumentIdentifier textDocument, FormattingOptions options) {
        this.textDocument = textDocument;
        this.options = options != null ? options : FormattingOptions.DEFAULT;
    }

    public DocumentFormattingParams(String uri, FormattingOptions options) {
        this(new TextDocumentIdentifier(uri), options);
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentFormattingParams{" + textDocument + ", " + options + '}';
    }
}
