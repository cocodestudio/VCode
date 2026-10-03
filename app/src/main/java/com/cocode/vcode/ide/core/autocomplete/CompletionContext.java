package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import com.cocode.vcode.ide.core.model.FileType;

import java.io.File;

/**
 * Encapsulates editor and document context passed to {@link CompletionProvider} instances.
 */
public class CompletionContext {

    private final Context context;
    private final String fullText;
    private final int cursorPos;
    private final FileType fileType;
    private final File currentFile;
    private final String wordBeforeCursor;

    public CompletionContext(Context context, String fullText, int cursorPos,
                             FileType fileType, File currentFile, String wordBeforeCursor) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.fullText = fullText != null ? fullText : "";
        this.cursorPos = cursorPos;
        this.fileType = fileType;
        this.currentFile = currentFile;
        this.wordBeforeCursor = wordBeforeCursor != null ? wordBeforeCursor : "";
    }

    public Context getContext() {
        return context;
    }

    public String getFullText() {
        return fullText;
    }

    public int getCursorPos() {
        return cursorPos;
    }

    public FileType getFileType() {
        return fileType;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public String getWordBeforeCursor() {
        return wordBeforeCursor;
    }
}
