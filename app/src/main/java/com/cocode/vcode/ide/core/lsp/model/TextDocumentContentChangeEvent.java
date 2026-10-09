package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * An event describing a change to a text document.
 * If range is null, the whole document content is replaced by {@link #text}.
 * Matches LSP 3.17 TextDocumentContentChangeEvent.
 */
public final class TextDocumentContentChangeEvent {

    public final LspRange range;
    public final int rangeLength;
    public final String text;

    public TextDocumentContentChangeEvent(LspRange range, int rangeLength, String text) {
        this.range = range;
        this.rangeLength = rangeLength;
        this.text = text != null ? text : "";
    }

    public TextDocumentContentChangeEvent(String fullText) {
        this(null, 0, fullText);
    }

    @NonNull
    @Override
    public String toString() {
        if (range != null) {
            return "ChangeEvent{" + range + " -> \"" + text + "\"}";
        }
        return "FullChangeEvent{length=" + text.length() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextDocumentContentChangeEvent)) return false;
        TextDocumentContentChangeEvent that = (TextDocumentContentChangeEvent) o;
        return rangeLength == that.rangeLength &&
                Objects.equals(range, that.range) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, rangeLength, text);
    }
}
