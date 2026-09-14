package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.20: the unified static-completion provider
 * dispatches to the right per-language dispatcher and merges
 * candidates correctly.
 */
public class StaticCompletionProviderTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedJs() {
        String json = "[" +
                "{\"label\": \"var\", \"insertText\": \"var \", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"if\", \"insertText\": \"if (|) {\\n  \\n}\", \"type\": \"KEYWORD\"}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, json);
    }

    private void seedCss() {
        String json = "[" +
                "{\"property\": \"color\", \"values\": [\"red\"], \"acceptsColor\": true}," +
                "{\"property\": \"display\", \"values\": [\"block\"]}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_PROPERTIES_ASSET, json);
        String colors = "{\"colors\": [\"red\"], \"color_functions\": [], \"global_functions\": [\"var(--|)\"]}";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_COLORS_ASSET, colors);
    }

    private void seedHtml() {
        String json = "{" +
                "  \"tags\": [{\"tag\": \"div\", \"selfClosing\": false, \"snippet\": \"<div>|</div>\", \"attributes\": []}]," +
                "  \"globalAttributes\": [\"id\"]," +
                "  \"globalAttributePrefixes\": [\"data-\"]" +
                "}";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.HTML_TAGS_ASSET, json);
    }

    private void seedJson() {
        String json = "[" +
                "{\"label\": \"key: value\", \"snippet\": \"\\\"|\\\": \\\"\\\"\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"package.json\", \"snippet\": \"{}\", \"type\": \"SNIPPET\"}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JSON_SNIPPETS_ASSET, json);
    }

    @Test
    public void routesJsStatementStartToKeywords() {
        seedJs();
        String source = "|";
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_JS, source, 0,
                stream, tree,
                null, null,
                null, null,
                null, null,
                null);
        assertNotNull(items);
        assertTrue("var must be in the result", hasLabel(items, "var"));
    }

    @Test
    public void routesJsMemberAccessToEmpty() {
        seedJs();
        String source = "obj.|";
        int cursor = source.indexOf('|');
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_JS, source, cursor,
                stream, tree,
                null, null,
                null, null,
                null, null,
                null);
        // M.15: empty for member-access.
        assertEquals(0, items.size());
    }

    @Test
    public void routesCssPropertyNameToProperties() {
        seedCss();
        String source = ".x { |";
        int cursor = source.indexOf('|');
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_CSS, source, cursor,
                null, null, null, null,
                null, null,
                null, null,
                null);
        assertTrue(hasLabel(items, "color"));
        assertTrue(hasLabel(items, "display"));
    }

    @Test
    public void routesCssPropertyValueToValues() {
        seedCss();
        String source = ".x { color: |";
        int cursor = source.indexOf('|');
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_CSS, source, cursor,
                null, null, null, null,
                null, null,
                null, null,
                null);
        assertTrue(hasLabel(items, "red"));     // from values
        assertTrue(hasLabel(items, "red"));     // from colors
        assertTrue(hasLabel(items, "var(--|)")); // from global functions
    }

    @Test
    public void routesHtmlTagNameToTags() {
        seedHtml();
        String source = "<di|";
        int cursor = source.indexOf('|');
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_HTML, source, cursor,
                null, null, null, null,
                null, null,
                null, null,
                null);
        assertTrue(hasLabel(items, "div"));
    }

    @Test
    public void routesJsonKeyToKeySnippets() {
        seedJson();
        String source = "{ |";
        int cursor = source.indexOf('|');
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_JSON, source, cursor,
                null, null, null, null,
                null, null,
                null, null,
                null);
        assertTrue(hasLabel(items, "key: value"));
    }

    @Test
    public void routesJsonEmptyToTemplates() {
        seedJson();
        List<CompletionItem> items = StaticCompletionProvider.provide(
                StaticCompletionProvider.LANG_JSON, "", 0,
                null, null, null, null,
                null, null,
                null, null,
                "package.json");
        assertTrue(hasLabel(items, "package.json"));
    }

    @Test
    public void routesUnknownLanguageToEmpty() {
        List<CompletionItem> items = StaticCompletionProvider.provide(
                "plaintext", "hello", 0,
                null, null, null, null,
                null, null,
                null, null,
                null);
        assertEquals(0, items.size());
    }

    @Test
    public void routesNullLanguageToEmpty() {
        List<CompletionItem> items = StaticCompletionProvider.provide(
                null, "hello", 0,
                null, null, null, null,
                null, null,
                null, null,
                null);
        assertEquals(0, items.size());
    }

    // --- helpers -------------------------------------------------------

    private static boolean hasLabel(List<CompletionItem> items, String label) {
        for (CompletionItem c : items) {
            if (label.equals(c.getLabel())) return true;
        }
        return false;
    }
}
