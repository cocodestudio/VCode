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

    @Test
    public void testMarkdownTriggerCharacters() {
        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);
        String expected = "[(#*`!|-/.\"<";
        org.junit.Assert.assertEquals("MarkdownAutoCompleteEngine trigger characters mismatch",
                expected, engine.getTriggerCharacters());
        org.junit.Assert.assertEquals("AutoCompleteEngine.FileType.MARKDOWN trigger characters mismatch",
                expected, com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine.getTriggerCharacters(
                        com.cocode.vcode.ide.core.model.FileType.MARKDOWN));
        com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer lspServer =
                new com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer();
        org.junit.Assert.assertEquals("MarkdownLspServer trigger characters mismatch",
                expected, lspServer.getTriggerCharacters());
    }

    @Test
    public void testEmbeddedCodeBlockAliases() {
        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);

        // JSX alias -> resolves to jsEngine
        String jsxDoc = "```jsx\nfunction renderButton() {}\nrender\n```";
        int jsxCursor = "```jsx\nfunction renderButton() {}\nrender".length();
        List<CompletionItem> jsxItems = engine.getSuggestions(jsxDoc, jsxCursor);
        assertNotNull(jsxItems);
        boolean hasButtonInJsx = false;
        for (CompletionItem item : jsxItems) {
            if ("renderButton".equals(item.getLabel())) {
                hasButtonInJsx = true;
                break;
            }
        }
        assertTrue("Embedded ```jsx block should provide JavaScript suggestions", hasButtonInJsx);

        // TSX alias -> resolves to tsEngine
        String tsxDoc = "```tsx\nfunction renderHeader() {}\nrender\n```";
        int tsxCursor = "```tsx\nfunction renderHeader() {}\nrender".length();
        List<CompletionItem> tsxItems = engine.getSuggestions(tsxDoc, tsxCursor);
        assertNotNull(tsxItems);
        boolean hasHeaderInTsx = false;
        for (CompletionItem item : tsxItems) {
            if ("renderHeader".equals(item.getLabel())) {
                hasHeaderInTsx = true;
                break;
            }
        }
        assertTrue("Embedded ```tsx block should provide TypeScript suggestions", hasHeaderInTsx);

        // MJS alias -> resolves to jsEngine
        String mjsDoc = "```mjs\nfunction parseModule() {}\nparse\n```";
        int mjsCursor = "```mjs\nfunction parseModule() {}\nparse".length();
        List<CompletionItem> mjsItems = engine.getSuggestions(mjsDoc, mjsCursor);
        assertNotNull(mjsItems);
        boolean hasModuleInMjs = false;
        for (CompletionItem item : mjsItems) {
            if ("parseModule".equals(item.getLabel())) {
                hasModuleInMjs = true;
                break;
            }
        }
        assertTrue("Embedded ```mjs block should provide JavaScript suggestions", hasModuleInMjs);

        // CJS alias -> resolves to jsEngine
        String cjsDoc = "```cjs\nfunction requireHelper() {}\nrequireH\n```";
        int cjsCursor = "```cjs\nfunction requireHelper() {}\nrequireH".length();
        List<CompletionItem> cjsItems = engine.getSuggestions(cjsDoc, cjsCursor);
        assertNotNull(cjsItems);
        boolean hasHelperInCjs = false;
        for (CompletionItem item : cjsItems) {
            if ("requireHelper".equals(item.getLabel())) {
                hasHelperInCjs = true;
                break;
            }
        }
        assertTrue("Embedded ```cjs block should provide JavaScript suggestions", hasHelperInCjs);
    }
}
