package com.cocode.vcode.ide.core.lsp;

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

    // JS / TS patterns
    private static final Pattern JS_FUNCTION = Pattern.compile(
            "(?:^|\\s)(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern JS_CONST_ARROW = Pattern.compile(
            "(?:^|\\s)(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|\\w+)\\s*=>",
            Pattern.MULTILINE);
    private static final Pattern JS_CLASS = Pattern.compile(
            "(?:^|\\s)(?:export\\s+)?(?:abstract\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern JS_VAR = Pattern.compile(
            "(?:^|\\s)(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*[=;]", Pattern.MULTILINE);

    // CSS patterns
    private static final Pattern CSS_CLASS_SELECTOR = Pattern.compile(
            "\\.([-\\w]+)\\s*\\{", Pattern.MULTILINE);
    private static final Pattern CSS_ID_SELECTOR = Pattern.compile(
            "#([-\\w]+)\\s*\\{", Pattern.MULTILINE);

    // HTML id / class attribute patterns
    private static final Pattern HTML_ID = Pattern.compile(
            "\\bid=[\"']([^\"']+)[\"']");
    private static final Pattern HTML_CLASS = Pattern.compile(
            "\\bclass=[\"']([^\"']+)[\"']");

    private SymbolExtractor() {
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

        findPattern(doc, text, JS_FUNCTION, SymbolEntry.KIND_FUNCTION, results);
        findPattern(doc, text, JS_CONST_ARROW, SymbolEntry.KIND_FUNCTION, results);
        findPattern(doc, text, JS_CLASS, SymbolEntry.KIND_CLASS, results);
        findPattern(doc, text, JS_VAR, SymbolEntry.KIND_VARIABLE, results);

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
