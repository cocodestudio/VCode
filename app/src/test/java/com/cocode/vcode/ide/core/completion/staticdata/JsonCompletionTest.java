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
 * Acceptance tests for M.16 (JSON key-position), M.17
 * (value-position), and M.18 (full-file template gating +
 * filename boosting).
 */
public class JsonCompletionTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedLoader() {
        String json = "[" +
                "{\"label\": \"{}\", \"snippet\": \"{\\n  |\\n}\", \"detail\": \"Empty Object\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"[]\", \"snippet\": \"[\\n  |\\n]\", \"detail\": \"Empty Array\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"key: value\", \"snippet\": \"\\\"|\\\": \\\"\\\"\", \"detail\": \"Key-Value Pair\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"key: object\", \"snippet\": \"\\\"|\\\": {\\n  \\n}\", \"detail\": \"Key-Object Pair\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"key: array\", \"snippet\": \"\\\"|\\\": [\\n  \\n]\", \"detail\": \"Key-Array Pair\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"key: number\", \"snippet\": \"\\\"|\\\": 0\", \"detail\": \"Key-Number Pair\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"key: boolean\", \"snippet\": \"\\\"|\\\": true\", \"detail\": \"Key-Boolean Pair\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"package.json\", \"snippet\": \"{\\n  \\\"name\\\": \\\"|\\\"\\n}\", \"detail\": \"Node.js Manifest\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"tsconfig.json\", \"snippet\": \"{\\n  \\\"compilerOptions\\\": {}\\n}\", \"detail\": \"TypeScript Config\", \"type\": \"SNIPPET\"}," +
                "{\"label\": \"eslintrc.json\", \"snippet\": \"{\\n  \\\"env\\\": {}\\n}\", \"detail\": \"ESLint Config\", \"type\": \"SNIPPET\"}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JSON_SNIPPETS_ASSET, json);
    }

    // --- M.16 ----------------------------------------------------------

    @Test
    public void cursorAfterOpenBraceIsKeyPosition() {
        String src = "{ |";
        int cursor = src.indexOf('|');
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(JsonStaticCompletionDispatcher.Position.KEY, pos);
    }

    @Test
    public void cursorAfterCommaInObjectIsKeyPosition() {
        String src = "{\"a\": 1, |";
        int cursor = src.indexOf('|');
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(JsonStaticCompletionDispatcher.Position.KEY, pos);
    }

    @Test
    public void keyPositionOffersKeySnippets() {
        seedLoader();
        String src = "{ |";
        int cursor = src.indexOf('|');
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.KEY, null);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        assertTrue("key: value must be offered", labels.contains("key: value"));
        assertTrue("key: object must be offered", labels.contains("key: object"));
        assertTrue("key: array must be offered", labels.contains("key: array"));
        assertTrue("key: number must be offered", labels.contains("key: number"));
        assertTrue("key: boolean must be offered", labels.contains("key: boolean"));
    }

    @Test
    public void keyPositionDoesNotOfferValueOrTemplateSnippets() {
        seedLoader();
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.KEY, null);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // M.16: structural value snippets and full-file templates
        // must not appear in key position.
        assertFalse("{} must not be offered in key position", labels.contains("{}"));
        assertFalse("[] must not be offered in key position", labels.contains("[]"));
        assertFalse("package.json must not be offered in key position",
                labels.contains("package.json"));
    }

    // --- M.17 ----------------------------------------------------------

    @Test
    public void cursorAfterColonIsValuePosition() {
        // M.17 acceptance: `"foo": |` suggests {}/[]; right after
        // `{` it does not (key snippets apply there instead).
        String src = "\"foo\": |";
        int cursor = src.indexOf('|');
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(JsonStaticCompletionDispatcher.Position.VALUE, pos);
    }

    @Test
    public void cursorAfterCommaInArrayIsValuePosition() {
        String src = "[1, |";
        int cursor = src.indexOf('|');
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(JsonStaticCompletionDispatcher.Position.VALUE, pos);
    }

    @Test
    public void valuePositionOffersStructuralSnippets() {
        seedLoader();
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.VALUE, null);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        // M.17: {} and [] are offered in value position.
        assertTrue("{} must be offered in value position", labels.contains("{}"));
        assertTrue("[] must be offered in value position", labels.contains("[]"));
        // Key snippets must NOT be offered in value position.
        assertFalse("key: value must not be offered in value position",
                labels.contains("key: value"));
    }

    // --- M.18 ----------------------------------------------------------

    @Test
    public void emptyFileIsFileEmptyPosition() {
        String src = "";
        int cursor = 0;
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(JsonStaticCompletionDispatcher.Position.FILE_EMPTY, pos);
    }

    @Test
    public void whitespaceOnlyFileIsFileEmptyPosition() {
        String src = "  \n  \t\n  ";
        int cursor = src.length();
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertEquals(JsonStaticCompletionDispatcher.Position.FILE_EMPTY, pos);
    }

    @Test
    public void nonEmptyFileIsNotFileEmptyPosition() {
        String src = "{\"a\":1}";
        int cursor = src.length();
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(src, cursor);
        assertNotEquals(JsonStaticCompletionDispatcher.Position.FILE_EMPTY, pos);
    }

    @Test
    public void fileEmptyPositionOffersFullFileTemplates() {
        // M.18 acceptance: opening a blank file named `package.json`
        // shows the `package.json` template suggestion first; a
        // non-empty file shows none of the full-file templates.
        seedLoader();
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.FILE_EMPTY, "package.json");
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        assertTrue("package.json must be offered", labels.contains("package.json"));
        assertTrue("tsconfig.json must be offered", labels.contains("tsconfig.json"));
        assertTrue("eslintrc.json must be offered", labels.contains("eslintrc.json"));
        // Key snippets are NOT full-file templates.
        assertFalse("key: value must not be offered in file-empty position",
                labels.contains("key: value"));
        // {} and [] are not full-file templates.
        assertFalse("{} must not be offered in file-empty position", labels.contains("{}"));
    }

    @Test
    public void matchingFileNameIsBoostedToTop() {
        // M.18: a file literally named `package.json` boosts the
        // `package.json` entry to the top via a high sort score.
        seedLoader();
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.FILE_EMPTY, "package.json");
        CompletionItem first = items.get(0);
        assertEquals("package.json", first.getLabel());
        assertEquals("boosted entry has a high sort score", 1000, first.getSortScore());
    }

    @Test
    public void nonMatchingFileNameDoesNotBoost() {
        seedLoader();
        // File is named "tsconfig.json" — the tsconfig entry is
        // boosted, not package.json.
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.FILE_EMPTY, "tsconfig.json");
        CompletionItem first = items.get(0);
        assertEquals("tsconfig.json", first.getLabel());
        assertEquals(1000, first.getSortScore());
    }

    @Test
    public void keyPositionDoesNotOfferFullFileTemplates() {
        // M.18: full-file templates are gated to FILE_EMPTY only.
        seedLoader();
        List<CompletionItem> items = JsonStaticCompletionDispatcher.buildCompletions(
                JsonStaticCompletionDispatcher.Position.KEY, "package.json");
        for (CompletionItem c : items) {
            assertFalse("full-file template must not appear in key position: " + c.getLabel(),
                    c.getLabel().contains("."));
        }
    }

    // --- helpers -------------------------------------------------------

    private static void assertNotEquals(Object a, Object b) {
        if (a == null ? b == null : a.equals(b)) {
            throw new AssertionError("expected " + a + " to not equal " + b);
        }
    }
}
