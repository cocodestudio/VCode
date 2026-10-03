package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.core.model.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata definition for a programming language supported natively or contributed
 * by third-party extensions.
 */
public class LanguageDefinition {

    private final String id;
    private final String displayName;
    private final List<String> extensions;
    private final List<String> mimeTypes;
    private final String lineCommentPrefix;
    private final String blockCommentStart;
    private final String blockCommentEnd;
    private final int iconResId;
    private final int colorResId;
    private final FileType fallbackFileType;

    public LanguageDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName != null ? builder.displayName : builder.id;
        this.extensions = Collections.unmodifiableList(new ArrayList<>(builder.extensions));
        this.mimeTypes = Collections.unmodifiableList(new ArrayList<>(builder.mimeTypes));
        this.lineCommentPrefix = builder.lineCommentPrefix;
        this.blockCommentStart = builder.blockCommentStart;
        this.blockCommentEnd = builder.blockCommentEnd;
        this.iconResId = builder.iconResId;
        this.colorResId = builder.colorResId;
        this.fallbackFileType = builder.fallbackFileType != null ? builder.fallbackFileType : FileType.TEXT;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public String getLineCommentPrefix() {
        return lineCommentPrefix;
    }

    public String getBlockCommentStart() {
        return blockCommentStart;
    }

    public String getBlockCommentEnd() {
        return blockCommentEnd;
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getColorResId() {
        return colorResId;
    }

    public FileType getFallbackFileType() {
        return fallbackFileType;
    }

    public static class Builder {
        private final String id;
        private String displayName;
        private final List<String> extensions = new ArrayList<>();
        private final List<String> mimeTypes = new ArrayList<>();
        private String lineCommentPrefix;
        private String blockCommentStart;
        private String blockCommentEnd;
        private int iconResId;
        private int colorResId;
        private FileType fallbackFileType;

        public Builder(String id) {
            this.id = id;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder addExtension(String extension) {
            if (extension != null && !extension.isEmpty()) {
                this.extensions.add(extension.toLowerCase());
            }
            return this;
        }

        public Builder addMimeType(String mimeType) {
            if (mimeType != null && !mimeType.isEmpty()) {
                this.mimeTypes.add(mimeType.toLowerCase());
            }
            return this;
        }

        public Builder lineComment(String prefix) {
            this.lineCommentPrefix = prefix;
            return this;
        }

        public Builder blockComment(String start, String end) {
            this.blockCommentStart = start;
            this.blockCommentEnd = end;
            return this;
        }

        public Builder icon(int iconResId) {
            this.iconResId = iconResId;
            return this;
        }

        public Builder color(int colorResId) {
            this.colorResId = colorResId;
            return this;
        }

        public Builder fallbackFileType(FileType fileType) {
            this.fallbackFileType = fileType;
            return this;
        }

        public LanguageDefinition build() {
            return new LanguageDefinition(this);
        }
    }
}
