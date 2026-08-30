package com.cocode.vcode.ide.core.language.md;

/**
 * A flat-array data container representing a stream of markdown lines.
 * Replaces object per line overhead, using parallel arrays to maintain
 * zero allocation overhead during fast typing.
 */
public final class MdLineStream {

    public static final byte L_BLANK = 0;
    public static final byte L_PARAGRAPH = 1;
    public static final byte L_HEADER = 2;
    public static final byte L_LIST_ITEM = 3;
    public static final byte L_CODE_FENCE = 4;
    public static final byte L_BLOCKQUOTE = 5;
    public static final byte L_THEMATIC_BREAK = 6;

    public byte[] lineTypes;
    public int[] lineStartOffsets;
    public int[] lineEndOffsets;
    public int lineCount;

    public MdLineStream(int maxLines) {
        this.lineTypes = new byte[maxLines];
        this.lineStartOffsets = new int[maxLines];
        this.lineEndOffsets = new int[maxLines];
        this.lineCount = 0;
    }
}
