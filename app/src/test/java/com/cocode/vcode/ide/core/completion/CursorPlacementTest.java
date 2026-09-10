package com.cocode.vcode.ide.core.completion;

import com.cocode.vcode.ide.core.autocomplete.EmmetParser;
import com.cocode.vcode.ide.core.completion.staticdata.CssStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader;
import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.servers.CssLspServer;
import com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsonLspServer;
import com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test suite verifying cursor placement contracts across
 * LSP servers, TypeScript completions, Emmet expansions, CSS function labels,
 * and completion JSON assets.
 */
public class CursorPlacementTest {

    @After
    public void tearDown() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    @Test
    public void tsKeywordsHaveZeroCursorOffsetAndHighScore() {
        TsAutoCompleteEngine engine = new TsAutoCompleteEngine(null);
        List<CompletionItem> items = engine.getSuggestions("type", 4);
        assertNotNull(items);
        assertFalse("Suggestions should not be empty", items.isEmpty());

        CompletionItem typeItem = null;
        for (CompletionItem item : items) {
            if ("type".equals(item.getLabel()) && item.getType() == CompletionItem.Type.KEYWORD) {
                typeItem = item;
                break;
            }
        }

        assertNotNull("TypeScript 'type' keyword suggestion must exist", typeItem);
        assertEquals("TypeScript keyword cursorOffset must be 0 (not 100)", 0, typeItem.getCursorOffset());
        assertEquals("TypeScript keyword sortScore must be 100", 100, typeItem.getSortScore());
    }

    @Test
    public void htmlLspServerReconstructsPipeForPositiveCursorOffset() {
        CompletionItem item = new CompletionItem("div", "<div></div>", "tag", CompletionItem.Type.TAG, 5);
        List<LspCompletionItem> lspItems = HtmlLspServer.convertCompletions(Collections.singletonList(item));
        assertNotNull(lspItems);
        assertEquals(1, lspItems.size());
        assertEquals("<div>|</div>", lspItems.get(0).insertText);
    }

    @Test
    public void jsLspServerReconstructsPipeForPositiveCursorOffset() {
        CompletionItem item = new CompletionItem("if", "if () {\n  \n}", "keyword", CompletionItem.Type.KEYWORD, 4);
        List<LspCompletionItem> lspItems = JsLspServer.convertCompletions(Collections.singletonList(item));
        assertNotNull(lspItems);
        assertEquals(1, lspItems.size());
        assertEquals("if (|) {\n  \n}", lspItems.get(0).insertText);
    }

    @Test
    public void jsonLspServerReconstructsPipeForPositiveCursorOffset() {
        CompletionItem item = new CompletionItem("{}", "{\n  \n}", "snippet", CompletionItem.Type.SNIPPET, 4);
        List<LspCompletionItem> lspItems = JsonLspServer.convertCompletions(Collections.singletonList(item));
        assertNotNull(lspItems);
        assertEquals(1, lspItems.size());
        assertEquals("{\n  |\n}", lspItems.get(0).insertText);
    }

    @Test
    public void cssLspServerReconstructsPipeForNegativeCursorOffset() {
        CompletionItem item = new CompletionItem("color", "color: ;", "prop", CompletionItem.Type.CSS_PROPERTY, -1);
        List<LspCompletionItem> lspItems = CssLspServer.convertCompletions(Collections.singletonList(item));
        assertNotNull(lspItems);
        assertEquals(1, lspItems.size());
        assertEquals("color: |;", lspItems.get(0).insertText);
    }

    @Test
    public void markdownLspServerPreservesExistingPipe() {
        CompletionItem item = new CompletionItem("bold", "**|**", "snippet", CompletionItem.Type.SNIPPET, 0);
        List<LspCompletionItem> lspItems = MarkdownLspServer.convertCompletions(Collections.singletonList(item));
        assertNotNull(lspItems);
        assertEquals(1, lspItems.size());
        assertEquals("**|**", lspItems.get(0).insertText);
    }

