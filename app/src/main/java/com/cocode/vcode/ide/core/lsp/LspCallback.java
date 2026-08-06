package com.cocode.vcode.ide.core.lsp;

/**
 * Generic asynchronous callback for LSP server results.
 * <p>
 * Results are always delivered on the Android main thread via
 * {@link com.cocode.vcode.ide.utils.ExecutorProvider#runOnMain(Runnable)}.
 *
 * @param <T> the result type
 */
public interface LspCallback<T> {

    /**
     * Called when the LSP request completed successfully.
     *
     * @param result the result — never null (servers return empty lists instead of null)
     */
    void onResult(T result);

    /**
     * Called when the LSP request failed (server not ready, parse error, etc.).
     *
     * @param errorMessage human-readable description of the failure
     */
    void onError(String errorMessage);
}
