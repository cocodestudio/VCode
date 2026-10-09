package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;

import java.util.ArrayList;
import java.util.List;

/**
 * AST-driven, language-aware symbol extractor.
 * <p>
 * Parses a {@link LspDocument} and returns a list of {@link SymbolEntry} objects
 * representing the declarations found in the file using AST engines.
 */
public final class SymbolExtractor {

    private SymbolExtractor() {
    }

    /**
     * Extracts the word at the given offset.
     */
    public static String extractWord(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return "";
        int start = offset;
        while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '_' || text.charAt(start - 1) == '$')) {
            start--;
        }
        int end = offset;
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_' || text.charAt(end) == '$')) {
            end++;
        }
        return text.substring(start, end);
    }

    /**
     * Extracts symbols from the given document based on its language.
     *
     * @param doc the document to analyse
     * @return list of extracted symbols, never null
     */
    public static List<SymbolEntry> extractSymbols(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.isEmpty()) {
            return new ArrayList<>();
        }

        switch (doc.languageId) {
            case "javascript":
            case "typescript":
                return extractJsSymbols(doc);
            case "css":
            case "scss":
                return extractCssSymbols(doc);
            case "html":
                return extractHtmlSymbols(doc);
            default:
                return new ArrayList<>();
        }
    }

    // -------------------------------------------------------------------------
    // JS / TS
    // -------------------------------------------------------------------------

    private static List<SymbolEntry> extractJsSymbols(LspDocument doc) {
        String text = doc.text;
        TokenStream stream = JsLexer.tokenize(text);
        JsSyntaxTree tree = JsParser.parseTopLevel(text, stream);
        return extractJsSymbolsFromTree(doc.uri, text, tree);
    }

    /**
     * Extracts JS/TS symbols directly from a pre-parsed syntax tree without re-lexing or re-parsing.
     */
    public static List<SymbolEntry> extractJsSymbolsFromTree(String uri, String text, JsSyntaxTree tree) {
        List<SymbolEntry> results = new ArrayList<>();
        if (tree == null || text == null) return results;

        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            String name = tree.nodeName[i];
            if (name == null || name.isEmpty() || "{destructure}".equals(name)) continue;
            if (!com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.isValidJsIdentifier(name)
                    && !"constructor".equals(name)) continue;

            int kind = -1;
            if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC) {
                kind = SymbolEntry.KIND_FUNCTION;
            } else if (type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER) {
                kind = SymbolEntry.KIND_METHOD;
            } else if (type == JsSyntaxTree.N_CLASS_DECL) {
                kind = SymbolEntry.KIND_CLASS;
            } else if (type == JsSyntaxTree.N_VAR_DECL) {
                if (tree.nodeParent[i] > 0 && tree.nodeType[tree.nodeParent[i]] == JsSyntaxTree.N_IMPORT) {
                    continue;
                }
                if (tree.nodeTypeAnn != null && tree.nodeTypeAnn[i] != null &&
                        (tree.nodeTypeAnn[i].startsWith("@IMPORT:") || tree.nodeTypeAnn[i].startsWith("@REQUIRE:") || tree.nodeTypeAnn[i].startsWith("@REQUIRE_PROP:"))) {
                    continue;
                }
                // Promote variables with arrow functions or function expressions to KIND_FUNCTION
                int start = tree.nodeStart[i];
                int end = Math.min(tree.nodeEnd[i], text.length());
                if (start >= 0 && start < end) {
                    String stmt = text.substring(start, end);
                    if (stmt.contains("=>") || stmt.contains("function")) {
                        kind = SymbolEntry.KIND_FUNCTION;
                    } else {
                        kind = SymbolEntry.KIND_VARIABLE;
                    }
                } else {
                    kind = SymbolEntry.KIND_VARIABLE;
                }
            }

            if (kind != -1) {
                // Find exact offset of the identifier
                int nameStart = tree.nodeStart[i];
                int nameIndex = text.indexOf(name, nameStart);
                if (nameIndex != -1 && nameIndex < tree.nodeEnd[i]) {
                    nameStart = nameIndex;
                }

                String detail = extractParametersDetail(tree, i);

                LspPosition pos = offsetToPosition(text, nameStart);
                LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
                results.add(new SymbolEntry(name, uri, range, kind, detail));
            }
        }

        return results;
    }

    private static String extractParametersDetail(JsSyntaxTree tree, int node) {
        if (tree == null || node <= 0 || node >= tree.nodeCount) return null;
        int type = tree.nodeType[node];
        if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC || type == JsSyntaxTree.N_METHOD) {
            List<String> params = new ArrayList<>();
            int child = tree.nodeChild[node];
            int guard = 0;
            while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
                if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                    String pName = tree.nodeName[child] != null ? tree.nodeName[child] : "arg";
                    if ((tree.nodeExtra[child] & JsSyntaxTree.FLAG_REST) != 0) {
                        pName = "..." + pName;
                    } else if ((tree.nodeExtra[child] & JsSyntaxTree.FLAG_DEFAULT) != 0) {
                        pName = pName + "=?";
                    }
                    params.add(pName);
                }
                child = tree.nodeSibling[child];
            }
            return String.join(", ", params);
        } else if (type == JsSyntaxTree.N_CLASS_DECL) {
            int child = tree.nodeChild[node];
            int guard = 0;
            while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
                if (tree.nodeType[child] == JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[child])) {
                    return extractParametersDetail(tree, child);
                }
                child = tree.nodeSibling[child];
            }
            // Check superclass in the same file if subclass has no explicit constructor
            String superName = tree.nodeTypeAnn[node];
            if (superName != null && !superName.isEmpty()) {
                for (int s = 1; s < tree.nodeCount; s++) {
                    if (tree.nodeType[s] == JsSyntaxTree.N_CLASS_DECL && superName.equals(tree.nodeName[s])) {
                        return extractParametersDetail(tree, s);
                    }
                }
            }
            return "";
        } else if (type == JsSyntaxTree.N_VAR_DECL) {
            int child = tree.nodeChild[node];
            if (child > 0 && child < tree.nodeCount) {
                return extractParametersDetail(tree, child);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // CSS / SCSS
    // -------------------------------------------------------------------------

    private static List<SymbolEntry> extractCssSymbols(LspDocument doc) {
        List<SymbolEntry> results = new ArrayList<>();
        String text = doc.text;

        CssTokenStream stream = CssLexer.tokenize(text);
        CssSyntaxTree tree = CssParser.parse(stream, text);

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_SELECTOR) {
                int parent = tree.nodeParent[i];
                if (parent > 0 && tree.nodeType[parent] == CssSyntaxTree.N_AT_RULE) {
                    continue; // Skip at-rules (@media, @keyframes, etc.)
                }
                String sel = tree.nodeName[i];
                int selStart = tree.nodeStart[i];
                if (sel == null || sel.isEmpty() || sel.trim().startsWith("@")) continue;
                int len = sel.length();
                int j = 0;
                int bracketDepth = 0;
                while (j < len) {
                    char c = sel.charAt(j);

                    // Skip string literals (e.g. [data-attr="foo.bar#baz"])
                    if (c == '"' || c == '\'') {
                        char quote = c;
                        j++;
                        while (j < len && sel.charAt(j) != quote) {
                            if (sel.charAt(j) == '\\') j++;
                            j++;
                        }
                        if (j < len) j++;
                        continue;
                    }

                    // Skip attribute selectors [ ... ]
                    if (c == '[') {
                        bracketDepth++;
                        j++;
                        continue;
                    } else if (c == ']') {
                        if (bracketDepth > 0) bracketDepth--;
                        j++;
                        continue;
                    }

                    if (bracketDepth > 0) {
                        j++;
                        continue;
                    }

                    if (c == '.' || c == '#') {
                        boolean isClass = (c == '.');
                        int symStartInSel = j;
                        j++;
                        int nameStartInSel = j;
                        while (j < len) {
                            char ch = sel.charAt(j);
                            if (ch == '_' || ch == '-' || Character.isLetterOrDigit(ch) || ch >= 128) {
                                j++;
                            } else {
                                break;
                            }
                        }
                        if (j > nameStartInSel) {
                            String nameWithoutPrefix = sel.substring(nameStartInSel, j);
                            if (j < len && sel.charAt(j) == '[' && nameWithoutPrefix.endsWith("-")) {
                                nameWithoutPrefix = nameWithoutPrefix.substring(0, nameWithoutPrefix.length() - 1);
                            }
                            boolean valid = isClass
                                    ? com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.isValidCssClassName(nameWithoutPrefix)
                                    : com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.isValidCssId(nameWithoutPrefix);
                            if (valid) {
                                String name = sel.substring(symStartInSel, symStartInSel + 1 + nameWithoutPrefix.length());
                                int absStart = selStart + symStartInSel;
                                LspPosition pos = offsetToPosition(text, absStart);
                                LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
                                results.add(new SymbolEntry(name, doc.uri, range, isClass ? SymbolEntry.KIND_CSS_CLASS : SymbolEntry.KIND_CSS_ID));
                            }
                        }
                    } else {
                        j++;
                    }
                }
            } else if (tree.nodeType[i] == CssSyntaxTree.N_PROPERTY) {
                String propName = tree.nodeName[i];
                if (propName != null && propName.startsWith("--")) {
                    int propStart = tree.nodeStart[i];
                    LspPosition pos = offsetToPosition(text, propStart);
                    LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + propName.length()));

                    String valText = null;
                    int sib = tree.nodeSibling[i];
                    if (sib > 0 && sib < tree.nodeCount && tree.nodeType[sib] == CssSyntaxTree.N_VALUE) {
                        valText = tree.nodeValue[sib];
                    }
                    results.add(new SymbolEntry(propName, doc.uri, range, SymbolEntry.KIND_CSS_VARIABLE, valText));
                }
            }
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // HTML
    // -------------------------------------------------------------------------

    private static List<SymbolEntry> extractHtmlSymbols(LspDocument doc) {
        List<SymbolEntry> results = new ArrayList<>();
        String text = doc.text;

        HtmlTokenStream stream = HtmlLexer.tokenize(text);
        ParseResult result = HtmlParser.parse(text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return results;

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                String attrName = tree.nodeName[i];
                String rawVal = tree.nodeValue[i];
                if (attrName == null || rawVal == null) continue;

                int attrStart = tree.nodeStart[i];
                int attrEnd = tree.nodeEnd[i];
                int equalsIdx = text.indexOf('=', attrStart);
                if (equalsIdx == -1 || equalsIdx >= attrEnd) continue;

                if ("id".equalsIgnoreCase(attrName)) {
                    String id = stripQuotes(rawVal).trim();
                    if (id.startsWith("#")) {
                        id = id.substring(1).trim();
                    }
                    if (!id.isEmpty() && com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.isValidHtmlId(id)) {
                        int valOffset = text.indexOf(id, equalsIdx);
                        if (valOffset != -1 && valOffset < attrEnd) {
                            LspPosition pos = offsetToPosition(text, valOffset);
                            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + id.length()));
                            results.add(new SymbolEntry(id, doc.uri, range, SymbolEntry.KIND_HTML_ID));
                        }
                    }
                } else if ("class".equalsIgnoreCase(attrName)) {
                    String unquoted = stripQuotes(rawVal);
                    int quoteOffset = text.indexOf(unquoted, equalsIdx);
                    if (quoteOffset == -1) quoteOffset = equalsIdx + 1;

                    int vLen = unquoted.length();
                    int start = 0;
                    for (int j = 0; j <= vLen; j++) {
                        if (j == vLen || Character.isWhitespace(unquoted.charAt(j))) {
                            if (j > start) {
                                String cls = unquoted.substring(start, j).trim();
                                if (!cls.isEmpty() && com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.isValidCssClassName(cls)) {
                                    int clsOffset = quoteOffset + start;
                                    LspPosition pos = offsetToPosition(text, clsOffset);
                                    LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + cls.length()));
                                    results.add(new SymbolEntry(cls, doc.uri, range, SymbolEntry.KIND_CSS_CLASS));
                                }
                            }
                            start = j + 1;
                        }
                    }
                }
            }
        }

        return results;
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

    /**
     * Converts a flat character offset to a zero-based (line, character) position.
     */
    public static LspPosition offsetToPosition(String text, int offset) {
        int line = 0;
        int lastNewline = -1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lastNewline = i;
            }
        }
        int character = offset - lastNewline - 1;
        return new LspPosition(line, Math.max(0, character));
    }
}
