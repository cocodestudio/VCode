package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionParams;
import com.cocode.vcode.ide.core.lsp.model.ColorInformation;
import com.cocode.vcode.ide.core.lsp.model.ColorPresentation;
import com.cocode.vcode.ide.core.lsp.model.ColorPresentationParams;
import com.cocode.vcode.ide.core.lsp.model.CompletionItem;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.DefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DocumentColorParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentDiagnosticParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentHighlight;
import com.cocode.vcode.ide.core.lsp.model.DocumentHighlightParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentRangeFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbol;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbolParams;
import com.cocode.vcode.ide.core.lsp.model.FoldingRange;
import com.cocode.vcode.ide.core.lsp.model.FoldingRangeParams;
import com.cocode.vcode.ide.core.lsp.model.Hover;
import com.cocode.vcode.ide.core.lsp.model.HoverParams;
import com.cocode.vcode.ide.core.lsp.model.InitializeParams;
import com.cocode.vcode.ide.core.lsp.model.PrepareRenameParams;
import com.cocode.vcode.ide.core.lsp.model.ReferenceParams;
import com.cocode.vcode.ide.core.lsp.model.RenameParams;
import com.cocode.vcode.ide.core.lsp.model.SemanticTokens;
import com.cocode.vcode.ide.core.lsp.model.SemanticTokensParams;
import com.cocode.vcode.ide.core.lsp.model.SignatureHelp;
import com.cocode.vcode.ide.core.lsp.model.SignatureHelpParams;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.TypeDefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.model.Problem;

import java.util.Collections;
import java.util.List;

/**
 * Contract that every in-process language server must fulfil.
 * <p>
 * Implements the full LSP 3.17 capability surface. All calls are in-process, strongly-typed
 * invocations executed off the Android UI thread (on CPU / IO thread pools).
 * Default methods provide safe no-op / fallback implementations for all LSP 3.17 methods,
 * preserving 100% backward compatibility with existing language server adapters.
 */
public interface LspServer {

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Legacy initialization hook using {@link ProjectIndex}.
     */
    void initialize(ProjectIndex index);

    /**
     * Initialization hook using the authoritative {@link com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService}.
     */
    default void initialize(com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService index) {
        initialize(ProjectIndex.getInstance());
    }

    /**
     * Modern LSP 3.17 initialize request hook.
     */
    default void initialize(InitializeParams params) {
    }

    /**
     * Called when the server is no longer needed (project closed / app going to background).
     */
    void shutdown();

    /**
     * @return true once the server has initialized and is ready to service requests.
     */
    boolean isReady();

    /**
     * The LSP language identifier string (e.g. {@code "html"}, {@code "javascript"}).
     */
    String getLanguageId();

    /**
     * Declares the server capabilities for capability negotiation.
     */
    default ServerCapabilities getCapabilities() {
        return ServerCapabilities.DEFAULT_WEB;
    }

    // -------------------------------------------------------------------------
    // Document Synchronization Notifications
    // -------------------------------------------------------------------------

    default void didOpen(TextDocumentItem document) {
    }

    default void didChange(VersionedTextDocumentIdentifier document, List<TextDocumentContentChangeEvent> changes) {
    }

    default void didSave(TextDocumentIdentifier document) {
    }

    default void didClose(TextDocumentIdentifier document) {
    }

    // -------------------------------------------------------------------------
    // LSP 3.17 Language Features
    // -------------------------------------------------------------------------

    default List<CompletionItem> completion(CompletionParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default CompletionItem resolveCompletion(CompletionItem item, CancellationToken token) {
        return item;
    }

    default List<Diagnostic> diagnostics(DocumentDiagnosticParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<CodeAction> codeAction(CodeActionParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<LspLocation> definition(DefinitionParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<LspLocation> typeDefinition(TypeDefinitionParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<LspLocation> references(ReferenceParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<DocumentHighlight> documentHighlight(DocumentHighlightParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default Hover hover(HoverParams params, CancellationToken token) {
        return null;
    }

    default SignatureHelp signatureHelp(SignatureHelpParams params, CancellationToken token) {
        return null;
    }

    default List<DocumentSymbol> documentSymbol(DocumentSymbolParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<TextEdit> formatting(DocumentFormattingParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<TextEdit> rangeFormatting(DocumentRangeFormattingParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default LspRange prepareRename(PrepareRenameParams params, CancellationToken token) {
        return null;
    }

    default WorkspaceEdit rename(RenameParams params, CancellationToken token) {
        return null;
    }

    default List<FoldingRange> foldingRange(FoldingRangeParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default SemanticTokens semanticTokens(SemanticTokensParams params, CancellationToken token) {
        return null;
    }

    default List<ColorInformation> documentColor(DocumentColorParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    default List<ColorPresentation> colorPresentation(ColorPresentationParams params, CancellationToken token) {
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Legacy in-process capability methods (retained for backward compatibility)
    // -------------------------------------------------------------------------

    List<LspCompletionItem> completion(LspDocument doc, LspPosition pos);

    List<Problem> diagnostics(LspDocument doc);

    LspLocation definition(LspDocument doc, LspPosition pos);

    List<LspLocation> references(LspDocument doc, LspPosition pos);

    List<LspLocation> rename(LspDocument doc, LspPosition pos);

    LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos);

    default HoverInfo hover(LspDocument doc, LspPosition pos) {
        return null;
    }
}
