package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A document highlight is a range inside a text document which deserves
 * special attention. Usually a declaration or usage of a symbol. Matches LSP 3.17 DocumentHighlight.
 */
public final class DocumentHighlight {

    public final LspRange range;
    public final DocumentHighlightKind kind;

    public DocumentHighlight(LspRange range, DocumentHighlightKind kind) {
        this.range = range;
        this.kind = kind != null ? kind : DocumentHighlightKind.TEXT;
    }

    public DocumentHighlight(LspRange range) {
        this(range, DocumentHighlightKind.TEXT);
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentHighlight{" + range + ", " + kind + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentHighlight)) return false;
        DocumentHighlight that = (DocumentHighlight) o;
        return Objects.equals(range, that.range) && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, kind);
    }
}
