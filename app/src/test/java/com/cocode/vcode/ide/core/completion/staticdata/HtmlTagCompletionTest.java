package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.8 (HTML tag-name completion),
 * M.9 (attribute-name completion), and M.10 (closing-tag AST-only).
 */
public class HtmlTagCompletionTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedLoader() {
        String json = "{" +
                "  \"tags\": [" +
                "    {\"tag\": \"div\", \"selfClosing\": false, \"snippet\": \"<div>|</div>\", \"detail\": \"Container\", \"attributes\": [\"id\", \"class\"]}," +
                "    {\"tag\": \"img\", \"selfClosing\": true, \"snippet\": \"<img src=\\\"|\\\" alt=\\\"\\\"/>\", \"detail\": \"Image\", \"attributes\": [\"src\", \"alt\"]}," +
                "    {\"tag\": \"a\", \"selfClosing\": false, \"snippet\": \"<a href=\\\"|\\\"></a>\", \"detail\": \"Link\", \"attributes\": [\"href\", \"target\"]}" +
                "  ]," +
                "  \"globalAttributes\": [\"id\", \"class\", \"style\", \"title\", \"role\"]," +
                "  \"globalAttributePrefixes\": [\"data-\", \"aria-\"]" +
                "}";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.HTML_TAGS_ASSET, json);
    }

    // --- M.8 ----------------------------------------------------------

    @Test
    public void cursorAfterOpenAngleIsTagNamePosition() {
        String src = "<di|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.TAG_NAME, pos);
    }

    @Test
    public void tagNameCompletionSuggestsAllTags() {
        seedLoader();
        String src = "<di|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        List<CompletionItem> items = HtmlStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        assertTrue("div must be suggested", labels.contains("div"));
        assertTrue("img must be suggested", labels.contains("img"));
        assertTrue("a must be suggested", labels.contains("a"));
    }

    @Test
    public void tagNameInsertionUsesSnippetWithCursorBetween() {
        // M.8 acceptance: `<di|` suggests `div` and inserts `<div>|</div>`
        // with cursor between the tags.
        seedLoader();
        String src = "<di|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        List<CompletionItem> items = HtmlStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        CompletionItem div = findByLabel(items, "div");
        assertNotNull("div must be present", div);
        assertEquals("<div></div>", div.getEffectiveInsertText());
        assertEquals(5, div.getCursorOffset());
    }

    @Test
    public void cursorAfterClosingAngleIsNotInTag() {
        String src = "<div>|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.NONE, pos);
    }

    // --- M.9 ----------------------------------------------------------

    @Test
    public void cursorAfterSpaceInTagIsAttributePosition() {
        String src = "<img |";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME, pos);
    }

    @Test
    public void attributeCompletionSuggestsTagSpecificAndGlobal() {
        // M.9 acceptance: inside `<img |>`, suggests `src`/`alt`/etc.
        // plus `id`/`class`/etc. global attributes, but not
        // attributes already typed on that same tag.
        seedLoader();
        String src = "<img |";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        List<CompletionItem> items = HtmlStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // Tag-specific attributes.
        assertTrue("src must be suggested", labels.contains("src"));
        assertTrue("alt must be suggested", labels.contains("alt"));
        // Global attributes.
        assertTrue("id (global) must be suggested", labels.contains("id"));
        assertTrue("class (global) must be suggested", labels.contains("class"));
        assertTrue("role (global) must be suggested", labels.contains("role"));
        // Prefix attrs.
        assertTrue("data- prefix must be suggested", labels.contains("data-"));
        assertTrue("aria- prefix must be suggested", labels.contains("aria-"));
        // Attributes from unrelated tags must NOT appear.
        assertEquals("href is for <a>, not <img>", false, labels.contains("href"));
    }

    @Test
    public void alreadyTypedAttributesAreExcluded() {
        seedLoader();
        // <img src="..." | — src is already typed.
        String src = "<img src=\"x.png\" |";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME, pos);
        List<CompletionItem> items = HtmlStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        assertEquals("src must be excluded (already typed)", false, labels.contains("src"));
        assertTrue("alt must still be suggested", labels.contains("alt"));
    }

    @Test
    public void cursorAfterEqualsSignIsNotAttributeName() {
        // In <img src="|">, the cursor is at value position,
        // not attribute-name position. We return NONE here;
        // value completion is out of M.8–M.10 scope.
        String src = "<img src=\"|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.NONE, pos);
    }

    // --- M.10 ---------------------------------------------------------

    @Test
    public void cursorAfterClosingAngleSlashIsClosingTagPosition() {
        String src = "<div><span></|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.CLOSING_TAG, pos);
    }

    @Test
    public void closingTagPositionBuildCompletionsReturnsEmpty() {
        // M.10: the static dispatcher returns empty for closing-tag
        // position. The AST-ancestry-only caller fills in the
        // actual suggestions.
        seedLoader();
        String src = "<div><span></|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        List<CompletionItem> items = HtmlStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        assertEquals(0, items.size());
    }

    @Test
    public void openTagNameResolutionReturnsLowercase() {
        // Cursor is inside the open tag, right after the tag name.
        String src = "<DIV |";
        int cursor = src.indexOf('|');
        String name = HtmlStaticCompletionDispatcher.resolveOpenTagName(src, cursor);
        assertEquals("div", name);
    }

    @Test
    public void openTagNameResolutionReturnsNullOutsideTag() {
        String src = "hello world|";
        int cursor = src.indexOf('|');
        String name = HtmlStaticCompletionDispatcher.resolveOpenTagName(src, cursor);
        assertEquals(null, name);
    }

    @Test
    public void openTagNameResolutionReturnsNullForClosingTag() {
        String src = "<div></di|";
        int cursor = src.indexOf('|');
        String name = HtmlStaticCompletionDispatcher.resolveOpenTagName(src, cursor);
        assertEquals(null, name);
    }

    // --- helpers -------------------------------------------------------

    private static CompletionItem findByLabel(List<CompletionItem> items, String label) {
        for (CompletionItem c : items) {
            if (label.equals(c.getLabel())) return c;
        }
        return null;
    }
}
