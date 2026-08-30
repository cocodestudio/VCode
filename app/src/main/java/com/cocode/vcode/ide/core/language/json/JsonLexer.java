package com.cocode.vcode.ide.core.language.json;

/**
 * Full-file tokenizer for JSON and JSONC.
 * Implements a zero-allocation state machine that outputs a flat-array JsonTokenStream.
 * Extremely fault-tolerant: handles unterminated strings and unrecognized characters.
 */
public class JsonLexer {

    public static JsonTokenStream tokenize(String source) {
        return tokenizeRegion(source, 0, source.length());
    }

    public static JsonTokenStream tokenizeRegion(String source, int regionStart, int regionEnd) {
        int length = source.length();
        byte[] types = new byte[length];
        int[] starts = new int[length];

        int i = regionStart;
        while (i < regionEnd) {
            char c = source.charAt(i);

            // Whitespace
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < regionEnd && Character.isWhitespace(source.charAt(i))) {
                    types[i] = JsonTokenStream.TK_WHITESPACE;
                    starts[i] = start;
                    i++;
                }
                continue;
            }

            // Punctuation
            if (c == '{') { types[i] = JsonTokenStream.TK_BRACE_OPEN; starts[i] = i; i++; continue; }
            if (c == '}') { types[i] = JsonTokenStream.TK_BRACE_CLOSE; starts[i] = i; i++; continue; }
            if (c == '[') { types[i] = JsonTokenStream.TK_BRACKET_OPEN; starts[i] = i; i++; continue; }
            if (c == ']') { types[i] = JsonTokenStream.TK_BRACKET_CLOSE; starts[i] = i; i++; continue; }
            if (c == ':') { types[i] = JsonTokenStream.TK_COLON; starts[i] = i; i++; continue; }
            if (c == ',') { types[i] = JsonTokenStream.TK_COMMA; starts[i] = i; i++; continue; }

            // Comments (JSONC)
            if (c == '/' && i + 1 < regionEnd) {
                char c2 = source.charAt(i + 1);
                if (c2 == '/') { // Line comment
                    int start = i;
                    while (i < regionEnd && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
                        types[i] = JsonTokenStream.TK_COMMENT;
                        starts[i] = start;
                        i++;
                    }
                    continue;
                } else if (c2 == '*') { // Block comment
                    int start = i;
                    types[i] = JsonTokenStream.TK_COMMENT; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_COMMENT; starts[i] = start; i++;
                    while (i < regionEnd) {
                        types[i] = JsonTokenStream.TK_COMMENT;
                        starts[i] = start;
                        if (source.charAt(i) == '*' && i + 1 < regionEnd && source.charAt(i + 1) == '/') {
                            i++;
                            types[i] = JsonTokenStream.TK_COMMENT;
                            starts[i] = start;
                            i++;
                            break;
                        }
                        i++;
                    }
                    continue;
                }
            }

            // String
            if (c == '"') {
                int start = i;
                types[i] = JsonTokenStream.TK_STRING;
                starts[i] = start;
                i++;
                while (i < regionEnd) {
                    char curr = source.charAt(i);
                    types[i] = JsonTokenStream.TK_STRING;
                    starts[i] = start;
                    if (curr == '"') {
                        i++;
                        break;
                    } else if (curr == '\\') {
                        i++;
                        if (i < regionEnd) {
                            types[i] = JsonTokenStream.TK_STRING;
                            starts[i] = start;
                            i++;
                        }
                    } else if (curr == '\n' || curr == '\r') {
                        // Unterminated string on this line, break to let the next line parse cleanly
                        break;
                    } else {
                        i++;
                    }
                }
                continue;
            }

            // Number
            if (c == '-' || (c >= '0' && c <= '9')) {
                int start = i;
                while (i < regionEnd) {
                    char curr = source.charAt(i);
                    if ((curr >= '0' && curr <= '9') || curr == '.' || curr == 'e' || curr == 'E' || curr == '-' || curr == '+') {
                        types[i] = JsonTokenStream.TK_NUMBER;
                        starts[i] = start;
                        i++;
                    } else {
                        break;
                    }
                }
                continue;
            }

            // Keywords (true, false, null) - strict zero-allocation matching
            if (c == 't' && i + 3 < regionEnd && source.charAt(i + 1) == 'r' && source.charAt(i + 2) == 'u' && source.charAt(i + 3) == 'e') {
                int start = i;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                continue;
            }
            if (c == 'f' && i + 4 < regionEnd && source.charAt(i + 1) == 'a' && source.charAt(i + 2) == 'l' && source.charAt(i + 3) == 's' && source.charAt(i + 4) == 'e') {
                int start = i;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                continue;
            }
            if (c == 'n' && i + 3 < regionEnd && source.charAt(i + 1) == 'u' && source.charAt(i + 2) == 'l' && source.charAt(i + 3) == 'l') {
                int start = i;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                continue;
            }

            // Error (Unrecognized character)
            int start = i;
            types[i] = JsonTokenStream.TK_ERROR;
            starts[i] = start;
            i++;
            while (i < regionEnd) {
                char curr = source.charAt(i);
                if (Character.isWhitespace(curr) || curr == '{' || curr == '}' || curr == '[' || curr == ']' || 
                    curr == ':' || curr == ',' || curr == '"' || curr == '/' || curr == '-' || 
                    (curr >= '0' && curr <= '9') || curr == 't' || curr == 'f' || curr == 'n') {
                    break;
                }
                types[i] = JsonTokenStream.TK_ERROR;
                starts[i] = start;
                i++;
            }
        }

        return new JsonTokenStream(types, starts);
    }
}
