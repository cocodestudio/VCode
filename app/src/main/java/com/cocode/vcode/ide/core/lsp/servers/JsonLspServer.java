package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonError;
import com.cocode.vcode.ide.core.language.json.JsonValidator;
import com.cocode.vcode.ide.core.language.json.ValidationReport;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDiagnostic;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
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
 *   <li><b>Diagnostics</b>: Uses {@link JsonValidator} to detect syntax errors and
 *       malformed JSON, mapped to LSP {@link LspDiagnostic} objects.</li>
 *   <li><b>Go to Definition</b>: Resolves file path string values (e.g. {@code "main": "./src/index.js"})
 *       to their corresponding file in the {@link ProjectIndex}.</li>
 *   <li><b>Find References</b>: Not applicable for JSON; returns empty list.</li>
 *   <li><b>Signature Help</b>: Not applicable for JSON; returns null.</li>
 * </ul>
 */
public final class JsonLspServer implements LspServer {

    /**
     * Reusable validator — stateless, safe to call from any thread.
     */
    private final JsonValidator validator = new JsonValidator();
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
    private static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacy) {
        if (legacy == null || legacy.isEmpty()) return Collections.emptyList();
        List<LspCompletionItem> result = new ArrayList<>(legacy.size());
        for (CompletionItem ci : legacy) {
            String insert = ci.getEffectiveInsertText();
            int curOffset = ci.getCursorOffset();
            if (curOffset < 0) {
                int pipeIdx = insert.length() + curOffset;
                if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                    insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                }
            }
            int kind = mapKind(ci.getType());
            result.add(new LspCompletionItem(
                    ci.getLabel(),
                    insert,
                    kind,
                    ci.getDetail(),
                    null,
                    ci.getReplaceLength()
            ));
        }
        return result;
    }

    /**
     * Maps legacy {@link CompletionItem.Type} to LSP completion item kind constants.
     */
    private static int mapKind(CompletionItem.Type type) {
        if (type == null) return LspCompletionItem.KIND_TEXT;
        switch (type) {
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case JSON_KEY:
                return LspCompletionItem.KIND_PROPERTY;
            case VALUE:
                return LspCompletionItem.KIND_VALUE;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }

    /**
     * Determines the token length at a 1-based (line, column) position for error range reporting.
     * Mirrors the logic in the existing {@code JsonLinter.getTokenLength()}.
     */
    private static int getTokenLength(String text, int line, int column) {
        int l = 1;
        int idx = 0;
        for (int i = 0; i < text.length(); i++) {
            if (l == line) {
                idx = i + column - 1;
                break;
            }
            if (text.charAt(i) == '\n') l++;
        }
        if (idx < 0 || idx >= text.length()) return 1;
        int end = idx;
        char c = text.charAt(idx);
        if (c == '"' || c == '\'') {
            do end++;
            while (end < text.length() && text.charAt(end) != c && text.charAt(end) != '\n');
            if (end < text.length()) end++;
        } else if (Character.isLetterOrDigit(c)) {
            while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) end++;
        } else {
            end = idx + 1;
        }
        return Math.max(1, end - idx);
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
    public List<LspDiagnostic> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        ValidationReport report = validator.validate(doc.text);
        List<JsonError> errors = report.getErrors();
        if (errors == null || errors.isEmpty()) return Collections.emptyList();

        List<LspDiagnostic> result = new ArrayList<>(errors.size());
        for (JsonError err : errors) {
            // JsonError uses 1-based line; LSP uses 0-based
            int line = Math.max(0, err.line - 1);
            int col = Math.max(0, err.column - 1);
            int tokenLen = getTokenLength(doc.text, err.line, err.column);

            int severity = "WARNING".equalsIgnoreCase(err.severity)
                    ? LspDiagnostic.SEVERITY_WARNING
                    : LspDiagnostic.SEVERITY_ERROR;

            LspRange range = new LspRange(line, col, line, col + tokenLen);
            result.add(new LspDiagnostic(range, severity, err.message, null, "json"));
        }
        return result;
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
}
