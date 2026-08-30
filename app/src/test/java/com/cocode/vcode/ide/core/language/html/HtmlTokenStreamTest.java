package com.cocode.vcode.ide.core.language.html;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlTokenStreamTest {

    @Test
    public void testTokenQueries() {
        // Hand-constructed mock array for testing query methods
        // Let's pretend we have a small tokenized fragment: <div class="a">
        // < (tag open), div (tag name), class (attr name), = (none), "a" (attr value), > (tag close)
        byte[] types = new byte[]{
            HtmlTokenStream.TK_TAG_OPEN,
            HtmlTokenStream.TK_TAG_NAME, HtmlTokenStream.TK_TAG_NAME, HtmlTokenStream.TK_TAG_NAME,
            HtmlTokenStream.TK_NONE, // 4 Let's use NONE
            HtmlTokenStream.TK_ATTR_NAME, HtmlTokenStream.TK_ATTR_NAME, HtmlTokenStream.TK_ATTR_NAME, HtmlTokenStream.TK_ATTR_NAME, HtmlTokenStream.TK_ATTR_NAME,
            HtmlTokenStream.TK_NONE, // 10 (=)
            HtmlTokenStream.TK_ATTR_VALUE, HtmlTokenStream.TK_ATTR_VALUE, HtmlTokenStream.TK_ATTR_VALUE,
            HtmlTokenStream.TK_TAG_CLOSE
        };
        
        int[] starts = new int[types.length]; // dummy starts array

        HtmlTokenStream stream = new HtmlTokenStream(types, starts);

        assertTrue(stream.isTagName(1));
        assertTrue(stream.isTagName(3));
        assertFalse(stream.isTagName(0));

        assertTrue(stream.isAttrName(5));
        assertTrue(stream.isAttrName(9));
        assertFalse(stream.isAttrName(4));

        assertTrue(stream.isAttrValue(11));
        assertTrue(stream.isAttrValue(13));
        assertFalse(stream.isAttrValue(10));

        // Test out of bounds
        assertFalse(stream.isTagName(-1));
        assertFalse(stream.isTagName(100));

        // Test masked
        assertTrue(stream.isMasked(12)); // ATTR_VALUE is masked
        assertFalse(stream.isMasked(1)); // TAG_NAME is not masked
    }

    @Test
    public void testAllTokenTypes() {
        byte[] types = new byte[]{
            HtmlTokenStream.TK_NONE,
            HtmlTokenStream.TK_TAG_OPEN,
            HtmlTokenStream.TK_TAG_CLOSE,
            HtmlTokenStream.TK_TAG_NAME,
            HtmlTokenStream.TK_ATTR_NAME,
            HtmlTokenStream.TK_ATTR_VALUE,
            HtmlTokenStream.TK_TEXT,
            HtmlTokenStream.TK_COMMENT,
            HtmlTokenStream.TK_DOCTYPE
        };
        int[] starts = new int[types.length];
        HtmlTokenStream stream = new HtmlTokenStream(types, starts);

        assertFalse(stream.isTagName(0));
        assertFalse(stream.isTagName(1));
        assertFalse(stream.isTagName(2));
        assertTrue(stream.isTagName(3));
        assertFalse(stream.isTagName(4));

        assertTrue(stream.isAttrName(4));
        assertTrue(stream.isAttrValue(5));
        assertTrue(stream.isText(6));
        assertTrue(stream.isComment(7));
        assertTrue(stream.isDoctype(8));

        assertTrue(stream.isMasked(5));
        assertTrue(stream.isMasked(7));
        assertFalse(stream.isMasked(6));
    }
}
