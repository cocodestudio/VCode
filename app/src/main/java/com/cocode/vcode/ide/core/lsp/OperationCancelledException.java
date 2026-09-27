package com.cocode.vcode.ide.core.lsp;

/**
 * Thrown when an asynchronous or cooperative background operation is cancelled
 * via a {@link CancellationToken}.
 */
public class OperationCancelledException extends RuntimeException {

    public OperationCancelledException() {
        super("Operation was cancelled");
    }

    public OperationCancelledException(String message) {
        super(message);
    }
}
