package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A range in a text document expressed as start and end positions.
 * Matching the Language Server Protocol 3.17 Range specification.
 */
public class LspRange {

    public final LspPosition start;
    public final LspPosition end;

    public LspRange(LspPosition start, LspPosition end) {
        this.start = start;
        this.end = end;
    }

    public LspRange(int startLine, int startChar, int endLine, int endChar) {
        this(new LspPosition(startLine, startChar), new LspPosition(endLine, endChar));
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + start + " - " + end + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LspRange)) return false;
        LspRange lspRange = (LspRange) o;
        return Objects.equals(start, lspRange.start) && Objects.equals(end, lspRange.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
