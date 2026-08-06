package com.cocode.vcode.ide.core.lsp;

/**
 * An entry in the {@link ProjectIndex} symbol table.
 * <p>
 * Represents a named declaration (variable, function, class, CSS rule, HTML id/class, etc.)
 * found anywhere in the project during indexing.
 */
public final class SymbolEntry {

    // -------------------------------------------------------------------------
    // Symbol kind constants
    // -------------------------------------------------------------------------
    public static final int KIND_VARIABLE = 1;
    public static final int KIND_FUNCTION = 2;
    public static final int KIND_CLASS = 3;
    public static final int KIND_INTERFACE = 4;
    public static final int KIND_PROPERTY = 5;
    public static final int KIND_CONSTANT = 6;
    public static final int KIND_CSS_CLASS = 7;
    public static final int KIND_CSS_ID = 8;
    public static final int KIND_HTML_ID = 9;

    /**
     * The symbol name as it appears in source (e.g. {@code "fetchUser"}, {@code ".btn-primary"}).
     */
    public final String name;

    /**
     * Absolute file path where the symbol is declared.
     */
    public final String uri;

    /**
     * Location of the declaration within the file.
     */
    public final LspRange range;

    /**
     * Symbol kind (one of KIND_* constants).
     */
    public final int kind;

    /**
     * Optional type information or signature string (e.g. {@code "function(url: string): Promise"}).
     * May be null.
     */
    public final String detail;

    public SymbolEntry(String name, String uri, LspRange range, int kind, String detail) {
        this.name = name;
        this.uri = uri;
        this.range = range;
        this.kind = kind;
        this.detail = detail;
    }

    public SymbolEntry(String name, String uri, LspRange range, int kind) {
        this(name, uri, range, kind, null);
    }
}
