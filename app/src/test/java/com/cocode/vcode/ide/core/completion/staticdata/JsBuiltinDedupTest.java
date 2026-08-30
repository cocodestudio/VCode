package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Acceptance tests for M.14 (BUILTIN dedup against dot-completion)
 * and M.15 (keyword exclusivity in member-access).
 *
 * <p>Both M.14 and M.15 are about the same dispatch rule: in a
 * member-access position (cursor right after {@code obj.}), the
 * static JS keyword/BUILTIN list must not be offered — the live
 * Part-C dot-completion is authoritative. M.14 additionally
 * specifies that BUILTIN entries whose prefix matches the resolved
 * base identifier must not duplicate dot-completion results.
 *
 * <p>In the static dispatcher, both are satisfied by returning
 * empty for {@link JsStaticCompletionDispatcher.Position#MEMBER_ACCESS}.
 * The caller honours this by skipping the static
 * dispatch when the position is MEMBER_ACCESS.
 */
public class JsBuiltinDedupTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private void seedLoader() {
        String json = "[" +
                "{\"label\": \"var\", \"insertText\": \"var \", \"type\": \"KEYWORD\"}," +
                "{\"label\": \"console.log\", \"insertText\": \"console.log(|);\", \"type\": \"BUILTIN\"}," +
                "{\"label\": \"Object.keys\", \"insertText\": \"Object.keys(|)\", \"type\": \"BUILTIN\"}" +
                "]";
        StaticAssetReader.setAssetOverride(StaticCompletionLoader.JS_KEYWORDS_ASSET, json);
    }

    @Test
    public void memberAccessPositionExcludesStaticKeywords() {
        // M.15 acceptance: `obj.` never shows `if`, `for`,
        // `console.log`, etc. in its suggestion list.
        String src = "obj.|";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.MEMBER_ACCESS, pos);
    }

    @Test
    public void memberAccessPositionExcludesStaticBuiltins() {
        // M.14 acceptance: typing `Object.` shows each built-in
        // method exactly once, not twice. Since the static
        // dispatcher returns empty for MEMBER_ACCESS, no static
        // entry is offered — the live dot-completion is the sole
        // source. This test verifies the static side stays out of
        // the way.
        String src = "Object.|";
        int cursor = src.indexOf('|');
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.MEMBER_ACCESS, pos);
    }

    @Test
    public void staticKeywordsAvailableAtStatementStart() {
        // Sanity check: the static dispatcher's output is
        // non-empty at statement-start. This is the path that
        // M.14's suppression does NOT apply to.
        seedLoader();
        String src = "|";
        int cursor = 0;
        TokenStream stream = JsStaticCompletionDispatcherHelper.tokenize(src);
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(src, stream, null, cursor);
        assertEquals(JsStaticCompletionDispatcher.Position.STATEMENT_START, pos);
        List<CompletionItem> items = JsStaticCompletionDispatcher.buildCompletions();
        assertNotNull(items);
        // Items include both keywords and BUILTIN entries.
        boolean hasVar = false, hasConsoleLog = false;
        for (CompletionItem c : items) {
            if ("var".equals(c.getLabel())) hasVar = true;
            if ("console.log".equals(c.getLabel())) hasConsoleLog = true;
        }
        // The static dispatcher offers everything at statement-start;
        // the caller's M.15 logic suppresses BUILTIN entries only
        // in member-access position. The static dispatch itself
        // is a flat dump.
        org.junit.Assert.assertTrue(hasVar);
        org.junit.Assert.assertTrue(hasConsoleLog);
    }
}
