package com.cocode.vcode.ide.core.language.html;

/**
 * Full-file tokenizer for HTML. 
 * Implements a zero-allocation state machine that outputs a flat-array HtmlTokenStream.
 * Handles tags, attributes, text nodes, comments, doctypes, and raw-text script/style blocks.
 */
public class HtmlLexer {

    private static final int STATE_TEXT = 0;
    private static final int STATE_TAG_OPEN = 1;
    private static final int STATE_TAG_NAME = 2;
    private static final int STATE_IN_TAG = 3;
    private static final int STATE_ATTR_NAME = 4;
    private static final int STATE_AFTER_ATTR_NAME = 5;
    private static final int STATE_BEFORE_ATTR_VALUE = 6;
    private static final int STATE_ATTR_VALUE_QUOTED = 7;
    private static final int STATE_ATTR_VALUE_UNQUOTED = 8;
    private static final int STATE_COMMENT = 9;
    private static final int STATE_DOCTYPE = 10;
    private static final int STATE_RAW_TEXT = 12;

    /**
     * Tokenizes an HTML source string into a new HtmlTokenStream.
     * @param source The HTML source code
     * @return A populated HtmlTokenStream
     */
    public static HtmlTokenStream tokenize(String source) {
        if (source == null) source = "";
        return tokenizeRegion(source, 0, source.length());
    }

