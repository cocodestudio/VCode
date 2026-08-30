package com.cocode.vcode.ide.core.language.html;

/**
 * A flat-array data container representing a stream of HTML syntax tokens.
 * This provides specific token types for HTML instead of generic masks.
 * 
 * Uses parallel arrays instead of object instances to maintain zero 
 * allocation overhead during fast typing.
 */
public final class HtmlTokenStream {

    // Token type constants (fits in a byte)
    public static final byte TK_NONE       = 0;
    public static final byte TK_TAG_OPEN   = 1; // <, </
    public static final byte TK_TAG_CLOSE  = 2; // >, />
    public static final byte TK_TAG_NAME   = 3; // div, span
    public static final byte TK_ATTR_NAME  = 4; // class, id
    public static final byte TK_ATTR_VALUE = 5; // "my-class"
    public static final byte TK_TEXT       = 6; // text between tags
    public static final byte TK_COMMENT    = 7; // <!-- comment -->
    public static final byte TK_DOCTYPE    = 8; // <!DOCTYPE html>

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
     * Constructs an HtmlTokenStream with the provided parallel arrays.
     * Note: This class only acts as a container and does no lexing itself.
     *
     * @param types      Array containing the token type for each character
     * @param tokenStart Array containing the start offset of the token for each character
     */
    public HtmlTokenStream(byte[] types, int[] tokenStart) {
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
     * Checks if the character at the given offset belongs to a tag name token.
     */
    public boolean isTagName(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_TAG_NAME;
    }

    /**
     * Checks if the character at the given offset belongs to an attribute name token.
     */
    public boolean isAttrName(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_ATTR_NAME;
    }

    /**
     * Checks if the character at the given offset belongs to an attribute value token.
     */
    public boolean isAttrValue(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_ATTR_VALUE;
    }

    /**
     * Checks if the character at the given offset belongs to a text token.
     */
    public boolean isText(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_TEXT;
    }

    /**
     * Checks if the character at the given offset belongs to a comment token.
     */
    public boolean isComment(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_COMMENT;
    }

    /**
     * Checks if the character at the given offset belongs to a doctype token.
     */
    public boolean isDoctype(int offset) {
        if (offset < 0 || offset >= length) return false;
        return types[offset] == TK_DOCTYPE;
    }

    /**
     * Backward compatibility shim matching the legacy TokenMask behavior.
     * 
     * @param offset The character index to check
     * @return true if the character is inside a comment or attribute value (often treated as strings)
     */
    public boolean isMasked(int offset) {
        if (offset < 0 || offset >= length) return false;
        byte t = types[offset];
        return t == TK_COMMENT || t == TK_ATTR_VALUE;
    }
}
