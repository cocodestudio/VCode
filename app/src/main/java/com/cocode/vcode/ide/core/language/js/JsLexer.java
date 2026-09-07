package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A single-pass, full-file JavaScript/TypeScript lexer.
 * It takes raw source code and populates a flat-array TokenStream.
 * This effectively replaces the regex-based TokenMask parsing, accurately
 * classifying every character into a specific TK_* constant.
 */
public class JsLexer {

    private static final Set<String> JS_KEYWORDS = JsKeywords.JS_KEYWORDS;

    public static TokenStream tokenize(String source) {
        return tokenize(source, null);
    }

    public static TokenStream tokenize(String source, TokenStream buffer) {
        int len = source.length();
        if (buffer == null) {
            buffer = new TokenStream(new byte[len], new int[len]);
        } else {
            buffer.reset(len);
        }

        if (len == 0) return buffer;
        lexRegion(source, buffer.types, buffer.tokenStart, 0, len, 0);
        return buffer;
    }

    public static TokenStream tokenizeRegion(String source, int start, int end) {
        int len = source.length();
        TokenStream buffer = new TokenStream(new byte[len], new int[len]);
        if (start < end) {
            lexRegion(source, buffer.types, buffer.tokenStart, start, end, 0);
            if (end < len) buffer.tokenStart[end] = end;
        }
        return buffer;
    }

    public static TokenStream relex(TokenStream existing, String newSource, int editOffset) {
        int newLen = newSource.length();
        int oldLen = existing.types.length;
        int delta = newLen - oldLen;

        byte[] newTypes = new byte[newLen];
        int[] newTokenStart = new int[newLen];

        // 1. Find safe start point (beginning of the line)
        int start = editOffset;
        if (start > newLen) start = newLen;
        while (start > 0 && newSource.charAt(start - 1) != '\n') {
            start--;
        }

        // If the start is inside a multi-line token in the old stream, push start back to the token's start
        int oldStart = start; // start is before editOffset; prefix is identical in old and new
        if (oldStart > 0 && oldStart < oldLen) {
            int ts = existing.tokenStart[oldStart];
            if (ts < start && ts >= 0) {
                start = ts; 
            }
        }
        if (start < 0) start = 0;

        // 2. Find safe end point
        int newEnd = editOffset;
        while (newEnd < newLen) {
            char c = newSource.charAt(newEnd);
            if (c == '\n') {
                newEnd++;
                break;
            }
            if (c == '`') {
                newEnd++;
                break;
            }
            if (c == '*' && newEnd + 1 < newLen && newSource.charAt(newEnd + 1) == '/') {
                newEnd += 2;
                break;
            }
            newEnd++;
        }

        int oldEnd = newEnd - delta;
        if (oldEnd >= 0 && oldEnd < oldLen) {
            int ts = existing.tokenStart[oldEnd];
            while (oldEnd < oldLen && existing.tokenStart[oldEnd] == ts) {
                oldEnd++;
            }
            newEnd = oldEnd + delta;
        }

        if (oldEnd < start || oldEnd > oldLen) {
            newEnd = newLen;
            oldEnd = oldLen;
        }

        // 3. Copy clean prefix
        if (start > 0) {
            System.arraycopy(existing.types, 0, newTypes, 0, start);
            System.arraycopy(existing.tokenStart, 0, newTokenStart, 0, start);
        }

        // 4. Copy clean suffix
        if (newEnd < newLen && oldEnd < oldLen) {
            int suffixLen = Math.min(newLen - newEnd, oldLen - oldEnd);
            System.arraycopy(existing.types, oldEnd, newTypes, newEnd, suffixLen);
            for (int k = 0; k < suffixLen; k++) {
                int shifted = existing.tokenStart[oldEnd + k] + delta;
                newTokenStart[newEnd + k] = shifted;
            }
        }

        // 5. Determine initial lastTokenType for regex disambiguation
        int lastTokenType = 0;
        int before = start - 1;
        while (before >= 0) {
            if (newTypes[before] == TokenStream.TK_WHITESPACE || newTypes[before] == TokenStream.TK_COMMENT) {
                before--;
                continue;
            }
            byte t = newTypes[before];
            if (t == TokenStream.TK_IDENTIFIER || t == TokenStream.TK_NUMBER || t == TokenStream.TK_STRING || t == TokenStream.TK_TEMPLATE || t == TokenStream.TK_REGEX) {
                lastTokenType = 1;
            } else if (t == TokenStream.TK_PUNCT) {
                char c = newSource.charAt(before);
                if (c == ')' || c == ']') {
                    lastTokenType = 1;
                }
            }
            break;
        }

        // 6. Lex the dirty region
        lexRegion(newSource, newTypes, newTokenStart, start, newEnd, lastTokenType);

        return new TokenStream(newTypes, newTokenStart);
    }

