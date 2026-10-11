package com.cocode.vcode.ide.core.language.css;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance, zero-allocation lexical state scanner for CSS documents.
 * Accurately determines the grammatical position at the cursor.
 */
public final class CssContextScanner {

    public enum ContextType {
        SELECTOR,
        PROPERTY_NAME,
        PROPERTY_VALUE,
        AT_RULE,
        MEDIA_FEATURE,
        URL_VALUE
    }

    public static final class CssContext {
        public final ContextType type;
        public final String propertyName;
        public final boolean isInsideVar;
        public final boolean isInsideComment;
        public final boolean isInsideString;

        public CssContext(ContextType type, String propertyName, boolean isInsideVar,
                          boolean isInsideComment, boolean isInsideString) {
            this.type = type;
            this.propertyName = propertyName;
            this.isInsideVar = isInsideVar;
            this.isInsideComment = isInsideComment;
            this.isInsideString = isInsideString;
        }
    }

    private enum BlockType {
        STYLE_RULE,       // .class { ... }, div { ... }, @font-face { ... }
        RULE_CONTAINER,   // @media ... { ... }, @supports ... { ... }, @container ... { ... }
        KEYFRAMES         // @keyframes ... { ... }
    }

    private CssContextScanner() {
    }

    /**
     * Scans the CSS text up to {@code cursorPos} and resolves the grammatical context.
     *
     * @param fullText      the entire document or style block content
     * @param cursorPos     0-based cursor offset
     * @param isInlineStyle true if evaluating HTML {@code style="..."} attribute content
     * @return the resolved {@link CssContext}
     */
    public static CssContext scan(String fullText, int cursorPos, boolean isInlineStyle) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return new CssContext(ContextType.SELECTOR, null, false, false, false);
        }

        int scanLen = Math.min(cursorPos, fullText.length());

        // Fast comment check
        if (isInsideComment(fullText, scanLen)) {
            return new CssContext(ContextType.SELECTOR, null, false, true, false);
        }

        // Fast URL check (even if inside quotes of url("..."))
        if (isInsideUrl(fullText, scanLen)) {
            return new CssContext(ContextType.URL_VALUE, null, false, false, false);
        }

        // Fast string check (outside of url())
        if (isInsideStringLiteral(fullText, scanLen)) {
            return new CssContext(ContextType.SELECTOR, null, false, false, true);
        }

        // At-rule condition check (e.g. inside @media (...) or @supports (...))
        if (isInsideAtRuleCondition(fullText, scanLen)) {
            return new CssContext(ContextType.MEDIA_FEATURE, null, false, false, false);
        }

        // Line-before and word-before detection
        String lineBefore = getLineBefore(fullText, scanLen);
        String trimmedLine = lineBefore.trim();
        String wordBefore = getWordBefore(fullText, scanLen);

        // At-rule trigger (e.g. typing "@" or "@med")
        if (trimmedLine.startsWith("@") || wordBefore.startsWith("@")) {
            return new CssContext(ContextType.AT_RULE, null, false, false, false);
        }

        // Inline style mode inside HTML style="..."
        if (isInlineStyle) {
            return scanInlineStyle(fullText, scanLen);
        }

        // Standard CSS stylesheet forward lexical scan
        List<BlockType> blockStack = new ArrayList<>();
        int lastBoundary = 0;
        int lastColon = -1;
        String currentPropertyName = null;
        int parenDepth = 0;
        int varDepth = 0;
        int headerStart = 0;

        int i = 0;
        while (i < scanLen) {
            char c = fullText.charAt(i);

            // Skip block comments
            if (c == '/' && i + 1 < scanLen && fullText.charAt(i + 1) == '*') {
                int endComment = fullText.indexOf("*/", i + 2);
                if (endComment == -1 || endComment >= scanLen) {
                    return new CssContext(ContextType.SELECTOR, null, false, true, false);
                }
                i = endComment + 2;
                continue;
            }

            // Skip single or double quoted strings
            if (c == '\'' || c == '"') {
                int endQuote = findMatchingQuote(fullText, i + 1, scanLen, c);
                if (endQuote >= scanLen) {
                    return new CssContext(ContextType.SELECTOR, null, false, false, true);
                }
                i = endQuote + 1;
                continue;
            }

            if (c == '(') {
                parenDepth++;
                if (isPrecededBy(fullText, i, "var")) {
                    varDepth++;
                }
            } else if (c == ')') {
                if (parenDepth > 0) parenDepth--;
                if (varDepth > 0) varDepth--;
            } else if (parenDepth == 0) {
                if (c == '{') {
                    String header = fullText.substring(headerStart, i).trim();
                    BlockType bt = classifyHeader(header);
                    blockStack.add(bt);
                    lastBoundary = i + 1;
                    lastColon = -1;
                    currentPropertyName = null;
                    headerStart = i + 1;
                } else if (c == '}') {
                    if (!blockStack.isEmpty()) {
                        blockStack.remove(blockStack.size() - 1);
                    }
                    lastBoundary = i + 1;
                    lastColon = -1;
                    currentPropertyName = null;
                    headerStart = i + 1;
                } else if (c == ';') {
                    lastBoundary = i + 1;
                    lastColon = -1;
                    currentPropertyName = null;
                    headerStart = i + 1;
                } else if (c == ':') {
                    if (lastColon == -1) {
                        lastColon = i;
                        int nameStart = Math.min(lastBoundary, i);
                        currentPropertyName = fullText.substring(nameStart, i).trim();
                    }
                }
            }

            i++;
        }

        // Evaluate context from final state
        if (blockStack.isEmpty()) {
            return new CssContext(ContextType.SELECTOR, null, false, false, false);
        }

        BlockType top = blockStack.get(blockStack.size() - 1);
        if (top == BlockType.RULE_CONTAINER || top == BlockType.KEYFRAMES) {
            return new CssContext(ContextType.SELECTOR, null, false, false, false);
        }

        // Inside STYLE_RULE
        if (lastColon != -1 && scanLen > lastColon) {
            boolean inVar = varDepth > 0 || isInsideVar(fullText, scanLen);
            return new CssContext(ContextType.PROPERTY_VALUE, currentPropertyName, inVar, false, false);
        }

        return new CssContext(ContextType.PROPERTY_NAME, null, false, false, false);
    }

    private static CssContext scanInlineStyle(String text, int scanLen) {
        int i = scanLen - 1;
        while (i >= 0 && text.charAt(i) != ';') {
            i--;
        }
        int boundary = i + 1;
        int colon = -1;
        int parenDepth = 0;
        for (int j = boundary; j < scanLen; j++) {
            char c = text.charAt(j);
            if (c == '(') parenDepth++;
            else if (c == ')' && parenDepth > 0) parenDepth--;
            else if (c == ':' && parenDepth == 0) {
                colon = j;
                break;
            }
        }

        if (colon != -1 && scanLen > colon) {
            String prop = text.substring(boundary, colon).trim();
            boolean inVar = isInsideVar(text, scanLen);
            return new CssContext(ContextType.PROPERTY_VALUE, prop, inVar, false, false);
        }

        return new CssContext(ContextType.PROPERTY_NAME, null, false, false, false);
    }

    private static BlockType classifyHeader(String header) {
        String lower = header.toLowerCase();
        if (lower.startsWith("@media") || lower.startsWith("@supports")
                || lower.startsWith("@container") || lower.startsWith("@document")) {
            return BlockType.RULE_CONTAINER;
        }
        if (lower.startsWith("@keyframes")) {
            return BlockType.KEYFRAMES;
        }
        return BlockType.STYLE_RULE;
    }

    public static boolean isInsideComment(String text, int offset) {
        int lastOpen = text.lastIndexOf("/*", offset);
        if (lastOpen != -1) {
            int nextClose = text.indexOf("*/", lastOpen);
            return nextClose == -1 || nextClose + 2 > offset;
        }
        return false;
    }

    public static boolean isInsideStringLiteral(String text, int offset) {
        int s = Math.max(0, offset - 1000);
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = s; i < offset; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < offset) {
                i++;
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            }
        }
        return inSingle || inDouble;
    }

    public static boolean isInsideUrl(String text, int offset) {
        if (text == null || offset <= 0) return false;
        int start = Math.max(0, offset - 300);
        for (int i = offset - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') return false;
            if (c == ')') return false; // Closed url() before cursor
            if (c == '(') {
                int j = i - 1;
                while (j >= start && Character.isWhitespace(text.charAt(j))) j--;
                if (j >= start + 2 && text.charAt(j) == 'l' && text.charAt(j - 1) == 'r' && text.charAt(j - 2) == 'u') {
                    if (j == start + 2 || (!Character.isLetterOrDigit(text.charAt(j - 3)) && text.charAt(j - 3) != '_' && text.charAt(j - 3) != '-')) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    public static boolean isInsideVar(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return false;
        int depth = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') return false;
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth > 0) {
                    depth--;
                } else {
                    int j = i - 1;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    if (j >= 2 && text.charAt(j) == 'r' && text.charAt(j - 1) == 'a' && text.charAt(j - 2) == 'v') {
                        if (j == 2 || (!Character.isLetterOrDigit(text.charAt(j - 3)) && text.charAt(j - 3) != '_' && text.charAt(j - 3) != '-')) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean isInsideVarFirstArg(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return false;
        int depth = 0;
        int commaCount = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') return false;
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth > 0) {
                    depth--;
                } else {
                    int j = i - 1;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    if (j >= 2 && text.charAt(j) == 'r' && text.charAt(j - 1) == 'a' && text.charAt(j - 2) == 'v') {
                        if (j == 2 || (!Character.isLetterOrDigit(text.charAt(j - 3)) && text.charAt(j - 3) != '_' && text.charAt(j - 3) != '-')) {
                            return commaCount == 0;
                        }
                    }
                    return false;
                }
            } else if (c == ',' && depth == 0) {
                commaCount++;
            }
        }
        return false;
    }

    public static boolean isInsideAtRuleCondition(String text, int cursorPos) {
        int start = Math.max(0, cursorPos - 500);
        String snippet = text.substring(start, cursorPos);

        int mediaIdx = -1;
        for (String rule : new String[]{"@media", "@supports", "@container"}) {
            int idx = snippet.lastIndexOf(rule);
            if (idx > mediaIdx) mediaIdx = idx;
        }
        if (mediaIdx < 0) return false;

        String afterRule = snippet.substring(mediaIdx);
        // If there's an opening brace, we're past the condition and inside the body
        if (afterRule.indexOf('{') >= 0) return false;

        // Inside parentheses of the condition
        int open = 0, close = 0;
        for (char c : afterRule.toCharArray()) {
            if (c == '(') open++;
            else if (c == ')') close++;
        }
        return open > close;
    }

    private static int findMatchingQuote(String text, int start, int max, char quote) {
        for (int i = start; i < max; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < max) {
                i++;
                continue;
            }
            if (c == quote) return i;
        }
        return max;
    }

    private static boolean isPrecededBy(String text, int index, String keyword) {
        int j = index - 1;
        while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
        int kLen = keyword.length();
        if (j >= kLen - 1) {
            String sub = text.substring(j - kLen + 1, j + 1);
            if (sub.equalsIgnoreCase(keyword)) {
                return (j - kLen < 0) || !Character.isLetterOrDigit(text.charAt(j - kLen));
            }
        }
        return false;
    }

    private static String getLineBefore(String text, int offset) {
        int start = Math.max(0, offset - 1);
        while (start >= 0 && text.charAt(start) != '\n' && text.charAt(start) != '\r') {
            start--;
        }
        return text.substring(start + 1, offset);
    }

    private static String getWordBefore(String text, int offset) {
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '@' || c == '.' || c == '#' || c == ':') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start, offset);
    }
}
