package com.cocode.vcode.ide.core.completion.staticdata;

/**
 * Single-cursor-offset snippet parser.
 *
 * <p>Takes a snippet string containing a single {@code |} character,
 * marker (e.g. {@code "if (|) {\n  \n}"}), strips the marker, and
 * returns {@code (insertText, cursorOffset)}.
 *
 * <p>When the {@code |} sits inside an existing token (e.g.
 * {@code "\"semi\": |true"} → {@code "\"semi\": true"}), the cursor
 * lands at the exact character offset where {@code |} was — not at
 * a "natural" token boundary. The marker is removed; the surrounding
 * token text is preserved unchanged.
 *
 * <p>Multi-tab-stop snippets (multiple {@code |} markers) are not
 * supported by the data this code consumes. If a snippet contains
 * more than one {@code |}, the first is used; the rest are removed
 * (treated as literal text). The data is single-marker by design.
 */
public final class SnippetCursorParser {

    private SnippetCursorParser() {
    }

    /**
     * Parse a snippet string. Returns {@code null} if the input is
     * null, empty, or contains no {@code |} marker. Returns a
     * {@link Result} with the {@code |} removed and the cursor offset
     * pointing to the marker's former position in the resulting
     * text.
     */
    public static Result parse(String snippet) {
        if (snippet == null || snippet.isEmpty()) return null;
        int firstPipe = snippet.indexOf('|');
        if (firstPipe < 0) return null;
        // The cursor offset is the number of characters in the
        // pre-pipe prefix. The insertText is the snippet with the
        // FIRST pipe removed; any subsequent pipes (which the data
        // shouldn't contain) are also removed.
        StringBuilder sb = new StringBuilder(snippet.length() - 1);
        sb.append(snippet, 0, firstPipe);
        for (int i = firstPipe + 1; i < snippet.length(); i++) {
            char c = snippet.charAt(i);
            if (c != '|') sb.append(c);
        }
        return new Result(sb.toString(), firstPipe);
    }

    /**
     * Convenience: returns just the insertText (cursor offset
     * discarded). Useful when the caller does not need the cursor
     * position (e.g. when the snippet will be inserted and the
     * cursor will be placed by the editor at end-of-insert).
     */
    public static String stripMarker(String snippet) {
        Result r = parse(snippet);
        return r != null ? r.insertText : snippet;
    }

    /**
     * Result of parsing a snippet.
     */
    public static final class Result {
        public final String insertText;
        /**
         * Cursor offset in {@code insertText} (0-based, character
         * index where the next keystroke should land).
         */
        public final int cursorOffset;

        public Result(String insertText, int cursorOffset) {
            this.insertText = insertText;
            this.cursorOffset = cursorOffset;
        }
    }
}