    private static void lexRegion(String source, byte[] types, int[] tokenStart, int startOffset, int endOffset, int initialLastTokenType) {
        int len = source.length();
        int i = startOffset;
        int lastTokenType = initialLastTokenType;

        while (i < endOffset) {
            char quote = source.charAt(i);

            // Whitespace
            if (Character.isWhitespace(quote)) {
                int start = i;
                while (i < endOffset && Character.isWhitespace(source.charAt(i))) i++;
                for (int k = start; k < i; k++) {
                    types[k] = TokenStream.TK_WHITESPACE;
                    tokenStart[k] = start;
                }
                continue;
            }

            // Block comment /* */
            if (quote == '/' && i + 1 < endOffset && source.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < endOffset) {
                    if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                if (i + 1 >= endOffset && i < endOffset && source.charAt(i) != '/') i = endOffset;
                for (int k = start; k < Math.min(i, endOffset); k++) {
                    if (k < types.length) {
                        types[k] = TokenStream.TK_COMMENT;
                        tokenStart[k] = start;
                    }
                }
                lastTokenType = 0;
                continue;
            }

            // Line comment //
            if (quote == '/' && i + 1 < endOffset && source.charAt(i + 1) == '/') {
                int start = i;
                while (i < endOffset && source.charAt(i) != '\n') i++;
                for (int k = start; k < Math.min(i, endOffset); k++) {
                    types[k] = TokenStream.TK_COMMENT;
                    tokenStart[k] = start;
                }
                lastTokenType = 0;
                continue;
            }

            // String literals ' "
            if (quote == '\'' || quote == '"') {
                int start = i;
                i++;
                while (i < endOffset) {
                    char sc = source.charAt(i);
                    if (sc == '\\') {
                        i += 2; 
                        continue;
                    }
                    if (sc == quote) {
                        i++;
                        break;
                    }
                    if (sc == '\n') break; 
                    i++;
                }
                for (int k = start; k < Math.min(i, endOffset); k++) {
                    if (k < types.length) {
                        types[k] = TokenStream.TK_STRING;
                        tokenStart[k] = start;
                    }
                }
                lastTokenType = 1;
                continue;
            }

            // Template literal `
            if (quote == '`') {
                int start = i;
                i++;
                while (i < endOffset) {
                    char tc = source.charAt(i);
                    if (tc == '\\') {
                        i += 2;
                        continue;
                    }
                    if (tc == '$' && i + 1 < endOffset && source.charAt(i + 1) == '{') {
                        for (int k = start; k < i && k < endOffset; k++) {
                            if (k < types.length) {
                                types[k] = TokenStream.TK_TEMPLATE;
                                tokenStart[k] = start;
                            }
                        }
                        if (i < types.length) {
                            types[i] = TokenStream.TK_PUNCT;
                            tokenStart[i] = i;
                        }
                        if (i + 1 < types.length) {
                            types[i + 1] = TokenStream.TK_PUNCT;
                            tokenStart[i + 1] = i + 1;
                        }
                        i += 2;
                        int exprStart = i;
                        int braceDepth = 1;
                        while (i < endOffset && braceDepth > 0) {
                            char q2 = source.charAt(i);
                            if (q2 == '{') {
                                braceDepth++;
                                i++;
                            } else if (q2 == '}') {
                                braceDepth--;
                                if (braceDepth == 0) {
                                    lexRegion(source, types, tokenStart, exprStart, i, 0);
                                    if (i < types.length) {
                                        types[i] = TokenStream.TK_PUNCT;
                                        tokenStart[i] = i;
                                    }
                                    i++;
                                    break;
                                }
                                i++;
                            } else if (q2 == '\'' || q2 == '"') {
                                char sq = q2;
                                int sStart = i;
                                i++;
                                while (i < endOffset) {
                                    if (source.charAt(i) == '\\') { i += 2; continue; }
                                    if (source.charAt(i) == sq) { i++; break; }
                                    i++;
                                }
                                for (int k = sStart; k < Math.min(i, endOffset); k++) {
                                    if (k < types.length) {
                                        types[k] = TokenStream.TK_STRING;
                                        tokenStart[k] = sStart;
                                    }
                                }
                            } else if (q2 == '`') {
                                i++;
                                while (i < endOffset) {
                                    if (source.charAt(i) == '\\') { i += 2; continue; }
                                    if (source.charAt(i) == '`') { i++; break; }
                                    if (source.charAt(i) == '$' && i + 1 < endOffset && source.charAt(i + 1) == '{') {
                                        i += 2;
                                        int innerDepth = 1;
                                        while (i < endOffset && innerDepth > 0) {
                                            char q3 = source.charAt(i);
                                            if (q3 == '{') { innerDepth++; i++; }
                                            else if (q3 == '}') { innerDepth--; i++; }
                                            else if (q3 == '\'' || q3 == '"' || q3 == '`') {
                                                char innerQuote = q3;
                                                i++;
                                                while (i < endOffset) {
                                                    if (source.charAt(i) == '\\') { i += 2; continue; }
                                                    if (source.charAt(i) == innerQuote) { i++; break; }
                                                    i++;
                                                }
                                            } else {
                                                i++;
                                            }
                                        }
                                    } else {
                                        i++;
                                    }
                                }
                            } else {
                                i++;
                            }
                        }
                        if (braceDepth > 0) {
                            lexRegion(source, types, tokenStart, exprStart, Math.min(i, endOffset), 0);
                        }
                        start = i;
                        continue;
                    }
                    if (tc == '`') {
                        int currentChunkStart = -1;
                        for (int k = start; k <= i && k < endOffset; k++) {
                            if (k < types.length && types[k] == TokenStream.TK_NONE) {
                                types[k] = TokenStream.TK_TEMPLATE;
                                if (currentChunkStart == -1) currentChunkStart = k;
                                tokenStart[k] = currentChunkStart;
                            } else {
                                currentChunkStart = -1;
                            }
                        }
                        i++;
                        start = i;
                        break;
                    }
                    i++;
                }

                if (i >= endOffset && start < endOffset) {
                    int currentChunkStart = -1;
                    for (int k = start; k < Math.min(i, endOffset); k++) {
                        if (k < types.length && types[k] == TokenStream.TK_NONE) {
                            types[k] = TokenStream.TK_TEMPLATE;
                            if (currentChunkStart == -1) currentChunkStart = k;
                            tokenStart[k] = currentChunkStart;
                        } else {
                            currentChunkStart = -1;
                        }
                    }
                }
                lastTokenType = 1;
                continue;
            }

            // Regex literal /
            if (quote == '/' && lastTokenType == 0) {
                if (i + 1 < endOffset && source.charAt(i + 1) != '*' && source.charAt(i + 1) != '/') {
                    int start = i;
                    i++;
                    boolean inCharClass = false;
                    while (i < endOffset) {
                        char rc = source.charAt(i);
                        if (rc == '\\') {
                            i += 2;
                            continue;
                        }
                        if (rc == '[') {
                            inCharClass = true;
                            i++;
                            continue;
                        }
                        if (rc == ']') {
                            inCharClass = false;
                            i++;
                            continue;
                        }
                        if (rc == '/' && !inCharClass) {
                            do i++;
                            while (i < endOffset && Character.isLetter(source.charAt(i)));
                            break;
                        }
                        if (rc == '\n') break;
                        i++;
                    }
                    for (int k = start; k < Math.min(i, endOffset); k++) {
                        if (k < types.length) {
                            types[k] = TokenStream.TK_REGEX;
                            tokenStart[k] = start;
                        }
                    }
                    lastTokenType = 1;
                    continue;
                }
            }

            // Numbers
            if (Character.isDigit(quote)) {
                int start = i;
                while (i < endOffset && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '.')) {
                    i++;
                }
                for (int k = start; k < Math.min(i, endOffset); k++) {
                    if (k < types.length) {
                        types[k] = TokenStream.TK_NUMBER;
                        tokenStart[k] = start;
                    }
                }
                lastTokenType = 1;
                continue;
            }

            // Identifiers and Keywords
            if (Character.isLetter(quote) || quote == '_' || quote == '$') {
                int start = i;
                while (i < endOffset && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_' || source.charAt(i) == '$')) {
                    i++;
                }
                String word = source.substring(start, i);
                byte type = JS_KEYWORDS.contains(word) ? TokenStream.TK_KEYWORD : TokenStream.TK_IDENTIFIER;
                for (int k = start; k < Math.min(i, endOffset); k++) {
                    if (k < types.length) {
                        types[k] = type;
                        tokenStart[k] = start;
                    }
                }
                
                if ("return".equals(word) || "typeof".equals(word) || "instanceof".equals(word)
                        || "in".equals(word) || "new".equals(word) || "delete".equals(word) 
                        || "void".equals(word) || "throw".equals(word) || "yield".equals(word)) {
                    lastTokenType = 0;
                } else {
                    lastTokenType = 1;
                }
                continue;
            }

            // Punctuation and Operators
            if (quote == '?' && i + 1 < endOffset && source.charAt(i + 1) == '.') {
                types[i] = TokenStream.TK_PUNCT;
                tokenStart[i] = i;
                types[i+1] = TokenStream.TK_PUNCT;
                tokenStart[i+1] = i;
                lastTokenType = 0;
                i += 2;
                continue;
            }

            if (quote == '=' && i + 1 < endOffset && source.charAt(i + 1) == '>') {
                types[i] = TokenStream.TK_OPERATOR;
                tokenStart[i] = i;
                types[i+1] = TokenStream.TK_OPERATOR;
                tokenStart[i+1] = i;
                lastTokenType = 0;
                i += 2;
                continue;
            }
            
            types[i] = isPunct(quote) ? TokenStream.TK_PUNCT : TokenStream.TK_OPERATOR;
            tokenStart[i] = i;

            if (quote == ')' || quote == ']') {
                lastTokenType = 1;
            } else {
                lastTokenType = 0;
            }
            i++;
        }
    }

    private static boolean isPunct(char c) {
        return c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']' 
            || c == ',' || c == ';' || c == '.' || c == ':';
    }
}
