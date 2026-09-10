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

import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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



    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacy) {
        if (legacy == null || legacy.isEmpty()) return Collections.emptyList();
        List<LspCompletionItem> result = new ArrayList<>(legacy.size());
        for (CompletionItem ci : legacy) {
            String insert = ci.getEffectiveInsertText();
            int curOffset = ci.getCursorOffset();
            if (insert != null && insert.indexOf('|') < 0) {
                if (curOffset < 0) {
                    int pipeIdx = insert.length() + curOffset;
                    if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                        insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                    }
                } else if (curOffset > 0 && curOffset <= insert.length()) {
                    insert = insert.substring(0, curOffset) + "|" + insert.substring(curOffset);
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

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File file = new File(doc.uri);
            List<Problem> problems = CssLinter.analyze(file, doc.text);
            
            // Filter out invalid lines
            List<Problem> filtered = new ArrayList<>();
            if (problems != null) {
                for (Problem p : problems) {
                    if (p != null && p.getLine() > 0) {
                        filtered.add(p);
                    }
                }
            }
            return com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.deduplicateAndSort(file, filtered);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        // 1. Check for @import via AST
        CssTokenStream stream = CssLexer.tokenize(doc.text);
        CssSyntaxTree tree = CssParser.parse(stream, doc.text);

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_AT_RULE) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    int child = tree.nodeChild[i];
                    while (child != 0) {
                        if (tree.nodeType[child] == CssSyntaxTree.N_SELECTOR) {
                            String selText = tree.nodeName[child];
                            if (selText != null && selText.startsWith("@import")) {
                                String importPath = extractQuotedString(selText);
                                if (importPath != null) {
                                    File base = new File(doc.uri).getParentFile();
                                    File target = new File(base, importPath);
                                    if (target.exists()) {
                                        return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
                                    }
                                }
                            }
                        }
                        child = tree.nodeSibling[child];
                    }
                }
            }
        }

        // 2. .class or #id selector → find its definition in HTML
        String selector = extractSelectorAtOffset(doc.text, offset);
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

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return Collections.emptyList();

        String selector = extractSelectorAtOffset(doc.text, offset);
        if (selector == null || projectIndex == null) return Collections.emptyList();

        // Strip leading . or # for plain name lookup
        String plainName = selector.startsWith(".") || selector.startsWith("#")
                ? selector.substring(1) : selector;
        plainName = plainName.trim();
        if (plainName.isEmpty()) return Collections.emptyList();
        boolean isId = selector.startsWith("#");

        final int MAX_REFS = 100;
        List<LspLocation> result = new ArrayList<>();
        for (String uri : projectIndex.getAllUris()) {
            if (result.size() >= MAX_REFS) break;
            LspDocument htmlDoc = projectIndex.getDocument(uri);
            if (htmlDoc == null || htmlDoc.text == null) continue;

            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                String searchTerm = isId ? "id=\"" + plainName + "\"" : plainName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = htmlDoc.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(htmlDoc.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = htmlDoc.text.indexOf(searchTerm, idx + step);
                }
            } else if (uri.endsWith(".js") || uri.endsWith(".ts")) {
                String searchTerm = isId ? plainName : "." + plainName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = htmlDoc.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(htmlDoc.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = htmlDoc.text.indexOf(searchTerm, idx + step);
                }
            }
        }
        return result;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public java.util.List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return java.util.Collections.emptyList();
    }

    private static String extractSelectorAtOffset(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return null;
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '.' || c == '#') {
                start--;
                break;
            } else if (c == '-' || c == '_' || Character.isLetterOrDigit(c)) {
                start--;
            } else {
                break;
            }
        }
        if (start >= text.length() || (text.charAt(start) != '.' && text.charAt(start) != '#')) {
            return null;
        }
        int end = start + 1;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '-' || c == '_' || Character.isLetterOrDigit(c)) {
                end++;
            } else {
                break;
            }
        }
        if (end > start + 1) {
            return text.substring(start, end);
        }
        return null;
    }

    private static String extractQuotedString(String str) {
        if (str == null) return null;
        int firstQuote = -1;
        char quoteChar = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"' || c == '\'') {
                firstQuote = i;
                quoteChar = c;
                break;
            }
        }
        if (firstQuote != -1) {
            int secondQuote = str.indexOf(quoteChar, firstQuote + 1);
            if (secondQuote != -1) {
                return str.substring(firstQuote + 1, secondQuote);
            }
        }
        return null;
    }
}
