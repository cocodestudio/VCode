package com.cocode.vcode.ide.core.lsp;

import android.content.Context;

import com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.FormattingOptions;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordinator for all in-process Language Server Protocol operations.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Lazy-initialises the correct {@link LspServer} the first time a file of a given
 *       language is opened.</li>
 *   <li>Routes requests (completion, diagnostics, definition, references, signature help)
 *       to the appropriate server.</li>
 *   <li>Ensures all server work runs on the IO thread pool and all callback deliveries
 *       happen on the Android main thread.</li>
 *   <li>Manages server shutdown when the project is closed.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   LspClientManager.getInstance().requestCompletion(doc, pos, (items, error) -> { ... });
 * </pre>
 *
 * <h3>Thread safety</h3>
 * Server instances are stored in a {@link ConcurrentHashMap} and initialised exactly once
 * via double-checked locking in {@link #getOrStartServer(String)}.
 */
public final class LspClientManager {

    private static volatile LspClientManager sInstance;

    /**
     * Active servers keyed by LSP language id (e.g. "html", "javascript").
     */
    private final ConcurrentHashMap<String, LspServer> servers = new ConcurrentHashMap<>();

    /**
     * Application context — used to pass to server engines for JSON asset loading.
     */
    private volatile Context appContext;

    private LspClientManager() {
    }

    public static LspClientManager getInstance() {
        if (sInstance == null) {
            synchronized (LspClientManager.class) {
                if (sInstance == null) sInstance = new LspClientManager();
            }
        }
        return sInstance;
    }

    /**
     * Stores the application context so that LSP servers can pass it to their autocomplete
     * engines for loading JSON keyword/property assets from the APK assets directory.
     * Must be called once before any server is used, e.g. from {@code LspEditorBridge.attach()}.
     *
     * @param context any Context; {@code getApplicationContext()} is called internally
     */
    public void setApplicationContext(Context context) {
        if (context != null) this.appContext = context.getApplicationContext();
    }

    // -------------------------------------------------------------------------
    // Request API — all callbacks delivered on the main thread
    // -------------------------------------------------------------------------

    /**
     * Requests completion items for the given position in the document with cancellation support.
     *
     * @param doc               current document snapshot
     * @param pos               caret position
     * @param cancellationToken optional token to abort execution if a newer keystroke occurred
     * @param callback          result delivered on the main thread
     */
    public void requestCompletion(LspDocument doc, LspPosition pos,
                                  CancellationToken cancellationToken,
                                  LspCallback<List<LspCompletionItem>> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                LspServer server = getOrStartServer(doc.languageId);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                if (server == null || !server.isReady()) {
                    deliverError(callback, "Language server not ready");
                    return;
                }

                // Route through LSP 3.17 completion to obtain rich items including additionalTextEdits
                CompletionParams params = new CompletionParams(
                        new TextDocumentIdentifier(doc.uri),
                        doc.toModelPosition(pos)
                );
                List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> modelItems = server.completion(params, cancellationToken);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;

                if (modelItems != null && !modelItems.isEmpty()) {
                    List<LspCompletionItem> converted = new ArrayList<>(modelItems.size());
                    for (com.cocode.vcode.ide.core.lsp.model.CompletionItem mi : modelItems) {
                        int kind = mi.kind != null ? mi.kind.getValue() : LspCompletionItem.KIND_TEXT;
                        String docStr = mi.documentation != null ? mi.documentation.value : null;
                        String insert = mi.insertText != null ? mi.insertText : mi.label;
                        int replaceLength = -1;
                        if (mi.textEdit != null && mi.textEdit.range != null) {
                            int startOffset = doc.toOffset(mi.textEdit.range.start);
                            int endOffset = doc.toOffset(mi.textEdit.range.end);
                            if (startOffset >= 0 && endOffset >= startOffset) {
                                replaceLength = endOffset - startOffset;
                            }
                        }
                        if (replaceLength < 0 && doc.text != null) {
                            int flatCursor = doc.toOffset(pos);
                            if (flatCursor > 0 && flatCursor <= doc.text.length()) {
                                int wordStart = flatCursor;
                                while (wordStart > 0) {
                                    char c = doc.text.charAt(wordStart - 1);
                                    if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-') {
                                        wordStart--;
                                    } else {
                                        break;
                                    }
                                }
                                replaceLength = flatCursor - wordStart;
                            }
                        }
                        converted.add(new LspCompletionItem(
                                mi.label,
                                insert,
                                kind,
                                mi.detail,
                                docStr,
                                replaceLength,
                                mi.additionalTextEdits
                        ));
                    }
                    deliverResult(callback, converted);
                    return;
                }

                // Fall back to legacy completion if LSP 3.17 returned empty
                List<LspCompletionItem> items = server.completion(doc, pos);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                if (items != null && !items.isEmpty() && doc.text != null) {
                    int flatCursor = doc.toOffset(pos);
                    int wordStart = flatCursor;
                    if (flatCursor > 0 && flatCursor <= doc.text.length()) {
                        while (wordStart > 0) {
                            char c = doc.text.charAt(wordStart - 1);
                            if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-') {
                                wordStart--;
                            } else {
                                break;
                            }
                        }
                    }
                    int defaultReplaceLen = flatCursor - wordStart;
                    List<LspCompletionItem> withReplace = new ArrayList<>(items.size());
                    for (LspCompletionItem item : items) {
                        if (item.replaceLength >= 0) {
                            withReplace.add(item);
                        } else {
                            withReplace.add(new LspCompletionItem(
                                    item.label,
                                    item.insertText,
                                    item.kind,
                                    item.detail,
                                    item.documentation,
                                    defaultReplaceLen,
                                    item.additionalTextEdits
                            ));
                        }
                    }
                    items = withReplace;
                }
                deliverResult(callback, items != null ? items : Collections.emptyList());
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    public void requestCompletion(LspDocument doc, LspPosition pos,
                                  LspCallback<List<LspCompletionItem>> callback) {
        requestCompletion(doc, pos, null, callback);
    }

    /**
     * Requests diagnostics (errors / warnings) for the document with cancellation support.
     * Runs on the dedicated diagnostic executor — isolated from disk I/O — so results
     * arrive immediately without waiting for auto-saves or project indexing to complete.
     *
     * @param doc               current document snapshot
     * @param cancellationToken optional token to abort execution if a newer keystroke occurred
     * @param callback          result delivered on the main thread
     */
    public void requestDiagnostics(LspDocument doc,
                                   CancellationToken cancellationToken,
                                   LspCallback<List<Problem>> callback) {
        ExecutorProvider.getInstance().runOnDiagnostic(() -> {
            try {
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                LspServer server = getOrStartServer(doc.languageId);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                if (server == null || !server.isReady()) {
                    deliverResult(callback, Collections.emptyList());
                    return;
                }
                List<Problem> diags = server.diagnostics(doc);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                deliverResult(callback, diags != null ? diags : Collections.emptyList());
            } catch (Throwable t) {
                deliverError(callback, t.getMessage());
            }
        });
    }

    public void requestDiagnostics(LspDocument doc,
                                   LspCallback<List<Problem>> callback) {
        requestDiagnostics(doc, null, callback);
    }

    /**
     * Requests the definition location for the symbol at the given position.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread; result may be null if not found
     */
    public void requestDefinition(LspDocument doc, LspPosition pos,
                                  LspCallback<LspLocation> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverError(callback, "Language server not ready");
                    return;
                }
                LspLocation loc = server.definition(doc, pos);
                deliverResult(callback, loc);
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    /**
     * Requests all references to the symbol at the given position.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread
     */
    public void requestReferences(LspDocument doc, LspPosition pos,
                                  LspCallback<List<LspLocation>> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverError(callback, "Language server not ready");
                    return;
                }
                List<LspLocation> refs = server.references(doc, pos);
                deliverResult(callback, refs != null ? refs : Collections.emptyList());
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    /**
     * Requests all locations of the symbol to be renamed.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread
     */
    public void requestRename(LspDocument doc, LspPosition pos,
                              LspCallback<List<LspLocation>> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverError(callback, "Language server not ready");
                    return;
                }
                List<LspLocation> refs = server.rename(doc, pos);
                deliverResult(callback, refs != null ? refs : Collections.emptyList());
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    /**
     * Requests signature help at the given position.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread; result may be null if not applicable
     */
    public void requestSignatureHelp(LspDocument doc, LspPosition pos,
                                     LspCallback<LspSignatureHelp> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverResult(callback, null);
                    return;
                }
                LspSignatureHelp help = server.signatureHelp(doc, pos);
                deliverResult(callback, help);
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    /**
     * Requests rich hover documentation for the symbol at the given position.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread; result may be null if not available
     */
    public void requestHover(LspDocument doc, LspPosition pos,
                             LspCallback<HoverInfo> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverResult(callback, null);
                    return;
                }
                HoverInfo hover = server.hover(doc, pos);
                for (com.cocode.vcode.ide.core.lsp.middleware.LspMiddleware mw : LspServerRegistry.getInstance().getMiddlewares()) {
                    hover = mw.onHover(doc, pos, hover);
                }
                deliverResult(callback, hover);
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    /**
     * Requests formatting for the given document using LSP 3.17 formatting pipeline.
     */
    public void requestFormatting(LspDocument doc, FormattingOptions options,
                                  LspCallback<List<TextEdit>> callback) {
        requestFormatting(doc, options, null, callback);
    }

    public void requestFormatting(LspDocument doc, FormattingOptions options,
                                  CancellationToken cancellationToken,
                                  LspCallback<List<TextEdit>> callback) {
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                LspServer server = getOrStartServer(doc.languageId);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                if (server == null || !server.isReady()) {
                    deliverResult(callback, Collections.emptyList());
                    return;
                }
                DocumentFormattingParams params = new DocumentFormattingParams(
                        new TextDocumentIdentifier(doc.uri),
                        options != null ? options : FormattingOptions.DEFAULT
                );
                List<TextEdit> edits = server.formatting(params, cancellationToken);
                if (cancellationToken != null && cancellationToken.isCancelled()) return;
                deliverResult(callback, edits != null ? edits : Collections.emptyList());
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Document Synchronization Notifications (LSP 3.17)
    // -------------------------------------------------------------------------

    public void didOpen(TextDocumentItem item) {
        if (item == null) return;
        LspDocumentManager.getInstance().openDocument(item);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(item.languageId);
                if (server != null && server.isReady()) {
                    server.didOpen(item);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public void didChange(VersionedTextDocumentIdentifier docId, List<TextDocumentContentChangeEvent> changes, String languageId, String fullText) {
        if (docId == null) return;
        LspDocumentManager.getInstance().updateDocument(docId, changes, fullText);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(languageId);
                if (server != null && server.isReady()) {
                    server.didChange(docId, changes);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public void didSave(TextDocumentIdentifier docId, String languageId) {
        if (docId == null) return;
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(languageId);
                if (server != null && server.isReady()) {
                    server.didSave(docId);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public void didClose(TextDocumentIdentifier docId, String languageId) {
        if (docId == null) return;
        LspDocumentManager.getInstance().closeDocument(docId);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            try {
                LspServer server = getOrStartServer(languageId);
                if (server != null && server.isReady()) {
                    server.didClose(docId);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Shuts down all active language servers and clears document caches and the {@link ProjectIndex}.
     * Should be called when the project is closed.
     */
    public void shutdownAll() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            for (LspServer server : servers.values()) {
                try {
                    server.shutdown();
                } catch (Exception ignored) {
                }
            }
            servers.clear();
            LspDocumentManager.getInstance().clear();
            ProjectIndex.getInstance().clear();
        });
    }

    /**
     * Returns true if the server for the given language id is currently running and ready.
     *
     * @param languageId LSP language identifier
     */
    public boolean isServerReady(String languageId) {
        LspServer server = servers.get(languageId);
        return server != null && server.isReady();
    }

    /**
     * Returns the active LspServer for the given language id, initialising it if needed.
     */
    public LspServer getServer(String languageId) {
        return getOrStartServer(languageId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the running server for the given language id, starting and initialising it
     * lazily on first use.
     *
     * @param languageId LSP language identifier
     * @return the server, or null if the language is not supported
     */
    private LspServer getOrStartServer(String languageId) {
        LspServer existing = servers.get(languageId);
        if (existing != null) return existing;

        // Double-checked to avoid creating two servers for the same language
        synchronized (this) {
            existing = servers.get(languageId);
            if (existing != null) return existing;

            LspServer newServer = createServer(languageId);
            if (newServer == null) return null;

            servers.put(languageId, newServer);
            // Initialise with unified WorkspaceIndexService
            newServer.initialize(WorkspaceIndexService.getInstance());
            return newServer;
        }
    }

    /**
     * Checks if an LSP server is registered and supported for the given language id.
     */
    public boolean hasServerForLanguage(String languageId) {
        return LspServerRegistry.getInstance().hasServer(languageId);
    }

    /**
     * Factory method that maps a language id to its server implementation via {@link LspServerRegistry}.
     *
     * @param languageId LSP language identifier
     * @return new uninitialised server instance, or null if unsupported
     */
    private LspServer createServer(String languageId) {
        return LspServerRegistry.getInstance().createServer(languageId, appContext);
    }

    private <T> void deliverResult(LspCallback<T> callback, T result) {
        if (callback == null) return;
        ExecutorProvider.getInstance().runOnMain(() -> callback.onResult(result));
    }

    private <T> void deliverError(LspCallback<T> callback, String message) {
        if (callback == null) return;
        ExecutorProvider.getInstance().runOnMain(() -> callback.onError(message));
    }
}
