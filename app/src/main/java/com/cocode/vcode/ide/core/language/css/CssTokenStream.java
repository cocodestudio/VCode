package com.cocode.vcode.ide.core.language.css;

/**
 * A flat-array data container representing a stream of CSS syntax tokens.
 * This provides specific token types for CSS instead of generic masks.
 * <p>
 * Uses parallel arrays instead of object instances to maintain zero
 * allocation overhead during fast typing.
 */
public final class CssTokenStream {

    // Token type constants (fits in a byte)
    public static final byte TK_NONE = 0;
    public static final byte TK_SELECTOR = 1; // e.g. .class, #id, div
    public static final byte TK_PROPERTY = 2; // e.g. color, margin
    public static final byte TK_VALUE = 3; // e.g. red, 10px
    public static final byte TK_PUNCT = 4; // e.g. {, }, :, ;
    public static final byte TK_COMMENT = 5; // e.g. /* comment */

    /**
     * Parallel array of token types. One byte per character in the source text.
     */
    public byte[] types;
    public int length;

    /**
     * Parallel array of token start offsets. For any character at index i,
     * tokenStart[i] points to the start offset of the token it belongs to.
     */
    public int[] tokenStart;

    /**
     * Constructs a CssTokenStream with the provided parallel arrays.
     * Note: This class only acts as a container and does no lexing itself.
     *
     * @param types      Array containing the token type for each character
     * @param tokenStart Array containing the start offset of the token for each character
     */
    public CssTokenStream(byte[] types, int[] tokenStart) {
        this.types = types;
        this.tokenStart = tokenStart;
        this.length = types.length;
    }

    public void reset(int newLength) {
        this.length = newLength;
        if (types == null || types.length < newLength) {
            types = new byte[newLength];
            tokenStart = new int[newLength];
        }
    }

    public boolean isSelector(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_SELECTOR;
    }

    public boolean isProperty(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_PROPERTY;
    }

    public boolean isValue(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_VALUE;
    }

    public boolean isPunct(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_PUNCT;
    }

    public boolean isComment(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_COMMENT;
    }
}
