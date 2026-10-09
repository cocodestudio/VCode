package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * An identifier to denote a specific version of a text document.
 * Matches LSP 3.17 VersionedTextDocumentIdentifier.
 */
public final class VersionedTextDocumentIdentifier extends TextDocumentIdentifier {

    public final int version;

    public VersionedTextDocumentIdentifier(String uri, int version) {
        super(uri);
        this.version = version;
    }

    @NonNull
    @Override
    public String toString() {
        return "VersionedTextDocumentIdentifier{" + uri + ", v=" + version + '}';
    }
}
