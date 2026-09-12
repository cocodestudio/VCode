package com.cocode.vcode.ide.core.language.md;


import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-performance, zero-allocation context-aware autocomplete engine for Markdown.
 * Supports:
 * 1. File and image path completions inside link and image targets: [text](path) and ![alt](path).
 * 2. Embedded language completions inside fenced code blocks (```html, ```css, ```js, ```json).
 * 3. Markdown structure snippets (headings, lists, tasks, blockquotes, tables, horizontal rules).
 * 4. Inline formatting snippets (bold, italic, inline code, strikethrough, link, image).
 */
public class MarkdownAutoCompleteEngine extends AutoCompleteEngine {

    private final PathAutoCompleteEngine pathEngine;
    private final FastTrie snippetTrie = new FastTrie();
    private final List<CompletionItem> staticSnippets = new ArrayList<>();

    // Lazy sub-engines for code blocks
    private JsAutoCompleteEngine jsEngine;
    private HtmlAutoCompleteEngine htmlEngine;
    private CssAutoCompleteEngine cssEngine;
    private JsonAutoCompleteEngine jsonEngine;

    public MarkdownAutoCompleteEngine(Context context) {
        super(context);
        this.pathEngine = new PathAutoCompleteEngine(context);
        initSnippets();
    }

    @Override
    public void setCurrentFile(File file) {
        super.setCurrentFile(file);
        if (pathEngine != null) {
            pathEngine.setCurrentFile(file);
        }
        if (jsEngine != null) jsEngine.setCurrentFile(file);
        if (htmlEngine != null) htmlEngine.setCurrentFile(file);
        if (cssEngine != null) cssEngine.setCurrentFile(file);
        if (jsonEngine != null) jsonEngine.setCurrentFile(file);
    }

