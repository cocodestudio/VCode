package com.cocode.vcode.ide.core.diagnostic.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenMaskTest {

    @Test
    public void testHtmlComments() {
        String html = "<div><!-- comment --></div>";
        TokenMask mask = TokenMask.build(html, "html");

        int openDivEnd = html.indexOf(">") + 1;
        int commentStart = html.indexOf("<!--");
        int commentEnd = html.indexOf("-->") + 2; // index of '>'
        int closeDivStart = html.indexOf("</");

        assertFalse(mask.inComment[openDivEnd - 1]);
        assertTrue(mask.inComment[commentStart]);
        assertTrue(mask.inComment[commentEnd]);
        assertFalse(mask.inComment[closeDivStart]);
    }

    @Test
    public void testJsComments() {
        String js = "let x = 1; // line comment\nlet y = 2; /* block comment */ let z;";
        TokenMask mask = TokenMask.build(js, "js");

        int lineCommentStart = js.indexOf("//");
        int lineCommentEnd = js.indexOf("\n") - 1; // 't' in comment
        int newline = js.indexOf("\n");

        assertFalse(mask.inComment[lineCommentStart - 1]);
        assertTrue(mask.inComment[lineCommentStart]);
        assertTrue(mask.inComment[lineCommentEnd]);
        assertFalse(mask.inComment[newline]);

        int blockCommentStart = js.indexOf("/*");
        int blockCommentEnd = js.indexOf("*/") + 1; // '/'
        int afterBlockComment = blockCommentEnd + 1;

        assertTrue(mask.inComment[blockCommentStart]);
        assertTrue(mask.inComment[blockCommentEnd]);
        assertFalse(mask.inComment[afterBlockComment]);
    }

    @Test
    public void testJsStrings() {
        String js = "let s1 = 'single'; let s2 = \"double\"; let s3 = `temp`;";
        TokenMask mask = TokenMask.build(js, "js");

        int singleStart = js.indexOf("'");
        int singleEnd = js.indexOf("'", singleStart + 1);
        assertTrue(mask.inString[singleStart]);
        assertTrue(mask.inString[singleStart + 1]);
        assertTrue(mask.inString[singleEnd]);

        int doubleStart = js.indexOf("\"");
        int doubleEnd = js.indexOf("\"", doubleStart + 1);
        assertTrue(mask.inString[doubleStart]);
        assertTrue(mask.inString[doubleEnd]);
        
        int tempStart = js.indexOf("`");
        int tempEnd = js.indexOf("`", tempStart + 1);
        assertTrue(mask.inString[tempStart]);
        assertTrue(mask.inString[tempEnd]);
    }

    @Test
    public void testJsRegex() {
        String js = "let r = /abc/g;";
        TokenMask mask = TokenMask.build(js, "js");

        int regexStart = js.indexOf("/");
        int regexEnd = js.indexOf("/", regexStart + 1);
        
        assertTrue(mask.inRegex[regexStart]);
        assertTrue(mask.inRegex[regexStart + 1]);
        assertTrue(mask.inRegex[regexEnd]);
    }
}
