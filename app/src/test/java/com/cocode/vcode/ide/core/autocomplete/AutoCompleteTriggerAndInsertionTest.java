package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AutoCompleteTriggerAndInsertionTest {

    @Test
    public void testTriggerCharactersPerLanguage() {
        // CSS
        String cssTriggers = AutoCompleteEngine.getTriggerCharacters(FileType.CSS);
        assertNotNull(cssTriggers);
        assertTrue(cssTriggers.contains("."));
        assertTrue(cssTriggers.contains("#"));
        assertTrue(cssTriggers.contains(":"));

        // HTML
        String htmlTriggers = AutoCompleteEngine.getTriggerCharacters(FileType.HTML);
        assertNotNull(htmlTriggers);
        assertTrue(htmlTriggers.contains("<"));
        assertTrue(htmlTriggers.contains("."));
        assertTrue(htmlTriggers.contains("#"));

        // JS & TS
        String jsTriggers = AutoCompleteEngine.getTriggerCharacters(FileType.JAVASCRIPT);
        assertNotNull(jsTriggers);
        assertTrue(jsTriggers.contains("."));
        assertTrue(jsTriggers.contains("/"));

        String tsTriggers = AutoCompleteEngine.getTriggerCharacters(FileType.TYPESCRIPT);
        assertNotNull(tsTriggers);
        assertTrue(tsTriggers.contains("."));
        assertTrue(tsTriggers.contains(":"));

        // JSON
        String jsonTriggers = AutoCompleteEngine.getTriggerCharacters(FileType.JSON);
        assertNotNull(jsonTriggers);
        assertTrue(jsonTriggers.contains("\""));
        assertTrue(jsonTriggers.contains(":"));

        // Markdown
        String mdTriggers = AutoCompleteEngine.getTriggerCharacters(FileType.MARKDOWN);
        assertNotNull(mdTriggers);
        assertTrue(mdTriggers.contains("["));
        // Ensure closing delimiters are never triggers
        for (FileType ft : new FileType[]{FileType.CSS, FileType.HTML, FileType.JAVASCRIPT,
                FileType.TYPESCRIPT, FileType.JSON, FileType.MARKDOWN, FileType.SVG}) {
            String triggers = AutoCompleteEngine.getTriggerCharacters(ft);
            assertFalse("Trigger characters for " + ft + " must not contain '>'", triggers.contains(">"));
            assertFalse("Trigger characters for " + ft + " must not contain '}'", triggers.contains("}"));
            assertFalse("Trigger characters for " + ft + " must not contain ')'", triggers.contains(")"));
            assertFalse("Trigger characters for " + ft + " must not contain ']'", triggers.contains("]"));
        }
    }

    @Test
    public void testLanguageServerTriggerCharactersSanitized() {
        com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer htmlServer = new com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer();
        com.cocode.vcode.ide.core.lsp.servers.CssLspServer cssServer = new com.cocode.vcode.ide.core.lsp.servers.CssLspServer();
        com.cocode.vcode.ide.core.lsp.servers.JsLspServer jsServer = new com.cocode.vcode.ide.core.lsp.servers.JsLspServer();
        com.cocode.vcode.ide.core.lsp.servers.TsLspServer tsServer = new com.cocode.vcode.ide.core.lsp.servers.TsLspServer();
        com.cocode.vcode.ide.core.lsp.servers.JsonLspServer jsonServer = new com.cocode.vcode.ide.core.lsp.servers.JsonLspServer();
        com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer mdServer = new com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer();

        com.cocode.vcode.ide.core.lsp.LspServer[] servers = new com.cocode.vcode.ide.core.lsp.LspServer[]{
                htmlServer, cssServer, jsServer, tsServer, jsonServer, mdServer
        };

        for (com.cocode.vcode.ide.core.lsp.LspServer s : servers) {
            String triggers = s.getTriggerCharacters();
            assertNotNull("Server " + s.getClass().getSimpleName() + " triggers must not be null", triggers);
            assertFalse("Server " + s.getClass().getSimpleName() + " triggers must not contain '>'", triggers.contains(">"));
            assertFalse("Server " + s.getClass().getSimpleName() + " triggers must not contain '}'", triggers.contains("}"));
            assertFalse("Server " + s.getClass().getSimpleName() + " triggers must not contain ')'", triggers.contains(")"));
            assertFalse("Server " + s.getClass().getSimpleName() + " triggers must not contain ']'", triggers.contains("]"));
        }
    }

    @Test
    public void testCssAutoCompleteDotAndWordTrigger() {
        CssAutoCompleteEngine engine = new CssAutoCompleteEngine();
        String cssContent = ".card { color: red; }\n#main { margin: 0; }\n";

        // Case 1: Typing '.' at the end of file (selector position)
        String inputDot = cssContent + ".";
        int cursorDot = inputDot.length();
        List<CompletionItem> dotCompletions = engine.getSuggestions(inputDot, cursorDot);
        assertNotNull(dotCompletions);
        assertFalse("Completions after '.' should not be empty", dotCompletions.isEmpty());

        boolean hasCardDot = false;
        for (CompletionItem item : dotCompletions) {
            if (".card".equals(item.getInsertText())) {
                hasCardDot = true;
                assertEquals("Replace length for '.' should be 1", 1, item.getReplaceLength());
                break;
            }
        }
        assertTrue("Completions after '.' should contain '.card'", hasCardDot);

        // Case 2: Typing '.c' at the end of file (selector position with prefix)
        String inputDotC = cssContent + ".c";
        int cursorDotC = inputDotC.length();
        List<CompletionItem> dotCCompletions = engine.getSuggestions(inputDotC, cursorDotC);
        assertNotNull(dotCCompletions);
        assertFalse("Completions after '.c' should not be empty", dotCCompletions.isEmpty());

        boolean hasCardDotC = false;
        for (CompletionItem item : dotCCompletions) {
            if (".card".equals(item.getInsertText())) {
                hasCardDotC = true;
                assertEquals("Replace length for '.c' should be 2", 2, item.getReplaceLength());
                break;
            }
        }
        assertTrue("Completions after '.c' should contain '.card'", hasCardDotC);
    }

    @Test
    public void testCssAutoCompleteHashAndWordTrigger() {
        CssAutoCompleteEngine engine = new CssAutoCompleteEngine();
        String cssContent = ".card { color: red; }\n#main { margin: 0; }\n";

        // Case 1: Typing '#'
        String inputHash = cssContent + "#";
        int cursorHash = inputHash.length();
        List<CompletionItem> hashCompletions = engine.getSuggestions(inputHash, cursorHash);
        assertNotNull(hashCompletions);
        assertFalse("Completions after '#' should not be empty", hashCompletions.isEmpty());

        boolean hasMainHash = false;
        for (CompletionItem item : hashCompletions) {
            if ("#main".equals(item.getInsertText())) {
                hasMainHash = true;
                assertEquals("Replace length for '#' should be 1", 1, item.getReplaceLength());
                break;
            }
        }
        assertTrue("Completions after '#' should contain '#main'", hasMainHash);

        // Case 2: Typing '#m'
        String inputHashM = cssContent + "#m";
        int cursorHashM = inputHashM.length();
        List<CompletionItem> hashMCompletions = engine.getSuggestions(inputHashM, cursorHashM);
        assertNotNull(hashMCompletions);
        assertFalse("Completions after '#m' should not be empty", hashMCompletions.isEmpty());

        boolean hasMainHashM = false;
        for (CompletionItem item : hashMCompletions) {
            if ("#main".equals(item.getInsertText())) {
                hasMainHashM = true;
                assertEquals("Replace length for '#m' should be 2", 2, item.getReplaceLength());
                break;
            }
        }
        assertTrue("Completions after '#m' should contain '#main'", hasMainHashM);
    }

    @Test
    public void testSingleDotAndHashNormalization() {
        CssAutoCompleteEngine engine = new CssAutoCompleteEngine();
        String textWithRepeatedDots = "...nav-item { padding: 8px; }\n";
        List<CompletionItem> items = engine.getSuggestions(textWithRepeatedDots + ".", textWithRepeatedDots.length() + 1);

        for (CompletionItem item : items) {
            if (item.getInsertText().startsWith(".")) {
                assertFalse("Completion item should never start with double dots", item.getInsertText().startsWith(".."));
            }
            if (item.getInsertText().startsWith("#")) {
                assertFalse("Completion item should never start with double hashes", item.getInsertText().startsWith("##"));
            }
        }
    }

    @Test
    public void testJsAutoCompleteSequentialKeystrokesReplaceLength() {
        com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine engine =
                new com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine(null);

        // Step 1: user types 'c'
        List<CompletionItem> itemsC = engine.getSuggestions("c", 1);
        CompletionItem logC = null;
        for (CompletionItem item : itemsC) {
            if ("console.log".equals(item.getLabel())) {
                logC = item;
                break;
            }
        }
        assertNotNull("Should contain console.log when typing 'c'", logC);
        assertEquals("Replace length for 'c' must be 1", 1, logC.getReplaceLength());

        // Step 2: user types 'co'
        List<CompletionItem> itemsCo = engine.getSuggestions("co", 2);
        CompletionItem logCo = null;
        for (CompletionItem item : itemsCo) {
            if ("console.log".equals(item.getLabel())) {
                logCo = item;
                break;
            }
        }
        assertNotNull("Should contain console.log when typing 'co'", logCo);
        assertEquals("Replace length for 'co' must be 2", 2, logCo.getReplaceLength());

        // Step 3: user types 'con'
        List<CompletionItem> itemsCon = engine.getSuggestions("con", 3);
        CompletionItem logCon = null;
        for (CompletionItem item : itemsCon) {
            if ("console.log".equals(item.getLabel())) {
                logCon = item;
                break;
            }
        }
        assertNotNull("Should contain console.log when typing 'con'", logCon);
        assertEquals("Replace length for 'con' must be 3", 3, logCon.getReplaceLength());
    }

    @Test
    public void testExplicitReplaceLengthContract() {
        CompletionItem defaultItem = new CompletionItem("test", "test", "detail", CompletionItem.Type.KEYWORD);
        assertEquals("Default replace length should be -1", -1, defaultItem.getReplaceLength());

        CompletionItem explicitZero = new CompletionItem("zero", "zero", "detail", CompletionItem.Type.KEYWORD);
        explicitZero.setReplaceLength(0);
        assertEquals(0, explicitZero.getReplaceLength());

        CompletionItem explicitPos = new CompletionItem("pos", "pos", "detail", CompletionItem.Type.KEYWORD);
        explicitPos.setReplaceLength(5);
        assertEquals(5, explicitPos.getReplaceLength());
    }
}
