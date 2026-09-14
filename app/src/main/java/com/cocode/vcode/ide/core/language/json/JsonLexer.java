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
                if (!isValidJsonNumber(source, start, i)) {
                    for (int k = start; k < i; k++) {
                        types[k] = JsonTokenStream.TK_ERROR;
                    }
                }
                continue;
            }

            // Keywords (true, false, null) - strict zero-allocation matching with identifier boundary checks
            if (c == 't' && i + 3 < regionEnd && source.charAt(i + 1) == 'r' && source.charAt(i + 2) == 'u' && source.charAt(i + 3) == 'e') {
                if (i + 4 >= regionEnd || !isIdentPart(source.charAt(i + 4))) {
                    int start = i;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    continue;
                }
            }
            if (c == 'f' && i + 4 < regionEnd && source.charAt(i + 1) == 'a' && source.charAt(i + 2) == 'l' && source.charAt(i + 3) == 's' && source.charAt(i + 4) == 'e') {
                if (i + 5 >= regionEnd || !isIdentPart(source.charAt(i + 5))) {
                    int start = i;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    continue;
                }
            }
            if (c == 'n' && i + 3 < regionEnd && source.charAt(i + 1) == 'u' && source.charAt(i + 2) == 'l' && source.charAt(i + 3) == 'l') {
                if (i + 4 >= regionEnd || !isIdentPart(source.charAt(i + 4))) {
                    int start = i;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    types[i] = JsonTokenStream.TK_KEYWORD; starts[i] = start; i++;
                    continue;
                }
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
                    (curr >= '0' && curr <= '9')) {
                    break;
                }
                types[i] = JsonTokenStream.TK_ERROR;
                starts[i] = start;
                i++;
            }
        }

        return new JsonTokenStream(types, starts);
    }

    private static boolean isIdentPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$';
    }

    static boolean isValidJsonNumber(String source, int start, int end) {
        if (start >= end) return false;
        int idx = start;

        // Optional leading minus
        if (source.charAt(idx) == '-') {
            idx++;
            if (idx == end) return false;
        }

        // Integer part
        char c = source.charAt(idx);
        if (c == '0') {
            idx++;
            // Leading zero cannot be followed by another digit in JSON
            if (idx < end && source.charAt(idx) >= '0' && source.charAt(idx) <= '9') {
                return false;
            }
        } else if (c >= '1' && c <= '9') {
            idx++;
            while (idx < end && source.charAt(idx) >= '0' && source.charAt(idx) <= '9') {
                idx++;
            }
        } else {
            return false;
        }

        // Optional fraction part
        if (idx < end && source.charAt(idx) == '.') {
            idx++;
            if (idx == end) return false;
            if (source.charAt(idx) < '0' || source.charAt(idx) > '9') return false;
            while (idx < end && source.charAt(idx) >= '0' && source.charAt(idx) <= '9') {
                idx++;
            }
        }

        // Optional exponent part
        if (idx < end && (source.charAt(idx) == 'e' || source.charAt(idx) == 'E')) {
            idx++;
            if (idx == end) return false;
            if (source.charAt(idx) == '+' || source.charAt(idx) == '-') {
                idx++;
                if (idx == end) return false;
            }
            if (source.charAt(idx) < '0' || source.charAt(idx) > '9') return false;
            while (idx < end && source.charAt(idx) >= '0' && source.charAt(idx) <= '9') {
                idx++;
            }
        }

        return idx == end;
    }
}
