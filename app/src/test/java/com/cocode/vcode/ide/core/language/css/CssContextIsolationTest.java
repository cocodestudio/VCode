package com.cocode.vcode.ide.core.language.css;

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

/**
 * Acceptance tests for Phase 5 CSS grammatical context isolation,
 * CSS Emmet scoping, and replacement length guarantees.
 */
public class CssContextIsolationTest {

    private CssAutoCompleteEngine engine;

    @Before
    public void setup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
        CssDefinitions.loadCssDefinitions();
        engine = new CssAutoCompleteEngine(null);
    }

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    @Test
    public void selectorPositionSuggestsSelectorsOnly() {
        String css = ".card ";
        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        assertNotNull(items);
        assertFalse("Selector suggestions should not be empty", items.isEmpty());

        Set<String> labels = extractLabels(items);

        // Property names, property values, and Emmet shorthands must NEVER leak into selector position
        assertFalse("display property must not be suggested in selector position", labels.contains("display"));
        assertFalse("color property must not be suggested in selector position", labels.contains("color"));
        assertFalse("value 'flex' must not be suggested in selector position", labels.contains("flex"));
        assertFalse("value 'red' must not be suggested in selector position", labels.contains("red"));
        assertFalse("Emmet 'df' must not be suggested in selector position", labels.contains("df"));
        assertFalse("Emmet 'm10' must not be suggested in selector position", labels.contains("m10"));

        // When typing 'd' in selector position, tag 'div' is offered, not 'display'
        String cssTyped = ".card d";
        List<CompletionItem> typedItems = engine.getSuggestions(cssTyped, cssTyped.length());
        Set<String> typedLabels = extractLabels(typedItems);
        assertTrue("div should be suggested as selector when typing 'd'", typedLabels.contains("div"));
        assertFalse("display property must not be suggested in selector position", typedLabels.contains("display"));
    }

    @Test
    public void mediaQueryRootIsSelectorPositionNotProperty() {
        // Inside @media block root, the cursor is at the selector position of nested rules
        String css = "@media (min-width: 600px) {\n  ";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.SELECTOR, ctx.type);

        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        // Properties and Emmet expansions must NOT be offered in media query root
        assertFalse("display must not be offered in media root selector position", labels.contains("display"));
        assertFalse("color must not be offered in media root selector position", labels.contains("color"));
        assertFalse("Emmet 'df' must not be offered in media root selector position", labels.contains("df"));

        // When typing a tag prefix inside media root, tags are offered
        String cssTyped = "@media (min-width: 600px) {\n  d";
        List<CompletionItem> typedItems = engine.getSuggestions(cssTyped, cssTyped.length());
        Set<String> typedLabels = extractLabels(typedItems);
        assertTrue("div should be offered in media query root", typedLabels.contains("div"));
        assertFalse("display property must not be offered in media query root", typedLabels.contains("display"));
    }

    @Test
    public void mediaQueryNestedRuleIsPropertyNamePosition() {
        String css = "@media (min-width: 600px) {\n  .box {\n    dis";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.PROPERTY_NAME, ctx.type);

        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        assertTrue("CSS properties must be suggested inside rule within @media", labels.contains("display"));
        assertFalse("Selectors like div must not be suggested inside declaration block", labels.contains("div"));
    }

    @Test
    public void propertyNamePositionSuggestsPropertiesAndEmmet() {
        String css = ".box {\n  col";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.PROPERTY_NAME, ctx.type);

        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        assertTrue("color should be suggested in property name position", labels.contains("color"));
        assertFalse("red should not be suggested in property name position", labels.contains("red"));
        assertFalse("div tag should not be suggested in property name position", labels.contains("div"));

        String cssDis = ".box {\n  dis";
        Set<String> disLabels = extractLabels(engine.getSuggestions(cssDis, cssDis.length()));
        assertTrue("display should be suggested in property name position", disLabels.contains("display"));

        // Emmet expansion in property name position
        String emmetCss = ".box {\n  m10";
        List<CompletionItem> emmetItems = engine.getSuggestions(emmetCss, emmetCss.length());
        assertNotNull(emmetItems);
        boolean foundEmmet = false;
        for (CompletionItem item : emmetItems) {
            if ("m10".equals(item.getLabel()) && "margin: 10px;".equals(item.getEffectiveInsertText())) {
                foundEmmet = true;
                assertEquals(3, item.getReplaceLength());
                break;
            }
        }
        assertTrue("Emmet 'm10' should expand to 'margin: 10px;' with replaceLength 3", foundEmmet);
    }

    @Test
    public void propertyValuePositionSuggestsValuesColorsAndFunctions() {
        String css = ".box {\n  color: re";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.PROPERTY_VALUE, ctx.type);
        assertEquals("color", ctx.propertyName);

        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        assertTrue("red color should be suggested for color property", labels.contains("red"));
        assertFalse("display property should not be suggested in value position", labels.contains("display"));
        assertFalse("margin property should not be suggested in value position", labels.contains("margin"));
        assertFalse("div selector should not be suggested in value position", labels.contains("div"));

        String cssVar = ".box {\n  color: v";
        Set<String> varLabels = extractLabels(engine.getSuggestions(cssVar, cssVar.length()));
        boolean hasVar = false;
        for (String lbl : varLabels) {
            if (lbl.startsWith("var(")) {
                hasVar = true;
                break;
            }
        }
        assertTrue("var() function should be suggested", hasVar);
    }

    @Test
    public void propertyValueDisplayDoesNotSuggestPaletteColors() {
        String css = ".box {\n  display: ";
        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        assertTrue("flex should be suggested for display", labels.contains("flex"));
        assertTrue("block should be suggested for display", labels.contains("block"));
        assertFalse("red should not be suggested for display", labels.contains("red"));
        assertFalse("blue should not be suggested for display", labels.contains("blue"));
    }

    @Test
    public void atRulePositionSuggestsAtRulesOnly() {
        String css = "@med";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.AT_RULE, ctx.type);

        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        assertTrue("@media should be suggested for @med", labels.contains("@media"));
        assertFalse("color property should not be suggested for @med", labels.contains("color"));
        assertFalse("flex value should not be suggested for @med", labels.contains("flex"));

        for (CompletionItem item : items) {
            assertEquals(4, item.getReplaceLength());
        }
    }

    @Test
    public void mediaFeatureConditionSuggestsFeatures() {
        String css = "@media (min-";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.MEDIA_FEATURE, ctx.type);

        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        Set<String> labels = extractLabels(items);

        assertTrue("min-width should be suggested in media feature condition", labels.contains("min-width"));
        assertFalse("color property should not be suggested in media condition", labels.contains("color"));

        for (CompletionItem item : items) {
            assertEquals("min-".length(), item.getReplaceLength());
        }
    }

    @Test
    public void urlPositionDetectedAccurately() {
        String css = ".hero {\n  background: url(\"images/";
        CssContextScanner.CssContext ctx = CssContextScanner.scan(css, css.length(), false);
        assertEquals(CssContextScanner.ContextType.URL_VALUE, ctx.type);
        assertFalse(ctx.isInsideString);
    }

    @Test
    public void allCompletionItemsHaveExplicitReplaceLength() {
        // 1. Selector query
        String selCss = "di";
        List<CompletionItem> selItems = engine.getSuggestions(selCss, selCss.length());
        for (CompletionItem item : selItems) {
            assertTrue("Replace length must be >= 0", item.getReplaceLength() >= 0);
            assertEquals(2, item.getReplaceLength());
        }

        // 2. Property query
        String propCss = ".box {\n  col";
        List<CompletionItem> propItems = engine.getSuggestions(propCss, propCss.length());
        for (CompletionItem item : propItems) {
            assertTrue("Replace length must be >= 0", item.getReplaceLength() >= 0);
            assertEquals(3, item.getReplaceLength());
        }

        // 3. Value query
        String valCss = ".box {\n  display: fl";
        List<CompletionItem> valItems = engine.getSuggestions(valCss, valCss.length());
        for (CompletionItem item : valItems) {
            assertTrue("Replace length must be >= 0", item.getReplaceLength() >= 0);
            assertEquals(2, item.getReplaceLength());
        }
    }

    @Test
    public void pseudoElementTypingDoesNotDuplicateColons() {
        String css = "a::bef";
        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        assertNotNull(items);

        boolean foundBefore = false;
        for (CompletionItem item : items) {
            if (item.getLabel().contains("before")) {
                foundBefore = true;
                // When preceded by ::, insertText should NOT add extra colons
                assertFalse("insertText must not start with ':' when typing '::'",
                        item.getEffectiveInsertText().startsWith(":"));
                assertEquals("bef".length(), item.getReplaceLength());
            }
        }
        assertTrue("::before should be suggested", foundBefore);
    }

    @Test
    public void commentsAndStringsDoNotProduceSuggestions() {
        String commentCss = ".box {\n  /* col";
        List<CompletionItem> commentItems = engine.getSuggestions(commentCss, commentCss.length());
        assertTrue("No suggestions inside unclosed block comment", commentItems.isEmpty());

        String stringCss = ".box {\n  content: \"hel";
        List<CompletionItem> stringItems = engine.getSuggestions(stringCss, stringCss.length());
        assertTrue("No suggestions inside unclosed string literal", stringItems.isEmpty());
    }

    private static Set<String> extractLabels(List<CompletionItem> items) {
        Set<String> set = new HashSet<>();
        if (items != null) {
            for (CompletionItem item : items) {
                set.add(item.getLabel());
            }
        }
        return set;
    }
}
