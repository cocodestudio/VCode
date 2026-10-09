package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Represents a color range in a document matching LSP 3.17 ColorInformation specification.
 */
public final class ColorInformation {

    public final LspRange range;
    public final Color color;

    public ColorInformation(LspRange range, Color color) {
        this.range = range;
        this.color = color;
    }

    @NonNull
    @Override
    public String toString() {
        return "ColorInformation{" + range + ", " + color + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColorInformation)) return false;
        ColorInformation that = (ColorInformation) o;
        return Objects.equals(range, that.range) && Objects.equals(color, that.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, color);
    }
}
