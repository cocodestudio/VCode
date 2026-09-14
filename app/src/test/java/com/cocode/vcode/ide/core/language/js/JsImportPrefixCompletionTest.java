package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for K.2: typing {@code import { fetch}} should
 * suggest {@code fetchData} if exported anywhere in the project, with
 * no stale results after a recent edit to the source file.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class JsImportPrefixCompletionTest extends BaseJsAstTest {

    private static final String FILE_A = "/virtual/A.js";
    private static final String FILE_B = "/virtual/B.js";

    @After
    public void cleanup() {
        ProjectIndex.getInstance().clear();
    }

    @Test
    public void importPrefixCompletionSuggestsCrossFileExports() {
        // File B exports `fetchData`.
        publishParseResult(FILE_B, "export function fetchData() { return 1; }");

        // File A has an open import block.
        JsAutoCompleteEngine engine = newEngine(FILE_A);
        String text = "import { fetch";
        int cursor = text.length();
        List<CompletionItem> items = engine.getSuggestions(text, cursor);

        Set<String> labels = labels(items);
        assertTrue("fetchData must be suggested for 'import { fetch' across the project",
                labels.contains("fetchData"));
    }

    @Test
    public void importPrefixCompletionCarriesSourceUri() {
        publishParseResult(FILE_B, "export function fetchData() {}");
        JsAutoCompleteEngine engine = newEngine(FILE_A);
        List<CompletionItem> items = engine.getSuggestions("import { fetch", "import { fetch".length());
        CompletionItem fetchData = findByLabel(items, "fetchData");
        assertNotNull("fetchData suggestion must be present", fetchData);
        // The URI is canonicalized by ProjectIndex.updateParseResult
        // to File.getAbsolutePath() form.
        String expectedB = new File(FILE_B).getAbsolutePath();
        assertEquals("source URI must point at file B (canonicalized)",
                expectedB, fetchData.getSourceUri());
    }

    @Test
    public void staleResultsAreClearedAfterEdit() {
        // First publish: file B exports `fetchData`.
        publishParseResult(FILE_B, "export function fetchData() {}");
        JsAutoCompleteEngine engine = newEngine(FILE_A);
        List<CompletionItem> first = engine.getSuggestions("import { fetch", "import { fetch".length());
        assertTrue("fetchData should be present initially",
                labels(first).contains("fetchData"));

        // Edit file B: rename `fetchData` to `fetchUser`.
        publishParseResult(FILE_B, "export function fetchUser() {}");
        // The second engine instance ensures caches are fresh.
        JsAutoCompleteEngine engine2 = newEngine(FILE_A);
        List<CompletionItem> second = engine2.getSuggestions("import { fetch", "import { fetch".length());
        Set<String> labelsAfter = labels(second);
        assertTrue("fetchUser should be present after the edit", labelsAfter.contains("fetchUser"));
        assertFalse("fetchData should NOT be present after the rename",
                labelsAfter.contains("fetchData"));
    }

    @Test
    public void localFileExportsAreNotSuggestedCrossFile() {
        // File A exports `fetchData` itself. Cross-file completion
        // from within A should NOT suggest A's own exports (those
        // are reachable directly without an import).
        publishParseResult(FILE_A, "export function fetchData() {}");
        JsAutoCompleteEngine engine = newEngine(FILE_A);
        List<CompletionItem> items = engine.getSuggestions("import { fetch", "import { fetch".length());
        assertFalse("local exports must not be suggested as cross-file completions",
                labels(items).contains("fetchData"));
    }

    @Test
    public void emptyPrefixStillReturnsAllExports() {
        publishParseResult(FILE_B, "export function alpha() {}\nexport function beta() {}");
        JsAutoCompleteEngine engine = newEngine(FILE_A);
        List<CompletionItem> items = engine.getSuggestions("import { ", "import { ".length());
        Set<String> labels = labels(items);
        assertTrue("empty prefix should still return all exports", labels.contains("alpha"));
        assertTrue("empty prefix should still return all exports", labels.contains("beta"));
    }

    @Test
    public void alreadyImportedNameIsFiltered() {
        publishParseResult(FILE_B, "export function fetchData() {}");
        JsAutoCompleteEngine engine = newEngine(FILE_A);
        // The user has already typed `fetchData, ` in the import block.
        String text = "import { fetchData, ";
        int cursor = text.length();
        List<CompletionItem> items = engine.getSuggestions(text, cursor);
        assertFalse("fetchData should be filtered out (already imported)",
                labels(items).contains("fetchData"));
    }

    // --- helpers --------------------------------------------------------

    private static JsAutoCompleteEngine newEngine(String currentFileUri) {
        Context ctx = RuntimeEnvironment.getApplication();
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(ctx);
        engine.setCurrentFile(new File(currentFileUri));
        return engine;
    }

    private void publishParseResult(String uri, String source) {
        ProjectIndex.getInstance().updateParseResult(
                uri, parseFile(uri, source));
    }

    private static Set<String> labels(List<CompletionItem> items) {
        Set<String> s = new HashSet<>();
        if (items == null) return s;
        for (CompletionItem c : items) s.add(c.getLabel());
        return s;
    }

    private static CompletionItem findByLabel(List<CompletionItem> items, String label) {
        if (items == null) return null;
        for (CompletionItem c : items) {
            if (label.equals(c.getLabel())) return c;
        }
        return null;
    }
}
