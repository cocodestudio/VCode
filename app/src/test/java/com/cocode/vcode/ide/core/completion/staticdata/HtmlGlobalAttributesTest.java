package com.cocode.vcode.ide.core.completion.staticdata;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.7: html_tags.json has globalAttributes
 * and globalAttributePrefixes at the top level.
 */
public class HtmlGlobalAttributesTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    private static String loadRealAsset() {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = HtmlGlobalAttributesTest.class.getResourceAsStream(
                "/html_tags.json")) {
            assertNotNull("test resource /html_tags.json must be present", is);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    @Test
    public void realAssetFileHasGlobalAttributes() {
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.HTML_TAGS_ASSET, loadRealAsset());
        String[] attrs = StaticCompletionLoader.getHtmlGlobalAttributes();
        Set<String> set = new HashSet<>();
        for (String a : attrs) set.add(a);
        assertTrue("id", set.contains("id"));
        assertTrue("class", set.contains("class"));
        assertTrue("style", set.contains("style"));
        assertTrue("title", set.contains("title"));
        assertTrue("dir", set.contains("dir"));
        assertTrue("lang", set.contains("lang"));
        assertTrue("tabindex", set.contains("tabindex"));
        assertTrue("hidden", set.contains("hidden"));
        assertTrue("draggable", set.contains("draggable"));
        assertTrue("contenteditable", set.contains("contenteditable"));
        assertTrue("spellcheck", set.contains("spellcheck"));
        assertTrue("translate", set.contains("translate"));
        assertTrue("role", set.contains("role"));
    }

    @Test
    public void realAssetFileHasGlobalAttributePrefixes() {
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.HTML_TAGS_ASSET, loadRealAsset());
        String[] prefixes = StaticCompletionLoader.getHtmlGlobalAttributePrefixes();
        System.err.println("=== prefixes test, len=" + prefixes.length);
        assertEquals(2, prefixes.length);
        assertEquals("data-", prefixes[0]);
        assertEquals("aria-", prefixes[1]);
    }

    @Test
    public void realAssetFileStillContainsAllTags() {
        // Back-compat: the loader still finds the same tag list as
        // before the global-attribute addition.
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.HTML_TAGS_ASSET, loadRealAsset());
        StaticCompletionItem[] tags = StaticCompletionLoader.getHtmlTags();
        // Spot-check a few well-known tags.
        Set<String> tagNames = new HashSet<>();
        for (StaticCompletionItem t : tags) tagNames.add(t.label);
        assertTrue("div must be present", tagNames.contains("div"));
        assertTrue("html must be present", tagNames.contains("html"));
        assertTrue("script must be present", tagNames.contains("script"));
        assertTrue("img must be present", tagNames.contains("img"));
    }
}
