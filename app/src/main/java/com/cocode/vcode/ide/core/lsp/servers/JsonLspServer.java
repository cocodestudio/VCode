package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonLinter;
import com.cocode.vcode.ide.core.lsp.LspCompletionConverter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * In-process Language Server for JSON files.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Completions</b>: Delegates to the existing {@link JsonAutoCompleteEngine},
 *       which already provides schema-aware completions for {@code package.json},
 *       {@code tsconfig.json}, etc., as well as generic JSON key/value suggestions.</li>
 *   <li><b>Diagnostics</b>: Uses {@link JsonLinter} to detect syntax errors and
 *       malformed JSON, mapped to {@link Problem} objects.</li>
 *   <li><b>Go to Definition</b>: Resolves file path string values (e.g. {@code "main": "./src/index.js"})
 *       to their corresponding file in the {@link ProjectIndex}.</li>
 *   <li><b>Find References</b>: Not applicable for JSON; returns empty list.</li>
 *   <li><b>Signature Help</b>: Not applicable for JSON; returns null.</li>
 * </ul>
 */
public final class JsonLspServer implements LspServer {

    /**
     * Reusable autocomplete engine.
     * {@link JsonAutoCompleteEngine} requires a {@link android.content.Context} only for
     * schema asset loading; since we target offline in-process use we pass null and
     * rely on the statically-initialised schema maps in the engine.
     */
    private final JsonAutoCompleteEngine completeEngine = new JsonAutoCompleteEngine(null);
    private volatile boolean ready = false;

    // -------------------------------------------------------------------------
    // LspServer contract
    // -------------------------------------------------------------------------

    /**
     * Converts the legacy {@link CompletionItem} list (from the existing engine) to LSP
     * {@link LspCompletionItem} list.
     */
    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacy) {
        return LspCompletionConverter.convert(legacy);
    }


    /**
     * Extracts the string value of the JSON key or value at the given flat offset.
     * Returns null if the cursor is not inside a string.
     */
    private static String extractStringValueAtCursor(String text, int offset) {
        if (text == null || offset < 0 || offset >= text.length()) return null;
        // Walk backward to find opening quote
        int start = offset - 1;
        while (start >= 0 && text.charAt(start) != '"' && text.charAt(start) != '\n') start--;
        if (start < 0 || text.charAt(start) != '"') return null;
        // Walk forward to find closing quote
        int end = offset;
        while (end < text.length() && text.charAt(end) != '"' && text.charAt(end) != '\n') end++;
        if (end >= text.length() || text.charAt(end) != '"') return null;
        return text.substring(start + 1, end);
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        // JsonAutoCompleteEngine initialises its schema maps statically; nothing
        // async is needed here. Mark ready immediately.
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    // -------------------------------------------------------------------------
    // Go to Definition
    // -------------------------------------------------------------------------

    @Override
    public boolean isReady() {
        return ready;
    }

    // -------------------------------------------------------------------------
    // Find References — not meaningful for JSON
    // -------------------------------------------------------------------------

    @Override
    public String getLanguageId() {
        return "json";
    }

    // -------------------------------------------------------------------------
    // Signature Help — not applicable for JSON
    // -------------------------------------------------------------------------

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        // Delegate to the existing JsonAutoCompleteEngine which already handles
        // schema detection, context detection, key/value split, etc.
        List<CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        return convertCompletions(legacy);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File docFile = doc.uri != null ? new File(doc.uri) : null;
            return JsonLinter.analyze(docFile, doc.text);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        // Find the string value the cursor is inside
        String value = extractStringValueAtCursor(doc.text, doc.toOffset(pos));
        if (value == null || value.isEmpty()) return null;

        // Only resolve values that look like file paths
        if (!value.startsWith("./") && !value.startsWith("../") && !value.startsWith("/")) {
            return null;
        }

        // Resolve relative to the project root
        String projectRoot = ProjectIndex.getInstance().getProjectRoot();
        if (projectRoot == null) return null;

        java.io.File base = new java.io.File(doc.uri).getParentFile();
        java.io.File target = new java.io.File(base, value);

        // Try exact path first, then with common extensions appended
        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        for (String ext : new String[]{".js", ".ts", ".json", ".html", ".css"}) {
            java.io.File withExt = new java.io.File(target.getAbsolutePath() + ext);
            if (withExt.exists()) {
                return new LspLocation(withExt.getAbsolutePath(), new LspRange(0, 0, 0, 0));
            }
        }
        return null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        return Collections.emptyList();
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public java.util.List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return java.util.Collections.emptyList();
    }
}
