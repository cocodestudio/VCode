package com.cocode.vcode.ide.core.lsp;

import androidx.annotation.NonNull;

/**
 * Zero-based line and character position within a document.
 * Matches the LSP specification's {@code Position} type exactly.
 */
public final class LspPosition {

    /**
     * Zero-based line number.
     */
    public final int line;

    /**
     * Zero-based character offset within the line (UTF-16 code units per LSP spec).
     */
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
}
