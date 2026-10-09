package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Parameters for the initialize request matching LSP 3.17 InitializeParams.
 */
public final class InitializeParams {

    public final String rootUri;
    public final String rootPath;
    public final ClientCapabilities capabilities;
    public final String clientName;
    public final String clientVersion;

    public InitializeParams(String rootUri,
                            String rootPath,
                            ClientCapabilities capabilities,
                            String clientName,
                            String clientVersion) {
        this.rootUri = rootUri != null ? rootUri : "";
        this.rootPath = rootPath != null ? rootPath : "";
        this.capabilities = capabilities != null ? capabilities : ClientCapabilities.DEFAULT;
        this.clientName = clientName != null ? clientName : "VCode";
        this.clientVersion = clientVersion != null ? clientVersion : "6.0.0";
    }

    public InitializeParams(String rootUri) {
        this(rootUri, rootUri, ClientCapabilities.DEFAULT, "VCode", "6.0.0");
    }

    @NonNull
    @Override
    public String toString() {
        return "InitializeParams{root='" + rootUri + "', client=" + clientName + " " + clientVersion + '}';
    }
}
