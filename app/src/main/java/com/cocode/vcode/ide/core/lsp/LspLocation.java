package com.cocode.vcode.ide.core.lsp;

/**
 * A file location returned by definition / reference requests.
 * <p>
 * Mirrors the LSP specification's {@code Location} type.
 */
public final class LspLocation extends com.cocode.vcode.ide.core.lsp.model.LspLocation {

    public LspLocation(String uri, com.cocode.vcode.ide.core.lsp.model.LspRange range) {
        super(uri, range);
    }

    public LspLocation(String uri, int startLine, int startChar, int endLine, int endChar) {
        super(uri, startLine, startChar, endLine, endChar);
    }
}
