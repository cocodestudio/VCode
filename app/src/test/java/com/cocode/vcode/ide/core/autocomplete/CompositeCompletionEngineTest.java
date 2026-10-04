package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompositeCompletionEngineTest {

    private CompositeCompletionEngine engine;

    @Before
    public void setUp() {
        engine = new CompositeCompletionEngine(null);
    }

    @Test
    public void testCustomProviderRegistrationAndExecution() {
        CompletionProvider mockProvider = new CompletionProvider() {
            @Override
            public boolean canProvide(CompletionContext context) {
                return context.getFileType() == FileType.JAVASCRIPT;
            }

            @Override
            public List<CompletionItem> provideCompletions(CompletionContext context) {
                List<CompletionItem> items = new ArrayList<>();
                items.add(new CompletionItem("customFunction", "customFunction()", "function", "Plugin"));
                items.add(new CompletionItem("customVariable", "customVariable", "variable", "Plugin"));
                return items;
            }

            @Override
            public int getPriority() {
                return PRIORITY_HIGH;
            }
        };

        engine.registerProvider(mockProvider);
        assertEquals(1, engine.getCustomProviders().size());

        // Context for JavaScript
        engine.setFileType(FileType.JAVASCRIPT);
        List<CompletionItem> results = engine.getSuggestions("const x = custom", 16);

        assertFalse(results.isEmpty());
        boolean hasCustom = false;
        for (CompletionItem item : results) {
            if ("customFunction".equals(item.getLabel())) {
                hasCustom = true;
                break;
            }
        }
        assertTrue("Results should contain completions from custom provider", hasCustom);

        // Unregister provider
        engine.unregisterProvider(mockProvider);
        assertEquals(0, engine.getCustomProviders().size());
    }

    @Test
    public void testMultipleProvidersDeduplication() {
        CompletionProvider highPriorityProvider = new CompletionProvider() {
            @Override
            public boolean canProvide(CompletionContext context) {
                return true;
            }

            @Override
            public List<CompletionItem> provideCompletions(CompletionContext context) {
                List<CompletionItem> items = new ArrayList<>();
                items.add(new CompletionItem("sharedItem", "sharedItem(high)", "High", CompletionItem.Type.FUNCTION));
                return items;
            }

            @Override
            public int getPriority() {
                return PRIORITY_HIGH;
            }
        };

        CompletionProvider lowPriorityProvider = new CompletionProvider() {
            @Override
            public boolean canProvide(CompletionContext context) {
                return true;
            }

            @Override
            public List<CompletionItem> provideCompletions(CompletionContext context) {
                List<CompletionItem> items = new ArrayList<>();
                items.add(new CompletionItem("sharedItem", "sharedItem(low)", "Low", CompletionItem.Type.FUNCTION));
                return items;
            }

            @Override
            public int getPriority() {
                return PRIORITY_LOW;
            }
        };

        engine.registerProvider(highPriorityProvider);
        engine.registerProvider(lowPriorityProvider);

        List<CompletionItem> results = engine.getSuggestions("shared", 6);
        assertEquals(1, results.size());
        assertEquals("sharedItem", results.get(0).getLabel());
        assertEquals("High", results.get(0).getDetail());
    }

    @Test
    public void testFuzzyAcronymAndBoundaryMatching() {
        int clgScore = CompositeCompletionEngine.computeAcronymMatchScore("console.log", "clg");
        assertTrue("clg should match console.log acronym/boundary with high score", clgScore >= 550);

        int gebiScore = CompositeCompletionEngine.computeAcronymMatchScore("getElementById", "gebi");
        assertTrue("gebi should match getElementById acronym", gebiScore >= 550);

        List<CompletionItem> candidates = new ArrayList<>();
        candidates.add(new CompletionItem("clear", "clear()", "void", CompletionItem.Type.FUNCTION));
        candidates.add(new CompletionItem("console.log", "console.log()", "void", CompletionItem.Type.FUNCTION));
        candidates.add(new CompletionItem("cloneNode", "cloneNode()", "Node", CompletionItem.Type.FUNCTION));

        List<CompletionItem> filtered = engine.fuzzyFilter(candidates, "clg");
        assertFalse(filtered.isEmpty());
        assertEquals("console.log", filtered.get(0).getLabel());
    }

    @Test
    public void testCompletionItemCustomAttributes() {
        CompletionItem item = new CompletionItem("pluginAction", "pluginAction()", "detail", CompletionItem.Type.FUNCTION);
        assertEquals(0, item.getCustomColor());

        item.setCustomColor(0xFF123456);
        assertEquals(0xFF123456, item.getCustomColor());

        CompletionItem copy = new CompletionItem(item);
        assertEquals(0xFF123456, copy.getCustomColor());
        assertEquals("pluginAction", copy.getLabel());
    }
}
