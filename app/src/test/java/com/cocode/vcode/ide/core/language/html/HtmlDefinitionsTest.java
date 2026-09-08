package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HtmlDefinitionsTest {

    private HtmlAutoCompleteEngine engine;

    @Before
    public void setUp() {
        HtmlDefinitions.ensureLoaded();
        engine = new HtmlAutoCompleteEngine(null);
    }

    @After
    public void tearDown() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    @Test
    public void testGlobalAttributesLoadedAndFormatted() {
        List<CompletionItem> globals = HtmlDefinitions.getGlobalAttrs();
        assertNotNull(globals);
        assertTrue("Must contain at least 42 global attributes", globals.size() >= 42);

        Set<String> labels = new HashSet<>();
        for (CompletionItem ci : globals) {
            labels.add(ci.getLabel());
        }

        // Spot-check standard HTML5 global attributes
        assertTrue(labels.contains("class"));
        assertTrue(labels.contains("id"));
        assertTrue(labels.contains("style"));
        assertTrue(labels.contains("title"));
        assertTrue(labels.contains("lang"));
        assertTrue(labels.contains("dir"));
        assertTrue(labels.contains("tabindex"));
        assertTrue(labels.contains("hidden"));
        assertTrue(labels.contains("popover"));
        assertTrue(labels.contains("autofocus"));
        assertTrue(labels.contains("inert"));
        assertTrue(labels.contains("data-"));
        assertTrue(labels.contains("aria-label"));
        assertTrue(labels.contains("enterkeyhint"));
        assertTrue(labels.contains("inputmode"));

        // Verify boolean attribute formatting
        CompletionItem hidden = findItem(globals, "hidden");
        assertNotNull(hidden);
        assertEquals("hidden", hidden.getEffectiveInsertText());

        CompletionItem inert = findItem(globals, "inert");
        assertNotNull(inert);
        assertEquals("inert", inert.getEffectiveInsertText());

        CompletionItem popover = findItem(globals, "popover");
        assertNotNull(popover);
        assertEquals("popover", popover.getEffectiveInsertText());

        CompletionItem autofocus = findItem(globals, "autofocus");
        assertNotNull(autofocus);
        assertEquals("autofocus", autofocus.getEffectiveInsertText());

        // Verify data- prefix template
        CompletionItem data = findItem(globals, "data-");
        assertNotNull(data);
        assertEquals("data-|=\"\"", data.getEffectiveInsertText());
    }

    @Test
    public void testEventAttributesLoaded() {
        List<CompletionItem> events = HtmlDefinitions.getEventAttrs();
        assertNotNull(events);
        assertEquals("Must contain 61 event attributes", 61, events.size());

        Set<String> labels = new HashSet<>();
        for (CompletionItem ci : events) {
            labels.add(ci.getLabel());
        }

        assertTrue(labels.contains("onclick"));
        assertTrue(labels.contains("ondblclick"));
        assertTrue(labels.contains("onkeydown"));
        assertTrue(labels.contains("onkeyup"));
        assertTrue(labels.contains("onchange"));
        assertTrue(labels.contains("oninput"));
        assertTrue(labels.contains("onsubmit"));
        assertTrue(labels.contains("ontouchstart"));
        assertTrue(labels.contains("onpointerdown"));
        assertTrue(labels.contains("onanimationstart"));
        assertTrue(labels.contains("ontoggle"));
    }

    @Test
    public void testDoctypesLoaded() {
        List<CompletionItem> doctypes = HtmlDefinitions.getDoctypeItems();
        assertNotNull(doctypes);
        assertEquals(2, doctypes.size());

        CompletionItem html5 = findItem(doctypes, "<!DOCTYPE html>");
        assertNotNull(html5);
        assertEquals("<!DOCTYPE html>\n", html5.getEffectiveInsertText());

        CompletionItem comment = findItem(doctypes, "<!-- -->");
        assertNotNull(comment);
        assertEquals("<!-- | -->", comment.getEffectiveInsertText());
    }

    @Test
    public void testEntitiesLoaded() {
        List<CompletionItem> entities = HtmlDefinitions.getEntityItems();
        assertNotNull(entities);
        assertEquals(35, entities.size());

        Set<String> labels = new HashSet<>();
        for (CompletionItem ci : entities) {
            labels.add(ci.getLabel());
        }

        assertTrue(labels.contains("&amp;"));
        assertTrue(labels.contains("&lt;"));
        assertTrue(labels.contains("&gt;"));
        assertTrue(labels.contains("&copy;"));
        assertTrue(labels.contains("&euro;"));
        assertTrue(labels.contains("&star;"));
        assertTrue(labels.contains("&hearts;"));
    }

    @Test
    public void testAttributeValuesAndTagOverrides() {
        String[] methodValues = HtmlDefinitions.getAttributeValues("method");
        assertNotNull(methodValues);
        assertEquals(3, methodValues.length);
        assertEquals("get", methodValues[0]);

        // Button type tag override: should only be button, submit, reset
        String[] buttonTypes = HtmlDefinitions.getAttributeValues("button", "type");
        assertNotNull(buttonTypes);
        assertEquals(3, buttonTypes.length);
        assertEquals("button", buttonTypes[0]);
        assertEquals("submit", buttonTypes[1]);
        assertEquals("reset", buttonTypes[2]);

        // Input type: should have all 22 types
        String[] inputTypes = HtmlDefinitions.getAttributeValues("input", "type");
        assertNotNull(inputTypes);
        assertEquals(22, inputTypes.length);

        // Script type: should have module, text/javascript, application/json
        String[] scriptTypes = HtmlDefinitions.getAttributeValues("script", "type");
        assertNotNull(scriptTypes);
        assertEquals(3, scriptTypes.length);
        assertEquals("module", scriptTypes[0]);
    }

    @Test
    public void testStaticDispatcherContextAndExclusion() {
        String src = "<div |";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME, pos);

        List<CompletionItem> items = HtmlStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem ci : items) labels.add(ci.getLabel());

        assertTrue(labels.contains("class"));
        assertTrue(labels.contains("hidden"));
        assertTrue(labels.contains("onclick"));
        assertTrue(labels.contains("data-"));

        // Boolean attribute insertText must not have ="|"
        CompletionItem hidden = findItem(items, "hidden");
        assertNotNull(hidden);
        assertEquals("hidden", hidden.getEffectiveInsertText());

        // Already typed attributes must be excluded
        String src2 = "<div id=\"foo\" |";
        int cursor2 = src2.indexOf('|');
        List<CompletionItem> items2 = HtmlStaticCompletionDispatcher.buildCompletions(
                HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME, src2, cursor2);
        Set<String> labels2 = new HashSet<>();
        for (CompletionItem ci : items2) labels2.add(ci.getLabel());
        assertFalse("id must be excluded because it is already present", labels2.contains("id"));
        assertTrue("class must still be suggested", labels2.contains("class"));

        // Editing attribute at cursor must NOT be excluded
        String src3 = "<div cl|";
        int cursor3 = src3.indexOf('|');
        List<CompletionItem> items3 = HtmlStaticCompletionDispatcher.buildCompletions(
                HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME, src3, cursor3);
        Set<String> labels3 = new HashSet<>();
        for (CompletionItem ci : items3) labels3.add(ci.getLabel());
        assertTrue("class must be suggested when editing cl", labels3.contains("class"));
    }

    @Test
    public void testCursorAfterEqualsIsNotAttributeName() {
        String src = "<input type=|";
        int cursor = src.indexOf('|');
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals("Cursor right after = is attribute value, not attribute name",
                HtmlStaticCompletionDispatcher.Position.NONE, pos);
    }

    @Test
    public void testEngineAttributeValueSuggestions() {
        // 1. Quoted button type -> button, submit, reset only
        String btnSrc = "<button type=\"|\"";
        int btnCursor = btnSrc.indexOf('|');
        List<CompletionItem> btnCompletions = engine.getSuggestions(btnSrc, btnCursor);
        assertEquals(3, btnCompletions.size());
        assertEquals("button", btnCompletions.get(0).getLabel());
        assertEquals("button", btnCompletions.get(0).getEffectiveInsertText());

        // 2. Unquoted input type -> inserts with quotes
        String inputSrc = "<input type=|";
        int inputCursor = inputSrc.indexOf('|');
        List<CompletionItem> inputCompletions = engine.getSuggestions(inputSrc, inputCursor);
        assertFalse(inputCompletions.isEmpty());
        CompletionItem textItem = findItem(inputCompletions, "text");
        assertNotNull(textItem);
        assertEquals("\"text\"", textItem.getEffectiveInsertText());
        assertEquals(0, textItem.getReplaceLength());

        // 3. Multi-value space-separated attribute
        String relSrc = "<a rel=\"noopener |\"";
        int relCursor = relSrc.indexOf('|');
        List<CompletionItem> relCompletions = engine.getSuggestions(relSrc, relCursor);
        assertFalse(relCompletions.isEmpty());
        CompletionItem noopener = findItem(relCompletions, "noopener");
        assertTrue("Already chosen token 'noopener' must be excluded", noopener == null);
        CompletionItem noreferrer = findItem(relCompletions, "noreferrer");
        assertNotNull(noreferrer);
        assertEquals("noreferrer", noreferrer.getEffectiveInsertText());
        assertEquals(0, noreferrer.getReplaceLength());
    }

    @Test
    public void testEntityCompletionsAndContextGating() {
        // 1. In HTML text: typing &
        String src1 = "Hello &|";
        int cur1 = src1.indexOf('|');
        List<CompletionItem> items1 = engine.getSuggestions(src1, cur1);
        assertFalse(items1.isEmpty());
        CompletionItem copy1 = findItem(items1, "&copy;");
        assertNotNull(copy1);
        assertEquals(1, copy1.getReplaceLength());

        // 2. In HTML text: typing &co
        String src2 = "Hello &co|";
        int cur2 = src2.indexOf('|');
        List<CompletionItem> items2 = engine.getSuggestions(src2, cur2);
        assertFalse(items2.isEmpty());
        CompletionItem copy2 = findItem(items2, "&copy;");
        assertNotNull(copy2);
        assertEquals(3, copy2.getReplaceLength());

        // 3. In <script>: & must not trigger HTML entities
        String srcScript = "<script>\nconst a = 1 &|\n</script>";
        int curScript = srcScript.indexOf('|');
        List<CompletionItem> itemsScript = engine.getSuggestions(srcScript, curScript);
        CompletionItem copyScript = findItem(itemsScript, "&copy;");
        assertTrue("HTML entities must be suppressed inside <script>", copyScript == null);

        // 4. In <style>: & must not trigger HTML entities
        String srcStyle = "<style>\n.btn &|\n</style>";
        int curStyle = srcStyle.indexOf('|');
        List<CompletionItem> itemsStyle = engine.getSuggestions(srcStyle, curStyle);
        CompletionItem copyStyle = findItem(itemsStyle, "&copy;");
        assertTrue("HTML entities must be suppressed inside <style>", copyStyle == null);
    }

    @Test
    public void testDoctypeCompletionsWithReplaceLength() {
        String src = "<!|";
        int cur = src.indexOf('|');
        List<CompletionItem> items = engine.getSuggestions(src, cur);
        assertFalse(items.isEmpty());
        CompletionItem doctype = findItem(items, "<!DOCTYPE html>");
        assertNotNull(doctype);
        assertEquals(2, doctype.getReplaceLength());

        CompletionItem comment = findItem(items, "<!-- -->");
        assertNotNull(comment);
        assertEquals(2, comment.getReplaceLength());
    }

    private static CompletionItem findItem(List<CompletionItem> items, String label) {
        if (items == null) return null;
        for (CompletionItem ci : items) {
            if (label.equals(ci.getLabel())) return ci;
        }
        return null;
    }
}
