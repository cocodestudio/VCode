package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Position in a text document expressed as zero-based line and character offset.
 * Character offset represents the UTF-16 code unit offset within the line per LSP 3.17.
 */
public class LspPosition {

    public final int line;
    public final int character;

    public LspPosition(int line, int character) {
        this.line = line;
        this.character = character;
    }

    @NonNull
    @Override
    public String toString() {
        return line + ":" + character;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LspPosition)) return false;
        LspPosition that = (LspPosition) o;
        return line == that.line && character == that.character;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, character);
    }
}
