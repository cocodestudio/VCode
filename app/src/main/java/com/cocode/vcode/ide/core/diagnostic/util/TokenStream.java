package com.cocode.vcode.ide.core.diagnostic.util;

/**
 * A flat-array data container representing a stream of syntax tokens.
 * This replaces the legacy TokenMask, providing specific token types
 * (keyword, identifier, string, etc.) instead of just boolean masks.
 * <p>
 * Uses parallel arrays instead of object instances to maintain zero
 * allocation overhead during fast typing.
 */
public final class TokenStream {

    // Token type constants (fits in a byte)
    public static final byte TK_NONE = 0;
    public static final byte TK_KEYWORD = 1;
    public static final byte TK_IDENTIFIER = 2;
    public static final byte TK_NUMBER = 3;
    public static final byte TK_STRING = 4;   // ' " ` (formerly inString)
    public static final byte TK_COMMENT = 5;   // // /* (formerly inComment)
    public static final byte TK_REGEX = 6;   // /.../ (formerly inRegex)
    public static final byte TK_PUNCT = 7;   // { } ( ) , ; etc.
    public static final byte TK_OPERATOR = 8;   // = + - * etc.
    public static final byte TK_WHITESPACE = 9;
    public static final byte TK_TEMPLATE = 10;  // template literal body

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
     * Constructs a TokenStream with the provided parallel arrays.
     * Note: This class only acts as a container and does no lexing itself.
     *
     * @param types      Array containing the token type for each character
     * @param tokenStart Array containing the start offset of the token for each character
     */
    public TokenStream(byte[] types, int[] tokenStart) {
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

    /**
     * Backward compatibility shim matching the legacy TokenMask behavior.
     *
     * @param offset The character index to check
     * @return true if the character is inside a string, comment, regex, or template literal
     */
    public boolean isMasked(int offset) {
        if (offset < 0 || offset >= length) return false;
        byte t = types[offset];
        return t == TK_STRING || t == TK_COMMENT || t == TK_REGEX || t == TK_TEMPLATE;
    }

    /**
     * Checks if the character at the given offset belongs to a keyword token.
     */
    public boolean isKeyword(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_KEYWORD;
    }

    /**
     * Checks if the character at the given offset belongs to an identifier token.
     */
    public boolean isIdentifier(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_IDENTIFIER;
    }
}
