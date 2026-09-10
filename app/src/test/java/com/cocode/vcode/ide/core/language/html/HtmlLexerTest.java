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
        assertTrue(stream.isText(1));
    }

    @Test
    public void testScriptWithLessThanComparison() {
        String source = "<script>for (let i = 0; i < len; i++) {}</script>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        // <script>
        assertEquals(HtmlTokenStream.TK_TAG_OPEN, stream.types[0]);
        assertTrue(stream.isTagName(1));
        assertEquals(HtmlTokenStream.TK_TAG_CLOSE, stream.types[7]);
        
        // Inner script content with '<' must be TK_TEXT
        int ltPos = source.indexOf('<', 8);
        int closingScriptPos = source.indexOf("</script");
        assertTrue("Found '<' in for loop", ltPos < closingScriptPos);
        assertTrue("Inner '<' is text", stream.isText(ltPos));
        
        // </script>
        assertEquals(HtmlTokenStream.TK_TAG_OPEN, stream.types[closingScriptPos]);
        assertEquals(HtmlTokenStream.TK_TAG_OPEN, stream.types[closingScriptPos + 1]);
        assertTrue(stream.isTagName(closingScriptPos + 2));
    }

    @Test
    public void testStyleContent() {
        String source = "<style>div > p { color: red; }</style>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        int closingStylePos = source.indexOf("</style");
        for (int i = 7; i < closingStylePos; i++) {
            assertTrue("Style body char " + i + " must be text", stream.isText(i));
        }
        assertEquals(HtmlTokenStream.TK_TAG_OPEN, stream.types[closingStylePos]);
    }

    @Test
    public void testUnquotedAttributeWithSlash() {
        String source = "<a href=/test/page/index.html>link</a>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        
        int pathStart = source.indexOf("/test");
        int pathEnd = source.indexOf(">link");
        for (int i = pathStart; i < pathEnd; i++) {
            assertTrue("Path char " + i + " must be attr value", stream.isAttrValue(i));
        }
        assertEquals(HtmlTokenStream.TK_TAG_CLOSE, stream.types[pathEnd]);
    }

    @Test
    public void testRawTextClosingNeedleNotMatchedOnPrefix() {
        String source = "<script>var s = '</scripting>';</script>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);

        int fakeClose = source.indexOf("</scripting>");
        // The </scripting> should be treated as text, not a tag close
        assertTrue("Character '<' of </scripting> should remain text", stream.isText(fakeClose));

        int realClose = source.indexOf("</script>");
        assertEquals("Real </script> should be TK_TAG_OPEN", HtmlTokenStream.TK_TAG_OPEN, stream.types[realClose]);
        assertEquals("Real </script> slash should be TK_TAG_OPEN", HtmlTokenStream.TK_TAG_OPEN, stream.types[realClose + 1]);
        assertTrue("Real tag name 's' should be tag name", stream.isTagName(realClose + 2));
    }
}
