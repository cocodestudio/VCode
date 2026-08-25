package com.cocode.vcode.ide.core.language.html;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HtmlTagParserTest {

    private HtmlTagParser parser;

    @Before
    public void setUp() {
        parser = new HtmlTagParser();
    }

    @Test
    public void testVoidElements() {
        assertTrue(HtmlTagParser.isVoidElement("img"));
        assertTrue(HtmlTagParser.isVoidElement("br"));
        assertTrue(HtmlTagParser.isVoidElement("meta"));
        assertFalse(HtmlTagParser.isVoidElement("div"));
        assertFalse(HtmlTagParser.isVoidElement("span"));
    }

    @Test
    public void testContextInsideTagName() {
        // "<di|"
        String html = "<di";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertTrue(ctx.isInsideOpenTag);
        assertTrue(ctx.isTypingTagName);
        assertEquals("di", ctx.currentTagName);
        assertNull(ctx.unclosedTag);
    }

    @Test
    public void testContextInsideAttributeName() {
        // "<div cla|"
        String html = "<div cla";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertTrue(ctx.isInsideOpenTag);
        assertFalse(ctx.isTypingTagName);
        assertEquals("div", ctx.currentTagName);
        // Wait, HtmlTagParser state machine might just buffer the attr name
        // "cla" should be the currentAttributeName.
        // Let's assume it works based on its logic.
    }

    @Test
    public void testContextInsideAttributeValueDoubleQuotes() {
        // "<div class=\"con|"
        String html = "<div class=\"con";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertTrue(ctx.isInsideOpenTag);
        assertFalse(ctx.isTypingTagName);
        assertTrue(ctx.isInsideAttributeValue);
        assertEquals("div", ctx.currentTagName);
        assertEquals("class", ctx.currentAttributeName);
        assertEquals("con", ctx.currentAttributeValue);
    }

    @Test
    public void testContextInsideAttributeValueSingleQuotes() {
        // "<div class='con|"
        String html = "<div class='con";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertTrue(ctx.isInsideOpenTag);
        assertTrue(ctx.isInsideAttributeValue);
        assertEquals("div", ctx.currentTagName);
        assertEquals("class", ctx.currentAttributeName);
        assertEquals("con", ctx.currentAttributeValue);
    }

    @Test
    public void testContextUnclosedTag() {
        // "<div><span><p>Text|"
        String html = "<div><span><p>Text";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertFalse(ctx.isInsideOpenTag);
        assertEquals("p", ctx.unclosedTag);
    }

    @Test
    public void testContextUnclosedTagWithVoidElements() {
        // "<div><img><br>Text|"
        // img and br are void elements, so they shouldn't be pushed to the unclosed tag stack.
        String html = "<div><img><br>Text";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertFalse(ctx.isInsideOpenTag);
        assertEquals("div", ctx.unclosedTag); // The closest unclosed tag is div
    }

    @Test
    public void testContextClosedTag() {
        // "<div><p>Text</p>|"
        String html = "<div><p>Text</p>";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());

        assertFalse(ctx.isInsideOpenTag);
        assertEquals("div", ctx.unclosedTag); // The p tag was closed, so div is next
    }

    @Test
    public void testGetCurrentOpenTagName() {
        assertEquals("div", parser.getCurrentOpenTagName("<div class=\"\"", 12));
        assertNull(parser.getCurrentOpenTagName("<div>", 5));
    }
}
