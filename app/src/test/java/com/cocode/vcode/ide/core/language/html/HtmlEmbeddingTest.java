package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HtmlEmbeddingTest {

    @Test
    public void testScriptBlocks() {
        String html = "<html>\n" +
                "<script>let x = 1;</script>\n" +
                "<body></body>\n" +
                "<script>let y = 2;</script>\n" +
                "</html>";
                
        HtmlTokenStream tokens = HtmlLexer.tokenize(html);
        ParseResult result = HtmlParser.parse(html, tokens);
        
        assertNotNull(result);
        assertNotNull(result.embeddedResults);
        assertEquals(2, result.embeddedResults.size());
        
        ParseResult.EmbeddedResult e1 = result.embeddedResults.get(0);
        ParseResult.EmbeddedResult e2 = result.embeddedResults.get(1);
        
        assertNotNull(e1.result.tree);
        assertNotNull(e2.result.tree);
        
        JsSyntaxTree t1 = e1.result.tree;
        JsSyntaxTree t2 = e2.result.tree;
        
        // Ensure they are independent trees
        assertTrue(t1.nodeCount > 0);
        assertTrue(t2.nodeCount > 0);
        
        // Ensure whole-file relative offsets
        int start1 = html.indexOf("let x = 1;");
        assertEquals(start1, e1.startOffset);
        
        int start2 = html.indexOf("let y = 2;");
        assertEquals(start2, e2.startOffset);
    }
    
    @Test
    public void testStyleBlocks() {
        String html = "<style>body { color: red; }</style>";
        
        HtmlTokenStream tokens = HtmlLexer.tokenize(html);
        ParseResult result = HtmlParser.parse(html, tokens);
        
        assertEquals(1, result.embeddedResults.size());
        ParseResult.EmbeddedResult e1 = result.embeddedResults.get(0);
        
        assertNotNull(e1.result.cssTree);
        assertTrue(e1.result.cssTree.nodeCount > 0);
        
        int start1 = html.indexOf("body { color: red; }");
        assertEquals(start1, e1.startOffset);
    }
    
    @Test
    public void testInlineStyleAttribute() {
        String html = "<div style=\"color: red; margin: 0;\"></div>";
        
        HtmlTokenStream tokens = HtmlLexer.tokenize(html);
        ParseResult result = HtmlParser.parse(html, tokens);
        
        assertEquals(1, result.embeddedResults.size());
        ParseResult.EmbeddedResult e1 = result.embeddedResults.get(0);
        
        assertNotNull(e1.result.cssTree);
        CssSyntaxTree cssTree = e1.result.cssTree;
        assertTrue(cssTree.nodeCount > 0);
        
        int start1 = html.indexOf("color: red; margin: 0;");
        assertEquals(start1, e1.startOffset);
        
        // Ensure it produced declarations (not full rules)
        boolean hasDecl = false;
        for (int i = 1; i < cssTree.nodeCount; i++) {
            if (cssTree.nodeType[i] == CssSyntaxTree.N_DECLARATION) {
                hasDecl = true;
                break;
            }
        }
        assertTrue("Should contain declaration nodes", hasDecl);
    }
    
    @Test
    public void testInlineEventHandlerAttribute() {
        String html = "<button onclick=\"foo(); bar();\">Click</button>";
        
        HtmlTokenStream tokens = HtmlLexer.tokenize(html);
        ParseResult result = HtmlParser.parse(html, tokens);
        
        assertEquals(1, result.embeddedResults.size());
        ParseResult.EmbeddedResult e1 = result.embeddedResults.get(0);
        
        assertNotNull(e1.result.tree);
        JsSyntaxTree jsTree = e1.result.tree;
        assertTrue(jsTree.nodeCount > 0);
        
        int start1 = html.indexOf("foo(); bar();");
        assertEquals(start1, e1.startOffset);
        
        // Ensure it produced statements
        int statementCount = 0;
        int child = jsTree.nodeChild[0]; // root children
        while (child != 0) {
            statementCount++;
            child = jsTree.nodeSibling[child];
        }
        assertEquals("Should produce two statement nodes", 2, statementCount);
    }
    
    @Test
    public void testMixedLanguageFile() {
        String html = "<html>\n" +
                "<style>body{margin:0}</style>\n" +
                "<body>\n" +
                "<div style=\"color:red\" onclick=\"doStuff()\"></div>\n" +
                "</body>\n" +
                "<script>console.log('hi');</script>\n" +
                "</html>";
                
        HtmlTokenStream tokens = HtmlLexer.tokenize(html);
        ParseResult result = HtmlParser.parse(html, tokens);
        
        assertEquals(4, result.embeddedResults.size());
    }
}
