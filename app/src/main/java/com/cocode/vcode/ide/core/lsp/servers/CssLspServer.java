package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssLinter;
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
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for CSS / SCSS files.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Completions</b>: Delegates to {@link CssAutoCompleteEngine} for property names,
 *       property values, selectors (class names resolved from indexed HTML files), pseudo-classes,
 *       pseudo-elements, and {@code @media} queries.</li>
 *   <li><b>Diagnostics</b>: Delegates to {@link CssLinter} (unknown properties, invalid values,
 *       missing units, duplicate properties, empty selectors).</li>
 *   <li><b>Go to Definition</b>: Resolves a CSS class selector to the HTML file(s) that use it,
 *       and {@code @import "..."} to the imported CSS file.</li>
 *   <li><b>Find References</b>: Finds all HTML files in the project that use a CSS class/id selector.</li>
 *   <li><b>Signature Help</b>: Not applicable for CSS.</li>
 * </ul>
 */
public final class CssLspServer implements LspServer {

    private static final Pattern SELECTOR_AT_CURSOR =
            Pattern.compile("([.#][\\w-]+)");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("@import\\s+[\"']([^\"']+)[\"']");
    private final CssAutoCompleteEngine completeEngine;
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public CssLspServer(Context context) {
        this.completeEngine = new CssAutoCompleteEngine(context);
    }

    public CssLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // LspServer contract
    // -------------------------------------------------------------------------

    private static String extractSelectorAtCursor(String line, int cursorChar) {
        Matcher m = SELECTOR_AT_CURSOR.matcher(line);
        while (m.find()) {
            if (cursorChar >= m.start() && cursorChar <= m.end()) {
                return m.group(1);
            }
        }
        return null;
    }

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
                    null
            ));
        }
        return result;
    }

    private static int mapKind(CompletionItem.Type type) {
        if (type == null) return LspCompletionItem.KIND_TEXT;
        switch (type) {
            case CSS_PROPERTY:
                return LspCompletionItem.KIND_PROPERTY;
            case CSS_VALUE:
                return LspCompletionItem.KIND_VALUE;
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }

    private static List<LspDiagnostic> convertProblems(List<Problem> problems) {
        if (problems == null || problems.isEmpty()) return Collections.emptyList();
        List<LspDiagnostic> result = new ArrayList<>(problems.size());
        for (Problem p : problems) {
            if (p == null) continue;
            // CssLinter occasionally reports line 0 (1-based) for selector-level checks
            // that haven't tracked the line correctly. Skip those rather than rendering
            // a spurious squiggle at the top of the file.
            if (p.getLine() <= 0) continue;
            int line = p.getLine() - 1; // Convert 1-based → 0-based
            int col = Math.max(0, p.getColumn());
            // getLength() from CssLinter covers the whole declaration span; clamp to a
            // reasonable maximum so the squiggle stays within the token that caused the error.
            int length = p.getLength() > 0 ? Math.min(p.getLength(), 80) : 1;
            int end = col + length;
            int severity = p.getSeverity() == Problem.Severity.ERROR
                    ? LspDiagnostic.SEVERITY_ERROR
                    : p.getSeverity() == Problem.Severity.WARNING
                    ? LspDiagnostic.SEVERITY_WARNING
                    : LspDiagnostic.SEVERITY_INFORMATION;
            result.add(new LspDiagnostic(
                    new LspRange(line, col, line, end),
                    severity,
                    p.getMessage(),
                    null,
                    "css"
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
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
    // Find References
    // -------------------------------------------------------------------------

    @Override
    public String getLanguageId() {
        return "css";
    }

    // -------------------------------------------------------------------------
    // Signature Help — not applicable for CSS
    // -------------------------------------------------------------------------

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        completeEngine.setCurrentFile(new File(doc.uri));
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

        File file = new File(doc.uri);
        List<Problem> problems = CssLinter.analyze(file, doc.text);
        return convertProblems(problems);
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        String lineText = doc.getLine(pos.line);

        // @import "..." → resolve imported CSS file
        Matcher importMatcher = IMPORT_PATTERN.matcher(lineText);
        while (importMatcher.find()) {
            if (pos.character >= importMatcher.start() && pos.character <= importMatcher.end()) {
                String importPath = importMatcher.group(1);
                File base = new File(doc.uri).getParentFile();
                File target = new File(base, importPath);
                if (target.exists()) {
                    return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
                }
            }
        }

        // .class or #id selector → find HTML file that uses it
        String selector = extractSelectorAtCursor(lineText, pos.character);
        if (selector != null && projectIndex != null) {
            return findSelectorUsageInHtml(selector);
        }

        return null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();

        String lineText = doc.getLine(pos.line);
        String selector = extractSelectorAtCursor(lineText, pos.character);
        if (selector == null || projectIndex == null) return Collections.emptyList();

        // Strip leading . or # for plain name lookup
        String plainName = selector.startsWith(".") || selector.startsWith("#")
                ? selector.substring(1) : selector;

        List<LspLocation> result = new ArrayList<>();
        for (String uri : projectIndex.getAllUris()) {
            if (!uri.endsWith(".html") && !uri.endsWith(".htm")) continue;
            LspDocument htmlDoc = projectIndex.getDocument(uri);
            if (htmlDoc == null || htmlDoc.text == null) continue;

            // Look for class="...plainName..." or id="plainName"
            String searchTerm = selector.startsWith("#")
                    ? "id=\"" + plainName + "\""
                    : plainName; // class may appear in class="... name ..."
            int idx = htmlDoc.text.indexOf(searchTerm);
            if (idx >= 0) {
                LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor
                        .offsetToPosition(htmlDoc.text, idx);
                result.add(new LspLocation(uri, new LspRange(
                        refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
            }
        }
        return result;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    private LspLocation findSelectorUsageInHtml(String selector) {
        if (projectIndex == null) return null;
        String plainName = selector.startsWith(".") || selector.startsWith("#")
                ? selector.substring(1) : selector;
        boolean isId = selector.startsWith("#");

        for (String uri : projectIndex.getAllUris()) {
            if (!uri.endsWith(".html") && !uri.endsWith(".htm")) continue;
            LspDocument htmlDoc = projectIndex.getDocument(uri);
            if (htmlDoc == null || htmlDoc.text == null) continue;

            String searchTerm = isId
                    ? "id=\"" + plainName + "\""
                    : "class=\"" + plainName;
            int idx = htmlDoc.text.indexOf(searchTerm);
            if (idx >= 0) {
                LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor
                        .offsetToPosition(htmlDoc.text, idx);
                return new LspLocation(uri, new LspRange(refPos,
                        new LspPosition(refPos.line, refPos.character + searchTerm.length())));
            }
        }
        return null;
    }
}

