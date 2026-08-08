package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.html.HtmlTagParser;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDiagnostic;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern ATTR_AT_CURSOR =
            Pattern.compile("(?:id|class|src|href|action|data-[\\w-]+)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern ID_ATTR = Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern CLASS_ATTR = Pattern.compile("\\bclass\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern SRC_ATTR = Pattern.compile("\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern HREF_ATTR = Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']");
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

    private static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacyItems) {
        if (legacyItems == null || legacyItems.isEmpty()) return Collections.emptyList();
        List<LspCompletionItem> result = new ArrayList<>(legacyItems.size());
        for (CompletionItem ci : legacyItems) {
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
            case TAG:
                return LspCompletionItem.KIND_CLASS;
            case ATTRIBUTE:
                return LspCompletionItem.KIND_PROPERTY;
            case VALUE:
                return LspCompletionItem.KIND_VALUE;
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            case FILE:
                return LspCompletionItem.KIND_FILE;
            case FOLDER:
                return LspCompletionItem.KIND_FOLDER;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------

    private static List<LspDiagnostic> convertProblems(List<Problem> problems) {
        if (problems == null || problems.isEmpty()) return Collections.emptyList();
        List<LspDiagnostic> result = new ArrayList<>(problems.size());
        for (Problem p : problems) {
            if (p == null) continue;
            // Problem uses 1-based line; LSP uses 0-based
            int line = Math.max(0, p.getLine() - 1);
            int col = Math.max(0, p.getColumn());
            int end = col + Math.max(1, p.getLength());
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
                    "html"
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    /**
     * Extracts the value of the named attribute if the cursor is positioned inside it.
     * Returns null otherwise.
     */
    private static String extractAttrValue(String line, String attrName, int cursorChar) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(attrName) + "\\s*=\\s*[\"']([^\"']*)[\"']");
        Matcher m = p.matcher(line);
        while (m.find()) {
            if (cursorChar >= m.start() && cursorChar <= m.end()) {
                return m.group(1);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Go to Definition
    // -------------------------------------------------------------------------

    /**
     * Extracts the first class name from a {@code class="..."} attribute if the cursor is inside it.
     */
    private static String extractFirstClass(String line, int cursorChar) {
        Matcher m = CLASS_ATTR.matcher(line);
        while (m.find()) {
            if (cursorChar >= m.start() && cursorChar <= m.end()) {
                String[] classes = m.group(1).split("\\s+");
                return classes.length > 0 ? classes[0] : null;
            }
        }
        return null;
    }

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
    public List<LspDiagnostic> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        File file = new File(doc.uri);
        List<Problem> problems = HtmlLinter.analyze(file, doc.text);
        return convertProblems(problems);
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0) return null;

        // Find the attribute context around the cursor
        String lineText = doc.getLine(pos.line);

        // Check for src= or href= (file reference)
        LspLocation fileRef = resolveFileReference(lineText, pos, doc);
        if (fileRef != null) return fileRef;

        // Check for id= (jump to JS getElementById usage)
        String idValue = extractAttrValue(lineText, "id", pos.character);
        if (idValue != null && projectIndex != null) {
            return findIdUsageInJs(idValue);
        }

        // Check for class= (jump to CSS rule)
        String classValue = extractFirstClass(lineText, pos.character);
        if (classValue != null && projectIndex != null) {
            return findCssRule(classValue);
        }

        return null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();

        String lineText = doc.getLine(pos.line);
        List<LspLocation> refs = new ArrayList<>();

        // If cursor is on an id value, find all JS/HTML usages
        String idValue = extractAttrValue(lineText, "id", pos.character);
        if (idValue != null && projectIndex != null) {
            refs.addAll(findUsagesInProject(idValue, true));
        }

        // If cursor is on a class value, find all HTML/JS usages
        String classValue = extractFirstClass(lineText, pos.character);
        if (classValue != null && projectIndex != null) {
            refs.addAll(findUsagesInProject(classValue, false));
        }

        return refs;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    /**
     * Resolves src="..." or href="..." to an actual file in the project.
     */
    private LspLocation resolveFileReference(String lineText, LspPosition pos, LspDocument doc) {
        String path = extractAttrValue(lineText, "src", pos.character);
        if (path == null) path = extractAttrValue(lineText, "href", pos.character);
        if (path == null) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return null;

        File base = new File(doc.uri).getParentFile();
        File target = new File(base, path);
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
        String pattern = "getElementById(\"" + idValue + "\")";
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
        String cssSelector = "." + className;
        for (SymbolEntry sym : projectIndex.findSymbolsByPrefix(cssSelector)) {
            if (sym.kind == SymbolEntry.KIND_CSS_CLASS && sym.name.equals(cssSelector)) {
                return new LspLocation(sym.uri, sym.range);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Cross-file completion helpers (Phase 2 IntelliSense)
    // -------------------------------------------------------------------------

    private List<LspLocation> findUsagesInProject(String name, boolean isId) {
        List<LspLocation> result = new ArrayList<>();
        if (projectIndex == null || name == null) return result;
        for (String uri : projectIndex.getAllUris()) {
            com.cocode.vcode.ide.core.lsp.LspDocument d = projectIndex.getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                String searchTerm = isId ? "id=\"" + name + "\"" : name;
                int idx = d.text.indexOf(searchTerm);
                while (idx >= 0) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = d.text.indexOf(searchTerm, idx + searchTerm.length());
                }
            } else if (uri.endsWith(".js") || uri.endsWith(".ts")) {
                String searchTerm = isId ? name : "." + name;
                int idx = d.text.indexOf(searchTerm);
                while (idx >= 0) {
                    LspPosition refPos = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = d.text.indexOf(searchTerm, idx + searchTerm.length());
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
