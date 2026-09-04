package com.cocode.vcode.ide.core.language.svg;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SvgAutoCompleteEngineTest {

    @Test
    public void testSvgTagSuggestions() {
        SvgAutoCompleteEngine engine = new SvgAutoCompleteEngine(null);
        String text = "<c";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasCircle = false;
        for (CompletionItem item : items) {
            if ("circle".equals(item.getLabel())) {
                hasCircle = true;
                break;
            }
        }
        assertTrue("Expected 'circle' element suggestion after '<c'", hasCircle);
    }

    @Test
    public void testSvgAttributeSuggestions() {
        SvgAutoCompleteEngine engine = new SvgAutoCompleteEngine(null);
        String text = "<svg view";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasViewBox = false;
        for (CompletionItem item : items) {
            if ("viewBox".equals(item.getLabel())) {
                hasViewBox = true;
                break;
            }
        }
        assertTrue("Expected 'viewBox' attribute suggestion on <svg>", hasViewBox);
    }

    @Test
    public void testSvgAttributeValueSuggestions() {
        SvgAutoCompleteEngine engine = new SvgAutoCompleteEngine(null);
        String text = "<svg fill=\"";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasNone = false;
        for (CompletionItem item : items) {
            if ("none".equals(item.getLabel())) {
                hasNone = true;
                break;
            }
        }
        assertTrue("Expected 'none' value suggestion for fill attribute", hasNone);
    }

    @Test
    public void testSvgCloseTagReplacement() {
        SvgAutoCompleteEngine engine = new SvgAutoCompleteEngine(null);
        String text = "<svg><g></";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasCloseG = false;
        for (CompletionItem item : items) {
            if ("</g>".equals(item.getLabel())) {
                hasCloseG = true;
                // Replace length should cover the '</' (2 chars)
                assertEquals(2, item.getReplaceLength());
                break;
            }
        }
        assertTrue("Expected '</g>' close tag suggestion", hasCloseG);
    }

    @Test
    public void testSvgCommentSuppression() {
        SvgAutoCompleteEngine engine = new SvgAutoCompleteEngine(null);
        String text = "<svg><!-- comment ";
        List<CompletionItem> items = engine.getSuggestions(text, text.length());
        assertNotNull(items);
        assertTrue("Expected empty suggestions inside SVG comment", items.isEmpty());
    }
}
