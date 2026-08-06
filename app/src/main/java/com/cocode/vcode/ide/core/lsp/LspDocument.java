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

    public LspDocument(String uri, String text, String languageId, int version) {
        this.uri = uri;
        this.text = text;
        this.languageId = languageId;
        this.version = version;
    }

    /**
     * Returns the number of lines in the document.
     */
    public int lineCount() {
        if (text == null || text.isEmpty()) return 1;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    /**
     * Returns the text of a specific zero-based line, without the trailing newline.
     * Returns an empty string if the line index is out of bounds.
     */
    public String getLine(int lineIndex) {
        if (text == null || text.isEmpty()) return "";
        int line = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            boolean atEnd = (i == text.length());
            boolean atNewLine = !atEnd && text.charAt(i) == '\n';
            if (atEnd || atNewLine) {
                if (line == lineIndex) {
                    return text.substring(start, i);
                }
                line++;
                start = i + 1;
            }
        }
        return "";
    }

    /**
     * Converts a zero-based (line, character) position to a flat character offset.
     * Returns -1 if the position is out of range.
     */
    public int toOffset(LspPosition pos) {
        if (text == null) return -1;
        int line = 0;
        int i = 0;
        while (i < text.length() && line < pos.line) {
            if (text.charAt(i) == '\n') line++;
            i++;
        }
        int offset = i + pos.character;
        return offset <= text.length() ? offset : -1;
    }
}
