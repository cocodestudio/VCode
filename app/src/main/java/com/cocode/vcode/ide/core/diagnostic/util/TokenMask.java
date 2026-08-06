package com.cocode.vcode.ide.core.diagnostic.util;

public class TokenMask {
    public final boolean[] inString;
    public final boolean[] inComment;
    public final boolean[] inRegex;

    private TokenMask(boolean[] inString, boolean[] inComment, boolean[] inRegex) {
        this.inString = inString;
        this.inComment = inComment;
        this.inRegex = inRegex;
    }

    public static TokenMask build(String source, String language) {
        int len = source.length();
        boolean[] inString = new boolean[len];
        boolean[] inComment = new boolean[len];
        boolean[] inRegex = new boolean[len];

        if (len == 0) return new TokenMask(inString, inComment, inRegex);

        boolean isCss = "css".equals(language) || "scss".equals(language);
        boolean isHtml = "html".equals(language);
        boolean isJs = "js".equals(language) || "ts".equals(language);

        int i = 0;
        // track last non-ws token type for regex disambiguation
        // 0=none/operator/keyword, 1=identifier/number/closeParen
        int lastTokenType = 0;

        while (i < len) {
            char quote = source.charAt(i);

            // ── HTML comment <!-- --> ──────────────────────────────────────
            if (isHtml && quote == '<' && i + 3 < len
                    && source.charAt(i + 1) == '!'
                    && source.charAt(i + 2) == '-'
                    && source.charAt(i + 3) == '-') {
                int start = i;
                i += 4;
                while (i + 2 < len) {
                    if (source.charAt(i) == '-' && source.charAt(i + 1) == '-' && source.charAt(i + 2) == '>') {
                        i += 3;
                        break;
                    }
                    i++;
                }
                // if no close found, mark till end
                if (i + 2 >= len && !(i + 2 < len)) {
                    // already at end
                }
                for (int k = start; k < Math.min(i, len); k++) inComment[k] = true;
                lastTokenType = 0;
                continue;
            }

            // ── Block comment /* */ ────────────────────────────────────────
            if ((isJs || isCss || isHtml) && quote == '/' && i + 1 < len && source.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < len) {
                    if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                if (i + 1 >= len && i < len && source.charAt(i) != '/') i = len; // unterminated
                for (int k = start; k < Math.min(i, len); k++) inComment[k] = true;
                lastTokenType = 0;
                continue;
            }

            // ── Line comment // ────────────────────────────────────────────
            if (isJs && quote == '/' && i + 1 < len && source.charAt(i + 1) == '/') {
                int start = i;
                while (i < len && source.charAt(i) != '\n') i++;
                for (int k = start; k < Math.min(i, len); k++) inComment[k] = true;
                lastTokenType = 0;
                continue;
            }

            // ── String literals ' " ────────────────────────────────────────
            if (!isCss && (quote == '\'' || quote == '"')) {
                int start = i;
                i++;
                while (i < len) {
                    char sc = source.charAt(i);
                    if (sc == '\\') {
                        i += 2; // skip escaped char
                        continue;
                    }
                    if (sc == quote) {
                        i++;
                        break;
                    }
                    if (sc == '\n') break; // unterminated line
                    i++;
                }
                for (int k = start; k < Math.min(i, len); k++) inString[k] = true;
                lastTokenType = 1;
                continue;
            }

            // CSS strings
            if (isCss && (quote == '\'' || quote == '"')) {
                int start = i;
                i++;
                while (i < len) {
                    char sc = source.charAt(i);
                    if (sc == '\\') {
                        i += 2;
                        continue;
                    }
                    if (sc == quote) {
                        i++;
                        break;
                    }
                    i++;
                }
                for (int k = start; k < Math.min(i, len); k++) inString[k] = true;
                lastTokenType = 1;
                continue;
            }

            // ── Template literal ` ─────────────────────────────────────────
            if (isJs && quote == '`') {
                int start = i;
                i++;
                int braceDepth = 0;
                while (i < len) {
                    char tc = source.charAt(i);
                    if (tc == '\\') {
                        i += 2;
                        continue;
                    }
                    if (tc == '$' && i + 1 < len && source.charAt(i + 1) == '{') {
                        // mark up to ${ as string, then track expression
                        for (int k = start; k <= i + 1 && k < len; k++) inString[k] = true;
                        i += 2;
                        braceDepth = 1;
                        // scan expression — nested backticks not handled recursively (rare edge)
                        while (i < len && braceDepth > 0) {
                            char q2 = source.charAt(i);
                            if (q2 == '{') braceDepth++;
                            else if (q2 == '}') {
                                braceDepth--;
                                if (braceDepth == 0) {
                                    i++;
                                    break;
                                }
                            } else if (q2 == '\'' || q2 == '"') {
                                // skip inner string
                                i++;
                                while (i < len) {
                                    if (source.charAt(i) == '\\') {
                                        i += 2;
                                        continue;
                                    }
                                    if (source.charAt(i) == q2) {
                                        i++;
                                        break;
                                    }
                                    i++;
                                }
                                continue;
                            }
                            i++;
                        }
                        start = i; // resume template from here
                        continue;
                    }
                    if (tc == '`') {
                        for (int k = start; k <= i && k < len; k++) inString[k] = true;
                        i++;
                        break;
                    }
                    i++;
                }
                if (i >= len) {
                    // unterminated — mark rest
                    for (int k = start; k < len; k++) if (!inString[k]) inString[k] = true;
                }
                lastTokenType = 1;
                continue;
            }

            // ── Regex literal / ────────────────────────────────────────────
            if (isJs && quote == '/' && lastTokenType == 0) {
                // Confirm next char is not * or / (those are comments, handled above)
                if (i + 1 < len && source.charAt(i + 1) != '*' && source.charAt(i + 1) != '/') {
                    int start = i;
                    i++;
                    boolean inCharClass = false;
                    while (i < len) {
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
                            // skip flags
                            do i++;
                            while (i < len && Character.isLetter(source.charAt(i)));
                            break;
                        }
                        if (rc == '\n') break; // unterminated
                        i++;
                    }
                    for (int k = start; k < Math.min(i, len); k++) inRegex[k] = true;
                    lastTokenType = 1;
                    continue;
                }
            }

            // ── Track last token type for regex disambiguation ──────────────
            if (quote == ')' || quote == ']' || Character.isDigit(quote)) {
                lastTokenType = 1;
            } else if (Character.isLetter(quote) || quote == '_' || quote == '$') {
                // peek ahead to check if identifier
                int wstart = i;
                while (i < len && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_' || source.charAt(i) == '$'))
                    i++;
                String word = source.substring(wstart, i);
                // After return/typeof/etc → operator context
                if ("return".equals(word) || "typeof".equals(word) || "instanceof".equals(word)
                        || "in".equals(word) || "of".equals(word) || "new".equals(word)
                        || "delete".equals(word) || "void".equals(word) || "throw".equals(word)) {
                    lastTokenType = 0;
                } else {
                    lastTokenType = 1;
                }
                continue;
            } else if (quote == '=' || quote == '(' || quote == ',' || quote == '[' || quote == '!'
                    || quote == '&' || quote == '|' || quote == '?' || quote == ':' || quote == ';'
                    || quote == '+' || quote == '-' || quote == '*' || quote == '%' || quote == '<'
                    || quote == '>' || quote == '{' || quote == '\n') {
                lastTokenType = 0;
            }

            i++;
        }

        return new TokenMask(inString, inComment, inRegex);
    }

    public boolean isMasked(int offset) {
        if (offset < 0 || offset >= inString.length) return false;
        return inString[offset] || inComment[offset] || inRegex[offset];
    }
}
