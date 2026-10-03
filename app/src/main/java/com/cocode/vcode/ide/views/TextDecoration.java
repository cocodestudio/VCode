package com.cocode.vcode.ide.views;

import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Represents a visual text decoration rendered on top of or behind editor text.
 * Supports squiggly problem underlines, range background highlights, gutter markers,
 * and inline badges/markers.
 */
public class TextDecoration {

    public enum Type {
        /**
         * Bézier-wave squiggly line rendered underneath text (e.g. linter errors/warnings).
         */
        SQUIGGLY_UNDERLINE,

        /**
         * Rectangular background tint rendered behind characters (e.g. search matches, selection ranges).
         */
        BACKGROUND_HIGHLIGHT,

        /**
         * Margin/gutter indicator on the left line-number column (e.g. Git diff additions/modifications).
         */
        GUTTER_INDICATOR,

        /**
         * Small inline glyph or marker indicator.
         */
        INLINE_MARKER
    }

    private final String id;
    private final Type type;
    private final int startLine;
    private final int startCol;
    private final int endLine;
    private final int endCol;
    private final int color;
    private final String tooltip;
    private final Object tag;

    private Path cachedPath;

    public TextDecoration(@NonNull String id,
                          @NonNull Type type,
                          int startLine,
                          int startCol,
                          int endLine,
                          int endCol,
                          int color,
                          @Nullable String tooltip,
                          @Nullable Object tag) {
        this.id = id;
        this.type = type;
        this.startLine = startLine;
        this.startCol = startCol;
        this.endLine = endLine;
        this.endCol = endCol;
        this.color = color;
        this.tooltip = tooltip;
        this.tag = tag;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartCol() {
        return startCol;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndCol() {
        return endCol;
    }

    public int getColor() {
        return color;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    @Nullable
    public Object getTag() {
        return tag;
    }

    @Nullable
    public Path getCachedPath() {
        return cachedPath;
    }

    public void setCachedPath(@Nullable Path cachedPath) {
        this.cachedPath = cachedPath;
    }

    public boolean intersectsLine(int line) {
        return line >= startLine && line <= endLine;
    }

    public boolean intersectsRange(int firstLine, int lastLine) {
        return endLine >= firstLine && startLine <= lastLine;
    }

    public static Builder builder(@NonNull Type type) {
        return new Builder(type);
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private final Type type;
        private int startLine;
        private int startCol;
        private int endLine;
        private int endCol;
        private int color;
        private String tooltip;
        private Object tag;

        public Builder(@NonNull Type type) {
            this.type = type;
        }

        public Builder id(String id) {
            if (id != null) this.id = id;
            return this;
        }

        public Builder range(int startLine, int startCol, int endLine, int endCol) {
            this.startLine = startLine;
            this.startCol = startCol;
            this.endLine = endLine;
            this.endCol = endCol;
            return this;
        }

        public Builder line(int line, int startCol, int endCol) {
            this.startLine = line;
            this.startCol = startCol;
            this.endLine = line;
            this.endCol = endCol;
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder tooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder tag(Object tag) {
            this.tag = tag;
            return this;
        }

        public TextDecoration build() {
            return new TextDecoration(id, type, startLine, startCol, endLine, endCol, color, tooltip, tag);
        }
    }
}
