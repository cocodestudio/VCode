package com.cocode.vcode.ide.core.completion.staticdata;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.1: shared StaticCompletionItem + one-time
 * loader.
 */
public class StaticCompletionLoaderTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    @Test
    public void loadsJsKeywordsFromJson() {
        String json = "[\n" +
                "  {\"label\": \"var\", \"insertText\": \"var \", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"},\n" +
                "  {\"label\": \"fetch\", \"insertText\": \"fetch('|')\", \"detail\": \"B\", \"type\": \"BUILTIN\"}\n" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, json);
        StaticCompletionItem[] items = StaticCompletionLoader.getJsKeywords();
        assertEquals(2, items.length);
        assertEquals("var", items[0].label);
        assertEquals("var ", items[0].insertText);
        assertEquals("KEYWORD", items[0].type);
        assertEquals("fetch", items[1].label);
        assertEquals("fetch('|')", items[1].insertText);
    }

    @Test
    public void loadsHtmlTagsWithAttributes() {
        String json = "[\n" +
                "  {\"tag\": \"div\", \"selfClosing\": false, \"snippet\": \"<div>|</div>\", \"detail\": \"Container\", \"attributes\": [\"id\", \"class\"]},\n" +
                "  {\"tag\": \"img\", \"selfClosing\": true, \"snippet\": \"<img src=\\\"|\\\" alt=\\\"\\\"/>\", \"detail\": \"Image\", \"attributes\": [\"src\", \"alt\"]}\n" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.HTML_TAGS_ASSET, json);
        StaticCompletionItem[] items = StaticCompletionLoader.getHtmlTags();
        assertEquals(2, items.length);
        assertEquals("div", items[0].label);
        assertEquals("<div>|</div>", items[0].insertText);
        assertFalse("div is not self-closing", items[0].selfClosing);
        assertNotNull(items[0].attributes);
        assertEquals(2, items[0].attributes.length);
        assertEquals("id", items[0].attributes[0]);
        assertTrue("img is self-closing", items[1].selfClosing);
    }

    @Test
    public void loadsCssPropertiesWithValues() {
        String json = "[\n" +
                "  {\"property\": \"color\", \"detail\": \"Typography\", \"values\": [\"red\", \"blue\"], \"acceptsColor\": true},\n" +
                "  {\"property\": \"display\", \"detail\": \"Box\", \"values\": [\"block\", \"flex\"]}\n" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_PROPERTIES_ASSET, json);
        StaticCompletionItem[] items = StaticCompletionLoader.getCssProperties();
        assertEquals(2, items.length);
        assertEquals("color", items[0].label);
        assertTrue("color accepts color values", items[0].acceptsColor);
        assertNotNull(items[0].values);
        assertEquals(2, items[0].values.length);
        assertFalse("display does not accept color", items[1].acceptsColor);
    }

    @Test
    public void cssPropertyLookupIsCaseInsensitive() {
        String json = "[{\"property\": \"Color\", \"values\": [\"red\"], \"acceptsColor\": true}]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_PROPERTIES_ASSET, json);
        assertNotNull(StaticCompletionLoader.getCssProperty("color"));
        assertNotNull(StaticCompletionLoader.getCssProperty("COLOR"));
        assertNotNull(StaticCompletionLoader.getCssProperty("Color"));
        assertNull(StaticCompletionLoader.getCssProperty("unknown"));
    }

    @Test
    public void loadsCssColorsWithGlobalFunctions() {
        String json = "{\n" +
                "  \"colors\": [\"red\", \"blue\"],\n" +
                "  \"color_functions\": [\"rgb(|)\"],\n" +
                "  \"global_functions\": [\"var(--|)\", \"calc(|)\"]\n" +
                "}";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_COLORS_ASSET, json);
        StaticCompletionLoader.CssColorSets sets = StaticCompletionLoader.parseCssColors(json);
        assertEquals(2, sets.colors.length);
        assertEquals("red", sets.colors[0]);
        assertEquals(1, sets.colorFunctions.length);
        assertEquals(2, sets.globalFunctions.length);
        assertEquals("var(--|)", sets.globalFunctions[0]);
    }

    @Test
    public void loadsJsonSnippetsWithSnippetField() {
        // json_snippets uses "snippet" not "insertText" — verify
        // both keys are accepted.
        String json = "[\n" +
                "  {\"label\": \"{}\", \"snippet\": \"{\\n  |\\n}\", \"detail\": \"Empty Object\", \"type\": \"SNIPPET\"}\n" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JSON_SNIPPETS_ASSET, json);
        StaticCompletionItem[] items = StaticCompletionLoader.getJsonSnippets();
        assertEquals(1, items.length);
        assertEquals("{}", items[0].label);
        assertEquals("{\n  |\n}", items[0].insertText);
    }

    @Test
    public void loaderCachesResults() {
        // Set the override to one value, then call getJsKeywords,
        // then change the override and call again. The second call
        // must return the cached array, not re-parse the new
        // override. (This is the M.1 acceptance check: zero JSON
        // re-parsing per request.)
        String firstJson = "[{\"label\": \"a\", \"insertText\": \"a\", \"type\": \"KEYWORD\"}]";
        String secondJson = "[{\"label\": \"b\", \"insertText\": \"b\", \"type\": \"KEYWORD\"}]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, firstJson);
        StaticCompletionItem[] first = StaticCompletionLoader.getJsKeywords();
        assertEquals(1, first.length);
        assertEquals("a", first[0].label);

        // Change the override behind the loader's back.
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, secondJson);
        StaticCompletionItem[] second = StaticCompletionLoader.getJsKeywords();
        assertTrue("second call must return the SAME cached array reference",
                first == second);
        assertEquals("a", second[0].label);
    }

    @Test
    public void loading5000CompletionsAfterStartupDoesZeroReparsing() {
        // Build a JSON of 5000 keywords, set as the asset, then call
        // the loader 5000 times. The first call populates the cache;
        // every subsequent call returns the same cached array. We
        // detect "re-parsing" by mutating the override after the
        // first call — if the loader re-parses, the later calls
        // would see the mutated override.
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 5000; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"label\": \"k").append(i)
              .append("\", \"insertText\": \"k\", \"type\": \"KEYWORD\"}");
        }
        sb.append("]");
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, sb.toString());

        StaticCompletionItem[] first = StaticCompletionLoader.getJsKeywords();
        assertEquals(5000, first.length);

        // Mutate the override — if the loader re-parses, the cache
        // would now contain the new "ZZZ" entry.
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.JS_KEYWORDS_ASSET,
                "[{\"label\": \"ZZZ\", \"insertText\": \"Z\", \"type\": \"KEYWORD\"}]");
        for (int i = 0; i < 5000; i++) {
            StaticCompletionItem[] again = StaticCompletionLoader.getJsKeywords();
            assertTrue("iteration " + i + " must return cached array (reference equal)",
                    first == again);
            assertEquals(5000, again.length);
        }
    }

    @Test
    public void malformedJsonReturnsEmpty() {
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, "{ not valid json");
        StaticCompletionItem[] items = StaticCompletionLoader.getJsKeywords();
        assertNotNull(items);
        assertEquals(0, items.length);
    }
}
