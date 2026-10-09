package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.lsp.model.LspPosition;

import java.util.Arrays;

/**
 * High-performance line offset cache providing O(1) position ↔ flat offset mapping.
 * Precomputes line boundary offsets in a compact primitive int[] array to eliminate linear
 * string iteration across LSP document operations.
 */
public final class OffsetIndexCache {

    private final int[] lineStarts;
    private final int textLength;

    public OffsetIndexCache(String text) {
        if (text == null || text.isEmpty()) {
            this.lineStarts = new int[]{0};
            this.textLength = 0;
            return;
        }

        this.textLength = text.length();
        int newlineCount = 0;
        for (int i = 0; i < textLength; i++) {
            if (text.charAt(i) == '\n') {
                newlineCount++;
            }
        }

        int[] starts = new int[newlineCount + 1];
        starts[0] = 0;
        int line = 1;
        for (int i = 0; i < textLength; i++) {
            if (text.charAt(i) == '\n') {
                starts[line++] = i + 1;
            }
        }
        this.lineStarts = starts;
    }

    /**
     * Returns the number of lines in the document (at least 1).
     */
    public int getLineCount() {
        return lineStarts.length;
    }

    /**
     * Returns the total text length in characters.
     */
    public int getTextLength() {
        return textLength;
    }

    /**
     * Returns the 0-based character offset of the start of the given line.
     */
    public int getLineStartOffset(int line) {
        if (line < 0 || line >= lineStarts.length) return -1;
        return lineStarts[line];
    }

    /**
     * Returns the 0-based character offset of the end of the given line (inclusive of line chars, exclusive of newline).
     */
    public int getLineEndOffset(int line) {
        if (line < 0 || line >= lineStarts.length) return -1;
        int nextStart = (line + 1 < lineStarts.length) ? lineStarts[line + 1] : textLength;
        return nextStart;
    }

    /**
     * Returns the line text without trailing line terminator.
     */
    public String getLine(String text, int line) {
        if (text == null || line < 0 || line >= lineStarts.length) return "";
        int start = lineStarts[line];
        int end = (line + 1 < lineStarts.length) ? lineStarts[line + 1] : text.length();
        if (end > start && text.charAt(end - 1) == '\n') {
            end--;
            if (end > start && text.charAt(end - 1) == '\r') {
                end--;
            }
        }
        return (start <= end && end <= text.length()) ? text.substring(start, end) : "";
    }

    /**
     * Converts a 0-based (line, character) coordinate to a flat character offset in O(1).
     * Returns -1 if line or character is invalid.
     */
    public int toOffset(int line, int character) {
        if (line < 0 || line >= lineStarts.length) return -1;
        if (character < 0) return -1;
        int start = lineStarts[line];
        int offset = start + character;
        return Math.min(offset, textLength);
    }

    /**
     * Converts an {@link LspPosition} to a flat character offset in O(1).
     */
    public int toOffset(LspPosition pos) {
        if (pos == null) return -1;
        return toOffset(pos.line, pos.character);
    }

    /**
     * Converts a flat character offset into an {@link com.cocode.vcode.ide.core.lsp.LspPosition}.
     */
    public com.cocode.vcode.ide.core.lsp.LspPosition toPosition(int offset) {
        if (offset <= 0 || textLength == 0) {
            return new com.cocode.vcode.ide.core.lsp.LspPosition(0, 0);
        }
        int target = Math.min(offset, textLength);
        int idx = Arrays.binarySearch(lineStarts, target);
        if (idx >= 0) {
            return new com.cocode.vcode.ide.core.lsp.LspPosition(idx, 0);
        }
        int line = (-idx - 1) - 1;
        if (line < 0) line = 0;
        int col = target - lineStarts[line];
        return new com.cocode.vcode.ide.core.lsp.LspPosition(line, col);
    }
}
