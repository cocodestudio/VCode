package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying TwoTierIndex direct bucket indexing, binary search,
 * and fuzzy abbreviation fallback.
 */
public class TwoTierIndexTest {

    @Test
    public void emptyIndexReturnsEmptyResults() {
        TwoTierIndex index = new TwoTierIndex(new ArrayList<>());
        assertEquals(0, index.size());
        assertTrue(index.filter("test", 10).isEmpty());
        assertNull(index.getExact("test"));
    }

    @Test
    public void prefixLookupUsesTierOneDirectBucket() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("div", "<div>|</div>", "Container", CompletionItem.Type.TAG));
        items.add(new CompletionItem("dialog", "<dialog>|</dialog>", "Dialog", CompletionItem.Type.TAG));
        items.add(new CompletionItem("datalist", "<datalist>|</datalist>", "List", CompletionItem.Type.TAG));
        items.add(new CompletionItem("span", "<span>|</span>", "Inline", CompletionItem.Type.TAG));
        items.add(new CompletionItem("section", "<section>|</section>", "Section", CompletionItem.Type.TAG));

        TwoTierIndex index = new TwoTierIndex(items);
        assertEquals(5, index.size());

        List<CompletionItem> diResults = index.filter("di", 10);
        assertEquals(2, diResults.size());
        assertEquals("dialog", diResults.get(0).getLabel());
        assertEquals("div", diResults.get(1).getLabel());

        List<CompletionItem> sResults = index.filter("s", 10);
        assertEquals(2, sResults.size());
        assertEquals("section", sResults.get(0).getLabel());
        assertEquals("span", sResults.get(1).getLabel());
    }

    @Test
    public void fuzzyAbbreviationFallbackFindsSubsequenceMatches() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("background-color", "background-color: |;", "", CompletionItem.Type.CSS_PROPERTY));
        items.add(new CompletionItem("background-clip", "background-clip: |;", "", CompletionItem.Type.CSS_PROPERTY));
        items.add(new CompletionItem("border-radius", "border-radius: |;", "", CompletionItem.Type.CSS_PROPERTY));
        items.add(new CompletionItem("display", "display: |;", "", CompletionItem.Type.CSS_PROPERTY));

        TwoTierIndex index = new TwoTierIndex(items);

        // "bgc" does not match any prefix directly, so Tier 2 triggers
        List<CompletionItem> bgcResults = index.filter("bgc", 5);
        assertFalse(bgcResults.isEmpty());
        boolean hasBgClip = false;
        boolean hasBgColor = false;
        for (CompletionItem item : bgcResults) {
            if ("background-clip".equals(item.getLabel())) hasBgClip = true;
            if ("background-color".equals(item.getLabel())) hasBgColor = true;
        }
        assertTrue("Expected background-clip or background-color for 'bgc'", hasBgClip || hasBgColor);

        // "br" should match border-radius
        List<CompletionItem> brResults = index.filter("br", 5);
        assertFalse(brResults.isEmpty());
        assertEquals("border-radius", brResults.get(0).getLabel());
    }

    @Test
    public void getExactLookupMatchesCaseInsensitively() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("color", "color", "", CompletionItem.Type.CSS_PROPERTY));
        items.add(new CompletionItem("display", "display", "", CompletionItem.Type.CSS_PROPERTY));

        TwoTierIndex index = new TwoTierIndex(items);
        assertNotNull(index.getExact("color"));
        assertNotNull(index.getExact("COLOR"));
        assertNotNull(index.getExact("Display"));
        assertNull(index.getExact("margin"));
    }

    @Test
    public void emptyPrefixReturnsTopSortedItems() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("z-index", "z-index", "", CompletionItem.Type.CSS_PROPERTY));
        items.add(new CompletionItem("align-items", "align-items", "", CompletionItem.Type.CSS_PROPERTY));
        items.add(new CompletionItem("color", "color", "", CompletionItem.Type.CSS_PROPERTY));

        TwoTierIndex index = new TwoTierIndex(items);
        List<CompletionItem> topTwo = index.filter("", 2);
        assertEquals(2, topTwo.size());
        assertEquals("align-items", topTwo.get(0).getLabel());
        assertEquals("color", topTwo.get(1).getLabel());
    }
}
