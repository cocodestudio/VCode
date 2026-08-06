package com.cocode.vcode.ide.core.editor.indent;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.model.Problem;

import java.util.List;

/**
 * Utility class responsible for pair-matching bracket tokens under the cursor.
 * Supports structural highlighting and scope navigation for parentheses (), brackets [], and braces {}.
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
        if (text == null || colors == null || colors.length == 0 || tokens == null) return;

        boolean[] mask = computeStringCommentMask(text, 0, text.length());
        int depth = initialDepth;

        for (int i = 0; i < text.length(); i++) {
            if (mask[i]) continue;

            char c = text.charAt(i);
            boolean isOpen = c == '(' || c == '{' || c == '[';
            boolean isClose = c == ')' || c == '}' || c == ']';
            if (!isOpen && !isClose) continue;

            if (isOpen) {
                int colorIdx = depth % colors.length;
                tokens.add(new com.cocode.vcode.ide.core.editor.highlight.HighlightToken(0, i, i + 1, colors[colorIdx], false));
                depth++;
            } else {
                depth = Math.max(0, depth - 1);
                int colorIdx = depth % colors.length;
                tokens.add(new com.cocode.vcode.ide.core.editor.highlight.HighlightToken(0, i, i + 1, colors[colorIdx], false));
            }
        }
    }

    public static int computeBracketDepth(String text, int initialDepth) {
        if (text == null) return initialDepth;
        boolean[] mask = computeStringCommentMask(text, 0, text.length());
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

        java.util.Stack<int[]> parenStack = new java.util.Stack<>();
        java.util.Stack<int[]> bracketStack = new java.util.Stack<>();
        java.util.Stack<int[]> braceStack = new java.util.Stack<>();

        int line = 1;
        int col = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') parenStack.push(new int[]{line, col});
            else if (c == '[') bracketStack.push(new int[]{line, col});
            else if (c == '{') braceStack.push(new int[]{line, col});
            else if (c == ')') {
                if (parenStack.isEmpty()) {
                    problems.add(new Problem(file, line, col, 1, "Unmatched closing parenthesis ')'", Problem.Severity.ERROR));
                } else parenStack.pop();
            } else if (c == ']') {
                if (bracketStack.isEmpty()) {
                    problems.add(new Problem(file, line, col, 1, "Unmatched closing bracket ']'", Problem.Severity.ERROR));
                } else bracketStack.pop();
            } else if (c == '}') {
                if (braceStack.isEmpty()) {
                    problems.add(new Problem(file, line, col, 1, "Unmatched closing brace '}'", Problem.Severity.ERROR));
                } else braceStack.pop();
            }

            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        while (!parenStack.isEmpty()) {
            int[] pos = parenStack.pop();
            problems.add(new Problem(file, pos[0], pos[1], 1, "Unclosed parenthesis '('", Problem.Severity.ERROR));
        }
        while (!bracketStack.isEmpty()) {
            int[] pos = bracketStack.pop();
            problems.add(new Problem(file, pos[0], pos[1], 1, "Unclosed bracket '['", Problem.Severity.ERROR));
        }
        while (!braceStack.isEmpty()) {
            int[] pos = braceStack.pop();
            problems.add(new Problem(file, pos[0], pos[1], 1, "Unclosed brace '{'", Problem.Severity.ERROR));
        }

        return problems;
    }

    private static boolean isInStringOrComment(CharSequence text, int pos) {
        boolean inSingle = false, inDouble = false, inTemplate = false;
        boolean inLineComment = false, inBlockComment = false;

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
            if (!inSingle && !inDouble && !inTemplate) {
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
            if (c == '\'' && !inDouble && !inTemplate) inSingle = !inSingle;
            else if (c == '"' && !inSingle && !inTemplate) inDouble = !inDouble;
            else if (c == '`' && !inSingle && !inDouble) inTemplate = !inTemplate;
        }
        return inSingle || inDouble || inTemplate || inLineComment || inBlockComment;
    }

    private static boolean[] computeStringCommentMask(CharSequence text, int from, int to) {
        boolean[] mask = new boolean[to - from];
        boolean inSingle = false, inDouble = false, inTemplate = false;
        boolean inLineComment = false, inBlockComment = false;

        for (int i = from; i < to; i++) {
            mask[i - from] = inSingle || inDouble || inTemplate || inLineComment || inBlockComment;

            char c = text.charAt(i);
            char n = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

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
            if (!inSingle && !inDouble && !inTemplate) {
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
            }
            if (c == '\'' && !inDouble && !inTemplate) inSingle = !inSingle;
            else if (c == '"' && !inSingle && !inTemplate) inDouble = !inDouble;
            else if (c == '`' && !inSingle && !inDouble) inTemplate = !inTemplate;
        }
        return mask;
    }

    /**
     * Inspects the character directly under the cursor and tracks its matching sibling balance
     * by scanning either forward or backward through the document text.
     *
     * @param text      The complete text buffer of the document.
     * @param cursorPos The current 0-based index position of the cursor caret.
     * @return A MatchResult object detailing the pair coordinates and discovery status.
     */
    public MatchResult findMatch(CharSequence text, int cursorPos) {
        // Guard check against empty inputs or indices targeting outer space bounds
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
            // Case 1: Cursor is resting on an open bracket token -> Scan forward to locate closure
            char closeChar = CLOSE_BRACKETS.charAt(openIdx);
            int depth = 1; // Track nesting level changes

            int limit = Math.min(text.length(), cursorPos + MAX_SCAN_DISTANCE);
            for (int i = cursorPos + 1; i < limit; i++) {
                char ch = text.charAt(i);
                if (ch == c)
                    depth++;          // Found another identical open token; increment depth level
                if (ch == closeChar)
                    depth--;  // Found matching target closer; decrement depth level

                // Nesting balanced out to zero; we found the exact sibling token match!
                if (depth == 0) return new MatchResult(cursorPos, i, true);
            }
        } else if (closeIdx >= 0) {
            // Case 2: Cursor is resting on a close bracket token -> Scan backward to locate opening
            char openChar = OPEN_BRACKETS.charAt(closeIdx);
            int depth = 1; // Track nesting level changes backwards

            int limit = Math.max(0, cursorPos - MAX_SCAN_DISTANCE);
            for (int i = cursorPos - 1; i >= limit; i--) {
                char ch = text.charAt(i);
                if (ch == c)
                    depth++;         // Found another identical close token; increment depth level
                if (ch == openChar) depth--;  // Found matching target opener; decrement depth level

                // Nesting balanced out to zero; pair tracking successfully satisfied
                if (depth == 0) return new MatchResult(i, cursorPos, true);
            }
        }

        // No matching partner token was discovered within document limits
        return null;
    }

    /**
     * Finds the innermost bracket pair that ENCLOSES the cursor position.
     * Used when the cursor is not sitting on a bracket itself.
     *
     * @param text      Full document text.
     * @param cursorPos Current cursor offset (0-based).
     * @return MatchResult with openPos/closePos, or found=false if not inside any bracket.
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
     * Immutable container capturing the absolute coordinates of matched structural pairs.
     */
    public static class MatchResult {
        public final int openPos;   // Index coordinate of the opening element
        public final int closePos;  // Index coordinate of the closing element
        public final boolean found; // True if the partner token was successfully located

        public MatchResult(int openPos, int closePos, boolean found) {
            this.openPos = openPos;
            this.closePos = closePos;
            this.found = found;
        }
    }
}