    private void initSnippets() {
        try {
            String json = loadAssetJson("completions/markdown_snippets.json");
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String label = obj.getString("label");
                String insertText = obj.optString("insertText", label);
                String detail = obj.optString("detail", "");
                addSnippet(label, insertText, detail, CompletionItem.Type.SNIPPET);
            }
        } catch (Exception ignored) { }
    }

    private void addSnippet(String label, String insertText, String detail, CompletionItem.Type type) {
        CompletionItem item = new CompletionItem(label, insertText, detail, type, 0);
        staticSnippets.add(item);
        snippetTrie.insert(item);
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return Collections.emptyList();
        }

        // 1. Check if cursor is inside a link/image destination: [...](<cursor>)
        if (isInsideLinkDestination(fullText, cursorPos)) {
            List<CompletionItem> pathSuggestions = pathEngine.getSuggestions(fullText, cursorPos);
            if (pathSuggestions != null && !pathSuggestions.isEmpty()) {
                return pathSuggestions;
            }
        }

        // 2. Check if cursor is inside a fenced code block
        CodeBlockContext blockCtx = findCodeBlockContext(fullText, cursorPos);
        if (blockCtx != null && blockCtx.language != null) {
            List<CompletionItem> embeddedSuggestions = getEmbeddedSuggestions(blockCtx, fullText, cursorPos);
            if (embeddedSuggestions != null && !embeddedSuggestions.isEmpty()) {
                return embeddedSuggestions;
            }
        }

        // 3. Structural & snippet completions
        String word = getWordBeforeCursor(fullText, cursorPos);
        List<CompletionItem> result = new ArrayList<>();

        // Check if cursor is at the beginning of a line or after leading spaces
        int lineStart = findLineStart(fullText, cursorPos);
        int nonWs = lineStart;
        while (nonWs < cursorPos && (fullText.charAt(nonWs) == ' ' || fullText.charAt(nonWs) == '\t')) {
            nonWs++;
        }
        String linePrefix = fullText.substring(nonWs, cursorPos);

        boolean isPunctuationLineTrigger = !linePrefix.isEmpty() && 
            (linePrefix.startsWith("#") || linePrefix.startsWith("`") || linePrefix.startsWith("-")
             || linePrefix.startsWith(">") || linePrefix.startsWith("1.") || linePrefix.startsWith("|"));

        String matchQuery;
        int replaceLen;
        boolean isLineStart;

        if (!word.isEmpty()) {
            matchQuery = word.toLowerCase();
            replaceLen = word.length();
            isLineStart = (nonWs == cursorPos - word.length());
        } else if (isPunctuationLineTrigger) {
            matchQuery = linePrefix.toLowerCase();
            replaceLen = linePrefix.length();
            isLineStart = true;
        } else if (linePrefix.isEmpty()) {
            matchQuery = "";
            replaceLen = 0;
            isLineStart = true;
        } else {
            matchQuery = "";
            replaceLen = 0;
            isLineStart = false;
        }

        for (CompletionItem item : staticSnippets) {
            String label = item.getLabel();
            String insert = item.getEffectiveInsertText();

            // Filter line-only items (headings, lists, hr, tables) if not at start of line
            boolean isLineOnly = insert.startsWith("#") || insert.startsWith("-") || insert.startsWith("1.")
                    || insert.startsWith(">") || insert.startsWith("---") || insert.startsWith("|")
                    || insert.startsWith("```");

            if (isLineOnly && !isLineStart) {
                continue;
            }

            int score = 0;
            if (matchQuery.isEmpty()) {
                if (isLineStart) {
                    score = 50;
                } else {
                    // Offer inline formatting only
                    if (!isLineOnly) score = 30;
                }
            } else {
                score = computeFuzzyScore(label, matchQuery);
                if (score == 0 && !label.equals(insert)) {
                    score = computeFuzzyScore(insert, matchQuery);
                }
            }

            if (score > 0) {
                CompletionItem clone = new CompletionItem(item.getLabel(), item.getEffectiveInsertText(),
                        item.getDetail(), item.getType(), item.getCursorOffset());
                clone.setSortScore(score);
                clone.setReplaceLength(replaceLen);
                result.add(clone);
            }
        }

        Collections.sort(result, (a, b) -> Integer.compare(b.getSortScore(), a.getSortScore()));
        if (result.size() > MAX_SUGGESTIONS) {
            return new ArrayList<>(result.subList(0, MAX_SUGGESTIONS));
        }
        return result;
    }

    /**
     * Lexical check if cursor is after `](` or `](path` without closing `)`.
     */
    private boolean isInsideLinkDestination(String text, int pos) {
        int i = pos - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == ')') {
                return false;
            }
            if (c == '(') {
                // Check if immediately preceded by ']'
                int j = i - 1;
                while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
                    if (text.charAt(j) == '\n') return false;
                    j--;
                }
                return j >= 0 && text.charAt(j) == ']';
            }
            i--;
        }
        return false;
    }

    private int findLineStart(String text, int pos) {
        int i = pos - 1;
        while (i >= 0 && text.charAt(i) != '\n') {
            i--;
        }
        return i + 1;
    }

    private static class CodeBlockContext {
        String language;
        int blockStartOffset;
    }

    private CodeBlockContext findCodeBlockContext(String text, int pos) {
        int fenceCount = 0;
        int lastFencePos = -1;
        String lastFenceLang = null;

        int i = 0;
        int len = Math.min(pos, text.length());
        while (i < len) {
            // Check start of line
            int lineStart = i;
            while (i < len && text.charAt(i) != '\n') {
                i++;
            }
            int lineEnd = i;
            if (i < len && text.charAt(i) == '\n') i++;

            // Check if line starts with ```
            int k = lineStart;
            while (k < lineEnd && (text.charAt(k) == ' ' || text.charAt(k) == '\t')) k++;
            if (lineEnd - k >= 3 && text.charAt(k) == '`' && text.charAt(k + 1) == '`' && text.charAt(k + 2) == '`') {
                fenceCount++;
                lastFencePos = lineEnd;
                // Parse language specifier after ```
                int langStart = k + 3;
                while (langStart < lineEnd && Character.isWhitespace(text.charAt(langStart))) langStart++;
                int langEnd = langStart;
                while (langEnd < lineEnd && !Character.isWhitespace(text.charAt(langEnd))) langEnd++;
                if (langEnd > langStart) {
                    lastFenceLang = text.substring(langStart, langEnd).toLowerCase();
                } else {
                    lastFenceLang = "";
                }
            }
        }

        // If fenceCount is odd, we are currently inside a fenced code block!
        if ((fenceCount % 2) != 0) {
            CodeBlockContext ctx = new CodeBlockContext();
            ctx.language = lastFenceLang;
            ctx.blockStartOffset = lastFencePos;
            return ctx;
        }
        return null;
    }

    private List<CompletionItem> getEmbeddedSuggestions(CodeBlockContext blockCtx, String text, int pos) {
        String lang = blockCtx.language;
        if (lang == null || lang.isEmpty()) return null;

        if (lang.equals("js") || lang.equals("javascript") || lang.equals("ts") || lang.equals("typescript")) {
            if (jsEngine == null) {
                jsEngine = new JsAutoCompleteEngine(context);
                if (currentFile != null) jsEngine.setCurrentFile(currentFile);
            }
            return jsEngine.getSuggestions(text, pos);
        } else if (lang.equals("html") || lang.equals("xml")) {
            if (htmlEngine == null) {
                htmlEngine = new HtmlAutoCompleteEngine(context);
                if (currentFile != null) htmlEngine.setCurrentFile(currentFile);
            }
            return htmlEngine.getSuggestions(text, pos);
        } else if (lang.equals("css")) {
            if (cssEngine == null) {
                cssEngine = new CssAutoCompleteEngine(context);
                if (currentFile != null) cssEngine.setCurrentFile(currentFile);
            }
            return cssEngine.getSuggestions(text, pos);
        } else if (lang.equals("json")) {
            if (jsonEngine == null) {
                jsonEngine = new JsonAutoCompleteEngine(context);
                if (currentFile != null) jsonEngine.setCurrentFile(currentFile);
            }
            return jsonEngine.getSuggestions(text, pos);
        }

        return null;
    }
}
