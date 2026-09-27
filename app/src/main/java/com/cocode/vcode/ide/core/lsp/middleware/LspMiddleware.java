package com.cocode.vcode.ide.core.lsp.middleware;

import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.model.Problem;

import java.util.List;

/**
 * Interceptor pipeline for LSP requests and responses, allowing framework extensions,
 * caching layers, or analytics to enrich language server results without altering server code.
 */
public interface LspMiddleware {

    default List<LspCompletionItem> onCompletion(LspDocument doc, LspPosition pos, List<LspCompletionItem> items) {
        return items;
    }

    default List<Problem> onDiagnostics(LspDocument doc, List<Problem> diagnostics) {
        return diagnostics;
    }

    default LspSignatureHelp onSignatureHelp(LspDocument doc, LspPosition pos, LspSignatureHelp help) {
        return help;
    }
}
