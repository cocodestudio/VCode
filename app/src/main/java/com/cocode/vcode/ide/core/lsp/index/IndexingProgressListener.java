package com.cocode.vcode.ide.core.lsp.index;

/**
 * Listener interface for monitoring project symbol indexing progress and status.
 */
public interface IndexingProgressListener {

    /**
     * Invoked when indexing commences.
     *
     * @param totalFiles total candidate files discovered in workspace
     */
    void onIndexingStarted(int totalFiles);

    /**
     * Invoked when a file is scanned or processed.
     *
     * @param processedFiles count of files processed so far
     * @param totalFiles     total count of files
     * @param currentFileName name of the file being processed
     */
    void onIndexingProgress(int processedFiles, int totalFiles, String currentFileName);

    /**
     * Invoked upon successful completion of the indexing pass.
     *
     * @param totalIndexedSymbols total symbols indexed across segments
     * @param durationMs          total indexing duration in milliseconds
     */
    void onIndexingCompleted(int totalIndexedSymbols, long durationMs);

    /**
     * Invoked when indexing encounters an error.
     *
     * @param error the encountered throwable
     */
    void onIndexingError(Throwable error);
}
