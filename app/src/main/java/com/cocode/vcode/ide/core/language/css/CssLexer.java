package com.cocode.vcode.ide.core.language.css;

/**
 * Full-file tokenizer for CSS. 
 * Implements a zero-allocation state machine that outputs a flat-array CssTokenStream.
 * Handles selectors, properties, values, punctuation, and comments.
 */
public class CssLexer {

    private static final int STATE_IDLE = 0;
    private static final int STATE_SELECTOR = 1;
    private static final int STATE_PROPERTY_NAME = 2;
    private static final int STATE_PROPERTY_VALUE = 3;
    private static final int STATE_STRING = 4;
    private static final int STATE_COMMENT = 5;

    public static CssTokenStream tokenize(String source) {
        return tokenizeRegion(source, 0, source.length());
    }

    public static CssTokenStream tokenizeRegion(String source, int startOffset, int endOffset) {
        int length = source.length();
        byte[] types = new byte[length];
        int[] starts = new int[length];

        if (startOffset < endOffset) {
            lexRegion(source, types, starts, startOffset, endOffset);
        }
        
        return new CssTokenStream(types, starts);
    }

    private static void lexRegion(String source, byte[] types, int[] starts, int startOffset, int endOffset) {
        int length = source.length();
        int state = STATE_IDLE;
        int returnState = STATE_IDLE;
        int currentTokenStart = startOffset;
        byte currentTokenType = CssTokenStream.TK_NONE;
        char quoteChar = 0;

        int i = startOffset;
        while (i < endOffset) {
            char c = source.charAt(i);
            boolean advance = true;

            switch (state) {
                case STATE_IDLE:
                    if (c == '{' || c == '}' || c == ';') {
                        currentTokenStart = i;
                        currentTokenType = CssTokenStream.TK_PUNCT;
                    } else if (c == '/' && i + 1 < endOffset && source.charAt(i + 1) == '*') {
                        currentTokenStart = i;
                        currentTokenType = CssTokenStream.TK_COMMENT;
                        state = STATE_COMMENT;
                        returnState = STATE_IDLE;
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        types[i + 1] = currentTokenType; starts[i + 1] = currentTokenStart;
                        advance = false;
                        i += 2;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = CssTokenStream.TK_NONE;
                    } else {
                        if (isSelector(source, i, endOffset)) {
                            state = STATE_SELECTOR;
                        } else {
                            state = STATE_PROPERTY_NAME;
                        }
                        advance = false; // reprocess in new state
                    }
                    break;

                case STATE_SELECTOR:
                    if (c == '{' || c == '}' || c == ';') {
                        state = STATE_IDLE;
                        advance = false;
                    } else if (c == '/' && i + 1 < endOffset && source.charAt(i + 1) == '*') {
                        currentTokenStart = i;
                        currentTokenType = CssTokenStream.TK_COMMENT;
                        state = STATE_COMMENT;
                        returnState = STATE_SELECTOR;
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        types[i + 1] = currentTokenType; starts[i + 1] = currentTokenStart;
                        advance = false;
                        i += 2;
                    } else if (c == '"' || c == '\'') {
                        if (currentTokenType != CssTokenStream.TK_SELECTOR) {
                            currentTokenStart = i;
                            currentTokenType = CssTokenStream.TK_SELECTOR;
                        }
                        state = STATE_STRING;
                        returnState = STATE_SELECTOR;
                        quoteChar = c;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = CssTokenStream.TK_NONE;
                    } else {
                        if (currentTokenType != CssTokenStream.TK_SELECTOR) {
                            currentTokenStart = i;
                            currentTokenType = CssTokenStream.TK_SELECTOR;
                        }
                    }
                    break;

                case STATE_PROPERTY_NAME:
                    if (c == ':') {
                        currentTokenStart = i;
                        currentTokenType = CssTokenStream.TK_PUNCT;
                        state = STATE_PROPERTY_VALUE;
                    } else if (c == ';' || c == '{' || c == '}') {
                        state = STATE_IDLE;
                        advance = false;
                    } else if (c == '/' && i + 1 < endOffset && source.charAt(i + 1) == '*') {
                        currentTokenStart = i;
                        currentTokenType = CssTokenStream.TK_COMMENT;
                        state = STATE_COMMENT;
                        returnState = STATE_PROPERTY_NAME;
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        types[i + 1] = currentTokenType; starts[i + 1] = currentTokenStart;
                        advance = false;
                        i += 2;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = CssTokenStream.TK_NONE;
                    } else {
                        if (currentTokenType != CssTokenStream.TK_PROPERTY) {
                            currentTokenStart = i;
                            currentTokenType = CssTokenStream.TK_PROPERTY;
                        }
                    }
                    break;

                case STATE_PROPERTY_VALUE:
                    if (c == ';' || c == '}' || c == '{') {
                        state = STATE_IDLE;
                        advance = false;
                    } else if (c == '/' && i + 1 < endOffset && source.charAt(i + 1) == '*') {
                        currentTokenStart = i;
                        currentTokenType = CssTokenStream.TK_COMMENT;
                        state = STATE_COMMENT;
                        returnState = STATE_PROPERTY_VALUE;
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        types[i + 1] = currentTokenType; starts[i + 1] = currentTokenStart;
                        advance = false;
                        i += 2;
                    } else if (c == '"' || c == '\'') {
                        if (currentTokenType != CssTokenStream.TK_VALUE) {
                            currentTokenStart = i;
                            currentTokenType = CssTokenStream.TK_VALUE;
                        }
                        state = STATE_STRING;
                        returnState = STATE_PROPERTY_VALUE;
                        quoteChar = c;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = CssTokenStream.TK_NONE;
                    } else {
                        if (currentTokenType != CssTokenStream.TK_VALUE) {
                            currentTokenStart = i;
                            currentTokenType = CssTokenStream.TK_VALUE;
                        }
                    }
                    break;

                case STATE_STRING:
                    if (c == quoteChar) {
                        state = returnState;
                    } else if (c == '\n') {
                        state = returnState;
                        advance = false; // re-process \n in the return state
                    } else if (c == '\\') {
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        if (i + 1 < endOffset) {
                            types[i + 1] = currentTokenType; starts[i + 1] = currentTokenStart;
                        }
                        advance = false;
                        i += 2;
                    }
                    break;

                case STATE_COMMENT:
                    if (c == '*' && i + 1 < endOffset && source.charAt(i + 1) == '/') {
                        types[i] = currentTokenType; starts[i] = currentTokenStart;
                        types[i + 1] = currentTokenType; starts[i + 1] = currentTokenStart;
                        state = returnState;
                        currentTokenType = CssTokenStream.TK_NONE; // Reset type so returnState correctly starts a new token
                        advance = false;
                        i += 2;
                    }
                    break;
            }

            if (advance) {
                if (i < endOffset) {
                    types[i] = currentTokenType;
                    starts[i] = currentTokenStart;
                }
                i++;
            }
        }
    }

