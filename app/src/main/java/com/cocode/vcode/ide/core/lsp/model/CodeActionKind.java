package com.cocode.vcode.ide.core.lsp.model;

/**
 * Standard CodeAction kinds matching LSP 3.17 CodeActionKind.
 */
public final class CodeActionKind {

    public static final String EMPTY = "";
    public static final String QUICKFIX = "quickfix";
    public static final String REFACTOR = "refactor";
    public static final String REFACTOR_EXTRACT = "refactor.extract";
    public static final String REFACTOR_INLINE = "refactor.inline";
    public static final String REFACTOR_REWRITE = "refactor.rewrite";
    public static final String SOURCE = "source";
    public static final String SOURCE_ORGANIZE_IMPORTS = "source.organizeImports";
    public static final String SOURCE_FIX_ALL = "source.fixAll";

    private CodeActionKind() {}
}
