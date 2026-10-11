package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying StaticCompletionLoader.warmUp() pre-populates all
 * in-memory registries, TwoTierIndexes, and maps.
 */
public class StaticCompletionWarmUpTest {

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    @Test
    public void warmUpPrepopulatesAllRegistriesAndIndexes() {
        StaticCompletionLoader.warmUp();

        // HTML
        List<CompletionItem> htmlTags = StaticCompletionLoader.getHtmlTagCompletionItems();
        assertNotNull(htmlTags);
        assertFalse(htmlTags.isEmpty());
        TwoTierIndex htmlTagIndex = StaticCompletionLoader.getHtmlTagIndex();
        assertNotNull(htmlTagIndex);
        assertTrue(htmlTagIndex.size() > 0);
        Map<String, List<CompletionItem>> htmlAttrs = StaticCompletionLoader.getHtmlTagAttributeMap();
        assertNotNull(htmlAttrs);
        assertFalse(htmlAttrs.isEmpty());
        String boilerplate = StaticCompletionLoader.getHtmlBoilerplate();
        assertNotNull(boilerplate);

        // CSS
        List<CompletionItem> cssProps = StaticCompletionLoader.getCssPropertyCompletionItems();
        assertNotNull(cssProps);
        assertFalse(cssProps.isEmpty());
        TwoTierIndex cssPropIndex = StaticCompletionLoader.getCssPropertyIndex();
        assertNotNull(cssPropIndex);
        assertTrue(cssPropIndex.size() > 0);
        Map<String, List<String>> cssVals = StaticCompletionLoader.getCssPropertyValueMap();
        assertNotNull(cssVals);
        assertFalse(cssVals.isEmpty());
        List<CompletionItem> cssTags = StaticCompletionLoader.getCssHtmlTagCompletionItems();
        assertNotNull(cssTags);
        assertFalse(cssTags.isEmpty());
        TwoTierIndex cssTagIndex = StaticCompletionLoader.getCssHtmlTagIndex();
        assertNotNull(cssTagIndex);
        assertTrue(cssTagIndex.size() > 0);
        List<CompletionItem> cssColors = StaticCompletionLoader.getCssColorCompletionItems();
        assertNotNull(cssColors);
        assertFalse(cssColors.isEmpty());
        TwoTierIndex cssColorIndex = StaticCompletionLoader.getCssColorIndex();
        assertNotNull(cssColorIndex);
        assertTrue(cssColorIndex.size() > 0);
        List<CompletionItem> cssFuncs = StaticCompletionLoader.getCssGlobalFunctionItems();
        assertNotNull(cssFuncs);
        assertFalse(cssFuncs.isEmpty());

        // JS
        List<CompletionItem> jsKeywords = StaticCompletionLoader.getJsKeywordCompletionItems();
        assertNotNull(jsKeywords);
        assertFalse(jsKeywords.isEmpty());
        TwoTierIndex jsIndex = StaticCompletionLoader.getJsKeywordIndex();
        assertNotNull(jsIndex);
        assertTrue(jsIndex.size() > 0);

        // JSON
        List<CompletionItem> jsonSnippets = StaticCompletionLoader.getJsonSnippetCompletionItems();
        assertNotNull(jsonSnippets);
        assertFalse(jsonSnippets.isEmpty());
        TwoTierIndex jsonIndex = StaticCompletionLoader.getJsonSnippetIndex();
        assertNotNull(jsonIndex);
        assertTrue(jsonIndex.size() > 0);

        // Subsequent queries return identical cached reference
        assertSame(htmlTags, StaticCompletionLoader.getHtmlTagCompletionItems());
        assertSame(htmlTagIndex, StaticCompletionLoader.getHtmlTagIndex());
        assertSame(cssProps, StaticCompletionLoader.getCssPropertyCompletionItems());
        assertSame(cssPropIndex, StaticCompletionLoader.getCssPropertyIndex());
        assertSame(jsKeywords, StaticCompletionLoader.getJsKeywordCompletionItems());
        assertSame(jsIndex, StaticCompletionLoader.getJsKeywordIndex());
        assertSame(jsonSnippets, StaticCompletionLoader.getJsonSnippetCompletionItems());
        assertSame(jsonIndex, StaticCompletionLoader.getJsonSnippetIndex());
    }
}
