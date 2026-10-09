package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A `MarkupContent` literal represents a string value which content can be represented in
 * different formats (e.g. Markdown or plain text). Matches LSP 3.17 MarkupContent.
 */
public final class MarkupContent {

    public final String kind;
    public final String value;

    public MarkupContent(String kind, String value) {
        this.kind = kind != null ? kind : MarkupKind.MARKDOWN;
        this.value = value != null ? value : "";
    }

    public static MarkupContent markdown(String markdown) {
        return new MarkupContent(MarkupKind.MARKDOWN, markdown);
    }

    public static MarkupContent plainText(String plainText) {
        return new MarkupContent(MarkupKind.PLAIN_TEXT, plainText);
    }

    @NonNull
    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarkupContent)) return false;
        MarkupContent that = (MarkupContent) o;
        return Objects.equals(kind, that.kind) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }
}
