package com.cocode.vcode.ide.core.lsp.model;

/**
 * Common folding range kinds matching LSP 3.17 FoldingRangeKind.
 */
public final class FoldingRangeKind {

    public static final String COMMENT = "comment";
    public static final String IMPORTS = "imports";
    public static final String REGION = "region";

    private FoldingRangeKind() {}
}
