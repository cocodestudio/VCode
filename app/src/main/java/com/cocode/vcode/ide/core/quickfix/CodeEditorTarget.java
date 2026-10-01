package com.cocode.vcode.ide.core.quickfix;

import android.content.Context;

/**
 * Editor buffer abstraction for applying quick fixes.
 */
public interface CodeEditorTarget {

    /**
     * @return Number of lines in the document buffer.
     */
    int getLineCount();

    /**
     * Returns the text of the line at the given 0-based line index.
     *
     * @param line 0-based line index
     * @return String content of the line without trailing newline
     */
    String getLineText(int line);

    /**
     * Replaces the character range spanning (startLine, startCol) to (endLine, endCol) with the given text.
     */
    void replaceRange(int startLine, int startCol, int endLine, int endCol, String text);

    /**
     * Inserts text at the current cursor position or EOF.
     */
    void insertText(String text);

    /**
     * Commits prior typing and opens an atomic undo transaction.
     */
    void beginAtomicGroup();

    /**
     * Closes the atomic undo transaction.
     */
    void endAtomicGroup();

    /**
     * @return Entire document text as a CharSequence.
     */
    CharSequence getText();

    /**
     * @return Android Context for localized string resolution, or null in headless tests.
     */
    Context getContext();
}
