package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
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
 * Acceptance tests for M.12 (JS keyword baseline), M.13
 * (structural-keyword AST gating), M.15 (keyword exclusivity in
 * member-access).
 */
public class JsKeywordCompletionTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedLoader() {
        String json = "[" +
                "{\"label\": \"var\", \"insertText\": \"var \", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"if\", \"insertText\": \"if (|) {\\n  \\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"else\", \"insertText\": \"else {\\n  |\\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"else if\", \"insertText\": \"else if (|) {\\n  \\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"for\", \"insertText\": \"for (let i = 0; i < |; i++) {\\n  \\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"try\", \"insertText\": \"try {\\n  |\\n} catch (error) {\\n  console.error(error);\\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"catch\", \"insertText\": \"catch (error) {\\n  |\\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"finally\", \"insertText\": \"finally {\\n  |\\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"class\", \"insertText\": \"class | {\\n  constructor() {\\n    \\n  }\\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"extends\", \"insertText\": \"extends \", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"switch\", \"insertText\": \"switch (|) {\\n  case :\\n    break;\\n  default:\\n    break;\\n}\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"case\", \"insertText\": \"case |:\\n  break;\", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"default\", \"insertText\": \"default \", \"detail\": \"Keyword\", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"console.log\", \"insertText\": \"console.log(|);\", \"detail\": \"Built-in\", \"type\": \"BUILTIN\"}," +
                "{\"label\": \"Object.keys\", \"insertText\": \"Object.keys(|)\", \"detail\": \"Built-in\", \"type\": \"BUILTIN\"}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, json);
    }

    // --- M.12 baseline ------------------------------------------------

    @Test
    public void statementStartDetectsAtSourceStart() {
        String src = "|";
        int cursor = 0;
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.STATEMENT_START, pos);
    }

    @Test
    public void statementStartDetectsAfterSemicolon() {
        String src = "var x = 1;|";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.STATEMENT_START, pos);
    }

    @Test
    public void statementStartDetectsAfterOpenBrace() {
        String src = "function f() { |";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.STATEMENT_START, pos);
    }

    @Test
    public void identifierDetectsAfterVarKeyword() {
        String src = "var fo|";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.IDENTIFIER, pos);
    }

    @Test
    public void memberAccessDetectsAfterDot() {
        String src = "obj.|";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.MEMBER_ACCESS, pos);
    }

    @Test
    public void statementStartCompletionIncludesKeywords() {
        seedLoader();
        List<CompletionItem> items = JsStaticCompletionDispatcher.buildCompletions();
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        assertTrue("var must be present", labels.contains("var"));
        assertTrue("if must be present", labels.contains("if"));
        assertTrue("for must be present", labels.contains("for"));
        assertTrue("try must be present", labels.contains("try"));
        assertTrue("class must be present", labels.contains("class"));
    }

    @Test
    public void statementStartCompletionIncludesBuiltins() {
        // M.12 acceptance: BUILTIN entries (e.g. console.log,
        // Object.keys) appear at the start of a new statement.
        seedLoader();
        List<CompletionItem> items = JsStaticCompletionDispatcher.buildCompletions();
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : items) labels.add(c.getLabel());
        assertTrue("console.log must be present", labels.contains("console.log"));
        assertTrue("Object.keys must be present", labels.contains("Object.keys"));
    }

    @Test
    public void ifSnippetCursorLandsInsideParentheses() {
        seedLoader();
        List<CompletionItem> items = JsStaticCompletionDispatcher.buildCompletions();
        CompletionItem ifItem = findByLabel(items, "if");
        assertNotNull(ifItem);
        assertEquals("if () {\n  \n}", ifItem.getEffectiveInsertText());
        assertEquals(4, ifItem.getCursorOffset());
    }

    // --- M.13 structural-keyword gating --------------------------------

    @Test
    public void elseNotSuggestedAtUnrelatedStatement() {
        // M.13 acceptance: `else` is not suggested at the start of
        // an unrelated statement.
        seedLoader();
        String src = "var x = 1;|";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertFalse("else must be excluded at unrelated statement-start",
                labels.contains("else"));
        assertFalse("else if must also be excluded", labels.contains("else if"));
    }

    @Test
    public void elseIsSuggestedAfterIfStatement() {
        seedLoader();
        String src = "if (x) {}|";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertTrue("else must be allowed after `if`", labels.contains("else"));
    }

    @Test
    public void caseNotSuggestedOutsideSwitch() {
        // M.13 acceptance: `case` is not suggested outside a switch
        // body.
        seedLoader();
        String src = "var x = 1;|";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertFalse("case must be excluded outside switch", labels.contains("case"));
        assertFalse("default must be excluded outside switch", labels.contains("default"));
    }

    @Test
    public void caseIsSuggestedInsideSwitch() {
        seedLoader();
        String src = "switch (x) { |";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertTrue("case must be allowed inside switch", labels.contains("case"));
        assertTrue("default must be allowed inside switch", labels.contains("default"));
    }

    @Test
    public void catchSuggestedAfterTryBlock() {
        seedLoader();
        String src = "try {}|";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertTrue("catch must be allowed after try-block", labels.contains("catch"));
        assertTrue("finally must be allowed after try-block", labels.contains("finally"));
    }

    @Test
    public void catchNotSuggestedUnrelated() {
        seedLoader();
        String src = "var x = 1;|";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertFalse("catch must be excluded at unrelated statement-start",
                labels.contains("catch"));
    }

    @Test
    public void extendsSuggestedAfterClassName() {
        // M.13: `extends` only immediately after a class name in
        // an in-progress `class Foo |` parse.
        seedLoader();
        String src = "class Foo |";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertTrue("extends must be allowed after class name", labels.contains("extends"));
    }

    @Test
    public void extendsNotSuggestedUnrelated() {
        seedLoader();
        String src = "var x = 1;|";
        int cursor = src.indexOf('|');
        List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> filtered = JsStaticCompletionDispatcher.filterStructural(
                all, src, cursor);
        Set<String> labels = new HashSet<>();
        for (CompletionItem c : filtered) labels.add(c.getLabel());
        assertFalse("extends must be excluded at unrelated statement-start",
                labels.contains("extends"));
    }

    // --- M.15 member-access exclusion -----------------------------------

    @Test
    public void memberAccessPositionIsRecognised() {
        // M.15: in dot-completion context, the static keyword list
        // is suppressed entirely.
        String src = "obj.|";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.MEMBER_ACCESS, pos);
    }

    // --- helpers -------------------------------------------------------

    private static CompletionItem findByLabel(List<CompletionItem> items, String label) {
        for (CompletionItem c : items) {
            if (label.equals(c.getLabel())) return c;
        }
        return null;
    }
}
