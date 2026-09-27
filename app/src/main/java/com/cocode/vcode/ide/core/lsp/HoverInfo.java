package com.cocode.vcode.ide.core.lsp;

/**
 * Result model for a textDocument/hover request.
 * Encapsulates the symbol's source range and rich markdown documentation.
 */
public final class HoverInfo {
    private final LspRange range;
    private final String markdownContent;

    public HoverInfo(LspRange range, String markdownContent) {
        this.range = range;
        this.markdownContent = markdownContent;
    }

    public HoverInfo(String markdownContent) {
        this(null, markdownContent);
    }

    public LspRange getRange() {
        return range;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }
}
