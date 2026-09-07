package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;

import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
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
    static List<SymbolEntry> extractSymbols(LspDocument doc) {
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
        List<SymbolEntry> results = new ArrayList<>();
        String text = doc.text;

        TokenStream stream = JsLexer.tokenize(text);
        JsSyntaxTree tree = JsParser.parseTopLevel(text, stream);

        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            String name = tree.nodeName[i];
            if (name == null || name.isEmpty() || "{destructure}".equals(name)) continue;

            int kind = -1;
            if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC || 
                type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER) {
                kind = SymbolEntry.KIND_FUNCTION;
            } else if (type == JsSyntaxTree.N_CLASS_DECL) {
                kind = SymbolEntry.KIND_CLASS;
            } else if (type == JsSyntaxTree.N_VAR_DECL) {
                // Promote variables with arrow functions or function expressions to KIND_FUNCTION
                String stmt = text.substring(tree.nodeStart[i], tree.nodeEnd[i]);
                if (stmt.contains("=>") || stmt.contains("function")) {
                    kind = SymbolEntry.KIND_FUNCTION;
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

                LspPosition pos = offsetToPosition(text, nameStart);
                LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
                results.add(new SymbolEntry(name, doc.uri, range, kind));
            }
        }

        return results;
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
                String sel = tree.nodeName[i];
                int selStart = tree.nodeStart[i];
                if (sel == null || sel.isEmpty()) continue;
                int len = sel.length();
                int j = 0;
                while (j < len) {
                    char c = sel.charAt(j);
                    if (c == '.' || c == '#') {
                        boolean isClass = (c == '.');
                        int symStartInSel = j;
                        j++;
                        int nameStartInSel = j;
                        while (j < len) {
                            char ch = sel.charAt(j);
                            if (ch == '_' || ch == '-' || Character.isLetterOrDigit(ch)) {
                                j++;
                            } else {
                                break;
                            }
                        }
                        if (j > nameStartInSel) {
                            String name = sel.substring(symStartInSel, j);
                            int absStart = selStart + symStartInSel;
                            LspPosition pos = offsetToPosition(text, absStart);
                            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
                            results.add(new SymbolEntry(name, doc.uri, range, isClass ? SymbolEntry.KIND_CSS_CLASS : SymbolEntry.KIND_CSS_ID));
                        }
                    } else {
                        j++;
                    }
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
                    if (!id.isEmpty()) {
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
                                String cls = unquoted.substring(start, j);
                                int clsOffset = quoteOffset + start;
                                LspPosition pos = offsetToPosition(text, clsOffset);
                                LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + cls.length()));
                                results.add(new SymbolEntry(cls, doc.uri, range, SymbolEntry.KIND_CSS_CLASS));
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
