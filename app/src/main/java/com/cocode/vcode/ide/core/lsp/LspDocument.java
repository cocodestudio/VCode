package com.cocode.vcode.ide.core.lsp;

/**
 * Immutable snapshot of a document at a specific version.
 * <p>
 * Passed to {@link LspServer} on every request. Being immutable ensures the server
 * can process the document on a background thread without racing with further edits
 * on the UI thread.
 */
public final class LspDocument {

    /**
     * Absolute file path used as a stable identifier (e.g. {@code /storage/.../index.html}).
     */
    public final String uri;

    /**
     * Full text content of the document at this version.
     */
    public final String text;

    /**
     * LSP language identifier (e.g. {@code "html"}, {@code "css"}, {@code "javascript"}).
     * Must match {@link LspServer#getLanguageId()}.
     */
    public final String languageId;

    /**
     * Monotonically increasing counter, incremented on every edit.
     * Allows servers to detect stale responses and discard them.
     */
    public final int version;

    /**
     * Precomputed line offset cache providing O(1) position ↔ flat offset lookups.
     */
    private final OffsetIndexCache indexCache;

    public LspDocument(String uri, String text, String languageId, int version) {
        this(uri, text, languageId, version, null);
    }

    public LspDocument(String uri, String text, String languageId, int version, OffsetIndexCache cache) {
        this.uri = uri;
        this.text = text != null ? text : "";
        this.languageId = languageId != null ? languageId : "plaintext";
        this.version = version;
        this.indexCache = (cache != null) ? cache : new OffsetIndexCache(this.text);
    }

    /**
     * Returns the precomputed line offset cache for this document.
     */
    public OffsetIndexCache getOffsetIndexCache() {
        return indexCache;
    }

    /**
     * Returns the number of lines in the document in O(1).
     */
    public int lineCount() {
        return indexCache.getLineCount();
    }

    /**
     * Returns the text of a specific zero-based line, without the trailing newline in O(1).
     * Returns an empty string if the line index is out of bounds.
     */
    public String getLine(int lineIndex) {
        return indexCache.getLine(text, lineIndex);
    }

    /**
     * Converts a zero-based (line, character) position to a flat character offset in O(1).
     * Returns -1 if the position is out of range.
     */
    public int toOffset(LspPosition pos) {
        return indexCache.toOffset(pos);
    }

    /**
     * Converts an {@link com.cocode.vcode.ide.core.lsp.model.LspPosition} to a flat character offset in O(1).
     */
    public int toOffset(com.cocode.vcode.ide.core.lsp.model.LspPosition pos) {
        return indexCache.toOffset(pos);
    }

    /**
     * Converts a flat character offset to a zero-based (line, character) position.
     */
    public LspPosition toPosition(int offset) {
        return indexCache.toPosition(offset);
    }

    /**
     * Converts a flat character offset to a model LspPosition.
     */
    public com.cocode.vcode.ide.core.lsp.model.LspPosition toModelPosition(int offset) {
        LspPosition p = toPosition(offset);
        return new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.line, p.character);
    }

    /**
     * Converts start and end flat offsets to a model LspRange.
     */
    public com.cocode.vcode.ide.core.lsp.model.LspRange toModelRange(int startOffset, int endOffset) {
        return new com.cocode.vcode.ide.core.lsp.model.LspRange(toModelPosition(startOffset), toModelPosition(endOffset));
    }
}
