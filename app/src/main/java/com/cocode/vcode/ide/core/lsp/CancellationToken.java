package com.cocode.vcode.ide.core.lsp;

/**
 * Lightweight cooperative cancellation token for background analysis, AST parsing,
 * linters, and completions.
 * <p>
 * Keystrokes or editor state changes can cancel the token instantly, allowing running
 * worker threads to abort work early before consuming CPU cycles or memory.
 */
public final class CancellationToken {

    private volatile boolean cancelled = false;

    /**
     * Marks this token as cancelled.
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * Checks if cancellation has been requested.
     *
     * @return true if cancelled, false otherwise
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Throws an {@link OperationCancelledException} if cancellation has been requested.
     */
    public void throwIfCancelled() throws OperationCancelledException {
        if (cancelled) {
            throw new OperationCancelledException();
        }
    }
}
