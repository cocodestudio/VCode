package com.cocode.vcode.ide.core.language.html;

/**
 * Full-file tokenizer for HTML. 
 * Implements a zero-allocation state machine that outputs a flat-array HtmlTokenStream.
 * Handles tags, attributes, text nodes, comments, and doctypes.
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
    private static final int STATE_TAG_CLOSE = 11;

    /**
     * Tokenizes an HTML source string into a new HtmlTokenStream.
     * @param source The HTML source code
     * @return A populated HtmlTokenStream
     */
    public static HtmlTokenStream tokenize(String source) {
        int length = source.length();
        byte[] types = new byte[length];
        int[] starts = new int[length];

        int state = STATE_TEXT;
        int currentTokenStart = 0;
        byte currentTokenType = HtmlTokenStream.TK_TEXT;
        char quoteChar = 0;
        
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            boolean advance = true;

            switch (state) {
                case STATE_TEXT:
                    if (c == '<') {
                        if (i + 3 < length && source.charAt(i + 1) == '!' && source.charAt(i + 2) == '-' && source.charAt(i + 3) == '-') {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_COMMENT;
                            state = STATE_COMMENT;
                            types[i] = currentTokenType; starts[i] = currentTokenStart;
                            types[i+1] = currentTokenType; starts[i+1] = currentTokenStart;
                            types[i+2] = currentTokenType; starts[i+2] = currentTokenStart;
                            types[i+3] = currentTokenType; starts[i+3] = currentTokenStart;
                            advance = false;
                            i += 4;
                        } else if (i + 1 < length && source.charAt(i + 1) == '!') {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_DOCTYPE;
                            state = STATE_DOCTYPE;
                        } else {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_TAG_OPEN;
                            state = STATE_TAG_OPEN;
                        }
                    } else {
                        if (currentTokenType != HtmlTokenStream.TK_TEXT) {
                            currentTokenStart = i;
                            currentTokenType = HtmlTokenStream.TK_TEXT;
                        }
                    }
                    break;

                case STATE_TAG_OPEN:
                    if (c == '/') {
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
                        advance = false; 
                    }
                    break;

                case STATE_TAG_NAME:
                    if (Character.isWhitespace(c)) {
                        state = STATE_IN_TAG;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (c == '>' || c == '/') {
                        state = STATE_IN_TAG;
                        advance = false;
                    }
                    break;

                case STATE_IN_TAG:
                    if (c == '>') {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_TAG_CLOSE;
                        state = STATE_TAG_CLOSE;
                    } else if (c == '/' && i + 1 < length && source.charAt(i + 1) == '>') {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_TAG_CLOSE;
                        state = STATE_TAG_CLOSE;
                    } else if (c == '<') {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_TAG_OPEN;
                        state = STATE_TAG_OPEN;
                    } else if (!Character.isWhitespace(c)) {
                        currentTokenStart = i;
                        currentTokenType = HtmlTokenStream.TK_ATTR_NAME;
                        state = STATE_ATTR_NAME;
                        advance = false;
                    } else {
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    }
                    break;

                case STATE_TAG_CLOSE:
                    if (c == '>') {
                        types[i] = HtmlTokenStream.TK_TAG_CLOSE;
                        starts[i] = currentTokenStart;
                        state = STATE_TEXT;
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        currentTokenStart = i + 1;
                        advance = false;
                        i++;
                    } else {
                        state = STATE_TEXT;
                        currentTokenType = HtmlTokenStream.TK_TEXT;
                        currentTokenStart = i;
                        advance = false;
                    }
                    break;

                case STATE_ATTR_NAME:
                    if (c == '=') {
                        state = STATE_BEFORE_ATTR_VALUE;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (Character.isWhitespace(c)) {
                        state = STATE_AFTER_ATTR_NAME;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (c == '>' || c == '/') {
                        state = STATE_IN_TAG;
                        advance = false;
                    }
                    break;

                case STATE_AFTER_ATTR_NAME:
                    if (c == '=') {
                        state = STATE_BEFORE_ATTR_VALUE;
                        currentTokenType = HtmlTokenStream.TK_NONE;
                    } else if (!Character.isWhitespace(c)) {
                        if (c == '>' || c == '/') {
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
                    } else if (c == '>' || c == '/') {
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
                    if (Character.isWhitespace(c) || c == '>' || c == '/') {
                        state = STATE_IN_TAG;
                        advance = false;
                    }
                    break;

                case STATE_COMMENT:
                    if (c == '-' && i + 2 < length && source.charAt(i + 1) == '-' && source.charAt(i + 2) == '>') {
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
                if (i < length) {
                    types[i] = currentTokenType;
                    starts[i] = currentTokenStart;
                }
                i++;
            }
        }

        return new HtmlTokenStream(types, starts);
    }
}
