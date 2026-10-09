package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Value-object that carries reference request context matching LSP 3.17 ReferenceContext.
 */
public final class ReferenceContext {

    /**
     * Include the declaration of the current symbol.
     */
    public final boolean includeDeclaration;

    public ReferenceContext(boolean includeDeclaration) {
        this.includeDeclaration = includeDeclaration;
    }

    public static final ReferenceContext INCLUDE_DECLARATION = new ReferenceContext(true);
    public static final ReferenceContext EXCLUDE_DECLARATION = new ReferenceContext(false);

    @NonNull
    @Override
    public String toString() {
        return "ReferenceContext{includeDecl=" + includeDeclaration + '}';
    }
}
