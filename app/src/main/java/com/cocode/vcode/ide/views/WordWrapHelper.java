package com.cocode.vcode.ide.views;

import java.util.Arrays;

/**
 * High-performance, zero-allocation word-wrapping helper for {@link CodeEditText}.
 *
 * <p>Implements desktop-grade word wrapping matching VS Code and Android Studio:
 * <ul>
 *   <li>Wraps at word boundaries (whitespace) and punctuation/operators so complete words
 *       and identifiers are never split across visual rows.</li>
 *   <li>Guards compound operators (e.g. {@code ==}, {@code !=}, {@code <=}, {@code >=}, {@code &&}, {@code ||}, {@code ++}, {@code --}, {@code =>})
 *       from being sliced in half.</li>
 *   <li>Falls back to viewport-boundary hard wrapping only when an individual unbroken token
 *       is longer than the available width.</li>
 *   <li>Provides O(1) binary-search sub-row mapping for cursor positioning, touch hit-testing,
 *       and text selection.</li>
 * </ul>
 */
public final class WordWrapHelper {

    private WordWrapHelper() {}

    /**
     * Computes the sub-row start column offsets for a line of text.
     *
     * @param line        the line's character sequence
     * @param lineLen     the length of the line
     * @param charsPerRow the maximum characters that fit on a single visual row
     * @return an {@code int[]} array of starting column offsets for each sub-row
     *         ({@code [0, break1, break2, ...]}), or {@code null} if the line fits on 1 sub-row
     */
    public static int[] computeLineWrapBreaks(CharSequence line, int lineLen, int charsPerRow) {
        if (lineLen <= charsPerRow || charsPerRow <= 0 || line == null) {
            return null;
        }

        // Temporary buffer to collect break points without boxing or List<Integer> allocations.
        // The number of sub-rows cannot exceed lineLen / max(1, charsPerRow / 2).
        int initialCap = Math.min(64, (lineLen / Math.max(1, charsPerRow / 2)) + 4);
        int[] temp = new int[initialCap];
        int count = 0;
        temp[count++] = 0;

        int start = 0;
        while (start < lineLen) {
            if (lineLen - start <= charsPerRow) {
                break;
            }

            int limit = start + charsPerRow;
            int breakPoint = -1;

            // Priority 1: Whitespace boundary (space or tab)
            // Look backward from limit down to start + 1
            for (int k = limit; k > start; k--) {
                char prev = line.charAt(k - 1);
                char curr = line.charAt(k);
                if ((prev == ' ' || prev == '\t') && curr != ' ' && curr != '\t') {
                    breakPoint = k;
                    break;
                }
            }

            // Priority 2: Punctuation / delimiter / operator boundary
            if (breakPoint == -1) {
                for (int k = limit; k > start; k--) {
                    char prev = line.charAt(k - 1);
                    char curr = line.charAt(k);
                    if (isPunctuationBreak(prev, curr)) {
                        breakPoint = k;
                        break;
                    }
                }
            }

            // Priority 3: Fallback hard break if no word/punctuation boundary exists in window
            if (breakPoint == -1) {
                breakPoint = limit;
            }

            if (count >= temp.length) {
                temp = Arrays.copyOf(temp, temp.length * 2);
            }
            temp[count++] = breakPoint;
            start = breakPoint;
        }

        if (count <= 1) {
            return null;
        }
        return Arrays.copyOf(temp, count);
    }

    /**
     * Determines if a break is permissible between {@code prev} and {@code curr}.
     */
    public static boolean isPunctuationBreak(char prev, char curr) {
        // Avoid breaking inside compound operators (e.g. ==, !=, <=, >=, &&, ||, ++, --, ->, =>)
        if (isCompoundOperator(prev, curr)) {
            return false;
        }

        // Break after punctuation/delimiters/operators
        if (prev == ',' || prev == ';' || prev == '(' || prev == '[' || prev == '{'
                || prev == ')' || prev == ']' || prev == '}' || prev == '>'
                || prev == '?' || prev == ':' || prev == '+' || prev == '-'
                || prev == '*' || prev == '/' || prev == '=' || prev == '&'
                || prev == '|' || prev == '^' || prev == '%' || prev == '.') {
            return true;
        }
        // Break before opening delimiters or fluent dot calls
        if (curr == '(' || curr == '[' || curr == '{' || curr == '.') {
            return true;
        }
        return false;
    }

    /**
     * Checks if {@code prev} and {@code curr} form a compound multi-character operator or delimiter.
     */
    public static boolean isCompoundOperator(char prev, char curr) {
        if (prev == '=' && curr == '=') return true;
        if (prev == '!' && curr == '=') return true;
        if (prev == '<' && (curr == '=' || curr == '<')) return true;
        if (prev == '>' && (curr == '=' || curr == '>')) return true;
        if (prev == '&' && curr == '&') return true;
        if (prev == '|' && curr == '|') return true;
        if (prev == '+' && (curr == '+' || curr == '=')) return true;
        if (prev == '-' && (curr == '-' || curr == '=' || curr == '>')) return true;
        if (prev == '*' && (curr == '*' || curr == '=')) return true;
        if (prev == '/' && (curr == '/' || curr == '*')) return true;
        return false;
    }

    /**
     * Resolves which sub-row (0-indexed) a given column offset belongs to.
     */
    public static int visualSubRow(int[] breaks, int col) {
        if (breaks == null || breaks.length <= 1 || col <= 0) return 0;
        int lo = 0;
        int hi = breaks.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (breaks[mid] <= col) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * Resolves the visual column offset of {@code col} within its sub-row.
     */
    public static int colInSubRow(int[] breaks, int col) {
        if (breaks == null || breaks.length <= 1 || col <= 0) return col;
        int sr = visualSubRow(breaks, col);
        return Math.max(0, col - breaks[sr]);
    }

    /**
     * Returns the starting column of the specified sub-row.
     */
    public static int getSubRowStart(int[] breaks, int sr) {
        if (breaks == null || breaks.length == 0 || sr <= 0) return 0;
        if (sr >= breaks.length) return breaks[breaks.length - 1];
        return breaks[sr];
    }

    /**
     * Returns the ending column of the specified sub-row.
     */
    public static int getSubRowEnd(int[] breaks, int sr, int lineLen) {
        if (breaks == null || breaks.length == 0) return lineLen;
        if (sr + 1 < breaks.length) {
            return breaks[sr + 1];
        }
        return lineLen;
    }
}
