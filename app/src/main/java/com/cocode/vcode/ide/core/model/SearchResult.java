package com.cocode.vcode.ide.core.model;

import androidx.annotation.NonNull;

/**
 * Model representing a search match in the editor with start/end character offsets, line number, and column.
 */
public class SearchResult {

    public final int absoluteStart; // 0-based character start index in the document
    public final int absoluteEnd;   // 0-based character end index (exclusive)
    public final int lineNumber;    // 1-indexed line number
    public final int columnStart;   // 1-indexed column start

    public SearchResult(int absoluteStart, int absoluteEnd, int lineNumber, int columnStart) {
        this.absoluteStart = absoluteStart;
        this.absoluteEnd = absoluteEnd;
        this.lineNumber = lineNumber;
        this.columnStart = columnStart;
    }

    /**
     * Returns the character length of this match.
     */
    public int length() {
        return absoluteEnd - absoluteStart;
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchResult{line=" + lineNumber + ", col=" + columnStart
                + ", start=" + absoluteStart + ", end=" + absoluteEnd + "}";
    }
}