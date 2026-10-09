package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for the document range formatting request matching LSP 3.17 DocumentRangeFormattingParams.
 */
public final class DocumentRangeFormattingParams {

    public final TextDocumentIdentifier textDocument;
    public final LspRange range;
    public final FormattingOptions options;

    public DocumentRangeFormattingParams(TextDocumentIdentifier textDocument, LspRange range, FormattingOptions options) {
        this.textDocument = textDocument;
        this.range = range;
        this.options = options != null ? options : FormattingOptions.DEFAULT;
    }

    public DocumentRangeFormattingParams(String uri, LspRange range, FormattingOptions options) {
        this(new TextDocumentIdentifier(uri), range, options);
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentRangeFormattingParams{" + textDocument + " at " + range + ", " + options + '}';
    }
}