    /**
     * Tokenizes a sub-region of an HTML source string into a new HtmlTokenStream.
     * @param source The HTML source code
     * @param startOffset start offset in source
     * @param endOffset end offset in source
     * @return A populated HtmlTokenStream with source-relative offsets
     */
    public static HtmlTokenStream tokenizeRegion(String source, int startOffset, int endOffset) {
        if (source == null) source = "";
        int len = source.length();
        int regionStart = Math.max(0, Math.min(startOffset, len));
        int regionEnd = Math.max(regionStart, Math.min(endOffset, len));

        byte[] types = new byte[len];
        int[] starts = new int[len];

        int state = STATE_TEXT;
        int currentTokenStart = regionStart;
        byte currentTokenType = HtmlTokenStream.TK_TEXT;
        char quoteChar = 0;
        
        boolean isClosingTag = false;
        String rawTextClosingNeedle = null;
        int lastTagNameStart = -1;
        int lastTagNameEnd = -1;

        int i = regionStart;
        while (i < regionEnd) {
            char c = source.charAt(i);
            boolean advance = true;

            switch (state) {
                case STATE_TEXT:
                    if (c == '<') {
                        if (i + 3 < regionEnd && source.charAt(i + 1) == '!' && source.charAt(i + 2) == '-' && source.charAt(i + 3) == '-') {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_COMMENT;
                            state = STATE_COMMENT;
                            types[i] = currentTokenType; starts[i] = currentTokenStart;
                            types[i+1] = currentTokenType; starts[i+1] = currentTokenStart;
                            types[i+2] = currentTokenType; starts[i+2] = currentTokenStart;
                            types[i+3] = currentTokenType; starts[i+3] = currentTokenStart;
                            advance = false;
                            i += 4;
                        } else if (i + 1 < regionEnd && source.charAt(i + 1) == '!') {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_DOCTYPE;
                            state = STATE_DOCTYPE;
                        } else {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_TAG_OPEN;
                            state = STATE_TAG_OPEN;
                            isClosingTag = false;
                            lastTagNameStart = -1;
                            lastTagNameEnd = -1;
                        }
                    } else {
                        if (currentTokenType != HtmlTokenStream.TK_TEXT) {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_TEXT;
                        }
                    }
                    break;

                case STATE_RAW_TEXT:
                    if (c == '<' && rawTextClosingNeedle != null) {
                        int needleLen = rawTextClosingNeedle.length();
                        if (i + needleLen <= regionEnd) {
                            boolean match = true;
                            for (int k = 0; k < needleLen; k++) {
                                if (Character.toLowerCase(source.charAt(i + k)) != rawTextClosingNeedle.charAt(k)) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match && i + needleLen < regionEnd) {
                                char nextC = source.charAt(i + needleLen);
                                if (nextC != '>' && nextC != '/' && !Character.isWhitespace(nextC)) {
                                    match = false;
                                }
                            }
                            if (match) {
                                types[i] = HtmlTokenStream.TK_TAG_OPEN;
                                starts[i] = i;
                                if (i + 1 < regionEnd && source.charAt(i + 1) == '/') {
                                    types[i + 1] = HtmlTokenStream.TK_TAG_OPEN;
                                    starts[i + 1] = i;
                                    currentTokenStart = i + 2;
                                    currentTokenType = HtmlTokenStream.TK_TAG_NAME;
                                    lastTagNameStart = i + 2;
                                    lastTagNameEnd = -1;
                                    state = STATE_TAG_NAME;
                                    isClosingTag = true;
                                    rawTextClosingNeedle = null;
                                    advance = false;
                                    i += 2;
                                    break;
                                } else {
                                    currentTokenStart = i;
                                    currentTokenType = HtmlTokenStream.TK_TAG_OPEN;
                                    state = STATE_TAG_OPEN;
                                    isClosingTag = false;
                                    rawTextClosingNeedle = null;
                                    lastTagNameStart = -1;
                                    lastTagNameEnd = -1;
                                    advance = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (currentTokenType != HtmlTokenStream.TK_TEXT) {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                    }
                    break;

                case STATE_TAG_OPEN:
                    if (c == '/') {
                        isClosingTag = true;
                        types[i] = HtmlTokenStream.TK_TAG_OPEN;
                        starts[i] = currentTokenStart;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        state = STATE_TEXT;
                        types[i] = currentTokenType;
                        starts[i] = currentTokenStart;
                    } else {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_TAG_NAME;
                        state = STATE_TAG_NAME;
                        lastTagNameStart = i;
                        advance = false; 
                    }
                    break;

                case STATE_TAG_NAME:
                    if (Character.isWhitespace(c)) {
                        lastTagNameEnd = i;
                        state = STATE_IN_TAG;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (c == '>' || (c == '/' && i + 1 < regionEnd && source.charAt(i + 1) == '>')) {
                        lastTagNameEnd = i;
                        state = STATE_IN_TAG;
                        advance = false;
                    }
                    break;

                case STATE_IN_TAG:
                    if (c == '>') {
                        types[i] = HtmlTokenStream.TK_TAG_CLOSE;
                        starts[i] = i;
                        if (!isClosingTag && lastTagNameStart != -1 && lastTagNameEnd > lastTagNameStart) {
                            String tag = source.substring(lastTagNameStart, lastTagNameEnd).toLowerCase();
                            if ("script".equals(tag)) {
                                rawTextClosingNeedle = "</script";
                                state = STATE_RAW_TEXT;
                            } else if ("style".equals(tag)) {
                                rawTextClosingNeedle = "</style";
                                state = STATE_RAW_TEXT;
                            } else {
                                state = STATE_TEXT;
                            }
                        } else {
                            state = STATE_TEXT;
                        }
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        currentTokenStart = i + 1;
                        advance = false;
                        i++;
                    } else if (c == '/' && i + 1 < regionEnd && source.charAt(i + 1) == '>') {
                        types[i] = HtmlTokenStream.TK_TAG_CLOSE;
                        starts[i] = i;
                        types[i + 1] = HtmlTokenStream.TK_TAG_CLOSE;
                        starts[i + 1] = i;
                        state = STATE_TEXT;
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        currentTokenStart = i + 2;
                        advance = false;
                        i += 2;
                    } else if (c == '<') {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_TAG_OPEN;
                        state = STATE_TAG_OPEN;
                        isClosingTag = false;
                        lastTagNameStart = -1;
                        lastTagNameEnd = -1;
                    } else if (!Character.isWhitespace(c)) {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_ATTR_NAME;
                        state = STATE_ATTR_NAME;
                        advance = false;
                    } else {
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    }
                    break;

                case STATE_ATTR_NAME:
                    if (c == '=') {
                        state = STATE_BEFORE_ATTR_VALUE;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (Character.isWhitespace(c)) {
                        state = STATE_AFTER_ATTR_NAME;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (c == '>' || (c == '/' && i + 1 < regionEnd && source.charAt(i + 1) == '>')) {
                        state = STATE_IN_TAG;
                        advance = false;
                    }
                    break;

                case STATE_AFTER_ATTR_NAME:
                    if (c == '=') {
                        state = STATE_BEFORE_ATTR_VALUE;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (!Character.isWhitespace(c)) {
                        if (c == '>' || (c == '/' && i + 1 < regionEnd && source.charAt(i + 1) == '>')) {
                            state = STATE_IN_TAG;
                            advance = false;
                        } else {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_ATTR_NAME;
                            state = STATE_ATTR_NAME;
                            advance = false;
                        }
                    } else {
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    }
                    break;

                case STATE_BEFORE_ATTR_VALUE:
                    if (Character.isWhitespace(c)) {
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (c == '"' || c == '\'') {
                        quoteChar = c;
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_ATTR_VALUE;
                        state = STATE_ATTR_VALUE_QUOTED;
                    } else if (c == '>' || (c == '/' && i + 1 < regionEnd && source.charAt(i + 1) == '>')) {
                        state = STATE_IN_TAG;
                        advance = false;
                    } else {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_ATTR_VALUE;
                        state = STATE_ATTR_VALUE_UNQUOTED;
                        advance = false;
                    }
                    break;

                case STATE_ATTR_VALUE_QUOTED:
                    if (c == quoteChar) {
                        types[i] = HtmlTokenStream.TK_ATTR_VALUE;
                        starts[i] = currentTokenStart;
                        state = STATE_IN_TAG;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                        advance = false;
                        i++;
                    }
                    break;

                case STATE_ATTR_VALUE_UNQUOTED:
                    if (Character.isWhitespace(c) || c == '>' || (c == '/' && i + 1 < regionEnd && source.charAt(i + 1) == '>')) {
                        state = STATE_IN_TAG;
                        advance = false;
                    }
                    break;

                case STATE_COMMENT:
                    if (c == '-' && i + 2 < regionEnd && source.charAt(i + 1) == '-' && source.charAt(i + 2) == '>') {
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        types[i+1] = currentTokenType; starts[i+1] = currentTokenStart;
                        types[i+2] = currentTokenType; starts[i+2] = currentTokenStart;
                        state = STATE_TEXT;
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        currentTokenStart = i + 3;
                        advance = false;
                        i += 3;
                    }
                    break;

                case STATE_DOCTYPE:
                    if (c == '>') {
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        state = STATE_TEXT;
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        currentTokenStart = i + 1;
                        advance = false;
                        i++;
                    }
                    break;
            }

            if (advance) {
                if (i < regionEnd) {
                    types[i] = currentTokenType;
                    starts[i] = currentTokenStart;
                }
                i++;
            }
        }

        return new HtmlTokenStream(types, starts);
    }
}
