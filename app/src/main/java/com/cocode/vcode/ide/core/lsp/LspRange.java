package com.cocode.vcode.ide.core.lsp;

/**
 * A range within a text document, defined by a start and end {@link LspPosition}.
 * Matches the LSP specification's {@code Range} type.
 */
public final class LspRange extends com.cocode.vcode.ide.core.lsp.model.LspRange {

    public LspRange(com.cocode.vcode.ide.core.lsp.model.LspPosition start, com.cocode.vcode.ide.core.lsp.model.LspPosition end) {
        super(start, end);
    }

    public LspRange(int startLine, int startChar, int endLine, int endChar) {
        super(startLine, startChar, endLine, endChar);
    }
}
