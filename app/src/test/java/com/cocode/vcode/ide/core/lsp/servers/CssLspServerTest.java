package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.css.CssContextScanner;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.model.CompletionItem;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.LspPosition;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.model.Problem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CssLspServerTest {

    private CssLspServer server;

    @Before
    public void setup() {
        server = new CssLspServer(RuntimeEnvironment.getApplication());
        server.initialize(ProjectIndex.getInstance());
    }

    @Test
    public void testDiagnostics() {
        LspDocument doc = new LspDocument("/test.css", ".class { color: red; color: blue; }", "css", 1);
        List<Problem> problems = server.diagnostics(doc);

        assertNotNull(problems);
        boolean foundDuplicate = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("duplicate")) {
                foundDuplicate = true;
            }
        }
        assertTrue("Should detect duplicate property from linter", foundDuplicate);
    }

    @Test
    public void testIsInsideVarFirstArgContextScanner() {
        String code1 = ":root { --bg: #fff; } .box { color: var(--";
        assertTrue(CssContextScanner.isInsideVarFirstArg(code1, code1.length()));

        String code2 = ":root { --bg: #fff; } .box { color: var(--bg";
        assertTrue(CssContextScanner.isInsideVarFirstArg(code2, code2.length()));

        String code3 = ":root { --bg: #fff; } .box { color: var(";
        assertTrue(CssContextScanner.isInsideVarFirstArg(code3, code3.length()));

        String codeFallback = ":root { --bg: #fff; } .box { color: var(--bg, red";
        assertFalse(CssContextScanner.isInsideVarFirstArg(codeFallback, codeFallback.length()));

        String codeOutside = ":root { --bg: #fff; } .box { color: red; ";
        assertFalse(CssContextScanner.isInsideVarFirstArg(codeOutside, codeOutside.length()));
    }

    @Test
    public void testCssVariableCompletionShowsAllOnPrefix() {
        String css = ":root { --bg-primary: #fff; --bg-secondary: #eee; --card-bg: #ccc; --text-color: #000; }\n"
                + ".box { color: var(--";
        String uri = "file:///workspace/style.css";
        server.didOpen(new TextDocumentItem(uri, "css", 1, css));

        int charIdx = ".box { color: var(--".length();
        CompletionParams params = new CompletionParams(new TextDocumentIdentifier(uri), new LspPosition(1, charIdx));
        List<CompletionItem> items = server.completion(params, null);

        assertNotNull(items);
        assertTrue(items.size() >= 4);
        boolean hasBgPrimary = false;
        boolean hasTextColor = false;
        for (CompletionItem item : items) {
            if ("--bg-primary".equals(item.label)) hasBgPrimary = true;
            if ("--text-color".equals(item.label)) hasTextColor = true;
        }
        assertTrue("Should offer --bg-primary when typing var(--", hasBgPrimary);
        assertTrue("Should offer --text-color when typing var(--", hasTextColor);
    }

    @Test
    public void testCssVariableCompletionFiltersMatchingVariables() {
        String css = ":root { --bg-primary: #fff; --bg-secondary: #eee; --card-bg: #ccc; --text-color: #000; }\n"
                + ".box { color: var(--bg";
        String uri = "file:///workspace/style_filter.css";
        server.didOpen(new TextDocumentItem(uri, "css", 1, css));

        int charIdx = ".box { color: var(--bg".length();
        CompletionParams params = new CompletionParams(new TextDocumentIdentifier(uri), new LspPosition(1, charIdx));
        List<CompletionItem> items = server.completion(params, null);

        assertNotNull(items);
        assertFalse("Should find matching items", items.isEmpty());
        boolean hasTextColor = false;
        boolean hasBgPrimary = false;
        for (CompletionItem item : items) {
            if ("--text-color".equals(item.label)) hasTextColor = true;
            if ("--bg-primary".equals(item.label)) {
                hasBgPrimary = true;
                assertNotNull(item.textEdit);
                assertEquals(charIdx - 4, item.textEdit.range.start.character);
                assertEquals(charIdx, item.textEdit.range.end.character);
                assertEquals("--bg-primary", item.textEdit.newText);
            }
        }
        assertTrue("Should offer --bg-primary", hasBgPrimary);
        assertFalse("--text-color must not appear when typing --bg", hasTextColor);
    }

    @Test
    public void testCssVariableCompletionDismissesOnNonMatchingQuery() {
        String css = ":root { --bg-primary: #fff; --bg-secondary: #eee; --card-bg: #ccc; --text-color: #000; }\n"
                + ".box { color: var(--hshsd";
        String uri = "file:///workspace/style_nonmatch.css";
        server.didOpen(new TextDocumentItem(uri, "css", 1, css));

        int charIdx = ".box { color: var(--hshsd".length();
        CompletionParams params = new CompletionParams(new TextDocumentIdentifier(uri), new LspPosition(1, charIdx));
        List<CompletionItem> items = server.completion(params, null);

        assertNotNull(items);
        assertTrue("Completions list must be empty to dismiss popup when typing non-matching query", items.isEmpty());
    }

    @Test
    public void testLegacyCssVariableCompletionFiltering() {
        String css = ":root { --bg-primary: #fff; --bg-secondary: #eee; --card-bg: #ccc; --text-color: #000; }\n"
                + ".box { color: var(--bg";
        LspDocument doc = new LspDocument("/workspace/legacy.css", css, "css", 1);
        int charIdx = ".box { color: var(--bg".length();
        List<LspCompletionItem> items = server.completion(doc, new com.cocode.vcode.ide.core.lsp.LspPosition(1, charIdx));

        assertNotNull(items);
        assertFalse(items.isEmpty());
        boolean hasBgPrimary = false;
        boolean hasTextColor = false;
        for (LspCompletionItem item : items) {
            if ("--bg-primary".equals(item.label)) {
                hasBgPrimary = true;
                assertEquals(4, item.replaceLength);
            }
            if ("--text-color".equals(item.label)) hasTextColor = true;
        }
        assertTrue("Should offer --bg-primary", hasBgPrimary);
        assertFalse("--text-color must not appear when typing --bg", hasTextColor);

        String nonMatchCss = ":root { --bg-primary: #fff; }\n.box { color: var(--hshsd";
        LspDocument nonMatchDoc = new LspDocument("/workspace/legacy2.css", nonMatchCss, "css", 1);
        int nonMatchCharIdx = ".box { color: var(--hshsd".length();
        List<LspCompletionItem> nonMatchItems = server.completion(nonMatchDoc, new com.cocode.vcode.ide.core.lsp.LspPosition(1, nonMatchCharIdx));
        assertNotNull(nonMatchItems);
        assertTrue("Legacy completions must also be empty for non-matching var queries", nonMatchItems.isEmpty());
    }
}
