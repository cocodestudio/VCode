package com.cocode.vcode.ide.core.model;

import androidx.annotation.NonNull;

/**
 * Immutable data container describing an isolated search match occurrence.
 * Holds precise index offsets for editor text selection selection along with
 * formatted line and column metadata definitions for the search results UI display.
 */
public class SearchResult {

    public final int absoluteStart; // The global 0-based character start index in the document text
    public final int absoluteEnd;   // The global 0-based character end index boundary (exclusive)
    public final int lineNumber;    // Human-readable line placement coordinate (1-indexed)
    public final int columnStart;   // Human-readable column alignment start coordinate (1-indexed)

    /**
     * Instantiates a fully initialized instance describing an identified search result item.
     */
    public SearchResult(int absoluteStart, int absoluteEnd, int lineNumber, int columnStart) {
        this.absoluteStart = absoluteStart;
        this.absoluteEnd = absoluteEnd;
        this.lineNumber = lineNumber;
        this.columnStart = columnStart;
    }

    /**
     * Calculates the span length of the identified string sequence match.
     * Core indicator for highlight styling block sizes in the UI layer.
     */
    public int length() {
        return absoluteEnd - absoluteStart;
    }

    /**
     * Generates a descriptive log output representing this match's workspace positioning boundaries.
     */
    @NonNull
    @Override
    public String toString() {
        return "SearchResult{line=" + lineNumber + ", col=" + columnStart
                + ", start=" + absoluteStart + ", end=" + absoluteEnd + "}";
    }
}