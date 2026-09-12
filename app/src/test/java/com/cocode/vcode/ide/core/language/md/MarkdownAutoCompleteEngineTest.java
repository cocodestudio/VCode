package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MarkdownAutoCompleteEngineTest {

    @Test
    public void testHeadingsSuggestedAtLineStart() {
        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);
        String text = "#";
        List<CompletionItem> items = engine.getSuggestions(text, 1);
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasH1 = false;
        for (CompletionItem item : items) {
            if (item.getLabel().contains("Heading 1")) {
                hasH1 = true;
                break;
            }
        }
        assertTrue("Expected Heading 1 snippet at line start", hasH1);
    }

    @Test
    public void testFencedCodeBlockSnippet() {
        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);
        String text = "```";
        List<CompletionItem> items = engine.getSuggestions(text, 3);
        assertNotNull(items);

        boolean hasJsBlock = false;
        for (CompletionItem item : items) {
            if (item.getLabel().contains("```js") || item.getLabel().contains("Code Block")) {
                hasJsBlock = true;
                break;
            }
        }
        assertTrue("Expected code block snippet", hasJsBlock);
    }

    @Test
    public void testInlineFormattingSnippets() {
        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);
        String text = "bold";
        List<CompletionItem> items = engine.getSuggestions(text, 4);
        assertNotNull(items);

        boolean hasBold = false;
        for (CompletionItem item : items) {
            if ("**bold**".equals(item.getLabel())) {
                hasBold = true;
                break;
            }
        }
        assertTrue("Expected bold snippet when typing bold", hasBold);
    }
}
