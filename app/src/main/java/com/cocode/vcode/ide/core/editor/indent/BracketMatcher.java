package com.cocode.vcode.ide.core.editor.indent;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import java.util.List;

/**
 * Utility for matching paired brackets, parentheses, and braces.
 * Supports rainbow brackets, nesting depth calculation, mismatch detection, and scope matching.
 */
public class BracketMatcher {

    private static final String OPEN_BRACKETS = "({[";
    private static final String CLOSE_BRACKETS = ")}]";
    private static final int MAX_SCAN_DISTANCE = 5000;
    private int cachedCursor = -1;
    private int cachedLength = -1;
    private MatchResult cachedMatch = null;
    private boolean hasCache = false;

    public static void applyRainbowBrackets(List<HighlightToken> tokens, String text, int[] colors, int initialDepth) {
        applyRainbowBrackets(tokens, text, colors, initialDepth, 0, 0, null);
    }

    public static void applyRainbowBrackets(List<HighlightToken> tokens, String text, int[] colors, int initialDepth, int startState) {
        applyRainbowBrackets(tokens, text, colors, initialDepth, startState, 0, null);
    }

    public static void applyRainbowBrackets(List<HighlightToken> tokens, String text, int[] colors, int initialDepth, int startState, int lineIndex) {
        applyRainbowBrackets(tokens, text, colors, initialDepth, startState, lineIndex, null);
    }

