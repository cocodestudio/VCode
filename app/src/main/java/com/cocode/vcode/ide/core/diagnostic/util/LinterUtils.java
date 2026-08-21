package com.cocode.vcode.ide.core.diagnostic.util;

/**
 * Utility functions for linters (offset-to-line/column conversion, token length calculation, line slicing).
 */
public class LinterUtils {

    /**
     * Convert absolute char offset → 1-based line number.
     */
    public static int getLine(String text, int offset) {
        int line = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Convert absolute char offset → 1-based column number.
     */
    public static int getColumn(String text, int offset) {
        int col = 1;
        for (int i = offset - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') break;
            col++;
        }
        return col;
    }

    /**
     * Split text into lines (result[0] = line 1 content without \n).
     */
    public static String[] splitLines(String text) {
        if (text.isEmpty()) return new String[]{""};
        // count lines
        int count = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        String[] lines = new String[count];
        int lineIdx = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines[lineIdx++] = text.substring(start, i);
                start = i + 1;
            }
        }
        lines[lineIdx] = text.substring(start);
        return lines;
    }

    /**
     * Get the length of the token (word) starting at offset.
     */
    public static int tokenLength(String text, int offset) {
        int end = offset;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$') end++;
            else break;
        }
        return Math.max(1, end - offset);
    }

    /**
     * Returns absolute offset of the start of line N (1-based).
     */
    public static int lineStartOffset(String text, int lineNumber) {
        if (lineNumber <= 1) return 0;
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                if (line == lineNumber) return i + 1;
            }
        }
        return text.length();
    }

    /**
     * Returns column index (0-based) of first non-whitespace char in line.
     */
    public static int trimmedStart(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) return i;
        }
        return 0;
    }
}
