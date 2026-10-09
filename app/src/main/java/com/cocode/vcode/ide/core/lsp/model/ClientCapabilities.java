package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Client capabilities declaration matching LSP 3.17 ClientCapabilities.
 */
public final class ClientCapabilities {

    public final boolean snippetSupport;
    public final boolean hierarchicalDocumentSymbolSupport;
    public final boolean labelDetailsSupport;

    public ClientCapabilities(boolean snippetSupport,
                              boolean hierarchicalDocumentSymbolSupport,
                              boolean labelDetailsSupport) {
        this.snippetSupport = snippetSupport;
        this.hierarchicalDocumentSymbolSupport = hierarchicalDocumentSymbolSupport;
        this.labelDetailsSupport = labelDetailsSupport;
    }

    public ClientCapabilities() {
        this(true, true, true);
    }

    public static final ClientCapabilities DEFAULT = new ClientCapabilities();

    @NonNull
    @Override
    public String toString() {
        return "ClientCapabilities{snippets=" + snippetSupport + ", hierarchicalSymbols=" + hierarchicalDocumentSymbolSupport + '}';
    }
}
