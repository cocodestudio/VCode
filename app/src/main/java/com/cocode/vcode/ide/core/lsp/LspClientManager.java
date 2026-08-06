package com.cocode.vcode.ide.core.lsp;

import android.content.Context;

import com.cocode.vcode.ide.utils.ExecutorProvider;

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
     * Requests completion items for the given position in the document.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread
     */
    public void requestCompletion(LspDocument doc, LspPosition pos,
                                  LspCallback<List<LspCompletionItem>> callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverError(callback, "Language server not ready");
                    return;
                }
                List<LspCompletionItem> items = server.completion(doc, pos);
                deliverResult(callback, items != null ? items : Collections.emptyList());
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
    }

    /**
     * Requests diagnostics (errors / warnings) for the document.
     *
     * @param doc      current document snapshot
     * @param callback result delivered on the main thread
     */
    public void requestDiagnostics(LspDocument doc,
                                   LspCallback<List<LspDiagnostic>> callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                LspServer server = getOrStartServer(doc.languageId);
                if (server == null || !server.isReady()) {
                    deliverResult(callback, Collections.emptyList());
                    return;
                }
                List<LspDiagnostic> diags = server.diagnostics(doc);
                deliverResult(callback, diags != null ? diags : Collections.emptyList());
            } catch (Exception e) {
                deliverError(callback, e.getMessage());
            }
        });
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
        ExecutorProvider.getInstance().runOnIo(() -> {
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
        ExecutorProvider.getInstance().runOnIo(() -> {
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
     * Requests signature help at the given position.
     *
     * @param doc      current document snapshot
     * @param pos      caret position
     * @param callback result delivered on the main thread; result may be null if not applicable
     */
    public void requestSignatureHelp(LspDocument doc, LspPosition pos,
                                     LspCallback<LspSignatureHelp> callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
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

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Shuts down all active language servers and clears the {@link ProjectIndex}.
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
            // Initialise on the current IO thread
            newServer.initialize(ProjectIndex.getInstance());
            return newServer;
        }
    }

    /**
     * Factory method that maps a language id to its server implementation.
     * Add new language servers here as they are implemented.
     *
     * @param languageId LSP language identifier
     * @return new uninitialised server instance, or null if unsupported
     */
    private LspServer createServer(String languageId) {
        // Servers will be registered here as they are implemented in subsequent phases.
        switch (languageId) {
            case "html":
                return new com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer(appContext);
            case "css":
            case "scss":
                return new com.cocode.vcode.ide.core.lsp.servers.CssLspServer(appContext);
            case "javascript":
                return new com.cocode.vcode.ide.core.lsp.servers.JsLspServer(appContext);
            case "typescript":
                return new com.cocode.vcode.ide.core.lsp.servers.TsLspServer(appContext);
            case "json":
                return new com.cocode.vcode.ide.core.lsp.servers.JsonLspServer();
            case "markdown":
                return new com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer();
            default:
                return null;
        }
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
