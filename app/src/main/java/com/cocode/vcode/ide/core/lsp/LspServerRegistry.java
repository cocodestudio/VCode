package com.cocode.vcode.ide.core.lsp;

import android.content.Context;

import com.cocode.vcode.ide.core.lsp.middleware.LspMiddleware;
import com.cocode.vcode.ide.core.lsp.servers.CssLspServer;
import com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsonLspServer;
import com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer;
import com.cocode.vcode.ide.core.lsp.servers.TsLspServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry that maps LSP language identifiers to server factories and capabilities.
 */
public final class LspServerRegistry {

    private static volatile LspServerRegistry sInstance;

    private final Map<String, LspServerFactory> serverFactories = new ConcurrentHashMap<>();
    private final Map<String, ServerCapabilities> capabilitiesMap = new ConcurrentHashMap<>();
    private final List<LspMiddleware> middlewares = new CopyOnWriteArrayList<>();

    private LspServerRegistry() {
        registerDefaults();
    }

    public static LspServerRegistry getInstance() {
        if (sInstance == null) {
            synchronized (LspServerRegistry.class) {
                if (sInstance == null) {
                    sInstance = new LspServerRegistry();
                }
            }
        }
        return sInstance;
    }

    /**
     * Registers default out-of-the-box language servers for HTML, CSS, SCSS, JS, TS, JSON, and Markdown.
     */
    private void registerDefaults() {
        // HTML
        registerServer("html", HtmlLspServer::new, new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .setDefinitionProvider(true)
                .setReferencesProvider(true)
                .setFormattingProvider(true)
                .setHoverProvider(true)
                .setCodeActionProvider(true)
                .setDocumentSymbolProvider(true)
                .build());

        // CSS & SCSS
        LspServerFactory cssFactory = CssLspServer::new;
        ServerCapabilities cssCap = new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .setDefinitionProvider(true)
                .setReferencesProvider(true)
                .setFormattingProvider(true)
                .setHoverProvider(true)
                .build();
        registerServer("css", cssFactory, cssCap);
        registerServer("scss", cssFactory, cssCap);

        // JavaScript
        registerServer("javascript", JsLspServer::new, ServerCapabilities.DEFAULT_WEB);

        // TypeScript
        registerServer("typescript", TsLspServer::new, ServerCapabilities.DEFAULT_WEB);

        // JSON
        registerServer("json", ctx -> new JsonLspServer(), new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .setFormattingProvider(true)
                .build());

        // Markdown
        registerServer("markdown", MarkdownLspServer::new, new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .build());
    }

    /**
     * Dynamically registers or replaces an LSP server factory and its capabilities for a language.
     *
     * @param languageId   LSP language identifier (e.g. "html", "javascript", "python")
     * @param factory      factory instantiating the {@link LspServer}
     * @param capabilities declared capabilities of the server
     */
    public void registerServer(String languageId, LspServerFactory factory, ServerCapabilities capabilities) {
        if (languageId == null || factory == null) return;
        String key = languageId.toLowerCase();
        serverFactories.put(key, factory);
        if (capabilities != null) {
            capabilitiesMap.put(key, capabilities);
        }
    }

    /**
     * Returns true if an LSP server factory is registered for the specified language ID.
     */
    public boolean hasServer(String languageId) {
        if (languageId == null || "plaintext".equalsIgnoreCase(languageId)) return false;
        return serverFactories.containsKey(languageId.toLowerCase());
    }

    /**
     * Instantiates a new uninitialized {@link LspServer} for the given language ID.
     */
    public LspServer createServer(String languageId, Context context) {
        if (languageId == null) return null;
        LspServerFactory factory = serverFactories.get(languageId.toLowerCase());
        if (factory != null) {
            return factory.createServer(context);
        }
        return null;
    }

    /**
     * Returns declared server capabilities for the given language ID.
     */
    public ServerCapabilities getCapabilities(String languageId) {
        if (languageId == null) return ServerCapabilities.NONE;
        return capabilitiesMap.getOrDefault(languageId.toLowerCase(), ServerCapabilities.NONE);
    }

    /**
     * Adds an LSP middleware to the interceptor chain.
     */
    public void addMiddleware(LspMiddleware middleware) {
        if (middleware != null) {
            middlewares.add(middleware);
        }
    }

    public List<LspMiddleware> getMiddlewares() {
        return middlewares;
    }
}
