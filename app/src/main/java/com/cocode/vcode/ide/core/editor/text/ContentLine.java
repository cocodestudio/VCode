package com.cocode.vcode.ide.core.editor.text;

/**
 * A single logical line of text inside a {@link Content} document.
 *
 * <p>Backed by a Gap Buffer, providing O(1) time complexity for sequential character insertions
 * and deletions at the cursor. This solves massive performance bottlenecks on very long lines
 * (e.g. minified code) by avoiding O(N) array copies on every keystroke.
 *
 * <p>This class is <strong>not thread-safe</strong>. All mutations must occur under the write lock
 * of the owning {@link Content} instance, and all reads that need a stable snapshot must occur
 * under its read lock.
 */
public final class ContentLine {

    private static final int MIN_CAPACITY = 32;
    public java.util.List<com.cocode.vcode.ide.core.editor.highlight.HighlightToken> tokens;
    char[] buffer;
    int gapStart;
    int gapEnd;
    int tokenizerStartState;
    int tokenizerEndState;

    public ContentLine() {
        this.buffer = new char[MIN_CAPACITY];
        this.gapStart = 0;
        this.gapEnd = MIN_CAPACITY;
        this.tokenizerEndState = 0;
    }

    public ContentLine(CharSequence text) {
        int len = text.length();
        this.buffer = new char[Math.max(MIN_CAPACITY, len + MIN_CAPACITY)];
        for (int i = 0; i < len; i++) this.buffer[i] = text.charAt(i);
        this.gapStart = len;
        this.gapEnd = this.buffer.length;
        this.tokenizerEndState = 0;
    }

    ContentLine(char[] src, int offset, int count) {
        this.buffer = new char[Math.max(MIN_CAPACITY, count + MIN_CAPACITY)];
        System.arraycopy(src, offset, this.buffer, 0, count);
        this.gapStart = count;
        this.gapEnd = this.buffer.length;
        this.tokenizerEndState = 0;
    }

    private void moveGap(int target) {
        if (target == gapStart) return;
        if (target < gapStart) {
            int moveCount = gapStart - target;
            System.arraycopy(buffer, target, buffer, gapEnd - moveCount, moveCount);
            gapStart -= moveCount;
            gapEnd -= moveCount;
        } else {
            int moveCount = target - gapStart;
            System.arraycopy(buffer, gapEnd, buffer, gapStart, moveCount);
            gapStart += moveCount;
            gapEnd += moveCount;
        }
    }

    private void ensureGapSize(int required) {
        if (gapEnd - gapStart >= required) return;
        int currentLength = length();
        int newCap = Math.max(currentLength + required + MIN_CAPACITY, buffer.length * 2);
        char[] newBuffer = new char[newCap];

        System.arraycopy(buffer, 0, newBuffer, 0, gapStart);
        int afterGapCount = buffer.length - gapEnd;
        int newGapEnd = newCap - afterGapCount;
        System.arraycopy(buffer, gapEnd, newBuffer, newGapEnd, afterGapCount);

        buffer = newBuffer;
        gapEnd = newGapEnd;
    }

    public void insert(int column, char[] src, int srcOffset, int count) {
        if (count <= 0) return;
        moveGap(column);
        ensureGapSize(count);
        System.arraycopy(src, srcOffset, buffer, gapStart, count);
        gapStart += count;
        tokenizerEndState = 0;
    }

    public void insert(int column, CharSequence text) {
        int count = text.length();
        if (count == 0) return;
        moveGap(column);
        ensureGapSize(count);
        for (int i = 0; i < count; i++) {
            buffer[gapStart + i] = text.charAt(i);
        }
        gapStart += count;
        tokenizerEndState = 0;
    }

    public void delete(int startColumn, int endColumn) {
        int s = Math.max(0, startColumn);
        int e = Math.min(length(), endColumn);
        if (s >= e) return;
        moveGap(s);
        gapEnd += (e - s);
        tokenizerEndState = 0;
    }

    void appendFrom(ContentLine other, int otherColumn) {
        int count = other.length() - otherColumn;
        if (count <= 0) return;
        moveGap(length());
        ensureGapSize(count);
        other.getChars(otherColumn, other.length(), buffer, gapStart);
        gapStart += count;
        tokenizerEndState = 0;
    }

    ContentLine splitAt(int column) {
        int len = length();
        int newLen = len - column;
        char[] tailBuffer = new char[Math.max(MIN_CAPACITY, newLen + MIN_CAPACITY)];
        getChars(column, len, tailBuffer, 0);
        ContentLine tail = new ContentLine(tailBuffer, 0, newLen);
        delete(column, len);
        return tail;
    }

    public char charAt(int column) {
        if (column < gapStart) return buffer[column];
        return buffer[column + (gapEnd - gapStart)];
    }

    public int length() {
        return buffer.length - (gapEnd - gapStart);
    }

    public void getChars(int start, int end, char[] dest, int destOffset) {
        int count = end - start;
        if (start < gapStart) {
            int beforeGap = Math.min(count, gapStart - start);
            System.arraycopy(buffer, start, dest, destOffset, beforeGap);
            if (beforeGap < count) {
                System.arraycopy(buffer, gapEnd, dest, destOffset + beforeGap, count - beforeGap);
            }
        } else {
            System.arraycopy(buffer, start + (gapEnd - gapStart), dest, destOffset, count);
        }
    }

    public String toLineString() {
        int len = length();
        if (len == 0) return "";
        char[] tmp = new char[len];
        getChars(0, len, tmp, 0);
        return new String(tmp);
    }

    public int getTokenizerEndState() {
        return tokenizerEndState;
    }

    public void setTokenizerEndState(int state) {
        this.tokenizerEndState = state;
    }

    public int getTokenizerStartState() {
        return tokenizerStartState;
    }

    public void setTokenizerStartState(int state) {
        this.tokenizerStartState = state;
    }
}
