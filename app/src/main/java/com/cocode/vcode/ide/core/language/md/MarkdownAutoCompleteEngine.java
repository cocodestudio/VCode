package com.cocode.vcode.ide.core.language.md;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-performance, zero-allocation context-aware autocomplete engine for Markdown.
 * Supports:
 * - File and image path completions inside link and image targets: {@code [text](path)} and {@code ![alt](path)}.
 * - Local heading anchor slug completions: {@code [text](#anchor)} generated from document headers.
 * - Fenced code block opening languages: {@code ```html}, {@code ```css}, {@code ```js}, {@code ```ts}, etc.
 * - Embedded language completions inside fenced code blocks via coordinate-preserving text projection.
 * - Markdown structure snippets (headings, lists, tasks, blockquotes, tables, horizontal rules).
 * - Inline formatting snippets (bold, italic, inline code, strikethrough, link, image).
 */
public class MarkdownAutoCompleteEngine extends AutoCompleteEngine {

    public static final String[][] CODE_BLOCK_LANGUAGES = {
            {"html", "HTML - HyperText Markup Language"},
            {"css", "CSS - Cascading Style Sheets"},
            {"javascript", "JavaScript"},
            {"js", "JavaScript"},
            {"typescript", "TypeScript"},
            {"ts", "TypeScript"},
            {"json", "JSON - JavaScript Object Notation"},
            {"markdown", "Markdown"},
            {"md", "Markdown"},
            {"python", "Python"},
            {"py", "Python"},
            {"bash", "Bash / Shell Script"},
            {"sh", "Shell Script"},
            {"shell", "Shell Script"},
            {"sql", "SQL - Structured Query Language"},
            {"xml", "XML - Extensible Markup Language"},
            {"yaml", "YAML Ain't Markup Language"},
            {"yml", "YAML Ain't Markup Language"},
            {"java", "Java"},
            {"c", "C Programming Language"},
            {"cpp", "C++ Programming Language"},
            {"rust", "Rust"},
            {"go", "Go Programming Language"}
    };

    private final PathAutoCompleteEngine pathEngine;
    private final FastTrie snippetTrie = new FastTrie();
    private final List<CompletionItem> staticSnippets = new ArrayList<>();

    // Lazy sub-engines for code blocks
    private JsAutoCompleteEngine jsEngine;
    private TsAutoCompleteEngine tsEngine;
    private HtmlAutoCompleteEngine htmlEngine;
    private CssAutoCompleteEngine cssEngine;
    private JsonAutoCompleteEngine jsonEngine;

    public MarkdownAutoCompleteEngine(Context context) {
        super(context);
        if (context != null) {
            com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader.setAppContext(context);
        }
        this.pathEngine = new PathAutoCompleteEngine(context);
        initSnippets();
    }

    @Override
    public String getTriggerCharacters() {
        return "[(#*`!|-";
    }

    @Override
    public void setCurrentFile(File file) {
        super.setCurrentFile(file);
        if (pathEngine != null) {
            pathEngine.setCurrentFile(file);
        }
        if (jsEngine != null) jsEngine.setCurrentFile(file);
        if (tsEngine != null) tsEngine.setCurrentFile(file);
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
        } catch (Exception ignored) {
        }
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

        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(fullText, cursorPos);
        if (ctx.type == MarkdownContextScanner.ContextType.NONE) {
            return Collections.emptyList();
        }

        // 1. Code block opening fence language completion
        if (ctx.type == MarkdownContextScanner.ContextType.CODE_BLOCK_LANGUAGE) {
            return getCodeBlockLanguageSuggestions(ctx);
        }

        // 2. Embedded code block completions
        if (ctx.type == MarkdownContextScanner.ContextType.EMBEDDED_CODE_BLOCK) {
            return getEmbeddedSuggestions(ctx, fullText, cursorPos);
        }

        // 3. Heading anchor completions [text](#anchor|)
        if (ctx.type == MarkdownContextScanner.ContextType.HEADING_ANCHOR) {
            return getHeadingAnchorSuggestions(ctx, fullText);
        }

        // 4. Link file path completions [text](path|)
        if (ctx.type == MarkdownContextScanner.ContextType.LINK_FILE_PATH) {
            List<CompletionItem> pathSuggestions = pathEngine.getSuggestions(fullText, cursorPos);
            return pathSuggestions != null ? pathSuggestions : Collections.emptyList();
        }

        // 5. Snippet completions: Structural vs Inline
        return getSnippetSuggestions(ctx, fullText, cursorPos);
    }

    private List<CompletionItem> getCodeBlockLanguageSuggestions(MarkdownContextScanner.MarkdownContext ctx) {
        List<CompletionItem> list = new ArrayList<>();
        String query = ctx.query;
        for (String[] entry : CODE_BLOCK_LANGUAGES) {
            String lang = entry[0];
            String desc = entry[1];
            if (query.isEmpty() || lang.startsWith(query) || computeFuzzyScore(lang, query) > 0) {
                CompletionItem item = new CompletionItem(lang, lang, desc, CompletionItem.Type.KEYWORD, 0);
                item.setReplaceLength(ctx.replaceLength);
                list.add(item);
            }
        }
        // When query is empty, also include code block snippets for backwards compatibility
        if (query.isEmpty()) {
            for (CompletionItem snip : staticSnippets) {
                if (snip.getLabel().startsWith("```")) {
                    CompletionItem clone = new CompletionItem(
                            snip.getLabel(), snip.getEffectiveInsertText(), snip.getDetail(), snip.getType(), snip.getCursorOffset());
                    clone.setReplaceLength(3 + ctx.replaceLength);
                    list.add(clone);
                }
            }
        }
        return list;
    }

    private List<CompletionItem> getEmbeddedSuggestions(MarkdownContextScanner.MarkdownContext ctx, String text, int pos) {
        String lang = ctx.language;
        if (lang == null || lang.isEmpty()) return Collections.emptyList();

        // Project embedded code block by masking host markdown text with whitespace, preserving newlines
        char[] mask = new char[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            mask[i] = (c == '\n' || c == '\r') ? c : ' ';
        }
        int s = Math.max(0, Math.min(ctx.blockStartOffset, text.length()));
        int e = Math.max(s, Math.min(ctx.blockEndOffset, text.length()));
        for (int k = s; k < e; k++) {
            mask[k] = text.charAt(k);
        }
        String projected = new String(mask);

        AutoCompleteEngine engine = resolveEmbeddedEngine(lang);
        if (engine != null) {
            List<CompletionItem> items = engine.getSuggestions(projected, pos);
            return items != null ? items : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private AutoCompleteEngine resolveEmbeddedEngine(String lang) {
        if (lang.equals("js") || lang.equals("javascript")) {
            if (jsEngine == null) {
                jsEngine = new JsAutoCompleteEngine(context);
                if (currentFile != null) jsEngine.setCurrentFile(currentFile);
            }
            return jsEngine;
        } else if (lang.equals("ts") || lang.equals("typescript")) {
            if (tsEngine == null) {
                tsEngine = new TsAutoCompleteEngine(context);
                if (currentFile != null) tsEngine.setCurrentFile(currentFile);
            }
            return tsEngine;
        } else if (lang.equals("html") || lang.equals("xml")) {
            if (htmlEngine == null) {
                htmlEngine = new HtmlAutoCompleteEngine(context);
                if (currentFile != null) htmlEngine.setCurrentFile(currentFile);
            }
            return htmlEngine;
        } else if (lang.equals("css")) {
            if (cssEngine == null) {
                cssEngine = new CssAutoCompleteEngine(context);
                if (currentFile != null) cssEngine.setCurrentFile(currentFile);
            }
            return cssEngine;
        } else if (lang.equals("json")) {
            if (jsonEngine == null) {
                jsonEngine = new JsonAutoCompleteEngine(context);
                if (currentFile != null) jsonEngine.setCurrentFile(currentFile);
            }
            return jsonEngine;
        }
        return null;
    }

    private List<CompletionItem> getHeadingAnchorSuggestions(MarkdownContextScanner.MarkdownContext ctx, String fullText) {
        List<CompletionItem> anchorItems = new ArrayList<>();
        String query = ctx.query;
        MdLineStream stream = MdLexer.lex(fullText);
        MdSyntaxTree tree = MdParser.parseBlocks(stream, fullText);
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_HEADER) {
                String title = tree.nodeName[i];
                if (title != null && !title.isEmpty()) {
                    String slug = MdParser.toSlug(title);
                    int level = tree.nodeExtra[i];
                    if (query.isEmpty() || slug.startsWith(query) || computeFuzzyScore(slug, query) > 0) {
                        CompletionItem item = new CompletionItem(
                                "#" + slug, slug, title + " (H" + level + ")", CompletionItem.Type.VALUE, 0);
                        item.setReplaceLength(ctx.replaceLength);
                        anchorItems.add(item);
                    }
                }
            }
        }
        return anchorItems;
    }

    private List<CompletionItem> getSnippetSuggestions(MarkdownContextScanner.MarkdownContext ctx, String fullText, int cursorPos) {
        boolean isStructural = (ctx.type == MarkdownContextScanner.ContextType.STRUCTURAL_SNIPPET);
        String matchQuery = ctx.query;
        int replaceLen = ctx.replaceLength;

        List<CompletionItem> result = new ArrayList<>();
        for (CompletionItem item : staticSnippets) {
            String label = item.getLabel();
            String insert = item.getEffectiveInsertText();

            boolean isLineOnly = insert.startsWith("#") || insert.startsWith("-") || insert.startsWith("1.")
                    || insert.startsWith(">") || insert.startsWith("---") || insert.startsWith("|")
                    || insert.startsWith("```");

            if (isLineOnly && !isStructural) {
                continue;
            }

            int score = 0;
            if (matchQuery.isEmpty()) {
                if (isStructural) {
                    score = 50;
                } else {
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
}
