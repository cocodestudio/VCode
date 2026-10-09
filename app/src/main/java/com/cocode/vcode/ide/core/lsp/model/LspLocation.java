package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Represents a location inside a resource, such as a line inside a text file.
 * Matches LSP 3.17 Location type.
 */
public class LspLocation {

    public final String uri;
    public final LspRange range;

    public LspLocation(String uri, LspRange range) {
        this.uri = uri;
        this.range = range;
    }

    public LspLocation(String uri, int startLine, int startChar, int endLine, int endChar) {
        this(uri, new LspRange(startLine, startChar, endLine, endChar));
    }

    @NonNull
    @Override
    public String toString() {
        return uri + ":" + range;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LspLocation)) return false;
        LspLocation that = (LspLocation) o;
        return Objects.equals(uri, that.uri) && Objects.equals(range, that.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, range);
    }
}
