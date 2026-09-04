package com.cocode.vcode.ide.core.language.css;

import org.junit.Test;
import static org.junit.Assert.*;

public class CssParserTest {

    @Test
    public void testBasicRule() {
        String source = ".class { color: red; }";
        CssTokenStream stream = CssLexer.tokenize(source);
        CssSyntaxTree tree = CssParser.parse(stream, source);
        
        // Tree should have: 
        // 0: root
        // 1: N_RULE (0-22)
        // 2: N_SELECTOR (.class)
        // 3: N_DECLARATION (9-20)
        // 4: N_PROPERTY (color)
        // 5: N_VALUE (red)
        assertEquals(6, tree.nodeCount);
        
        int rule = 1;
        assertEquals(CssSyntaxTree.N_RULE, tree.nodeType[rule]);
        assertEquals(0, tree.nodeStart[rule]);
        assertEquals(22, tree.nodeEnd[rule]);
        
        int selector = tree.nodeChild[rule];
        assertEquals(CssSyntaxTree.N_SELECTOR, tree.nodeType[selector]);
        assertEquals(".class", tree.nodeName[selector]);
        
        int decl = tree.nodeSibling[selector];
        assertEquals(CssSyntaxTree.N_DECLARATION, tree.nodeType[decl]);
        
        int prop = tree.nodeChild[decl];
        assertEquals(CssSyntaxTree.N_PROPERTY, tree.nodeType[prop]);
        assertEquals("color", tree.nodeName[prop]);
        
        int val = tree.nodeSibling[prop];
        assertEquals(CssSyntaxTree.N_VALUE, tree.nodeType[val]);
        assertEquals("red", tree.nodeValue[val]);
    }

    @Test
    public void testAtRuleAndNesting() {
        String source = "@media screen { .c { color: red; } }";
        CssTokenStream stream = CssLexer.tokenize(source);
        CssSyntaxTree tree = CssParser.parse(stream, source);
        
        // 1: N_AT_RULE
        // 2: N_SELECTOR (@media screen)
        // 3: N_RULE
        // 4: N_SELECTOR (.c)
        // 5: N_DECLARATION
        // 6: N_PROPERTY (color)
        // 7: N_VALUE (red)
        assertEquals(8, tree.nodeCount);
        
        int atRule = 1;
        assertEquals(CssSyntaxTree.N_AT_RULE, tree.nodeType[atRule]);
        
        int atSel = tree.nodeChild[atRule];
        assertEquals(CssSyntaxTree.N_SELECTOR, tree.nodeType[atSel]);
        assertEquals("@media screen", tree.nodeName[atSel]);
        
        int innerRule = tree.nodeSibling[atSel];
        assertEquals(CssSyntaxTree.N_RULE, tree.nodeType[innerRule]);
        
        int innerSel = tree.nodeChild[innerRule];
        assertEquals(CssSyntaxTree.N_SELECTOR, tree.nodeType[innerSel]);
        assertEquals(".c", tree.nodeName[innerSel]);
    }

    @Test
    public void testBlocklessAtRule() {
        String source = "@import url(\"foo\");";
        CssTokenStream stream = CssLexer.tokenize(source);
        CssSyntaxTree tree = CssParser.parse(stream, source);
        
        assertEquals(3, tree.nodeCount);
        int atRule = 1;
        assertEquals(CssSyntaxTree.N_AT_RULE, tree.nodeType[atRule]);
        assertEquals(19, tree.nodeEnd[atRule]);
        
        int sel = tree.nodeChild[atRule];
        assertEquals(CssSyntaxTree.N_SELECTOR, tree.nodeType[sel]);
        assertEquals("@import url(\"foo\")", tree.nodeName[sel]);
    }

    @Test
    public void testMalformedRecovery() {
        String source = ".class { color red; margin: 0; }";
        CssTokenStream stream = CssLexer.tokenize(source);
        CssSyntaxTree tree = CssParser.parse(stream, source);
        
        // 1: N_RULE (.class)
        // 2: N_SELECTOR (.class)
        // 3: N_ERROR (color red;)
        // 4: N_DECLARATION (margin: 0;)
        // 5: N_PROPERTY (margin)
        // 6: N_VALUE (0)
        
        assertEquals(7, tree.nodeCount);
        
        int rule = 1;
        assertEquals(CssSyntaxTree.N_RULE, tree.nodeType[rule]);
        
        int sel = tree.nodeChild[rule];
        assertEquals(CssSyntaxTree.N_SELECTOR, tree.nodeType[sel]);
        assertEquals(".class", tree.nodeName[sel]);
        
        int errNode = tree.nodeSibling[sel];
        assertEquals(CssSyntaxTree.N_ERROR, tree.nodeType[errNode]);
        assertTrue(tree.nodeName[errNode].contains("Missing ':'"));
        
        int goodDecl = tree.nodeSibling[errNode];
        assertEquals(CssSyntaxTree.N_DECLARATION, tree.nodeType[goodDecl]);
        
        int prop = tree.nodeChild[goodDecl];
        assertEquals("margin", tree.nodeName[prop]);
    }
}
