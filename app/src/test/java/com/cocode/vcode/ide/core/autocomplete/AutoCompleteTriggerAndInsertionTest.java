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
        assertTrue(mdTriggers.contains("#"));
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
}
