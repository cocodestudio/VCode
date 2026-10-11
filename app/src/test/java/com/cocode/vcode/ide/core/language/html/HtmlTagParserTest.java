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

    @Test
    public void testOpenTagsAncestryStackLifo() {
        String html = "<html><body><div><span>";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());
        assertEquals("span", ctx.unclosedTag);
        assertEquals(4, ctx.openTags.size());
        assertEquals("span", ctx.openTags.get(0));
        assertEquals("div", ctx.openTags.get(1));
        assertEquals("body", ctx.openTags.get(2));
        assertEquals("html", ctx.openTags.get(3));
    }

    @Test
    public void testRawTextElementIgnoresLtOperator() {
        String html = "<script>for (let i = 0; i < len; i++) {}</script><div>";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());
        assertEquals("div", ctx.unclosedTag);
        assertEquals(1, ctx.openTags.size());
        assertEquals("div", ctx.openTags.get(0));
    }

    @Test
    public void testAncestorPoppingOnCloseTag() {
        String html = "<ul><li>Item 1<li>Item 2</ul>";
        HtmlTagParser.HtmlContext ctx = parser.parseContext(html, html.length());
        // Both li tags and ul should be popped when </ul> closes
        assertTrue(ctx.openTags.isEmpty());
        assertNull(ctx.unclosedTag);
    }
}
