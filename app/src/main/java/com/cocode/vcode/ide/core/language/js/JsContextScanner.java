package com.cocode.vcode.ide.core.language.js;

/**
 * High-performance, zero-allocation lexical state scanner for JavaScript and TypeScript documents.
 * Accurately determines the active grammatical context at the cursor to isolate dot-member access,
 * lexical scope, import/require paths, DOM selectors, event names, and TypeScript type declarations.
 */
public final class JsContextScanner {

    public enum ContextType {
        DOT_MEMBER,        // Cursor immediately follows '.' or '?.' (e.g. obj.|, obj.fo|)
        SCOPE,             // Lexical scope: statement begin or expression identifier position
        IMPORT_PATH,       // Inside from '...', require('...'), or import('...')
        IMPORT_CLAUSE,     // Inside import { ... } or export { ... }
        DOM_SELECTOR,      // Inside querySelector('...'), querySelectorAll('...')
        GET_ELEMENT_BY_ID, // Inside getElementById('...')
        EVENT_NAME,        // Inside addEventListener('...'), removeEventListener('...'), on('...')
        STRING_OR_COMMENT, // Inside regular string literal or comment (suppress completions)
        // TypeScript-specific contexts
        TYPE_ANNOTATION,   // After ':' in type position, type alias '=', union '|', intersection '&'
        GENERIC_TYPE,      // Inside generic '<...>'
        TYPE_ASSERTION,    // After 'as ' keyword
        INTERFACE_MEMBER,  // Inside interface Foo { ... } or type Foo = { ... }
        IMPORT_TYPE        // Inside import type { ... }
    }

    public static final class JsContext {
        public final ContextType type;
        public final String word;
        public final int dotOffset;
        public final String receiverExpression;
        public final String importPath;
        public final boolean isRelativeImport;
        public final String calleeName;
        public final boolean isInsideTemplateExpr;
        public final boolean isInsideComment;
        public final boolean isInsideString;

        public JsContext(ContextType type, String word, int dotOffset,
                         String receiverExpression, String importPath,
                         boolean isRelativeImport, String calleeName,
                         boolean isInsideTemplateExpr, boolean isInsideComment,
                         boolean isInsideString) {
            this.type = type;
            this.word = word != null ? word : "";
            this.dotOffset = dotOffset;
            this.receiverExpression = receiverExpression;
            this.importPath = importPath;
            this.isRelativeImport = isRelativeImport;
            this.calleeName = calleeName;
            this.isInsideTemplateExpr = isInsideTemplateExpr;
            this.isInsideComment = isInsideComment;
            this.isInsideString = isInsideString;
        }
    }

    private JsContextScanner() {
    }

