package com.cocode.vcode.ide.core.lsp;

import java.util.List;

/**
 * Contract that every in-process language server must fulfil.
 * <p>
 * Servers are instantiated lazily (when the first file of their language is opened) and run
 * exclusively on background threads supplied by {@link com.cocode.vcode.ide.utils.ExecutorProvider}.
 * All return values are plain Java objects — no JSON-RPC transport is used.
 */
public interface LspServer {

    /**
     * Called once after construction.
     * The server should use this opportunity to warm up internal indexes using the
     * supplied {@link ProjectIndex}, which already contains a snapshot of every file
     * in the project.
     *
     * @param index shared project-wide symbol index
     */
    void initialize(ProjectIndex index);

    /**
     * Called when the server is no longer needed (project closed / app going to background).
     * The server should release all resources it holds.
     */
    void shutdown();

    /**
     * @return true once {@link #initialize(ProjectIndex)} has finished and the server is
     * ready to service requests.
     */
    boolean isReady();

    /**
     * The LSP language identifier string (e.g. {@code "html"}, {@code "javascript"}).
     * Must match the value returned by
     * {@link com.cocode.vcode.ide.core.model.FileType#getLspLanguageId()}.
     */
    String getLanguageId();

    // -------------------------------------------------------------------------
    // Core LSP capability methods
    // -------------------------------------------------------------------------

    /**
     * Compute completion items at the given caret position in the document.
     *
     * @param doc current document snapshot
     * @param pos zero-based line/character position of the caret
     * @return list of completion items, never null
     */
    List<LspCompletionItem> completion(LspDocument doc, LspPosition pos);

    /**
     * Compute all diagnostics (errors / warnings) for the given document.
     *
     * @param doc current document snapshot
     * @return list of diagnostics, never null
     */
    List<LspDiagnostic> diagnostics(LspDocument doc);

    /**
     * Resolve the definition location for the symbol at the given position.
     *
     * @param doc current document snapshot
     * @param pos caret position
     * @return location of the definition, or null if not found
     */
    LspLocation definition(LspDocument doc, LspPosition pos);

    /**
     * Find all references to the symbol at the given position across the project.
     *
     * @param doc current document snapshot
     * @param pos caret position
     * @return list of reference locations, never null
     */
    List<LspLocation> references(LspDocument doc, LspPosition pos);

    /**
     * Compute signature help (parameter hints) at the given position.
     * Typically triggered when the user types {@code (} or {@code ,}.
     *
     * @param doc current document snapshot
     * @param pos caret position
     * @return signature help result, or null if not applicable
     */
    LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos);
}
