package com.cocode.vcode.ide.core.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Base event representing an operation or mutation occurring on a workspace document.
 */
public abstract class DocumentEvent implements IdeEvent {

    private final File file;
    private final long timestamp;

    public DocumentEvent(@NonNull File file) {
        this.file = file;
        this.timestamp = System.currentTimeMillis();
    }

    @NonNull
    public File getFile() {
        return file;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Dispatched when a document is opened into an editor tab.
     */
    public static class DocumentOpenEvent extends DocumentEvent {
        private final String uri;

        public DocumentOpenEvent(@NonNull File file, @Nullable String uri) {
            super(file);
            this.uri = uri;
        }

        @Nullable
        public String getUri() {
            return uri;
        }
    }

    /**
     * Dispatched when the text content of an open document is modified.
     */
    public static class DocumentChangeEvent extends DocumentEvent {
        private final String newContent;
        private final int changeOffset;
        private final int changeLength;

        public DocumentChangeEvent(@NonNull File file, @Nullable String newContent, int changeOffset, int changeLength) {
            super(file);
            this.newContent = newContent;
            this.changeOffset = changeOffset;
            this.changeLength = changeLength;
        }

        @Nullable
        public String getNewContent() {
            return newContent;
        }

        public int getChangeOffset() {
            return changeOffset;
        }

        public int getChangeLength() {
            return changeLength;
        }
    }

    /**
     * Dispatched when a document is saved to disk.
     */
    public static class DocumentSaveEvent extends DocumentEvent {
        public DocumentSaveEvent(@NonNull File file) {
            super(file);
        }
    }

    /**
     * Dispatched when an open document tab is closed.
     */
    public static class DocumentCloseEvent extends DocumentEvent {
        public DocumentCloseEvent(@NonNull File file) {
            super(file);
        }
    }
}
