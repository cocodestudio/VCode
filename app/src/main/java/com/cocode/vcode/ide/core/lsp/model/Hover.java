package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * The result of a hover request matching LSP 3.17 Hover.
 */
public final class Hover {

    public final MarkupContent contents;
    public final LspRange range;

    public Hover(MarkupContent contents, LspRange range) {
        this.contents = contents;
        this.range = range;
    }

    public Hover(MarkupContent contents) {
        this(contents, null);
    }

    public Hover(String markdownText) {
        this(MarkupContent.markdown(markdownText), null);
    }

    public Hover(String markdownText, LspRange range) {
        this(MarkupContent.markdown(markdownText), range);
    }

    @NonNull
    @Override
    public String toString() {
        return "Hover{" + contents + (range != null ? " at " + range : "") + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hover)) return false;
        Hover hover = (Hover) o;
        return Objects.equals(contents, hover.contents) && Objects.equals(range, hover.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents, range);
    }
}
