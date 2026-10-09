package com.cocode.vcode.ide.core.lsp;

import androidx.annotation.NonNull;

/**
 * Zero-based line and character position within a document.
 * Matches the LSP specification's {@code Position} type exactly.
 */
public final class LspPosition extends com.cocode.vcode.ide.core.lsp.model.LspPosition {

    public LspPosition(int line, int character) {
        super(line, character);
    }

    @NonNull
    @Override
    public String toString() {
        return line + ":" + character;
    }
}