    public static void applyRainbowBrackets(List<HighlightToken> tokens, String text, int[] colors, int initialDepth, int startState, int lineIndex, FileType fileType) {
        if (text == null || colors == null || colors.length == 0 || tokens == null) return;

        boolean[] mask = computeStringCommentMask(text, 0, text.length(), startState, fileType);
        int depth = initialDepth;

        for (int i = 0; i < text.length(); i++) {
            if (mask[i]) continue;

            char c = text.charAt(i);
            boolean isOpen = c == '(' || c == '{' || c == '[';
            boolean isClose = c == ')' || c == '}' || c == ']';
            if (!isOpen && !isClose) continue;

            if (isOpen) {
                int colorIdx = Math.abs(depth) % colors.length;
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colors[colorIdx], false));
                depth++;
            } else {
                depth = Math.max(0, depth - 1);
                int colorIdx = Math.abs(depth) % colors.length;
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colors[colorIdx], false));
            }
        }
    }

    public static int computeBracketDepth(CharSequence text, int initialDepth) {
        return computeBracketDepth(text, initialDepth, 0, null);
    }

    public static int computeBracketDepth(CharSequence text, int initialDepth, int startState) {
        return computeBracketDepth(text, initialDepth, startState, null);
    }

    public static int computeBracketDepth(CharSequence text, int initialDepth, int startState, FileType fileType) {
        if (text == null) return initialDepth;
        boolean[] mask = computeStringCommentMask(text, 0, text.length(), startState, fileType);
        int depth = initialDepth;
        for (int i = 0; i < text.length(); i++) {
            if (mask[i]) continue;
            char c = text.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            else if (c == ')' || c == '}' || c == ']') depth = Math.max(0, depth - 1);
        }
        return depth;
    }

    public static java.util.List<Problem> findMismatches(java.io.File file, String text) {
        java.util.List<Problem> problems = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return problems;

        FileType fileType = (file != null) ? FileType.fromFile(file) : null;
        boolean[] mask = computeStringCommentMask(text, 0, text.length(), 0, fileType);

        java.util.List<int[]> stack = new java.util.ArrayList<>();
        // int[]: [type (1='(', 2='[', 3='{'), line, col]

        int line = 1;
        int col = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!mask[i]) {
                if (c == '(') {
                    stack.add(new int[]{1, line, col});
                } else if (c == '[') {
                    stack.add(new int[]{2, line, col});
                } else if (c == '{') {
                    stack.add(new int[]{3, line, col});
                } else if (c == ')') {
                    handleClosing(file, line, col, 1, "parenthesis", ')', '(', stack, problems);
                } else if (c == ']') {
                    handleClosing(file, line, col, 2, "bracket", ']', '[', stack, problems);
                } else if (c == '}') {
                    handleClosing(file, line, col, 3, "brace", '}', '{', stack, problems);
                }
            }

            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        while (!stack.isEmpty()) {
            int[] pos = stack.remove(stack.size() - 1);
            String name = (pos[0] == 1) ? "parenthesis '('" : (pos[0] == 2) ? "bracket '['" : "brace '{'";
            problems.add(new Problem(file, pos[1], pos[2], 1, "Unclosed " + name, Problem.Severity.ERROR));
        }

        return problems;
    }

    private static void handleClosing(java.io.File file, int line, int col, int type, String name, char closeChar, char openChar,
                                      java.util.List<int[]> stack, java.util.List<Problem> problems) {
        if (stack.isEmpty()) {
            problems.add(new Problem(file, line, col, 1, "Unmatched closing " + name + " '" + closeChar + "'", Problem.Severity.ERROR));
            return;
        }

        int topIdx = stack.size() - 1;
        int[] top = stack.get(topIdx);
        if (top[0] == type) {
            stack.remove(topIdx);
            return;
        }

        // Look deeper in stack to see if the matching open bracket exists
        int matchIdx = -1;
        for (int k = topIdx - 1; k >= 0; k--) {
            if (stack.get(k)[0] == type) {
                matchIdx = k;
                break;
            }
        }

        if (matchIdx >= 0) {
            // Unclosed brackets exist between match and current closing
            while (stack.size() - 1 > matchIdx) {
                int[] unclosed = stack.remove(stack.size() - 1);
                String uName = (unclosed[0] == 1) ? "parenthesis '('" : (unclosed[0] == 2) ? "bracket '['" : "brace '{'";
                problems.add(new Problem(file, unclosed[1], unclosed[2], 1, "Unclosed " + uName, Problem.Severity.ERROR));
            }
            // Pop the matching open bracket
            stack.remove(matchIdx);
        } else {
            // Mismatched closing character
            String expectedClose = (top[0] == 1) ? "')'" : (top[0] == 2) ? "']'" : "'}'";
            problems.add(new Problem(file, line, col, 1,
                    "Mismatched closing " + name + " '" + closeChar + "': expected " + expectedClose,
                    Problem.Severity.ERROR));
        }
    }

    private static boolean isInStringOrComment(CharSequence text, int pos) {
        boolean inSingle = false, inDouble = false, inTemplate = false;
        boolean inLineComment = false, inBlockComment = false;
        int templateBraceDepth = 0;

        for (int i = 0; i < pos && i < text.length(); i++) {
            char c = text.charAt(i);
            char n = i < text.length() - 1 ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && n == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingle && !inDouble && (!inTemplate || templateBraceDepth > 0)) {
                if (c == '/' && n == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && n == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            if (c == '\\') {
                i++;
                continue;
            } // skip escaped chars
            if (c == '\'' && !inDouble && (!inTemplate || templateBraceDepth > 0)) inSingle = !inSingle;
            else if (c == '"' && !inSingle && (!inTemplate || templateBraceDepth > 0)) inDouble = !inDouble;
            else if (!inSingle && !inDouble) {
                if (!inTemplate) {
                    if (c == '`') inTemplate = true;
                } else {
                    if (templateBraceDepth == 0) {
                        if (c == '$' && n == '{') {
                            templateBraceDepth = 1;
                            i++;
                            continue;
                        } else if (c == '`') {
                            inTemplate = false;
                        }
                    } else {
                        if (c == '{') templateBraceDepth++;
                        else if (c == '}') templateBraceDepth--;
                    }
                }
            }
        }
        return inSingle || inDouble || inLineComment || inBlockComment || (inTemplate && templateBraceDepth == 0);
    }

    private static boolean[] computeStringCommentMask(CharSequence text, int from, int to) {
        return computeStringCommentMask(text, from, to, 0, null);
    }

    private static boolean[] computeStringCommentMask(CharSequence text, int from, int to, int startState) {
        return computeStringCommentMask(text, from, to, startState, null);
    }

    public static boolean[] computeStringCommentMask(CharSequence text, int from, int to, int startState, FileType fileType) {
        if (fileType == FileType.CSS) {
            return computeCssCommentMask(text, from, to, startState);
        } else if (fileType == FileType.HTML) {
            return computeHtmlCommentMask(text, from, to, startState);
        } else if (fileType == FileType.JSON) {
            return computeJsonCommentMask(text, from, to, startState);
        } else if (fileType == FileType.MARKDOWN) {
            return computeMarkdownCommentMask(text, from, to, startState);
        } else {
            return computeJsCommentMask(text, from, to, startState);
        }
    }

    private static boolean[] computeCssCommentMask(CharSequence text, int from, int to, int startState) {
        boolean[] mask = new boolean[to - from];
        boolean inBlockComment = (startState & 2) != 0;
        boolean inDouble = (startState & (1 << 13)) != 0;
        boolean inSingle = (startState & (1 << 14)) != 0;
        boolean inLineComment = false;

        for (int i = from; i < to; i++) {
            mask[i - from] = inBlockComment || inDouble || inSingle || inLineComment;

            char c = text.charAt(i);
            char n = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && n == '/') {
                    inBlockComment = false;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    i++;
                }
                continue;
            }
            if (c == '\\') {
                i++;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlockComment = true;
                mask[i - from] = true;
                if (i + 1 < to) mask[i + 1 - from] = true;
                i++;
                continue;
            }
            if (c == '/' && n == '/') {
                inLineComment = true;
                mask[i - from] = true;
                if (i + 1 < to) mask[i + 1 - from] = true;
                i++;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                mask[i - from] = true;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                mask[i - from] = true;
                continue;
            }
        }
        return mask;
    }

    private static boolean[] computeHtmlCommentMask(CharSequence text, int from, int to, int startState) {
        int outer = startState & 0xF;
        int inner = startState >>> 4;
        if (outer == 5) { // STATE_STYLE_CONTENT (embedded CSS)
            return computeCssCommentMask(text, from, to, inner);
        }
        if (outer == 6) { // STATE_SCRIPT_CONTENT (embedded JS)
            return computeJsCommentMask(text, from, to, inner);
        }

        boolean[] mask = new boolean[to - from];
        boolean inHtmlComment = (outer == 1);
        boolean inDouble = (outer == 3 || outer == 9 || outer == 11);
        boolean inSingle = (outer == 4 || outer == 10 || outer == 12);

        for (int i = from; i < to; i++) {
            mask[i - from] = inHtmlComment || inDouble || inSingle;

            char c = text.charAt(i);
            char n = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

            if (inHtmlComment) {
                if (c == '-' && n == '-' && i + 2 < text.length() && text.charAt(i + 2) == '>') {
                    inHtmlComment = false;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    if (i + 2 < to) mask[i + 2 - from] = true;
                    i += 2;
                }
                continue;
            }
            if (c == '\\') {
                i++;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (c == '<' && n == '!' && i + 3 < text.length() && text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
                inHtmlComment = true;
                mask[i - from] = true;
                if (i + 1 < to) mask[i + 1 - from] = true;
                if (i + 2 < to) mask[i + 2 - from] = true;
                if (i + 3 < to) mask[i + 3 - from] = true;
                i += 3;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                mask[i - from] = true;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                mask[i - from] = true;
                continue;
            }
        }
        return mask;
    }

    private static boolean[] computeJsonCommentMask(CharSequence text, int from, int to, int startState) {
        boolean[] mask = new boolean[to - from];
        boolean inDouble = (startState == 1);
        boolean inLineComment = false;

        for (int i = from; i < to; i++) {
            mask[i - from] = inDouble || inLineComment;

            char c = text.charAt(i);
            char n = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (c == '\\') {
                i++;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (c == '/' && n == '/') {
                inLineComment = true;
                mask[i - from] = true;
                if (i + 1 < to) mask[i + 1 - from] = true;
                i++;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                mask[i - from] = true;
                continue;
            }
        }
        return mask;
    }

    private static boolean[] computeMarkdownCommentMask(CharSequence text, int from, int to, int startState) {
        boolean[] mask = new boolean[to - from];
        boolean inFence = (startState == 1);

        for (int i = from; i < to; i++) {
            mask[i - from] = inFence;

            char c = text.charAt(i);
            if (c == '`' && i + 2 < text.length() && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
                inFence = !inFence;
                mask[i - from] = true;
                if (i + 1 < to) mask[i + 1 - from] = true;
                if (i + 2 < to) mask[i + 2 - from] = true;
                i += 2;
                continue;
            }
        }
        return mask;
    }

    private static boolean[] computeJsCommentMask(CharSequence text, int from, int to, int startState) {
        boolean[] mask = new boolean[to - from];
        int mode = startState & 0x7;
        int templateDepth = (startState >>> 3) & 0x7;
        int braceDepth = (startState >>> 6) & 0x3FF;

        boolean inSingle = (mode == 3);
        boolean inDouble = (mode == 2);
        boolean inBlockComment = (mode == 1);
        boolean inTemplate = (mode == 4 || mode == 5 || templateDepth > 0);
        boolean inLineComment = false;
        boolean inHtmlComment = false;
        int templateBraceDepth = (mode == 5) ? Math.max(1, braceDepth) : 0;

        for (int i = from; i < to; i++) {
            mask[i - from] = inSingle || inDouble || inLineComment || inBlockComment || inHtmlComment || (inTemplate && templateBraceDepth == 0);

            char c = text.charAt(i);
            char n = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && n == '/') {
                    inBlockComment = false;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    i++;
                }
                continue;
            }
            if (inHtmlComment) {
                if (c == '-' && n == '-' && i + 2 < text.length() && text.charAt(i + 2) == '>') {
                    inHtmlComment = false;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    if (i + 2 < to) mask[i + 2 - from] = true;
                    i += 2;
                }
                continue;
            }
            if (!inSingle && !inDouble && (!inTemplate || templateBraceDepth > 0)) {
                if (c == '<' && n == '!' && i + 3 < text.length() && text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
                    inHtmlComment = true;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    if (i + 2 < to) mask[i + 2 - from] = true;
                    if (i + 3 < to) mask[i + 3 - from] = true;
                    i += 3;
                    continue;
                }
                if (c == '/' && n == '/') {
                    inLineComment = true;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    i++;
                    continue;
                }
                if (c == '/' && n == '*') {
                    inBlockComment = true;
                    mask[i - from] = true;
                    if (i + 1 < to) mask[i + 1 - from] = true;
                    i++;
                    continue;
                }
            }
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '\n' && !inTemplate) {
                inSingle = false;
                inDouble = false;
            }
            if (c == '\'' && !inDouble && (!inTemplate || templateBraceDepth > 0)) inSingle = !inSingle;
            else if (c == '"' && !inSingle && (!inTemplate || templateBraceDepth > 0)) inDouble = !inDouble;
            else if (!inSingle && !inDouble) {
                if (!inTemplate) {
                    if (c == '`') inTemplate = true;
                } else {
                    if (templateBraceDepth == 0) {
                        if (c == '$' && n == '{') {
                            templateBraceDepth = 1;
                            mask[i - from] = true;
                            if (i + 1 < to) {
                                mask[i + 1 - from] = false;
                            }
                            i++;
                            continue;
                        } else if (c == '`') {
                            inTemplate = false;
                        }
                    } else {
                        if (c == '{') templateBraceDepth++;
                        else if (c == '}') templateBraceDepth--;
                    }
                }
            }
        }
        return mask;
    }

    /**
     * Finds the matching bracket pair for the bracket at cursorPos.
     */
    public MatchResult findMatch(CharSequence text, int cursorPos) {
        if (text == null || cursorPos < 0 || cursorPos >= text.length()) {
            return null;
        }

        if (hasCache && cachedCursor == cursorPos && cachedLength == text.length()) {
            return cachedMatch;
        }

        MatchResult result = findMatchInternal(text, cursorPos);

        cachedCursor = cursorPos;
        cachedLength = text.length();
        cachedMatch = result;
        hasCache = true;

        return result;
    }

    private MatchResult findMatchInternal(CharSequence text, int cursorPos) {
        char c = text.charAt(cursorPos);
        if (isInStringOrComment(text, cursorPos)) {
            return null;
        }

        int openIdx = OPEN_BRACKETS.indexOf(c);
        int closeIdx = CLOSE_BRACKETS.indexOf(c);

        if (openIdx >= 0) {
            char closeChar = CLOSE_BRACKETS.charAt(openIdx);
            int depth = 1;

            int limit = Math.min(text.length(), cursorPos + MAX_SCAN_DISTANCE);
            for (int i = cursorPos + 1; i < limit; i++) {
                char ch = text.charAt(i);
                if (ch == c)
                    depth++;
                if (ch == closeChar)
                    depth--;

                if (depth == 0) return new MatchResult(cursorPos, i, true);
            }
        } else if (closeIdx >= 0) {
            char openChar = OPEN_BRACKETS.charAt(closeIdx);
            int depth = 1;

            int limit = Math.max(0, cursorPos - MAX_SCAN_DISTANCE);
            for (int i = cursorPos - 1; i >= limit; i--) {
                char ch = text.charAt(i);
                if (ch == c)
                    depth++;
                if (ch == openChar) depth--;

                if (depth == 0) return new MatchResult(i, cursorPos, true);
            }
        }

        return null;
    }

    /**
     * Finds the innermost bracket pair that encloses the cursor position.
     */
    public MatchResult findEnclosing(CharSequence text, int cursorPos) {
        if (text == null || cursorPos <= 0) return null;

        if (hasCache && cachedCursor == cursorPos && cachedLength == text.length()) {
            return cachedMatch;
        }

        MatchResult result = findEnclosingInternal(text, cursorPos);
        cachedCursor = cursorPos;
        cachedLength = text.length();
        cachedMatch = result;
        hasCache = true;

        return result;
    }

    private MatchResult findEnclosingInternal(CharSequence text, int cursorPos) {
        if (text == null || cursorPos <= 0 || cursorPos > text.length()) return null;

        int depth_paren = 0;
        int depth_square = 0;
        int depth_brace = 0;

        int scanLimit = Math.max(0, cursorPos - MAX_SCAN_DISTANCE);
        boolean[] mask = computeStringCommentMask(text, scanLimit, cursorPos);

        for (int i = cursorPos - 1; i >= scanLimit; i--) {
            if (mask[i - scanLimit]) continue;

            char c = text.charAt(i);
            if (c == ')') depth_paren++;
            else if (c == ']') depth_square++;
            else if (c == '}') depth_brace++;
            else if (c == '(') {
                if (depth_paren == 0) return findMatchInternal(text, i);
                depth_paren--;
            } else if (c == '[') {
                if (depth_square == 0) return findMatchInternal(text, i);
                depth_square--;
            } else if (c == '{') {
                if (depth_brace == 0) return findMatchInternal(text, i);
                depth_brace--;
            }
        }
        return null;
    }

    /**
     * Result of a bracket match operation.
     */
    public static class MatchResult {
        public final int openPos;
        public final int closePos;
        public final boolean found;

        public MatchResult(int openPos, int closePos, boolean found) {
            this.openPos = openPos;
            this.closePos = closePos;
            this.found = found;
        }
    }
}