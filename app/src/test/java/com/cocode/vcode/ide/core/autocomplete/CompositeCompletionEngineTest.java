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
}
