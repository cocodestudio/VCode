package com.cocode.vcode.ide.core.language.html;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class HtmlContextIsolationTest {

    private HtmlAutoCompleteEngine engine;

    @Before
    public void setUp() {
        engine = new HtmlAutoCompleteEngine(RuntimeEnvironment.getApplication());
    }

    @Test
    public void testTagNameIsolation() {
        // "<di|" -> only tag names matching prefix; no attributes or closing tags
        String text = "<di";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertFalse(items.isEmpty());
        for (CompletionItem item : items) {
            String label = item.getLabel();
            assertTrue("Expected tag name item starting with di, got: " + label,
                    label.startsWith("di") || label.startsWith("<di"));
            assertFalse("Attributes forbidden in tag name position: " + label,
                    label.equals("class") || label.equals("id"));
            assertFalse("Closing tags forbidden in tag name position: " + label,
                    label.startsWith("</"));
        }
    }

    @Test
    public void testAttributeNameIsolation() {
        // "<div cl|" -> only attributes matching prefix; tags forbidden
        String text = "<div cl";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertFalse(items.isEmpty());
        for (CompletionItem item : items) {
            String label = item.getLabel();
            assertFalse("Tags forbidden in attribute position: " + label,
                    label.equals("code") || label.equals("canvas") || label.equals("div"));
            assertFalse("Closing tags forbidden in attribute position: " + label,
                    label.startsWith("</"));
        }
        boolean hasClass = false;
        for (CompletionItem item : items) {
            if ("class".equals(item.getLabel())) {
                hasClass = true;
                break;
            }
        }
        assertTrue("Expected 'class' attribute to be suggested", hasClass);
    }

    @Test
    public void testTagClosureSuppression() {
        // Immediately after typing '>', completion popup should be suppressed entirely
        String text = "<div>";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertTrue("Completion popup must be suppressed after typing '>'", items.isEmpty());

        String textWithSpace = "<div >";
        List<CompletionItem> itemsWithSpace = engine.getSuggestions(textWithSpace, textWithSpace.length());
        assertTrue("Completion popup must be suppressed after typing '>'", itemsWithSpace.isEmpty());
    }

    @Test
    public void testClosingTagPositionLifoAncestry() {
        // "<div><span></|" -> should suggest closing tags in LIFO order
        String text = "<div><span></";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertFalse(items.isEmpty());
        // First item should be span closing tag
        assertEquals("</span>", items.get(0).getLabel());
        for (CompletionItem item : items) {
            assertFalse("Attributes forbidden in closing tag position: " + item.getLabel(),
                    item.getLabel().equals("class"));
            assertFalse("Emmet expansion forbidden in closing tag position: " + item.getLabel(),
                    item.getDetail() != null && item.getDetail().contains("Emmet"));
        }
    }

    @Test
    public void testEmmetNotHijackedByCompletedClosingTagOnLine() {
        // "<div>lorem20|</div>" -> should trigger Emmet lorem expansion, NOT closing tag logic
        String text = "<div>lorem20</div>";
        int cursor = text.indexOf("20") + 2;
        List<CompletionItem> items = engine.getSuggestions(text, cursor);
        assertFalse(items.isEmpty());
        boolean hasEmmet = false;
        for (CompletionItem item : items) {
            if (item.getDetail() != null && item.getDetail().contains("Emmet")) {
                hasEmmet = true;
                break;
            }
        }
        assertTrue("Emmet lorem expansion must not be hijacked by closing tag on line", hasEmmet);
    }

    @Test
    public void testTextContentPositionSuggestsTagsAndEmmet() {
        // Blank line or indented text content: typing "div" suggests bare tag / Emmet
        String text = "div";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertFalse(items.isEmpty());
        boolean hasDiv = false;
        for (CompletionItem item : items) {
            if ("div".equals(item.getLabel())) {
                hasDiv = true;
            }
            assertFalse("Closing tags forbidden in text content: " + item.getLabel(),
                    item.getLabel().startsWith("</"));
        }
        assertTrue("Expected 'div' tag/snippet suggested in text content", hasDiv);
    }
}