    @Test
    public void emmetHtmlCursorPlacement() {
        String divExpanded = EmmetParser.expandHtml("div.card", null);
        assertNotNull(divExpanded);
        assertEquals("<div class=\"card\">|</div>", divExpanded);

        String imgExpanded = EmmetParser.expandHtml("img[src=\"\" alt=\"\"]", null);
        assertNotNull(imgExpanded);
        assertEquals("<img src=\"|\" alt=\"\">", imgExpanded);

        String listExpanded = EmmetParser.expandHtml("ul>li*2", null);
        assertNotNull(listExpanded);
        assertTrue("Emmet list expansion must contain cursor marker", listExpanded.contains("<li>|</li>"));
    }

    @Test
    public void cssAutoCompleteEngineStripsPipeFromFunctionLabel() {
        com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine engine =
                new com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine(null);
        String css = ".box { width: calc";
        List<CompletionItem> items = engine.getSuggestions(css, css.length());
        assertNotNull(items);
        boolean foundCalc = false;
        for (CompletionItem item : items) {
            if (item.getLabel().startsWith("calc")) {
                foundCalc = true;
                assertFalse("CSS completion item label must not contain pipe: " + item.getLabel(),
                        item.getLabel().contains("|"));
                assertTrue("CSS completion item insertText must contain pipe: " + item.getInsertText(),
                        item.getInsertText().contains("|"));
            }
        }
        assertTrue("calc function must be found in suggestions", foundCalc);
    }

    @Test
    public void markdownSnippetsAssetHasPipesInFormattingWrappers() throws Exception {
        String json = loadAsset("completions/markdown_snippets.json");
        JSONArray arr = new JSONArray(json);
        boolean foundBold = false;
        boolean foundCodeBlock = false;
        boolean foundLink = false;
        boolean foundTable = false;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String label = obj.getString("label");
            String insertText = obj.getString("insertText");

            if ("**bold**".equals(label)) {
                foundBold = true;
                assertEquals("**|**", insertText);
            } else if ("``` Code Block".equals(label)) {
                foundCodeBlock = true;
                assertEquals("```\n|\n```", insertText);
            } else if ("[link](url)".equals(label)) {
                foundLink = true;
                assertEquals("[|]()", insertText);
            } else if ("| Table".equals(label)) {
                foundTable = true;
                assertTrue("Table snippet must retain column pipes", insertText.contains("| Column 1 | Column 2 |"));
            }
        }

        assertTrue("Bold snippet must be found", foundBold);
        assertTrue("Code block snippet must be found", foundCodeBlock);
        assertTrue("Link snippet must be found", foundLink);
        assertTrue("Table snippet must be found", foundTable);
    }

    @Test
    public void htmlSvgSnippetHasSinglePipe() throws Exception {
        String json = loadAsset("completions/html_tags.json");
        JSONObject root = new JSONObject(json);
        JSONArray tags = root.getJSONArray("tags");

        JSONObject svgTag = null;
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.getJSONObject(i);
            if ("svg".equals(tag.getString("tag"))) {
                svgTag = tag;
                break;
            }
        }

        assertNotNull("SVG tag definition must exist", svgTag);
        String snippet = svgTag.getString("snippet");
        int firstPipe = snippet.indexOf('|');
        assertTrue("SVG snippet must contain a cursor marker", firstPipe >= 0);
        int secondPipe = snippet.indexOf('|', firstPipe + 1);
        assertEquals("SVG snippet must not contain a second pipe marker", -1, secondPipe);
    }

    private String loadAsset(String assetPath) throws Exception {
        java.io.File file = new java.io.File("src/main/assets/" + assetPath);
        if (!file.exists()) {
            file = new java.io.File("app/src/main/assets/" + assetPath);
        }
        try (InputStream is = new java.io.FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = is.read(bytes);
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }
}
