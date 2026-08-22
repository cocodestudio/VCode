package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssLinter;
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
                    null,
                    ci.getReplaceLength()
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
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        File file = new File(doc.uri);
        List<Problem> problems = CssLinter.analyze(file, doc.text);
        
        // CssLinter occasionally reports line 0 (1-based) for selector-level checks
        // that haven't tracked the line correctly. Filter those out rather than rendering
        // a spurious squiggle at the top of the file.
        if (problems != null) {
            List<Problem> filtered = new ArrayList<>();
            for (Problem p : problems) {
                if (p != null && p.getLine() > 0) {
                    filtered.add(p);
                }
            }
            return filtered;
        }
        return Collections.emptyList();
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

        // .class or #id selector → find its definition
        String selector = extractSelectorAtCursor(lineText, pos.character);
        if (selector != null && projectIndex != null) {
            List<LspLocation> defs = projectIndex.findDefinitions(selector);
            if (!defs.isEmpty()) {
                return defs.get(0);
            }
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
        boolean isId = selector.startsWith("#");

        List<LspLocation> result = new ArrayList<>();
        for (String uri : projectIndex.getAllUris()) {
            LspDocument htmlDoc = projectIndex.getDocument(uri);
            if (htmlDoc == null || htmlDoc.text == null) continue;

            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                String searchTerm = isId ? "id=\"" + plainName + "\"" : plainName;
                int idx = htmlDoc.text.indexOf(searchTerm);
                while (idx >= 0) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(htmlDoc.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = htmlDoc.text.indexOf(searchTerm, idx + searchTerm.length());
                }
            } else if (uri.endsWith(".js") || uri.endsWith(".ts")) {
                String searchTerm = isId ? plainName : "." + plainName;
                int idx = htmlDoc.text.indexOf(searchTerm);
                while (idx >= 0) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(htmlDoc.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = htmlDoc.text.indexOf(searchTerm, idx + searchTerm.length());
                }
            }
        }
        return result;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }


}

