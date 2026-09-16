package com.cocode.vcode.ide.core.language.json;

/**
 * A flat-array data container representing a stream of JSON(C) syntax tokens.
 * <p>
 * Uses parallel arrays instead of object instances to maintain zero
 * allocation overhead during fast typing.
 */
public final class JsonTokenStream {

    // Token type constants (fits in a byte)
    public static final byte TK_NONE = 0;
    public static final byte TK_BRACE_OPEN = 1; // {
    public static final byte TK_BRACE_CLOSE = 2; // }
    public static final byte TK_BRACKET_OPEN = 3; // [
    public static final byte TK_BRACKET_CLOSE = 4; // ]
    public static final byte TK_COLON = 5; // :
    public static final byte TK_COMMA = 6; // ,
    public static final byte TK_STRING = 7; // "..."
    public static final byte TK_NUMBER = 8; // 123.45
    public static final byte TK_KEYWORD = 9; // true, false, null
    public static final byte TK_WHITESPACE = 10;
    public static final byte TK_COMMENT = 11; // /*...*/ or //...
    public static final byte TK_ERROR = 12; // Unrecognized characters

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
     * Constructs a JsonTokenStream with the provided parallel arrays.
     * Note: This class only acts as a container and does no lexing itself.
     *
     * @param types      Array containing the token type for each character
     * @param tokenStart Array containing the start offset of the token for each character
     */
    public JsonTokenStream(byte[] types, int[] tokenStart) {
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

    public boolean isString(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_STRING;
    }

    public boolean isNumber(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_NUMBER;
    }

    public boolean isKeyword(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_KEYWORD;
    }
}
