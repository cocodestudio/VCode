package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
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
        return LspCompletionConverter.convert(legacy);
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Go to Definition
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    // -------------------------------------------------------------------------
    // Signature Help — not applicable for CSS
    // -------------------------------------------------------------------------

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "css";
    }

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

        // Check for @import via AST
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

        // Look up class or ID selector definition in HTML documents
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

    @Override
    public com.cocode.vcode.ide.core.lsp.HoverInfo hover(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        String text = doc.text;
        int len = text.length();
        if (offset >= len && offset > 0) offset = len - 1;
        if (len == 0) return null;

        // 1. Identify symbol boundaries around cursor
        int s = offset;
        while (s > 0 && isCssNameChar(text.charAt(s - 1))) {
            s--;
        }
        int e = offset;
        while (e < len && isCssNameChar(text.charAt(e))) {
            e++;
        }

        // Check prefixes immediately preceding s
        if (s >= 2 && text.charAt(s - 1) == ':' && text.charAt(s - 2) == ':') {
            s -= 2;
        } else if (s >= 1 && text.charAt(s - 1) == ':') {
            s -= 1;
        } else if (s >= 1 && text.charAt(s - 1) == '@') {
            s -= 1;
        } else if (s >= 1 && text.charAt(s - 1) == '.') {
            s -= 1;
        } else if (s >= 1 && text.charAt(s - 1) == '#') {
            s -= 1;
        }

        if (s >= e) return null;
        String word = text.substring(s, e).trim();
        if (word.isEmpty()) return null;

        String md = null;

        // 2. If starts with @, it's an at-rule (e.g. @media, @keyframes)
        if (word.startsWith("@")) {
            md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getAtRuleMarkdown(word);
        } else if (word.startsWith("::")) {
            // 3. Pseudo-element (e.g. ::before, ::after)
            md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getPseudoElementMarkdown(word);
        } else if (word.startsWith(":")) {
            // 4. Pseudo-class (e.g. :hover, :focus)
            md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getPseudoClassMarkdown(word);
        } else {
            // Check if function call (e.g. calc(, var()
            boolean isFunction = false;
            int next = e;
            while (next < len && Character.isWhitespace(text.charAt(next))) next++;
            if (next < len && text.charAt(next) == '(') {
                isFunction = true;
            }

            if (isFunction) {
                md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getFunctionMarkdown(word);
            }
            if (md == null) {
                md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getDocForToken(word);
            }
            if (md == null) {
                // Check HTML element doc for selectors like div, a, p
                md = com.cocode.vcode.ide.core.language.html.HtmlDocumentationProvider.getElementMarkdown(word);
            }
            if (md == null) {
                // Fallback to static completion item
                com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionItem item =
                        com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssProperty(word);
                if (item != null) {
                    String detail = item.detail != null ? item.detail : "CSS Property";
                    StringBuilder sb = new StringBuilder("### `").append(item.label).append("`\n\n").append(detail);
                    if (item.values != null && item.values.length > 0) {
                        sb.append("\n\n**Common Values:** `").append(String.join("`, `", item.values)).append("`");
                    }
                    md = sb.toString();
                }
            }
        }

        if (md != null) {
            com.cocode.vcode.ide.core.lsp.LspRange range = new com.cocode.vcode.ide.core.lsp.LspRange(doc.toPosition(s), doc.toPosition(e));
            return new com.cocode.vcode.ide.core.lsp.HoverInfo(range, md);
        }

        return null;
    }

    private static boolean isCssNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }
}
