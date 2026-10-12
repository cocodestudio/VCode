package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.autocomplete.VFSManager;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MarkdownAutoCompleteEngineTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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

    @Test
    public void testPeriodAtEndOfSentenceDoesNotTriggerSnippets() {
        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);
        String doc = "This is a completed sentence.";
        List<CompletionItem> items = engine.getSuggestions(doc, doc.length());
        assertTrue("Typing a period at the end of a prose sentence must not trigger inline snippets",
                items == null || items.isEmpty());
    }

    @Test
    public void testMarkdownLinkPathCompletionsAndDotCleanup() throws IOException {
        File projectRoot = tempFolder.newFolder("md_project");
        File appDir = new File(projectRoot, "app");
        appDir.mkdir();
        new File(appDir, "index.html").createNewFile();
        File readme = new File(projectRoot, "README.md");
        readme.createNewFile();
        VFSManager.getInstance().clear();

        MarkdownAutoCompleteEngine engine = new MarkdownAutoCompleteEngine(null);
        engine.setCurrentFile(readme);
        engine.setWorkspaceRoot(projectRoot);

        // 1. Inside [Click](app/| suggestions should include index.html
        String linkDoc = "[Click](app/";
        List<CompletionItem> items = engine.getSuggestions(linkDoc, linkDoc.length());
        assertNotNull(items);
        assertFalse(items.isEmpty());
        boolean hasIndex = false;
        for (CompletionItem item : items) {
            if ("index.html".equals(item.getLabel())) {
                hasIndex = true;
                assertEquals(0, item.getReplaceLength());
            }
        }
        assertTrue("Markdown link [Click](app/ should suggest index.html", hasIndex);

        // 2. Inside [Click](app/.| suggestions should replace the dot
        String dotLinkDoc = "[Click](app/.";
        List<CompletionItem> dotItems = engine.getSuggestions(dotLinkDoc, dotLinkDoc.length());
        assertNotNull(dotItems);
        assertFalse(dotItems.isEmpty());
        boolean hasIndexInDot = false;
        for (CompletionItem item : dotItems) {
            if ("index.html".equals(item.getLabel())) {
                hasIndexInDot = true;
                assertEquals("Selection after app/. must replace the dot", 1, item.getReplaceLength());
            }
        }
        assertTrue("Markdown link [Click](app/. should suggest index.html with replaceLength = 1", hasIndexInDot);
    }
}
