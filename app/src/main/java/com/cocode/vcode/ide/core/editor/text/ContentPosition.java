package com.cocode.vcode.ide.core.editor.text;

/**
 * Immutable value type representing a position in the {@link Content} model.
 *
 * <p>Both {@code line} and {@code column} are zero-indexed. A position at the very beginning of the
 * document is {@code (0, 0)}. A position just past the last character on a line whose length is
 * {@code n} has {@code column == n}.
 *
 * <p>This is the native coordinate system of the new editor architecture. All subsystems
 * (cursor, selection, syntax highlighting, bracket matching, autocomplete) use
 * {@code ContentPosition} rather than flat character offsets. Flat offsets are available on demand
 * via {@link Content#flatOffset(ContentPosition)} in O(1) amortised time.
 */
public final class ContentPosition {

    /**
     * Canonical start-of-document position.
     */
    public static final ContentPosition ZERO = new ContentPosition(0, 0);
    /**
     * Zero-indexed line index within the document.
     */
    public final int line;
    /**
     * Zero-indexed column index within the line (character position, not pixel position).
     */
    public final int column;

    public ContentPosition(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the earlier of {@code a} and {@code b} in document order.
     */
    public static ContentPosition min(ContentPosition a, ContentPosition b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * Returns the later of {@code a} and {@code b} in document order.
     */
    public static ContentPosition max(ContentPosition a, ContentPosition b) {
        return a.isBefore(b) ? b : a;
    }

    /**
     * Returns {@code true} if this position is strictly before {@code other} in document order.
     */
    public boolean isBefore(ContentPosition other) {
        if (this.line != other.line) return this.line < other.line;
        return this.column < other.column;
    }

    /**
     * Returns {@code true} if this position is at the same location as {@code other}.
     */
    public boolean isSameAs(ContentPosition other) {
        return this.line == other.line && this.column == other.column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentPosition)) return false;
        ContentPosition that = (ContentPosition) o;
        return line == that.line && column == that.column;
    }

    @Override
    public int hashCode() {
        return 31 * line + column;
    }

    @Override
    public String toString() {
        return "ContentPosition(" + line + ", " + column + ")";
    }
}
