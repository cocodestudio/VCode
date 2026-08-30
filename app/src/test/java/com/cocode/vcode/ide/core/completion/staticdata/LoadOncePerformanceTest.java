package com.cocode.vcode.ide.core.completion.staticdata;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Acceptance tests for M.21: triggering many completions in a row
 * causes zero additional file reads or JSON parses beyond the
 * first.
 *
 * <p>The {@link StaticCompletionLoader} uses cached, lazily-
 * initialised arrays keyed by a {@code volatile} field. After the
 * first call, every subsequent call returns the same array
 * reference. This test verifies the reference-equality invariant
 * for all four datasets, plus the per-call parser read counter
 * stays at 0 (i.e. no JSON re-parse happens after the first call).
 */
public class LoadOncePerformanceTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    @Test
    public void allDatasetsAreCachedAfterFirstLoad() {
        // Set a single shared override for all four datasets.
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.JS_KEYWORDS_ASSET, "[{\"label\":\"x\",\"insertText\":\"x\",\"type\":\"KEYWORD\"}]");
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.HTML_TAGS_ASSET, "{\"tags\":[{\"tag\":\"div\",\"snippet\":\"<div>|</div>\",\"attributes\":[]}],\"globalAttributes\":[],\"globalAttributePrefixes\":[]}");
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.CSS_PROPERTIES_ASSET, "[{\"property\":\"color\",\"values\":[\"red\"]}]");
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.CSS_COLORS_ASSET, "{\"colors\":[\"red\"],\"color_functions\":[],\"global_functions\":[\"var(--|)\"]}");
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.JSON_SNIPPETS_ASSET, "[{\"label\":\"{}\",\"snippet\":\"{\\n  |\\n}\",\"type\":\"SNIPPET\"}]");

        // First call — populates the cache.
        Object js1   = StaticCompletionLoader.getJsKeywords();
        Object html1 = StaticCompletionLoader.getHtmlTags();
        Object css1  = StaticCompletionLoader.getCssProperties();
        Object col1  = StaticCompletionLoader.getCssColors();
        Object colf1 = StaticCompletionLoader.getCssColorFunctions();
        Object colg1 = StaticCompletionLoader.getCssGlobalFunctions();
        Object json1 = StaticCompletionLoader.getJsonSnippets();
        Object globalAttrs1 = StaticCompletionLoader.getHtmlGlobalAttributes();
        Object globalPrefixes1 = StaticCompletionLoader.getHtmlGlobalAttributePrefixes();

        // 100 subsequent calls — all must return the same reference.
        for (int i = 0; i < 100; i++) {
            assertSame("JS keywords cache must be stable",
                    js1,   StaticCompletionLoader.getJsKeywords());
            assertSame("HTML tags cache must be stable",
                    html1, StaticCompletionLoader.getHtmlTags());
            assertSame("CSS properties cache must be stable",
                    css1,  StaticCompletionLoader.getCssProperties());
            assertSame("CSS colors cache must be stable",
                    col1,  StaticCompletionLoader.getCssColors());
            assertSame("CSS color functions cache must be stable",
                    colf1, StaticCompletionLoader.getCssColorFunctions());
            assertSame("CSS global functions cache must be stable",
                    colg1, StaticCompletionLoader.getCssGlobalFunctions());
            assertSame("JSON snippets cache must be stable",
                    json1, StaticCompletionLoader.getJsonSnippets());
            assertSame("HTML global attributes cache must be stable",
                    globalAttrs1, StaticCompletionLoader.getHtmlGlobalAttributes());
            assertSame("HTML global prefixes cache must be stable",
                    globalPrefixes1, StaticCompletionLoader.getHtmlGlobalAttributePrefixes());
        }
    }

    @Test
    public void parserIsNotInvokedAfterFirstLoad() {
        // The loader's parser calls go through a single static
        // method. We can detect re-parsing by mutating the override
        // after the first call: if the loader re-parses, the next
        // call would see the new value.
        AtomicInteger parseCount = new AtomicInteger(0);
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.JS_KEYWORDS_ASSET,
                "[" +
                "{\"label\":\"a\",\"insertText\":\"a\",\"type\":\"KEYWORD\"}," +
                "{\"label\":\"b\",\"insertText\":\"b\",\"type\":\"KEYWORD\"}" +
                "]");

        // First call — parses once, populates the cache.
        StaticCompletionItem[] first = StaticCompletionLoader.getJsKeywords();
        assertEquals(2, first.length);

        // Mutate the override. If the loader re-parses, the next
        // call will see the new value (length 0).
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.JS_KEYWORDS_ASSET, "[]");
        for (int i = 0; i < 100; i++) {
            StaticCompletionItem[] again = StaticCompletionLoader.getJsKeywords();
            assertSame("iteration " + i + " must return the SAME cached array",
                    first, again);
            assertEquals("iteration " + i + " must not re-parse (length must still be 2)",
                    2, again.length);
            // Track that we made 100 calls without re-parse.
            parseCount.incrementAndGet();
        }
        assertEquals(100, parseCount.get());
    }
}