    private static boolean isSelector(String source, int start, int endOffset) {
        char firstChar = source.charAt(start);
        if (firstChar == '.' || firstChar == '#' || firstChar == '&' || firstChar == '@' ||
            firstChar == '>' || firstChar == '+' || firstChar == '~' || firstChar == '[') {
            return true;
        }

        boolean inString = false;
        char quote = 0;
        boolean inComment = false;
        boolean hasColon = false;

        int limit = Math.min(endOffset, start + 512);

        for (int i = start; i < limit; i++) {
            char c = source.charAt(i);

            if (inComment) {
                if (c == '*' && i + 1 < limit && source.charAt(i + 1) == '/') {
                    inComment = false;
                    i++;
                }
                continue;
            }

            if (inString) {
                if (c == quote) {
                    inString = false;
                } else if (c == '\\') {
                    i++;
                }
                continue;
            }

            if (c == '/' && i + 1 < limit && source.charAt(i + 1) == '*') {
                inComment = true;
                i++;
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                continue;
            }

            if (c == '{') {
                return true;
            }
            if (c == ':') {
                hasColon = true;
            }

            if (c == ';' || c == '}') {
                return !hasColon; // if no colon, treat as selector (e.g. at-rule or syntax error)
            }
        }
        return !hasColon; // Default to selector only if no colon was found
    }
}