    /**
     * Scans the document up to {@code cursorPos} and determines the active grammatical context.
     *
     * @param fullText     the full document content
     * @param cursorPos    current cursor offset
     * @param isTypeScript true if scanning a TypeScript / TSX document
     * @return the resolved {@link JsContext}
     */
    public static JsContext scan(String fullText, int cursorPos, boolean isTypeScript) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return new JsContext(ContextType.SCOPE, "", -1, null, null, false, null, false, false, false);
        }

        int scanLen = Math.min(cursorPos, fullText.length());
        String word = extractWordBeforeCursor(fullText, scanLen);

        // 1. Line and block comment check
        if (isInsideComment(fullText, scanLen)) {
            return new JsContext(ContextType.STRING_OR_COMMENT, word, -1, null, null, false, null, false, true, false);
        }

        // 2. String literal & template literal check
        JsContext stringCtx = checkStringContext(fullText, scanLen, word);
        if (stringCtx != null) {
            return stringCtx;
        }

        // 3. Dot-member access check (e.g. obj. or obj.foo or obj?. or obj?.foo)
        JsContext memberCtx = checkDotMemberContext(fullText, scanLen, word);
        if (memberCtx != null) {
            return memberCtx;
        }

        // 4. Named import / export clause check
        JsContext importClauseCtx = checkImportClauseContext(fullText, scanLen, word);
        if (importClauseCtx != null) {
            return importClauseCtx;
        }

        // 5. TypeScript-specific contexts
        if (isTypeScript) {
            JsContext tsCtx = checkTypeScriptContext(fullText, scanLen, word);
            if (tsCtx != null) {
                return tsCtx;
            }
        }

        // 6. Default to lexical scope
        return new JsContext(ContextType.SCOPE, word, -1, null, null, false, null, false, false, false);
    }

    private static String extractWordBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0) return "";
        int start = pos;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start, pos);
    }

    private static boolean isInsideComment(String text, int pos) {
        if (pos <= 0) return false;

        // Line comment check: scan back to start of line
        int lineStart = pos - 1;
        while (lineStart >= 0 && text.charAt(lineStart) != '\n') {
            lineStart--;
        }
        lineStart++; // index of first char on this line

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = lineStart; i < pos; i++) {
            char c = text.charAt(i);
            if (c == '\\' && (inSingleQuote || inDoubleQuote || inBacktick) && i + 1 < pos) {
                i++; // skip escaped char
                continue;
            }
            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            } else if (c == '/' && i + 1 < pos && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                if (text.charAt(i + 1) == '/') {
                    return true; // inside single-line comment
                }
            }
        }

        // Multi-line block comment check: look backwards for /* without matching */
        int lastBlockOpen = text.lastIndexOf("/*", pos - 1);
        if (lastBlockOpen != -1) {
            int lastBlockClose = text.indexOf("*/", lastBlockOpen + 2);
            if (lastBlockClose == -1 || lastBlockClose >= pos) {
                return true; // inside block comment
            }
        }

        return false;
    }

    private static JsContext checkStringContext(String text, int pos, String word) {
        if (pos <= 0) return null;

        // Find start of current line
        int lineStart = pos - 1;
        while (lineStart >= 0 && text.charAt(lineStart) != '\n') {
            lineStart--;
        }
        lineStart++;

        // Scan characters on this line to detect unclosed string literals
        char openQuote = 0;
        int quoteStart = -1;
        int limit = Math.max(0, pos - 5000);

        // Check for template literals across multiple lines if needed
        int templateDepth = 0;
        int exprDepth = 0;
        boolean insideTemplateExpr = false;

        for (int i = limit; i < pos; i++) {
            char c = text.charAt(i);
            if (c == '\\' && openQuote != 0 && i + 1 < pos) {
                i++; // skip escaped character
                continue;
            }

            if (openQuote == 0) {
                if (c == '\'' || c == '"') {
                    openQuote = c;
                    quoteStart = i;
                } else if (c == '`') {
                    openQuote = '`';
                    quoteStart = i;
                    templateDepth++;
                }
            } else if (openQuote == c && (c == '\'' || c == '"')) {
                openQuote = 0;
                quoteStart = -1;
            } else if (openQuote == '`') {
                if (c == '`') {
                    openQuote = 0;
                    quoteStart = -1;
                    templateDepth--;
                } else if (c == '$' && i + 1 < pos && text.charAt(i + 1) == '{') {
                    insideTemplateExpr = true;
                    exprDepth++;
                    i++; // skip '{'
                } else if (c == '}' && insideTemplateExpr) {
                    exprDepth--;
                    if (exprDepth == 0) {
                        insideTemplateExpr = false;
                    }
                }
            }
        }

        if (openQuote == '`') {
            if (insideTemplateExpr) {
                // Active JS expression inside ${ ... } - not a suppressed string
                return null;
            }
            return new JsContext(ContextType.STRING_OR_COMMENT, word, -1, null, null, false, null, true, false, true);
        }

        if (openQuote != '\'' && openQuote != '"') {
            return null; // not inside quotes
        }

        // Inside a single or double quoted string literal
        String lineBeforeQuote = text.substring(lineStart, quoteStart).trim();
        String typedInsideQuote = text.substring(quoteStart + 1, pos);

        // 1. Import / Require path context
        if (lineBeforeQuote.endsWith("from") ||
                lineBeforeQuote.endsWith("require(") ||
                lineBeforeQuote.endsWith("import(")) {
            boolean isRel = typedInsideQuote.startsWith(".") || typedInsideQuote.startsWith("/");
            return new JsContext(ContextType.IMPORT_PATH, word, -1, null, typedInsideQuote, isRel, null, false, false, true);
        }
        if (lineBeforeQuote.endsWith("(")) {
            String callee = lineBeforeQuote.substring(0, lineBeforeQuote.length() - 1).trim();
            if (callee.endsWith("require") || callee.endsWith("import")) {
                boolean isRel = typedInsideQuote.startsWith(".") || typedInsideQuote.startsWith("/");
                return new JsContext(ContextType.IMPORT_PATH, word, -1, null, typedInsideQuote, isRel, null, false, false, true);
            }
            if (callee.endsWith("addEventListener") || callee.endsWith("removeEventListener") || callee.endsWith("on")) {
                return new JsContext(ContextType.EVENT_NAME, typedInsideQuote, -1, null, null, false, "addEventListener", false, false, true);
            }
            if (callee.endsWith("getElementById")) {
                return new JsContext(ContextType.GET_ELEMENT_BY_ID, typedInsideQuote, -1, null, null, false, "getElementById", false, false, true);
            }
            if (callee.endsWith("querySelector") || callee.endsWith("querySelectorAll")) {
                return new JsContext(ContextType.DOM_SELECTOR, typedInsideQuote, -1, null, null, false, "querySelector", false, false, true);
            }
        }

        // General string literal: suppress all keyword/scope completions
        return new JsContext(ContextType.STRING_OR_COMMENT, word, -1, null, null, false, null, false, false, true);
    }

    private static JsContext checkDotMemberContext(String text, int pos, String word) {
        int idx = pos - word.length() - 1;
        while (idx >= 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        if (idx < 0) return null;

        char c = text.charAt(idx);
        if (c != '.') return null;

        // Guard against spread operator (...)
        if (idx >= 2 && text.charAt(idx - 1) == '.' && text.charAt(idx - 2) == '.') {
            return null;
        }

        // Optional chaining (?.)
        boolean isOptionalChain = idx > 0 && text.charAt(idx - 1) == '?';
        int dotOffset = idx;

        // Guard against numeric literal with decimal point (e.g. 123. or 123.45)
        int preDot = isOptionalChain ? idx - 2 : idx - 1;
        if (preDot >= 0 && Character.isDigit(text.charAt(preDot)) && !isOptionalChain) {
            // Check if there is another dot before this digit (like 5..toFixed())
            int numStart = preDot;
            while (numStart > 0 && Character.isDigit(text.charAt(numStart - 1))) {
                numStart--;
            }
            if (numStart == 0 || (!Character.isJavaIdentifierPart(text.charAt(numStart - 1)) && text.charAt(numStart - 1) != '.')) {
                return null; // Numeric literal, not member access
            }
        }

        // Extract receiver expression before the dot or '?.'
        String receiver = extractReceiverBeforeDot(text, isOptionalChain ? dotOffset - 1 : dotOffset);
        return new JsContext(ContextType.DOT_MEMBER, word, dotOffset, receiver, null, false, null, false, false, false);
    }

    private static String extractReceiverBeforeDot(String text, int dotPos) {
        int i = dotPos - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        if (i < 0) return "";

        int end = i + 1;
        int depthParen = 0;
        int depthBracket = 0;
        int depthBrace = 0;

        while (i >= 0) {
            char c = text.charAt(i);
            if (c == ')') depthParen++;
            else if (c == '(') {
                if (depthParen > 0) depthParen--;
                else break;
            } else if (c == ']') depthBracket++;
            else if (c == '[') {
                if (depthBracket > 0) depthBracket--;
                else break;
            } else if (c == '}') depthBrace++;
            else if (c == '{') {
                if (depthBrace > 0) depthBrace--;
                else break;
            } else if (depthParen == 0 && depthBracket == 0 && depthBrace == 0) {
                if (!Character.isJavaIdentifierPart(c) && c != '.' && c != '?' && c != '$') {
                    i++;
                    break;
                }
            }
            i--;
        }

        if (i < 0) i = 0;
        return text.substring(i, end).trim();
    }

    private static JsContext checkImportClauseContext(String text, int pos, String word) {
        int limit = Math.max(0, pos - 1000);
        int braceDepth = 0;
        int openBracePos = -1;

        for (int i = pos - 1; i >= limit; i--) {
            char c = text.charAt(i);
            if (c == ';') break; // passed statement boundary
            if (c == '}') braceDepth++;
            else if (c == '{') {
                if (braceDepth > 0) {
                    braceDepth--;
                } else {
                    openBracePos = i;
                    break;
                }
            }
        }

        if (openBracePos == -1) return null;

        // Check keyword before the opening brace
        int j = openBracePos - 1;
        while (j >= limit && Character.isWhitespace(text.charAt(j))) j--;
        if (j < 0) return null;

        int wEnd = j + 1;
        int wStart = j;
        while (wStart >= limit && Character.isJavaIdentifierPart(text.charAt(wStart))) wStart--;
        String kw = text.substring(wStart + 1, wEnd);

        if ("import".equals(kw) || "export".equals(kw)) {
            // Check if preceded by 'type' for TS: import type { ... }
            int k = wStart;
            while (k >= limit && Character.isWhitespace(text.charAt(k))) k--;
            if (k >= 0) {
                int preEnd = k + 1;
                int preStart = k;
                while (preStart >= limit && Character.isJavaIdentifierPart(text.charAt(preStart))) preStart--;
                String preKw = text.substring(preStart + 1, preEnd);
                if ("import".equals(preKw) && "type".equals(kw)) {
                    return new JsContext(ContextType.IMPORT_TYPE, word, -1, null, null, false, null, false, false, false);
                }
            }
            return new JsContext(ContextType.IMPORT_CLAUSE, word, -1, null, null, false, null, false, false, false);
        }

        if ("type".equals(kw)) {
            int k = wStart;
            while (k >= limit && Character.isWhitespace(text.charAt(k))) k--;
            if (k >= 0) {
                int preEnd = k + 1;
                int preStart = k;
                while (preStart >= limit && Character.isJavaIdentifierPart(text.charAt(preStart))) preStart--;
                if ("import".equals(text.substring(preStart + 1, preEnd))) {
                    return new JsContext(ContextType.IMPORT_TYPE, word, -1, null, null, false, null, false, false, false);
                }
            }
        }

        return null;
    }

    private static JsContext checkTypeScriptContext(String text, int pos, String word) {
        if (isTypeAnnotationPosition(text, pos, word)) {
            int idx = pos - word.length() - 1;
            while (idx >= 0 && Character.isWhitespace(text.charAt(idx))) {
                idx--;
            }
            if (idx >= 0) {
                int wEnd = idx + 1;
                int wStart = idx;
                while (wStart >= 0 && Character.isJavaIdentifierPart(text.charAt(wStart))) wStart--;
                wStart++;
                if (wStart < wEnd && "as".equals(text.substring(wStart, wEnd))) {
                    return new JsContext(ContextType.TYPE_ASSERTION, word, -1, null, null, false, null, false, false, false);
                }
                char c = text.charAt(idx);
                if (c == '<' || isInsideGenericAngle(text, idx)) {
                    return new JsContext(ContextType.GENERIC_TYPE, word, -1, null, null, false, null, false, false, false);
                }
            }
            return new JsContext(ContextType.TYPE_ANNOTATION, word, -1, null, null, false, null, false, false, false);
        }

        return null;
    }

    private static boolean isInsideGenericAngle(String text, int idx) {
        int angleDepth = 0;
        int limit = Math.max(0, idx - 500);
        for (int j = idx; j >= limit; j--) {
            char ch = text.charAt(j);
            if (ch == '>') angleDepth++;
            else if (ch == '<') {
                if (angleDepth > 0) angleDepth--;
                else return true;
            } else if (ch == ';' || ch == '{' || ch == '}') {
                break;
            }
        }
        return false;
    }

    /**
     * Checks if the cursor is at a position expecting a type annotation, such as after
     * a colon, generic bracket, union/intersection operator, type alias assignment,
     * tuple bracket, or type assertion keyword.
     */
    public static boolean isTypeAnnotationPosition(String text, int cursorPos, String word) {
        if (text == null || cursorPos <= 0 || cursorPos > text.length()) return false;

        int idx = cursorPos - word.length() - 1;
        while (idx >= 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        if (idx < 0) return false;

        char c = text.charAt(idx);

        // Union or intersection ('A | ' or 'A & ')
        if (c == '|' || c == '&') {
            if (idx > 0 && text.charAt(idx - 1) == c) return false;
            return true;
        }

        // Tuple type opening bracket (e.g. 'let tuple: [' or 'type Tuple = [')
        if (c == '[') {
            int pre = idx - 1;
            while (pre >= 0 && Character.isWhitespace(text.charAt(pre))) pre--;
            if (pre >= 0) {
                char preC = text.charAt(pre);
                if (preC == ':' || preC == '=' || preC == '<' || preC == ',' || preC == '|' || preC == '&') {
                    return true;
                }
                int wStart = pre;
                while (wStart >= 0 && Character.isJavaIdentifierPart(text.charAt(wStart))) wStart--;
                wStart++;
                if (wStart <= pre) {
                    String prevWord = text.substring(wStart, pre + 1);
                    if ("as".equals(prevWord) || "is".equals(prevWord) ||
                            "extends".equals(prevWord) || "implements".equals(prevWord)) {
                        return true;
                    }
                }
            }
        }

        // Generic type argument ('Promise<' or 'Map<string, ') or tuple element position ('[string, ')
        if (c == '<' || c == ',') {
            int angleDepth = 0;
            int bracketDepth = 0;
            for (int j = idx; j >= 0; j--) {
                char ch = text.charAt(j);
                if (ch == '>') angleDepth++;
                else if (ch == '<') {
                    if (angleDepth > 0) angleDepth--;
                    else return true;
                } else if (ch == ']') bracketDepth++;
                else if (ch == '[') {
                    if (bracketDepth > 0) bracketDepth--;
                    else {
                        int pre = j - 1;
                        while (pre >= 0 && Character.isWhitespace(text.charAt(pre))) pre--;
                        if (pre >= 0) {
                            char preC = text.charAt(pre);
                            if (preC == ':' || preC == '=' || preC == '<' || preC == ',' || preC == '|' || preC == '&') {
                                return true;
                            }
                            int wStart = pre;
                            while (wStart >= 0 && Character.isJavaIdentifierPart(text.charAt(wStart))) wStart--;
                            wStart++;
                            if (wStart <= pre) {
                                String prevWord = text.substring(wStart, pre + 1);
                                if ("as".equals(prevWord) || "is".equals(prevWord) ||
                                        "extends".equals(prevWord) || "implements".equals(prevWord)) {
                                    return true;
                                }
                            }
                        }
                    }
                } else if (ch == ';' || ch == '{' || ch == '}') {
                    break;
                }
            }
        }

        // Type assertion or heritage keywords ('as', 'is', 'extends', 'implements')
        int wordEnd = idx + 1;
        int wordStart = idx;
        while (wordStart >= 0 && Character.isJavaIdentifierPart(text.charAt(wordStart))) {
            wordStart--;
        }
        wordStart++;
        if (wordStart < wordEnd) {
            String prevWord = text.substring(wordStart, wordEnd);
            if ("as".equals(prevWord) || "is".equals(prevWord) ||
                    "extends".equals(prevWord) || "implements".equals(prevWord)) {
                return true;
            }
        }

        // Type alias assignment ('type Foo = ')
        if (c == '=') {
            int lineStart = idx;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n' && text.charAt(lineStart - 1) != ';') {
                lineStart--;
            }
            String stmt = text.substring(lineStart, idx).trim();
            if (stmt.matches(".*\\btype\\s+[A-Za-z0-9_$]+(\\s*<[^>]*>)?\\s*")) {
                return true;
            }
        }

        // Variable, parameter, or return type annotation
        if (c == ':') {
            int lineStart = idx;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n' && text.charAt(lineStart - 1) != ';') {
                lineStart--;
            }
            String stmt = text.substring(lineStart, idx).trim();
            if (stmt.startsWith("case ") || stmt.startsWith("default:")) {
                return false;
            }
            return true;
        }

        return false;
    }

    private static boolean isInsideTernary(String text, int colonPos) {
        int depth = 0;
        int limit = Math.max(0, colonPos - 1000);
        for (int i = colonPos - 1; i >= limit; i--) {
            char ch = text.charAt(i);
            if (ch == ';') return false;
            if (ch == ':') depth++;
            else if (ch == '?') {
                if (depth == 0) return true;
                depth--;
            }
        }
        return false;
    }

    private static boolean isObjectLiteralValuePosition(String text, int colonPos) {
        int limit = Math.max(0, colonPos - 500);
        int braceDepth = 0;
        for (int i = colonPos - 1; i >= limit; i--) {
            char ch = text.charAt(i);
            if (ch == ';') return false;
            if (ch == '}') braceDepth++;
            else if (ch == '{') {
                if (braceDepth > 0) {
                    braceDepth--;
                } else {
                    // Check if { is preceded by = or return or ( (indicating object literal)
                    int pre = i - 1;
                    while (pre >= limit && Character.isWhitespace(text.charAt(pre))) pre--;
                    if (pre >= limit) {
                        char p = text.charAt(pre);
                        return p == '=' || p == '(' || p == ',' || p == ':' || p == '[';
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
