package com.cocode.vcode.ide.core.editor.highlight;

/**
 * An immutable colour token produced by the incremental syntax analyser.
 * Coordinates are relative to the line inside the document.
 * line, startCol and endCol are all zero-indexed.
 */
public final class HighlightToken {

    public final int line;
    public final int color;
    public final boolean underline;
    public final boolean hasPreviewColor;
    public final int previewColor;
    public int startCol;
    public int endCol;

    public HighlightToken(int line, int startCol, int endCol, int color, boolean underline, boolean hasPreviewColor, int previewColor) {
        this.line = line;
        this.startCol = startCol;
        this.endCol = endCol;
        this.color = color;
        this.underline = underline;
        this.hasPreviewColor = hasPreviewColor;
        this.previewColor = previewColor;
    }

    public HighlightToken(int line, int startCol, int endCol, int color, boolean underline) {
        this(line, startCol, endCol, color, underline, false, 0);
    }

    public HighlightToken(int line, int startCol, int endCol, int color) {
        this(line, startCol, endCol, color, false, false, 0);
    }
}
