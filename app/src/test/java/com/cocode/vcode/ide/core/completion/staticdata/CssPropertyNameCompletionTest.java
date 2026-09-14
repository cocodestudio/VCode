package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.4: CSS property-name completion is
 * AST-gated. Cursor right after {@code {} in a rule suggests
 * property names only, never values.
 */
public class CssPropertyNameCompletionTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedLoader() {
        String json = "[" +
                "{\"property\": \"color\", \"values\": [\"red\"], \"acceptsColor\": true}," +
                "{\"property\": \"display\", \"values\": [\"block\"]}," +
                "{\"property\": \"background-color\", \"values\": [\"red\"], \"acceptsColor\": true}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_PROPERTIES_ASSET, json);
    }

    @Test
    public void cursorAfterOpenBraceIsPropertyNamePosition() {
        String src = ".x { |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.PROPERTY_NAME, pos);
    }

    @Test
    public void cursorAfterSemicolonIsPropertyNamePosition() {
        String src = ".x { color: red; |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.PROPERTY_NAME, pos);
    }

    @Test
    public void cursorAfterColonIsPropertyValuePosition() {
        String src = ".x { color: |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.PROPERTY_VALUE, pos);
    }

    @Test
    public void cursorOutsideAnyBlockIsNonePosition() {
        // No '{' anywhere before the cursor in this source.
        String src = ".x |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.NONE, pos);
    }

    @Test
    public void cursorAfterClosingBraceIsNonePosition() {
        // '}' before cursor means we exited a block.
        String src = ".x { color: red; } |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.NONE, pos);
    }

    @Test
    public void propertyNameCompletionSuggestsOnlyProperties() {
        seedLoader();
        String src = ".x { |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.PROPERTY_NAME, pos);
        List<CompletionItem> items = CssStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // Property names from the static data appear.
        assertTrue("color must be suggested", labels.contains("color"));
        assertTrue("display must be suggested", labels.contains("display"));
        assertTrue("background-color must be suggested", labels.contains("background-color"));
        // Property *values* must NOT appear in property-name position.
        assertFalse("value 'red' must not be suggested in property-name position",
                labels.contains("red"));
        assertFalse("value 'block' must not be suggested in property-name position",
                labels.contains("block"));
    }

    @Test
    public void propertyNamePositionNeverReturnsEmpty() {
        seedLoader();
        String src = ".x { |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        List<CompletionItem> items = CssStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        assertNotNull(items);
        assertTrue("property-name position must produce at least one item", items.size() > 0);
    }

    @Test
    public void nonePositionReturnsEmpty() {
        seedLoader();
        String src = ".x |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        List<CompletionItem> items = CssStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        assertNotNull(items);
        assertEquals(0, items.size());
    }
}
