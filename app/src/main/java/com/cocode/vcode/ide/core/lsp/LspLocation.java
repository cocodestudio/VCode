package com.cocode.vcode.ide.core.lsp;

/**
 * A file location returned by definition / reference requests.
 * <p>
 * Mirrors the LSP specification's {@code Location} type.
 */
public final class LspLocation {

    /**
     * Absolute file path of the target document.
     */
    public final String uri;

    /**
     * The range within the target document.
     */
    public final LspRange range;

    public LspLocation(String uri, LspRange range) {
        this.uri = uri;
        this.range = range;
    }

    public LspLocation(String uri, int startLine, int startChar, int endLine, int endChar) {
        this(uri, new LspRange(startLine, startChar, endLine, endChar));
    }
}
