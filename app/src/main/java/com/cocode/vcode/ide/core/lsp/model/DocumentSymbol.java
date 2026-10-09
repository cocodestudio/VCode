package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents programming constructs like variables, classes, interfaces etc. that appear in a document.
 * Matches LSP 3.17 DocumentSymbol specification.
 */
public final class DocumentSymbol {

    public final String name;
    public final String detail;
    public final SymbolKind kind;
    public final List<SymbolTag> tags;
    public final LspRange range;
    public final LspRange selectionRange;
    public final List<DocumentSymbol> children;

    public DocumentSymbol(String name,
                          String detail,
                          SymbolKind kind,
                          List<SymbolTag> tags,
                          LspRange range,
                          LspRange selectionRange,
                          List<DocumentSymbol> children) {
        this.name = name != null ? name : "";
        this.detail = detail != null ? detail : "";
        this.kind = kind != null ? kind : SymbolKind.VARIABLE;
        this.tags = tags != null ? Collections.unmodifiableList(new ArrayList<>(tags)) : Collections.emptyList();
        this.range = range;
        this.selectionRange = selectionRange != null ? selectionRange : range;
        this.children = children != null ? Collections.unmodifiableList(new ArrayList<>(children)) : Collections.emptyList();
    }

    public DocumentSymbol(String name, SymbolKind kind, LspRange range, LspRange selectionRange) {
        this(name, null, kind, null, range, selectionRange, null);
    }

    public DocumentSymbol(String name, SymbolKind kind, LspRange range) {
        this(name, null, kind, null, range, range, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentSymbol{" + name + " (" + kind + ") at " + range + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentSymbol)) return false;
        DocumentSymbol that = (DocumentSymbol) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(detail, that.detail) &&
                kind == that.kind &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(range, that.range) &&
                Objects.equals(selectionRange, that.selectionRange) &&
                Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, detail, kind, tags, range, selectionRange, children);
    }
}
