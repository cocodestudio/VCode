package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.html.HtmlTagParser;
import com.cocode.vcode.ide.core.lsp.LspCompletionConverter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.ParseResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-process Language Server for HTML files.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Completions</b>: Delegates to {@link HtmlAutoCompleteEngine} for tag names,
 *       attribute names, attribute values, Emmet expansions, and inline CSS/JS completions.</li>
 *   <li><b>Diagnostics</b>: Delegates to {@link HtmlLinter} (unclosed tags, unknown attributes,
 *       missing required attributes, duplicate IDs).</li>
 *   <li><b>Go to Definition</b>:
 *     <ul>
 *       <li>{@code id="foo"} → finds {@code getElementById("foo")} in JS files via {@link ProjectIndex}.</li>
 *       <li>{@code class="foo"} → finds {@code .foo {}} in CSS files via {@link ProjectIndex}.</li>
 *       <li>{@code src="x.js"} / {@code href="x.css"} → navigates to that file.</li>
 *     </ul>
 *   </li>
 *   <li><b>Find References</b>: Finds all uses of the HTML {@code id} or {@code class}
 *       value under the cursor across the project.</li>
 *   <li><b>Signature Help</b>: Not applicable for HTML.</li>
 * </ul>
 */
public final class HtmlLspServer implements LspServer {

    private final HtmlAutoCompleteEngine completeEngine;
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    // -------------------------------------------------------------------------
    // LspServer contract
    // -------------------------------------------------------------------------

    public HtmlLspServer(Context context) {
        this.completeEngine = new HtmlAutoCompleteEngine(context);
    }

