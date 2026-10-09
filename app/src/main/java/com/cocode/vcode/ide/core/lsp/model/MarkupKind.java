package com.cocode.vcode.ide.core.lsp.model;

/**
 * Describes the content type that a client supports in various result literals
 * like `Hover`, `ParameterInfo`, or `CompletionItem`. Matches LSP 3.17 MarkupKind.
 */
public final class MarkupKind {

    public static final String PLAIN_TEXT = "plaintext";
    public static final String MARKDOWN = "markdown";

    private MarkupKind() {}
}
