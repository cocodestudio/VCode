package com.cocode.vcode.ide.core.language.html;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlLexerTest {

    @Test
    public void testBasicTag() {
        String source = "<div class=\"foo\" id=bar>hello</div>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        // <div
        assertTrue(stream.isTagName(1));
        assertTrue(stream.isTagName(2));
        assertTrue(stream.isTagName(3));
        
        // class
        assertTrue(stream.isAttrName(5));
        assertTrue(stream.isAttrName(9));
        
        // ="foo"
        assertFalse(stream.isAttrName(10)); // =
        assertTrue(stream.isAttrValue(11)); // "
        assertTrue(stream.isAttrValue(15)); // "
        
        // id=bar
        assertTrue(stream.isAttrName(17)); // i
        assertTrue(stream.isAttrName(18)); // d
        assertTrue(stream.isAttrValue(20)); // b
        assertTrue(stream.isAttrValue(22)); // r
        
        // >
        assertEquals(HtmlTokenStream.TK_TAG_CLOSE, stream.types[23]);
        
        // hello
        assertTrue(stream.isText(24));
        assertTrue(stream.isText(28));
        
        // </div>
        assertEquals(HtmlTokenStream.TK_TAG_OPEN, stream.types[29]);
        assertEquals(HtmlTokenStream.TK_TAG_OPEN, stream.types[30]); // </
        assertTrue(stream.isTagName(31));
        assertEquals(HtmlTokenStream.TK_TAG_CLOSE, stream.types[34]);
    }
    
    @Test
    public void testSelfClosing() {
        String source = "<img src='foo.png' />";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        assertTrue(stream.isTagName(1));
        assertTrue(stream.isTagName(3));
        
        assertTrue(stream.isAttrName(5));
        assertTrue(stream.isAttrValue(9)); // '
        assertTrue(stream.isAttrValue(17)); // '
        
        assertEquals(HtmlTokenStream.TK_TAG_CLOSE, stream.types[19]); // /
        assertEquals(HtmlTokenStream.TK_TAG_CLOSE, stream.types[20]); // >
    }

    @Test
    public void testComments() {
        String source = "<!-- comment -->";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        assertTrue(stream.isComment(0));
        assertTrue(stream.isComment(3));
        assertTrue(stream.isComment(10));
        assertTrue(stream.isComment(15)); // >
    }

    @Test
    public void testDoctype() {
        String source = "<!DOCTYPE html>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        assertTrue(stream.isDoctype(0));
        assertTrue(stream.isDoctype(14));
    }
    
    @Test
    public void testUnterminatedComment() {
        String source = "<!-- under construction";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        assertTrue(stream.isComment(0));
        assertTrue(stream.isComment(source.length() - 1));
    }
    
    @Test
    public void testMalformedTag() {
        String source = "< div >";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        // < space div > -> should fall back to text? 
        // Based on my implementation: '<', then ' ' -> invalid tag, back to text.
        assertTrue(stream.isText(1));
    }
}
