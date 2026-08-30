package com.cocode.vcode.ide.core.language.html;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlParserTest {

    @Test
    public void testValidNesting() {
        String source = "<div><span class=\"foo\">hello</span><br></div>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        HtmlSyntaxTree tree = HtmlParser.parse(source, stream).htmlTree;
        
        // Let's count:
        // 0: root
        // 1: div
        // 2: span
        // 3: attr(class)
        // 4: text(hello)
        // 5: br
        assertEquals(6, tree.nodeCount);
        
        int div = 1;
        int span = 2;
        int attr = 3;
        int text = 4;
        int br = 5;
        
        assertEquals("div", tree.nodeName[div]);
        assertEquals("span", tree.nodeName[span]);
        assertEquals("class", tree.nodeName[attr]);
        assertEquals("\"foo\"", tree.nodeValue[attr]);
        assertEquals("br", tree.nodeName[br]);
        
        // Check hierarchy
        assertEquals(0, tree.nodeParent[div]);
        assertEquals(div, tree.nodeParent[span]);
        assertEquals(div, tree.nodeParent[br]);
        assertEquals(span, tree.nodeParent[attr]);
        assertEquals(span, tree.nodeParent[text]);
        
        assertEquals(source.length(), tree.nodeEnd[div]);
    }

    @Test
    public void testUnclosedTagRecovery() {
        // Missing > on the div, should recover at the next <
        String source = "<div class=\"unclosed\" <span>hello</span>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        HtmlSyntaxTree tree = HtmlParser.parse(source, stream).htmlTree;
        
        int div = 1;
        int attr = 2;
        int span = 3;
        
        assertEquals("div", tree.nodeName[div]);
        assertEquals("class", tree.nodeName[attr]);
        assertEquals("span", tree.nodeName[span]);
        
        // span should be a child of div, because div was opened, just malformed
        assertEquals(div, tree.nodeParent[span]);
    }

    @Test
    public void testUnmatchedClosingTag() {
        String source = "<div></span></div>";
        HtmlTokenStream stream = HtmlLexer.tokenize(source);
        HtmlSyntaxTree tree = HtmlParser.parse(source, stream).htmlTree;
        
        int div = 1;
        int err = 2;
        
        assertEquals("div", tree.nodeName[div]);
        assertEquals(HtmlSyntaxTree.N_ERROR, tree.nodeType[err]);
        assertEquals("span", tree.nodeName[err]);
        assertEquals(div, tree.nodeParent[err]); // The error node is inside the div
        assertEquals(source.length(), tree.nodeEnd[div]);
    }
}
