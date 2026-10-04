package com.cocode.vcode.ide.core.editor.highlight;

/**
 * An immutable colour token produced by the incremental syntax analyser.
 * Coordinates are relative to the line inside the document.
 * line, startCol and endCol are all zero-indexed.
 */
public final class HighlightToken {

    // Semantic token type categories
    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_KEYWORD = 1;
    public static final int TYPE_STRING = 2;
    public static final int TYPE_NUMBER = 3;
    public static final int TYPE_FUNCTION = 4;
    public static final int TYPE_TYPE = 5;
    public static final int TYPE_VARIABLE = 6;
    public static final int TYPE_COMMENT = 7;
    public static final int TYPE_TAG = 8;
    public static final int TYPE_ATTRIBUTE = 9;
    public static final int TYPE_OPERATOR = 10;
    public static final int TYPE_PROPERTY = 11;

    public final int line;
    public final int color;
    public final int tokenType;
    public final boolean underline;
    public final boolean hasPreviewColor;
    public final int previewColor;
    public final GradientPreview gradientPreview;
    public int startCol;
    public int endCol;

    public HighlightToken(int line, int startCol, int endCol, int color, int tokenType, boolean underline, boolean hasPreviewColor, int previewColor, GradientPreview gradientPreview) {
        this.line = line;
        this.startCol = startCol;
        this.endCol = endCol;
        this.color = color;
        this.tokenType = tokenType;
        this.underline = underline;
        this.hasPreviewColor = hasPreviewColor;
        this.previewColor = previewColor;
        this.gradientPreview = gradientPreview;
    }

    public HighlightToken(int line, int startCol, int endCol, int color, boolean underline, boolean hasPreviewColor, int previewColor, GradientPreview gradientPreview) {
        this(line, startCol, endCol, color, TYPE_DEFAULT, underline, hasPreviewColor, previewColor, gradientPreview);
    }

    public HighlightToken(int line, int startCol, int endCol, int color, boolean underline, boolean hasPreviewColor, int previewColor) {
        this(line, startCol, endCol, color, TYPE_DEFAULT, underline, hasPreviewColor, previewColor, null);
    }

    public HighlightToken(int line, int startCol, int endCol, int color, boolean underline) {
        this(line, startCol, endCol, color, TYPE_DEFAULT, underline, false, 0, null);
    }

    public HighlightToken(int line, int startCol, int endCol, int color) {
        this(line, startCol, endCol, color, TYPE_DEFAULT, false, false, 0, null);
    }
}
