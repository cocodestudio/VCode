package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, language-aware symbol extractor.
 * <p>
 * Parses a {@link LspDocument} and returns a list of {@link SymbolEntry} objects
 * representing the top-level declarations found in the file. This is intentionally
 * kept fast and simple — it does NOT perform full AST parsing. Pattern-based heuristics
 * are sufficient for populating the project index with completion candidates and
 * definition targets.
 * <p>
 * Language-specific servers can build richer scope trees on top of this baseline.
 */
public final class SymbolExtractor {

    // JS/TS patterns replaced by AST in Phase 2

    // CSS patterns
    private static final Pattern CSS_CLASS_SELECTOR = Pattern.compile(
            "\\.([-\\w]+)\\s*(?:\\{|,)", Pattern.MULTILINE);
    private static final Pattern CSS_ID_SELECTOR = Pattern.compile(
            "#([-\\w]+)\\s*(?:\\{|,)", Pattern.MULTILINE);

    // HTML id / class attribute patterns
    private static final Pattern HTML_ID = Pattern.compile(
            "\\bid=[\"']([^\"']+)[\"']");
    private static final Pattern HTML_CLASS = Pattern.compile(
            "\\bclass=[\"']([^\"']+)[\"']");

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

        Matcher m = CSS_CLASS_SELECTOR.matcher(text);
        while (m.find()) {
            LspPosition pos = offsetToPosition(text, m.start(1));
            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + Objects.requireNonNull(m.group(1)).length()));
            results.add(new SymbolEntry("." + m.group(1), doc.uri, range, SymbolEntry.KIND_CSS_CLASS));
        }

        m = CSS_ID_SELECTOR.matcher(text);
        while (m.find()) {
            LspPosition pos = offsetToPosition(text, m.start(1));
            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + Objects.requireNonNull(m.group(1)).length()));
            results.add(new SymbolEntry("#" + m.group(1), doc.uri, range, SymbolEntry.KIND_CSS_ID));
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // HTML
    // -------------------------------------------------------------------------

    private static List<SymbolEntry> extractHtmlSymbols(LspDocument doc) {
        List<SymbolEntry> results = new ArrayList<>();
        String text = doc.text;

        Matcher m = HTML_ID.matcher(text);
        while (m.find()) {
            LspPosition pos = offsetToPosition(text, m.start(1));
            String id = m.group(1);
            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + Objects.requireNonNull(id).length()));
            results.add(new SymbolEntry(id, doc.uri, range, SymbolEntry.KIND_HTML_ID));
        }

        m = HTML_CLASS.matcher(text);
        while (m.find()) {
            // A class attribute may have multiple space-separated class names
            String[] classes = Objects.requireNonNull(m.group(1)).split("\\s+");
            int offset = m.start(1);
            for (String cls : classes) {
                if (!cls.isEmpty()) {
                    LspPosition pos = offsetToPosition(text, offset);
                    LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + cls.length()));
                    results.add(new SymbolEntry(cls, doc.uri, range, SymbolEntry.KIND_CSS_CLASS));
                }
                offset += cls.length() + 1; // +1 for space
            }
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static void findPattern(LspDocument doc, String text, Pattern pattern,
                                    int kind, List<SymbolEntry> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (name == null || name.isEmpty()) continue;
            LspPosition pos = offsetToPosition(text, m.start(1));
            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
            out.add(new SymbolEntry(name, doc.uri, range, kind));
        }
    }

    private static void findPatternWithDetail(LspDocument doc, String text, Pattern pattern,
                                    int kind, List<SymbolEntry> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            String detail = m.groupCount() >= 2 ? m.group(2) : null;
            if (name == null || name.isEmpty()) continue;
            LspPosition pos = offsetToPosition(text, m.start(1));
            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
            out.add(new SymbolEntry(name, doc.uri, range, kind, detail != null ? detail.trim() : null));
        }
    }

    private static void findPatternWithDetailArrow(LspDocument doc, String text, Pattern pattern,
                                    int kind, List<SymbolEntry> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            String detail = m.groupCount() >= 2 ? m.group(2) : null;
            if (detail == null && m.groupCount() >= 3) {
                detail = m.group(3);
            }
            if (name == null || name.isEmpty()) continue;
            LspPosition pos = offsetToPosition(text, m.start(1));
            LspRange range = new LspRange(pos, new LspPosition(pos.line, pos.character + name.length()));
            out.add(new SymbolEntry(name, doc.uri, range, kind, detail != null ? detail.trim() : null));
        }
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