    /**
     * No-arg constructor for backwards compatibility (no asset loading).
     */
    public HtmlLspServer() {
        this(null);
    }

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacyItems) {
        return LspCompletionConverter.convert(legacyItems);
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------



    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------



    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Signature Help — not applicable for HTML
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    // -------------------------------------------------------------------------
    // Private helpers — conversion
    // -------------------------------------------------------------------------

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "html";
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        // HtmlAutoCompleteEngine requires a File for file-relative src/href resolution.
        File file = new File(doc.uri);
        completeEngine.setCurrentFile(file);

        List<CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        List<LspCompletionItem> lspItems = new ArrayList<>(convertCompletions(legacy));

        // Enrich with cross-file completions when inside class="" or id=""
        HtmlTagParser tagParser = new HtmlTagParser();
        HtmlTagParser.HtmlContext ctx = tagParser.parseContext(doc.text, flatOffset);
        if (ctx.isInsideAttributeValue && ctx.currentAttributeName != null
                && projectIndex != null) {
            String prefix = ctx.currentAttributeValue != null ? ctx.currentAttributeValue : "";
            if ("class".equals(ctx.currentAttributeName)) {
                lspItems.addAll(getCssClassCompletions(prefix));
            } else if ("id".equals(ctx.currentAttributeName)) {
                lspItems.addAll(getIdCompletions(prefix));
            }
        }

        // Deduplicate completions
        List<LspCompletionItem> uniqueItems = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();
        for (LspCompletionItem item : lspItems) {
            if (seenLabels.add(item.label)) {
                uniqueItems.add(item);
            }
        }

        return uniqueItems;
    }

    // -------------------------------------------------------------------------
    // Private helpers — definition resolution
    // -------------------------------------------------------------------------

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File file = new File(doc.uri);
            List<Problem> problems = new ArrayList<>(com.cocode.vcode.ide.core.editor.indent.BracketMatcher.findMismatches(file, doc.text));
            List<Problem> htmlProblems = HtmlLinter.analyze(file, doc.text);
            if (htmlProblems != null) problems.addAll(htmlProblems);
            return com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.deduplicateAndSort(file, problems);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        HtmlTokenStream stream = HtmlLexer.tokenize(doc.text);
        ParseResult result = HtmlParser.parse(doc.text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return null;

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    String attrName = tree.nodeName[i];
                    String rawVal = tree.nodeValue[i];
                    if (attrName == null || rawVal == null) continue;
                    String val = stripQuotes(rawVal);

                    if ("src".equalsIgnoreCase(attrName) || "href".equalsIgnoreCase(attrName)) {
                        return resolveFileReference(val, doc);
                    }
                    if ("id".equalsIgnoreCase(attrName) && projectIndex != null) {
                        return findIdUsageInJs(val);
                    }
                    if ("class".equalsIgnoreCase(attrName) && projectIndex != null) {
                        String cls = findClassAtOffset(doc.text, tree.nodeStart[i], tree.nodeEnd[i], rawVal, offset);
                        if (cls != null) {
                            return findCssRule(cls);
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return Collections.emptyList();

        HtmlTokenStream stream = HtmlLexer.tokenize(doc.text);
        ParseResult result = HtmlParser.parse(doc.text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return Collections.emptyList();

        List<LspLocation> refs = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    String attrName = tree.nodeName[i];
                    String rawVal = tree.nodeValue[i];
                    if (attrName == null || rawVal == null) continue;
                    String val = stripQuotes(rawVal);

                    if ("id".equalsIgnoreCase(attrName) && projectIndex != null) {
                        refs.addAll(findUsagesInProject(val, true));
                    } else if ("class".equalsIgnoreCase(attrName) && projectIndex != null) {
                        String cls = findClassAtOffset(doc.text, tree.nodeStart[i], tree.nodeEnd[i], rawVal, offset);
                        if (cls != null) {
                            refs.addAll(findUsagesInProject(cls, false));
                        }
                    }
                }
            }
        }

        return refs;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public java.util.List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return java.util.Collections.emptyList();
    }

    private static String stripQuotes(String str) {
        if (str == null || str.length() < 2) return str != null ? str : "";
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private static String findClassAtOffset(String text, int attrStart, int attrEnd, String rawVal, int offset) {
        String val = stripQuotes(rawVal);
        int valStart = text.indexOf(val, attrStart);
        if (valStart == -1 || valStart > attrEnd) valStart = attrStart;

        int relOffset = offset - valStart;
        String[] parts = val.split("\\s+");
        int pos = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int pStart = val.indexOf(p, pos);
            if (pStart == -1) pStart = pos;
            int pEnd = pStart + p.length();
            if (relOffset >= pStart && relOffset <= pEnd) {
                return p;
            }
            pos = pEnd;
        }
        for (String p : parts) {
            if (!p.trim().isEmpty()) return p.trim();
        }
        return null;
    }

    /**
     * Resolves src="..." or href="..." to an actual file in the project.
     */
    private LspLocation resolveFileReference(String path, LspDocument doc) {
        if (path == null || path.trim().isEmpty()) return null;
        String trimmed = path.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return null;

        File base = new File(doc.uri).getParentFile();
        File target = new File(base, trimmed);
        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers — attribute value extraction
    // -------------------------------------------------------------------------

    /**
     * Finds the first JS file in the project index that calls getElementById with the given id.
     */
    private LspLocation findIdUsageInJs(String idValue) {
        if (projectIndex == null || idValue == null) return null;
        String trimmed = idValue.trim();
        if (trimmed.isEmpty()) return null;
        String pattern = "getElementById(\"" + trimmed + "\")";
        for (String uri : projectIndex.getAllUris()) {
            if (!uri.endsWith(".js") && !uri.endsWith(".ts")) continue;
            
            com.cocode.vcode.ide.core.lsp.LspDocument doc = projectIndex.getDocument(uri);
            if (doc == null || doc.text == null) continue;
            int idx = doc.text.indexOf(pattern);
            if (idx >= 0) {
                LspPosition pos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, idx);
                return new LspLocation(uri, new LspRange(pos, new LspPosition(pos.line, pos.character + pattern.length())));
            }
        }
        return null;
    }

    /**
     * Finds the CSS rule for the given class name in any CSS file in the project.
     */
    private LspLocation findCssRule(String className) {
        if (projectIndex == null || className == null) return null;
        String trimmed = className.trim();
        if (trimmed.isEmpty()) return null;
        String cssSelector = "." + trimmed;
        for (SymbolEntry sym : projectIndex.findSymbolsByPrefix(cssSelector)) {
            if (sym.kind == SymbolEntry.KIND_CSS_CLASS && sym.name.equals(cssSelector)) {
                return new LspLocation(sym.uri, sym.range);
            }
        }
        return null;
    }

    // Cross-file reference resolution

    private List<LspLocation> findUsagesInProject(String name, boolean isId) {
        List<LspLocation> result = new ArrayList<>();
        if (projectIndex == null || name == null) return result;
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) return result;

        final int MAX_REFS = 100;
        for (String uri : projectIndex.getAllUris()) {
            if (result.size() >= MAX_REFS) break;
            com.cocode.vcode.ide.core.lsp.LspDocument d = projectIndex.getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                String searchTerm = isId ? "id=\"" + trimmedName + "\"" : trimmedName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = d.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = d.text.indexOf(searchTerm, idx + step);
                }
            } else if (uri.endsWith(".js") || uri.endsWith(".ts")) {
                String searchTerm = isId ? trimmedName : "." + trimmedName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = d.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = d.text.indexOf(searchTerm, idx + step);
                }
            }
        }
        return result;
    }

    /**
     * Scans all indexed CSS files for {@code .className} selectors and returns them
     * as value completions. Used to power class="" attribute IntelliSense.
     */
    private List<LspCompletionItem> getCssClassCompletions(String prefix) {
        List<LspCompletionItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String uri : projectIndex.getAllUris()) {
            if (!uri.endsWith(".css") && !uri.endsWith(".scss")) continue;
            for (SymbolEntry sym : projectIndex.getFileSymbols(uri)) {
                if (sym.kind == SymbolEntry.KIND_CSS_CLASS) {
                    String cls = sym.name.substring(1);
                    if (seen.contains(cls)) continue;
                    if (prefix.isEmpty() || cls.startsWith(prefix)) {
                        seen.add(cls);
                        result.add(new LspCompletionItem(
                                cls, cls, LspCompletionItem.KIND_VALUE, "CSS class", null));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Scans all indexed HTML files for {@code id="..."} attributes and returns them
     * as value completions. Used to power id="" attribute IntelliSense.
     */
    private List<LspCompletionItem> getIdCompletions(String prefix) {
        List<LspCompletionItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String uri : projectIndex.getAllUris()) {
            if (!uri.endsWith(".html") && !uri.endsWith(".htm")) continue;
            for (SymbolEntry sym : projectIndex.getFileSymbols(uri)) {
                if (sym.kind == SymbolEntry.KIND_HTML_ID) {
                    String id = sym.name;
                    if (seen.contains(id)) continue;
                    if (prefix.isEmpty() || id.startsWith(prefix)) {
                        seen.add(id);
                        result.add(new LspCompletionItem(
                                id, id, LspCompletionItem.KIND_VALUE, "HTML id", null));
                    }
                }
            }
        }
        return result;
    }
}
