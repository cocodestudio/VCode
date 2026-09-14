package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.5 (property-value completion) and M.6
 * (unknown-property fallback).
 */
public class CssValueCompletionTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedLoader() {
        String props = "[" +
                "{\"property\": \"color\", \"values\": [\"red\", \"blue\", \"transparent\"], \"acceptsColor\": true}," +
                "{\"property\": \"display\", \"values\": [\"block\", \"flex\", \"inline\"]}," +
                "{\"property\": \"--my-var\", \"values\": []}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_PROPERTIES_ASSET, props);

        String colors = "{" +
                "\"colors\": [\"red\", \"blue\", \"green\"]," +
                "\"color_functions\": [\"rgb(|, , , )\", \"hsl(|)\"]," +
                "\"global_functions\": [\"var(--|)\", \"calc(|)\", \"min(|, )\"]" +
                "}";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.CSS_COLORS_ASSET, colors);
    }

    @Test
    public void colorPropertyOffersValuesColorsAndGlobalFunctions() {
        // M.5 acceptance: `color: |` suggests palette colors AND
        // color functions (and global functions).
        seedLoader();
        String src = ".x { color: |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        assertEquals(CssStaticCompletionDispatcher.Position.PROPERTY_VALUE, pos);
        List<CompletionItem> items = CssStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // Property's own values.
        assertTrue("red from values must be suggested", labels.contains("red"));
        assertTrue("blue from values must be suggested", labels.contains("blue"));
        assertTrue("transparent from values must be suggested", labels.contains("transparent"));
        // Colors from css_colors.json.
        assertTrue("green from colors must be suggested", labels.contains("green"));
        // Color functions.
        assertTrue("rgb() must be suggested", labels.contains("rgb(|, , , )"));
        assertTrue("hsl() must be suggested", labels.contains("hsl(|)"));
        // Global functions.
        assertTrue("var() must be suggested", labels.contains("var(--|)"));
        assertTrue("calc() must be suggested", labels.contains("calc(|)"));
    }

    @Test
    public void displayPropertyDoesNotOfferColors() {
        // M.5 acceptance: `display: |` does NOT suggest palette
        // colors or color functions — only the property's own
        // values and global functions.
        seedLoader();
        String src = ".x { display: |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        List<CompletionItem> items = CssStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // Property's own values.
        assertTrue("block must be suggested", labels.contains("block"));
        assertTrue("flex must be suggested", labels.contains("flex"));
        // Global functions always.
        assertTrue("var() must be suggested", labels.contains("var(--|)"));
        // No colors or color functions.
        assertFalse("red (color) must not be suggested for display", labels.contains("red"));
        assertFalse("green (color) must not be suggested for display", labels.contains("green"));
        assertFalse("rgb() (color function) must not be suggested for display",
                labels.contains("rgb(|, , , )"));
    }

    @Test
    public void unknownPropertyFallsBackToGlobalFunctionsOnly() {
        // M.6 acceptance: `--my-var: |` (custom property not in the
        // static map) still gets `var()`/`calc()`-style suggestions,
        // not an empty list and not the color palette.
        seedLoader();
        String src = ".x { --my-var: |";
        int cursor = src.indexOf('|');
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(src, null, null, cursor);
        List<CompletionItem> items = CssStaticCompletionDispatcher.buildCompletions(pos, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // Global functions are still offered.
        assertTrue("var() must be offered for unknown property", labels.contains("var(--|)"));
        assertTrue("calc() must be offered for unknown property", labels.contains("calc(|)"));
        // No colors for unknown property (do NOT guess a value set).
        assertFalse("red (color) must not be offered for unknown property", labels.contains("red"));
        assertFalse("rgb() must not be offered for unknown property",
                labels.contains("rgb(|, , , )"));
    }

    @Test
    public void propertyNameResolutionAcrossSemicolons() {
        // After `;`, the next property name should resolve fresh
        // — i.e. `color` is bound to its declaration, not the
        // previous one.
        seedLoader();
        String src = ".x { color: red; display: |";
        int cursor = src.indexOf('|');
        String resolved = CssStaticCompletionDispatcher.resolvePropertyName(src, null, cursor);
        assertEquals("display", resolved);
    }

    @Test
    public void propertyNameResolutionStopsAtOpenBrace() {
        // First declaration in a rule.
        seedLoader();
        String src = ".x { color: |";
        int cursor = src.indexOf('|');
        String resolved = CssStaticCompletionDispatcher.resolvePropertyName(src, null, cursor);
        assertEquals("color", resolved);
    }

    @Test
    public void propertyNameResolutionReturnsNullOutsideBlock() {
        // No '{' exists in the source — resolvePropertyName returns null.
        String src = ".x color: red; |";
        int cursor = src.indexOf('|');
        String resolved = CssStaticCompletionDispatcher.resolvePropertyName(src, null, cursor);
        assertEquals(null, resolved);
    }
}
