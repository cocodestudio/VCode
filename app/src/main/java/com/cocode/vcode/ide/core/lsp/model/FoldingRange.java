package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Represents a folding range in a document.
 * Matches LSP 3.17 FoldingRange.
 */
public final class FoldingRange {

    public final int startLine;
    public final int startCharacter;
    public final int endLine;
    public final int endCharacter;
    public final String kind;
    public final String collapsedText;

    public FoldingRange(int startLine,
                        int startCharacter,
                        int endLine,
                        int endCharacter,
                        String kind,
                        String collapsedText) {
        this.startLine = startLine;
        this.startCharacter = startCharacter;
        this.endLine = endLine;
        this.endCharacter = endCharacter;
        this.kind = kind;
        this.collapsedText = collapsedText;
    }

    public FoldingRange(int startLine, int endLine) {
        this(startLine, 0, endLine, 0, null, null);
    }

    public FoldingRange(int startLine, int endLine, String kind) {
        this(startLine, 0, endLine, 0, kind, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "FoldingRange{" + startLine + " to " + endLine + (kind != null ? " (" + kind + ")" : "") + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FoldingRange)) return false;
        FoldingRange that = (FoldingRange) o;
        return startLine == that.startLine &&
                startCharacter == that.startCharacter &&
                endLine == that.endLine &&
                endCharacter == that.endCharacter &&
                Objects.equals(kind, that.kind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLine, startCharacter, endLine, endCharacter, kind);
    }
}
