package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Parameters for a ColorPresentation request matching LSP 3.17 ColorPresentationParams.
 */
public final class ColorPresentationParams {

    public final TextDocumentIdentifier textDocument;
    public final Color color;
    public final LspRange range;

    public ColorPresentationParams(TextDocumentIdentifier textDocument, Color color, LspRange range) {
        this.textDocument = textDocument;
        this.color = color;
        this.range = range;
    }

    public ColorPresentationParams(String uri, Color color, LspRange range) {
        this(new TextDocumentIdentifier(uri), color, range);
    }

    @NonNull
    @Override
    public String toString() {
        return "ColorPresentationParams{" + textDocument + ", " + color + ", " + range + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColorPresentationParams)) return false;
        ColorPresentationParams that = (ColorPresentationParams) o;
        return Objects.equals(textDocument, that.textDocument) &&
                Objects.equals(color, that.color) &&
                Objects.equals(range, that.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textDocument, color, range);
    }
}
